package org.graysurf.util.logging

import ch.qos.logback.classic.pattern.LoggerConverter
import ch.qos.logback.classic.spi.ILoggingEvent

import scala.collection.JavaConverters._

class AkkaLoggerConverter extends LoggerConverter {

  private[this] val AkkaPath = """^akka://[^/]+/user(.+)$""".r

  override def convert(event: ILoggingEvent): String = {

    val mdc = event.getMDCPropertyMap.asScala
    mdc.get("akkaSource") match {
      case Some(AkkaPath(path)) ⇒
        path
      case _ ⇒
        super.convert(event)
    }
  }

}
