package org.freedesktop.dbus;

/**
 * Substitute bugged Gettext from com.github.bdeneuter.
 * The lib has no 'dbusjava_localized' class embedded.
 */
public class Gettext {
	public static String _(String s) {
		return s;
	}
}
