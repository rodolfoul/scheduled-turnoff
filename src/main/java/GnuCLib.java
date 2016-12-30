import com.sun.jna.Library;

public interface GnuCLib extends Library {

	int LINUX_REBOOT_CMD_RESTART = 0x1234567;
	int LINUX_REBOOT_CMD_POWER_OFF = 0x4321fedc;

	void sync();
	int reboot(int cmd);

}