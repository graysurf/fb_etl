package com.eitc.onlinecrm.etl.bq.actor

import akka.actor.{ Actor, ActorLogging }
import com.eitc.onlinecrm.etl.bq.util.BigQueryUtil

import scala.util.{ Failure, Success, Try }

class BqActor(schema: String, name: String) extends Actor with ActorLogging {

  override def receive: Receive = {
    case WriteStorageDataToBqMessage(filePath) ⇒
      log.info("start write data to bq")
      Try {
        BigQueryUtil.writeData(schema, name, filePath)
      } match {
        case Success(_) ⇒
          sender() ! Right(FinishMessage)
        case Failure(t) ⇒
          sender() ! Left(DeleteStorageDataMessage(filePath))
          log.error(t, "write data to bq fail")
      }
      log.info("write data to bq finish")
    case msg ⇒
      log.warning(s"${self.path.name} received unknown message $msg from $sender")
  }

}
