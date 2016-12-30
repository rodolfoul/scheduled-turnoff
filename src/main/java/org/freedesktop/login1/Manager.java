package org.freedesktop.login1;

import lombok.RequiredArgsConstructor;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt32;

import java.util.List;

public interface Manager extends DBusInterface {
	void PowerOff(Boolean arg);

	void LockSessions();

	void LockSession(String arg);

	List<Session> ListSessions();
}