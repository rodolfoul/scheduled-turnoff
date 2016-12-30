package org.freedesktop.login1;

import lombok.RequiredArgsConstructor;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt32;

@RequiredArgsConstructor
public class Session extends Struct {
	@Position(0)
	public final String sessionId;
	@Position(1)
	public final UInt32 userId;
	@Position(2)
	public final String userName;
	@Position(3)
	public final String seatId;
	@Position(4)
	public final Path sessionObjectPath;
}