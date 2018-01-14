package org.graysurf.util

import java.util.Properties

import org.quartz._
import org.quartz.core.jmx.JobDataMapSupport
import org.quartz.impl.StdSchedulerFactory

import scala.collection.JavaConverters._

object QuartzScheduler {

  private[this] lazy val scheduler = {
    val prop = new Properties()
    prop.load(getClass.getClassLoader.getResourceAsStream("quartz.property"))

    val s = new StdSchedulerFactory(prop).getScheduler
    sys.addShutdownHook(s.shutdown())
    s.start()
    s
  }

  def newJob(name: String, cron: String)(jobFunc: ⇒ Unit): Unit = {
    val data = JobDataMapSupport.newJobDataMap(Map[String, AnyRef]("job" → { () ⇒ jobFunc }).asJava)
    val group = s"group #${System.nanoTime()}"

    val job = JobBuilder.newJob(classOf[ExecuteJob])
      .usingJobData(data)
      .withIdentity(name, group)
      .build()

    val trigger = TriggerBuilder
      .newTrigger()
      .withIdentity(name, group)
      .withSchedule(CronScheduleBuilder.cronSchedule(cron))
      .build()

    scheduler.scheduleJob(job, trigger)
  }

  @DisallowConcurrentExecution
  class ExecuteJob extends Job {
    override def execute(context: JobExecutionContext): Unit = {
      context.getMergedJobDataMap.get("job").asInstanceOf[() ⇒ Unit].apply()
    }
  }

}

