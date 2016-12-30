import com.sun.jna.Native;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class TurnOffJob implements Job {

	private GnuCLib c = (GnuCLib) Native.loadLibrary("c", GnuCLib.class);

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		c.sync();
		int a = c.reboot(GnuCLib.LINUX_REBOOT_CMD_POWER_OFF);
	}
}