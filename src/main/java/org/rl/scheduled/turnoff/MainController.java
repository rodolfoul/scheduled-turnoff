package org.rl.scheduled.turnoff;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.CronExpression;
import org.quartz.SchedulerException;

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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

public class MainController {
	interface CLibrary extends Library {
		CLibrary INSTANCE = (CLibrary) Native.loadLibrary("c", CLibrary.class);

		int getpid();

		void chdir(String path) throws LastErrorException;
	}

	static {
		Path jarPath = Paths.get(MainController.class.getResource("/").getPath());
		CLibrary.INSTANCE.chdir(jarPath.getParent().toString());
		System.setProperty("user.dir", jarPath.toString());
	}

	/**
	 * LOGGER should come always after static block, as it sets the correct current directory for logger configuration.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	public static void main(String[] args) {
		new MainController().start();
	}

	private final Properties properties = new Properties();
	private CronExpression currentShutdownCron;
	private CronExpression currentStartupCron;

	public MainController() {
		try {
			currentStartupCron = new CronExpression("0 0 6 ? * *");
			currentShutdownCron = new CronExpression("0 30 1 ? * *");
		} catch (ParseException e) {
			LOGGER.error("Wrong cron expression", e);
		}
	}

	public void start() {
		checkRootPermission();

		configureProperties();
		configureScheduling();

	}

	private void configureProperties() {
		String propertiesFileName = "src/dist/config/cron-times.properties";
		final Path propertiesPath = Paths.get(MainController.class.getResource("/").getFile())
		                                 .resolve(propertiesFileName);

		if (Files.exists(propertiesPath)) {
			loadProperties(propertiesPath);
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
	}

	private void loadProperties(Path propertiesPath) {
		try (InputStream is = Files.newInputStream(propertiesPath)) {
			properties.load(is);

		} catch (IOException e) {
			LOGGER.error("Failed loading properties file.", e);
		}
	}

	private void configureScheduling() {
		schedulePowerOn();
		schedulePowerOff();
	}


	private void schedulePowerOff() {
		try {
			CronExpression cronExpression = new CronExpression(properties.getProperty("shutdown.cron"));
			if (currentShutdownCron.getExpressionSummary().equals(cronExpression.getExpressionSummary())) {
				return;
			}

			QuartzController.reschedulePowerOffJob(cronExpression);
			currentShutdownCron = cronExpression;

		} catch (ParseException e) {
			LOGGER.warn("Could not parse the given cron expression for shutdown.", e);
		} catch (SchedulerException e) {
			LOGGER.error("Failed while configuring cron for shutdown.", e);
		}
	}

	private void schedulePowerOn() {
		try {
			CronExpression startupExpression = new CronExpression(properties.getProperty("startup.cron"));

			if (currentStartupCron.getExpressionSummary().equals(startupExpression.getExpressionSummary())) {
				return;
			}
			Instant turnOnInstant = startupExpression.getNextValidTimeAfter(new Date()).toInstant();

			long turnOnEpoch = turnOnInstant.getEpochSecond();
			LOGGER.info("Setting next startup to {} -> {} -> {}", turnOnInstant, turnOnEpoch,
			            turnOnInstant.atZone(ZoneId.systemDefault()));

			try (Writer wakeAlarmStream = Files.newBufferedWriter(Paths.get("/sys/class/rtc/rtc0/wakealarm"))) {
				wakeAlarmStream.write("0\n");
			}
			try (Writer wakeAlarmStream = Files.newBufferedWriter(Paths.get("/sys/class/rtc/rtc0/wakealarm"))) {
				wakeAlarmStream.write(turnOnEpoch + "\n");
			}
			currentStartupCron = startupExpression;

		} catch (ParseException e) {
			LOGGER.warn("Could not parse cron expression for startup.cron", e);
		} catch (IOException e) {
			LOGGER.error("Failed while configuring next startup time.", e);
		}
	}


	private void checkRootPermission() {
		String userName = System.getProperty("user.name");
		LOGGER.debug("Current user: {}", userName);

		if (!"root".equals(userName)) {
			try {
				String cmdLine = getFullCommandLine();

				ProcessBuilder gksuBuilder = new ProcessBuilder("gksu", cmdLine);
				gksuBuilder.inheritIO();
				Process gksuProcess = gksuBuilder.start();
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

	private String getFullCommandLine() throws IOException {
		List<String> currentCmd = Arrays.asList(new String(
				Files.readAllBytes(Paths.get("/proc", Integer.toString(CLibrary.INSTANCE.getpid()), "cmdline")),
				StandardCharsets.UTF_8).split("\0"));

		StringBuilder cmdLine = new StringBuilder();
		boolean cpArgument = false;
		for (ListIterator<String> it = currentCmd.listIterator(); it.hasNext(); ) {
			String argument = it.next();
			if ("-cp".equals(argument) || "-classpath".equals(argument)) {
				cmdLine.append(" ").append(argument).append(" ").append(it.next());
				cpArgument = true;

			} else if (it.previousIndex() == 0) {
				cmdLine.append(argument);

			} else if ("-jar".equals(argument)) {
				cmdLine.append(" ").append(argument).append(" ").append(it.next());
			}
		}
		if (cpArgument) {
			cmdLine.append(System.getProperty("sun.java.command"));
		}
		return cmdLine.toString();
	}
}