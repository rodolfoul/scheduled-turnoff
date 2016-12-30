package org.rl.scheduled.turnoff;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MainController {
	private static final String DESLIGAMENTO = "0 30 1 ? * *";


	public static void main(String[] args) throws IOException, InterruptedException {
		restartWithPermissions();

//		JobDetail job = JobBuilder.newJob(PowerControlJob.class).build();
//		Trigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(DESLIGAMENTO))
//		                                .build();
//
//		//schedule it
//		Scheduler scheduler = new StdSchedulerFactory().getScheduler();
//		scheduler.start();
//		scheduler.scheduleJob(job, trigger);
	}

	private static void restartWithPermissions() throws IOException, InterruptedException {
		String userName = System.getProperty("user.name");
		System.out.println("Current User: " + userName);

		if (!"root".equals(userName)) {
			String jvmPath = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
			URL[] urls = ((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs();
			String classPath = Arrays.stream(urls).map(URL::getFile).collect(Collectors.joining(":"));
			String mainClassName = System.getProperty("sun.java.command");
			ProcessBuilder gksu = new ProcessBuilder("gksu",
			                                         String.format("%s -cp %s %s", jvmPath, classPath, mainClassName));
			gksu.inheritIO();
			Process process = gksu.start();
			int status = process.waitFor();
			if (status != 0) {
				System.err.println("Error, process exited with status code " + status);
			}
			System.exit(status);
		}
	}
}