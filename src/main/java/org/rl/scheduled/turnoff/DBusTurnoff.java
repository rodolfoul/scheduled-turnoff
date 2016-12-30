package org.rl.scheduled.turnoff;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.UInt32;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class DBusTurnoff implements Job {

	interface Bluemon extends DBusInterface
	{
		public DBus.Binding.Triplet<String, Boolean, UInt32>
		Status(String address);
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {

	}
}