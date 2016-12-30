import org.quartz.SchedulerException;

import java.io.IOException;

public class MainController {
	private static final String DESLIGAMENTO = "0 30 1 ? * *";

	public static void main(String[] args) throws IOException, SchedulerException {
		new TurnOffJob().execute(null);

//		JobDetail job = JobBuilder.newJob(TurnOffJob.class).build();
//		Trigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule("* * * ? * *"))
//		                                .build();
//
//		//schedule it
//		Scheduler scheduler = new StdSchedulerFactory().getScheduler();
//		scheduler.start();
//		scheduler.scheduleJob(job, trigger);
	}
}