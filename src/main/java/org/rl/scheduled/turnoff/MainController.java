package org.rl.scheduled.turnoff;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.CronExpression;
import org.quartz.SchedulerException;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class MainController {
	interface CLibrary extends Library {
		CLibrary INSTANCE = (CLibrary) Native.loadLibrary("c", CLibrary.class);

		int getpid();
	}

	static {
		System.setProperty("logDirectory", System.class.getResource("/").getPath());
	}

	/**
	 * LOGGER should come always after static block, as it sets the correct current directory for logger configuration.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	public static void main(String[] args) {
		SignalHandler gracefulExitHandler = (sig) -> {
			LOGGER.info("Exiting.");
			System.exit(0); //Sets correct exit code and exits the jvm.
		};

		Signal.handle(new Signal("INT"), gracefulExitHandler);
		Signal.handle(new Signal("TERM"), gracefulExitHandler);

		System.setProperty("org.quartz.threadPool.threadCount", "1");
		new MainController().start();
	}

	private final Properties properties = new Properties();
	private Instant currentShutdownInstant;
	private Instant currentStartupInstant;

	public void start() {
		checkRootPermission();

		if (configureProperties()) {
			configureScheduling();
		}
	}

	private boolean configureProperties() {
		String propertiesFileName = "cron-times.properties";
		final Path propertiesPath = Paths.get(MainController.class.getResource("/").getFile())
		                                 .resolve(propertiesFileName);

		boolean propertiesConfigured = false;
		if (Files.exists(propertiesPath)) {
			loadProperties(propertiesPath);
			propertiesConfigured = true;
		} else {
			LOGGER.warn("Properties file not found");
		}

		new Thread(() -> {
			Path watchedDir = propertiesPath.getParent();
			try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
				WatchKey registerKey = watchedDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY,
				                                           StandardWatchEventKinds.ENTRY_CREATE);
				while (true) {
					try {
						WatchKey eventKey = watchService.take();
						for (WatchEvent<?> watchEvent : eventKey.pollEvents()) {
							Path path = (Path) watchEvent.context();
							if (propertiesPath.equals(watchedDir.resolve(path))) {
								loadProperties(propertiesPath);
								configureScheduling();
							}
						}
					} catch (InterruptedException e) {
						LOGGER.warn("Interrupted while watching path for changes.", e);
					}
					registerKey.reset();
				}

			} catch (IOException e) {
				LOGGER.error("Exception while configuring watch service for properties file. " +
						             "Properties reloading will not function", e);
			}
		}).start();

		return propertiesConfigured;
	}

	private void loadProperties(Path propertiesPath) {
		try (InputStream is = Files.newInputStream(propertiesPath)) {
			properties.load(is);

		} catch (IOException e) {
			LOGGER.error("Failed loading properties file.", e);
		}
	}

	private void configureScheduling() {
		schedulePowerOff();
		schedulePowerOn();
	}


	private void schedulePowerOff() {
		try {
			Instant powerOffInstant = getMostRecentInstant(properties.getProperty("shutdown.cron"), Instant.now());
			if (currentShutdownInstant != null && currentShutdownInstant.equals(powerOffInstant)) {
				return;
			}

			QuartzController.reschedulePowerOffJob(powerOffInstant);
			currentShutdownInstant = powerOffInstant;

		} catch (ParseException e) {
			LOGGER.warn("Could not parse the given cron expression for shutdown.", e);
		} catch (SchedulerException e) {
			LOGGER.error("Failed while configuring cron for shutdown.", e);
		}
	}

	private void schedulePowerOn() {
		try {
			Instant referenceInstant = currentShutdownInstant;
			if (referenceInstant == null) {
				LOGGER.warn("Shutdown time not set!");
				referenceInstant = Instant.now();
			}

			Instant startupInstant = getMostRecentInstant(properties.getProperty("startup.cron"), referenceInstant);

			if (currentStartupInstant != null && currentStartupInstant.equals(startupInstant)) {
				return;
			}

			long turnOnEpoch = startupInstant.getEpochSecond();
			LOGGER.info("Setting next startup to {} -> {} ", startupInstant.atZone(ZoneId.systemDefault()),
			            turnOnEpoch);

			try (Writer wakeAlarmStream = Files.newBufferedWriter(Paths.get("/sys/class/rtc/rtc0/wakealarm"))) {
				wakeAlarmStream.write("0\n");
			}
			try (Writer wakeAlarmStream = Files.newBufferedWriter(Paths.get("/sys/class/rtc/rtc0/wakealarm"))) {
				wakeAlarmStream.write(turnOnEpoch + "\n");
			}
			currentStartupInstant = startupInstant;

		} catch (ParseException e) {
			LOGGER.warn("Could not parse cron expression for startup.cron", e);
		} catch (IOException e) {
			LOGGER.error("Failed while configuring next startup time.", e);
		}
	}


	private void checkRootPermission() {
		String userName = System.getProperty("user.name");
		LOGGER.debug("Current user: {}, pid: {}", userName, CLibrary.INSTANCE.getpid());

		if (!"root".equals(userName)) {
			try {
				List<String> cmdLine = new ArrayList<>(getCurrentCommandLine());
				cmdLine.add(0, "pkexec");

				ProcessBuilder pkExecBuilder = new ProcessBuilder(cmdLine);
				pkExecBuilder.inheritIO();
				Process gksuProcess = pkExecBuilder.start();
				int status = 0;

				try {
					status = gksuProcess.waitFor();
				} catch (InterruptedException e) {
					LOGGER.error("Unreacheable code, since no threads interrupt the current one.", e);
				}

				if (status != 0) {
					LOGGER.error("Error, process exited with status code {}", status);
				}
				System.exit(status);

			} catch (IOException e) {
				LOGGER.error("Failed while getting root privileges.", e);
				System.exit(1);
			}
		}
	}

	private List<String> getCurrentCommandLine() throws IOException {
		return Arrays.asList(new String(
				Files.readAllBytes(Paths.get("/proc", Integer.toString(CLibrary.INSTANCE.getpid()), "cmdline")),
				StandardCharsets.UTF_8).split("\0"));
	}

	private static Instant getMostRecentInstant(String multiExpressionCron,
	                                            Instant referenceInstant) throws ParseException {
		String[] expressions = multiExpressionCron.split("\\|");
		Date referenceDate = Date.from(referenceInstant);

		Instant mostRecentInstant = new CronExpression(expressions[0]).getNextValidTimeAfter(referenceDate).toInstant();

		if (expressions.length == 1) {
			return mostRecentInstant;
		}

		for (int i = 1; i < expressions.length; i++) {
			CronExpression cronExpression = new CronExpression(expressions[i]);
			Instant currentInstant = cronExpression.getNextValidTimeAfter(referenceDate).toInstant();

			if (mostRecentInstant.isAfter(currentInstant)) {
				mostRecentInstant = currentInstant;
			}
		}
		return mostRecentInstant;
	}
}