package org.freedesktop.login1;

import org.freedesktop.dbus.DBusInterface;

import java.util.List;

public interface Manager extends DBusInterface {
	void PowerOff(Boolean arg);

	void LockSession(String arg);

	List<Session> ListSessions();
}