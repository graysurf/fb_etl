package org.graysurf.util

import com.fasterxml.jackson.databind.{ DeserializationFeature, ObjectMapper, SerializationFeature }
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

object Mapper extends ObjectMapper with ScalaObjectMapper {
  registerModule(DefaultScalaModule)
  registerModule(new JavaTimeModule())
  configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  enable(SerializationFeature.INDENT_OUTPUT)
}
