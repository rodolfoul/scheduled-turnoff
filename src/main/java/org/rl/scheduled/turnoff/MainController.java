package org.rl.scheduled.turnoff;

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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MainController {
	public static final String SHUTDOWN_CRON = "0 30 1 ? * *";
	public static final String STARTUP_CRON = "0 0 6 ? * *";


	public static void main(String[] args) throws IOException, SchedulerException {
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

		if (!"root".equals(userName)) {
			String jvmPath = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
			URL[] urls = ((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs();
			String classPath = Arrays.stream(urls).map(URL::getFile).collect(Collectors.joining(":"));
			String mainClassName = System.getProperty("sun.java.command");
			ProcessBuilder gksu = new ProcessBuilder("gksu",
			                                         String.format("%s -cp %s %s", jvmPath, classPath, mainClassName));
			gksu.inheritIO();
			Process process = gksu.start();
			int status = 0;
			try {
				status = process.waitFor();
				if (status != 0) {
					System.err.println("Error, process exited with status code " + status);
				}
			} catch (InterruptedException e) {
				e.printStackTrace(); //Unreacheable code, since no threads interrupt the current one.
			}
			System.exit(status);
		}
	}
}