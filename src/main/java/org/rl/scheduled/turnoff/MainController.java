package org.rl.scheduled.turnoff;

import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.login1.Manager;
import org.freedesktop.login1.Session;

import java.lang.reflect.Field;
import java.util.List;

public class MainController {
	private static final String DESLIGAMENTO = "0 30 1 ? * *";


	static {
		try {
			String libLookupPath = "java.library.path";
			System.setProperty(libLookupPath, System.getProperty(libLookupPath) + ":/usr/lib/jni");
			Field sys_paths = ClassLoader.class.getDeclaredField("sys_paths");
			sys_paths.setAccessible(true);
			sys_paths.set(ClassLoader.class, null);

		} catch (IllegalAccessException | NoSuchFieldException e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) throws DBusException {
//		System.setProperty(libPath, System.getProperty(libPath) + ":");
		DBusConnection conn = DBusConnection.getConnection(DBusConnection.SYSTEM);
		Manager loginManager = conn
				.getRemoteObject("org.freedesktop.login1", "/org/freedesktop/login1", Manager.class);

		System.out.println(loginManager);
		List<Session> sessions = loginManager.ListSessions();
		loginManager.PowerOff(true);


//		new KernelTurnOffJob().execute(null);

//		JobDetail job = JobBuilder.newJob(KernelTurnOffJob.class).build();
//		Trigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule("* * * ? * *"))
//		                                .build();
//
//		//schedule it
//		Scheduler scheduler = new StdSchedulerFactory().getScheduler();
//		scheduler.start();
//		scheduler.scheduleJob(job, trigger);
	}
}