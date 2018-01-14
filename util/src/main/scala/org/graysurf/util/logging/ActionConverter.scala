package org.graysurf.util.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class ActionConverter extends ClassicConverter {
  def convert(event: ILoggingEvent): String = ""
}