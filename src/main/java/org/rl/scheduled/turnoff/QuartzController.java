package org.rl.scheduled.turnoff;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.login1.Manager;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.time.ZoneId;
import java.util.Date;

public class QuartzController {

	private static final Logger LOGGER = LogManager.getLogger();
	private static Scheduler scheduler;
	private static Trigger currentTrigger;
	private static JobDetail job = JobBuilder.newJob(PowerOffJob.class).build();

	static {
		try {
			scheduler = new StdSchedulerFactory().getScheduler();
			scheduler.start();
		} catch (SchedulerException e) {
			LOGGER.error("Could not set up quartz scheduler, exiting...", e);
			System.exit(1);
		}
	}


	public static void reschedulePowerOffJob(CronExpression cronExpression) throws SchedulerException {
		LOGGER.info("Next shutdown to occur at {}",
		            cronExpression.getNextValidTimeAfter(new Date()).toInstant().atZone(
				            ZoneId.systemDefault()));

		Trigger trigger = TriggerBuilder.newTrigger()
		                                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
		                                .forJob(job)
		                                .build();
		if (currentTrigger != null) {
			scheduler.rescheduleJob(currentTrigger.getKey(), trigger);
		} else {
			scheduler.scheduleJob(job, trigger);
		}
		currentTrigger = trigger;
	}

	private static class PowerOffJob implements Job {

		static {
			try {
				String libLookupPath = "java.library.path"; //dbus setup
				File libDir = new File(File.class.getResource("/libunix-java.so").getPath()).getParentFile();
				System.setProperty(libLookupPath, System.getProperty(libLookupPath) + ":" + libDir);
				Field sys_paths = ClassLoader.class.getDeclaredField("sys_paths");
				sys_paths.setAccessible(true);
				sys_paths.set(ClassLoader.class, null);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				LOGGER.error("Could not set up dbus lib. Exiting...", e);
				System.exit(1);
			}
		}

		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {
			try {
				LOGGER.info("Starting shutdown sequence");
				powerOffNow();

			} catch (DBusException e) {
				LOGGER.error("Exception while during power off", e);
				throw new JobExecutionException(e);
			}
		}

		private void powerOffNow() throws DBusException {
			DBusConnection conn = null;
			try {
				conn = DBusConnection.getConnection(DBusConnection.SYSTEM);
				Manager loginManager = conn
						.getRemoteObject("org.freedesktop.login1", "/org/freedesktop/login1", Manager.class);

				loginManager.PowerOff(false);

			} finally {
				if (conn != null) {
					conn.disconnect();
				}
			}

			LOGGER.debug("Power off signal successful exiting.");
			System.exit(0);
		}
	}
}