package org.freedesktop.login1;

import org.freedesktop.dbus.DBusInterface;

import java.util.List;

public interface Manager extends DBusInterface {
	void PowerOff(Boolean should);

	void LockSession(String sessionId);

	List<Session> ListSessions();
}