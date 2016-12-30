package org.rl.scheduled.turnoff;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public class MainController {
	private static final String DESLIGAMENTO = "0 30 1 ? * *";


	public static void main(String[] args) throws SchedulerException {
		JobDetail job = JobBuilder.newJob(DBusTurnoff.class).build();
		Trigger trigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(DESLIGAMENTO))
		                                .build();

		//schedule it
		Scheduler scheduler = new StdSchedulerFactory().getScheduler();
		scheduler.start();
		scheduler.scheduleJob(job, trigger);
	}
}