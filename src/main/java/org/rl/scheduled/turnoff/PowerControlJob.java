package org.rl.scheduled.turnoff;

import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.login1.Manager;
import org.quartz.CronExpression;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

public class PowerControlJob implements Job {

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
		try {
			CronExpression ligamento = new CronExpression(MainController.STARTUP_CRON);
			Date horaLigamento = ligamento.getNextValidTimeAfter(new Date());

			setNextStartUpTime(horaLigamento.toInstant());
			powerOffNow();
		} catch (DBusException | IOException | ParseException e) {
			throw new JobExecutionException(e);
		}
	}

	private void setNextStartUpTime(Instant startUpTime) throws IOException {
		try (DataOutputStream wakeAlarmStream = new DataOutputStream(
				new FileOutputStream("/sys/class/rtc/rtc0/wakealarm"))) {
			wakeAlarmStream.writeChars(String.format("0\n%s\n", startUpTime.getEpochSecond()));
		}

	}

	private void powerOffNow() throws JobExecutionException, DBusException {
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