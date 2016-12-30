package org.rl.scheduled.turnoff;

import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.login1.Manager;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.lang.reflect.Field;

public class DBusTurnoff implements Job {

	static {
		try {
			String libLookupPath = "java.library.path";
			System.setProperty(libLookupPath, System.getProperty(libLookupPath) + ":/usr/lib/jni");
			Field sys_paths = ClassLoader.class.getDeclaredField("sys_paths");
			sys_paths.setAccessible(true);
			sys_paths.set(ClassLoader.class, null);

		} catch (IllegalAccessException | NoSuchFieldException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		DBusConnection conn = null;
		try {
			conn = DBusConnection.getConnection(DBusConnection.SYSTEM);
			Manager loginManager = conn
					.getRemoteObject("org.freedesktop.login1", "/org/freedesktop/login1", Manager.class);

			loginManager.PowerOff(true);
		} catch (DBusException e) {
			throw new JobExecutionException(e);
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}

	}
}