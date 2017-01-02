package org.rl.scheduled.turnoff;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class MainController {
	interface CLibrary extends Library {
		CLibrary INSTANCE = (CLibrary) Native.loadLibrary("c", CLibrary.class);

		int getpid();

		void chdir(String path) throws LastErrorException;
	}

	static {
		File file = new File(MainController.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		CLibrary.INSTANCE.chdir(file.toString());
		System.setProperty("user.dir", file.toString());
	}

	public static final String SHUTDOWN_CRON = "0 30 1 ? * *";
	public static final String STARTUP_CRON = "0 0 6 ? * *";

	private static final Logger LOGGER = LogManager.getLogger();

	public static void main(String[] args) throws IOException, SchedulerException, DBusException {
		checkRootPermission();

		JobDetail job = JobBuilder.newJob(PowerControlJob.class).build();
		Trigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(SHUTDOWN_CRON))
		                                .build();

		Scheduler scheduler = new StdSchedulerFactory().getScheduler();
		scheduler.start();
		scheduler.scheduleJob(job, trigger);
	}

	private static void checkRootPermission() throws IOException {
		String userName = System.getProperty("user.name");
		LOGGER.debug("Current user: {}", userName);

		if (!"root".equals(userName)) {
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
					cmdLine.append(" ").append(argument);

				} else if ("-jar".equals(argument)) {
					cmdLine.append(" ").append(argument).append(" ").append(it.next());
				}
			}
			if (cpArgument) {
				cmdLine.append(System.getProperty("sun.java.command"));
			}

			ProcessBuilder gksu = new ProcessBuilder("gksu", cmdLine.toString());
			gksu.inheritIO();
			Process process = gksu.start();
			int status = 0;
			try {
				status = process.waitFor();
				if (status != 0) {
					System.err.println("Error, process exited with status code " + status);
				}
			} catch (InterruptedException e) {
				LOGGER.error("Unreacheable code, since no threads interrupt the current one.", e);
			}
			System.exit(status);
		}
	}
}