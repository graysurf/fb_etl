package org.graysurf.util.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

import scala.util.Random

object ProcessSerialConverter {
  val serial: String = Random.alphanumeric.take(20).filterNot(_.isUpper).take(4).toList.mkString("")
}

class ProcessSerialConverter extends ClassicConverter {
  def convert(event: ILoggingEvent): String = ProcessSerialConverter.serial
}