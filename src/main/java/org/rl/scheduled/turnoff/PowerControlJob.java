package org.rl.scheduled.turnoff;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.login1.Manager;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.File;
import java.lang.reflect.Field;

public class PowerControlJob implements Job {

	private static final Logger LOGGER = LogManager.getLogger();

	static {
		try {
			String libLookupPath = "java.library.path"; //dbus setup
			File libDir = new File(File.class.getResource("/libunix-java.so").getPath()).getParentFile();
			System.setProperty(libLookupPath, System.getProperty(libLookupPath) + ":" + libDir);
			Field sys_paths = ClassLoader.class.getDeclaredField("sys_paths");
			sys_paths.setAccessible(true);
			sys_paths.set(ClassLoader.class, null);

		} catch (IllegalAccessException | NoSuchFieldException e) {
			LOGGER.error("Exception while setting up dbus library path.", e);
			System.exit(1);
		}
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		try {
			powerOffNow();
		} catch (DBusException e) {
			LOGGER.error("Exception while during power control", e);
			throw new JobExecutionException(e);
		}
	}

	private void powerOffNow() throws DBusException {
		LOGGER.info("Starting shutdown sequence");

		DBusConnection conn = null;
		try {
			conn = DBusConnection.getConnection(DBusConnection.SYSTEM);
			Manager loginManager = conn
					.getRemoteObject("org.freedesktop.login1", "/org/freedesktop/login1", Manager.class);

			loginManager.PowerOff(true);
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
		System.exit(0);
	}
}