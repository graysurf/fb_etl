package org.graysurf.util.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

import scala.collection.JavaConverters._

class AkkaPathOrThreadConverter extends ClassicConverter {

  private[this] val ScalaGlobal = """(scala-execution-context-global)-([0-9]+$)""".r
  private[this] val QuartzThreadName = """(DefaultQuartzScheduler_Worker)-([0-9]+$)""".r
  private[this] val AkkaDispatcherThreadName = """.+-(akka\.actor|dispatcher)+\.(.+)\-([0-9]+$)""".r

  def convert(event: ILoggingEvent): String = {
    val mdc = event.getMDCPropertyMap.asScala

    val thread = mdc.getOrElse("sourceThread", event.getThreadName) match {
      case ScalaGlobal(_, id) ⇒
        s"scala-global #$id"
      case QuartzThreadName(_, id) ⇒
        s"quartz-worker #$id"
      case AkkaDispatcherThreadName(_, name, id) ⇒
        s"$name #$id"
      case name ⇒
        name
    }
    thread
  }
}