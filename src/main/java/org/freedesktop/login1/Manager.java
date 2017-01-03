package org.freedesktop.login1;

import org.freedesktop.dbus.DBusInterface;

import java.util.List;

public interface Manager extends DBusInterface {
	/**
	 * Results in the system being powered off, rebooted, suspend, hibernated or hibernated+suspended. The only argument
	 * is the PolicyKit interactivity boolean (see above). The main purpose of these calls is that they enforce
	 * PolicyKit policy and hence allow powering off/rebooting/suspending/hibernating even by unprivileged users. They
	 * also enforce inhibition locks. UIs should expose these calls as primary mechanism to
	 * poweroff/reboot/suspend/hibernate/hybrid-sleep the machine.
	 *
	 * @param interactivity The user_interaction boolean parameters can be used to control whether PolicyKit should
	 *                      interactively ask the user for authentication credentials if it needs to.
	 */
	void PowerOff(Boolean interactivity);

	/**
	 * Asks the session with the specified ID to activate the screen lock.
	 */
	void LockSession(String sessionId);

	/**
	 * Returns an array with all current sessions. The structures in the array consist of the following fields: session
	 * id, user id, user name, seat id, session object path. If a session does not have a seat attached the seat id
	 * field will be an empty string.
	 */
	List<Session> ListSessions();
}