package com.eitc.onlinecrm.etl.bq.actor

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.actor.{ Actor, ActorLogging }
import com.google.cloud.storage.{ BlobId, StorageOptions }

import scala.util.{ Failure, Success, Try }

class StorageActor(schema: String, name: String, bucket: String) extends Actor with ActorLogging {

  private[this] val storage = StorageOptions.getDefaultInstance.getService
  private[this] val baseBucket = storage.get(bucket)

  override def receive: Receive = {
    case WriteMessage(time, data) ⇒
      log.info("start write data to storage")
      val filePath = getFilePath(time)
      Try {
        baseBucket.create(filePath, data.getBytes("utf-8"), "text/plain")
      } match {
        case Success(_) ⇒
          log.info("write data to storage finish")
          sender() ! Right(WriteStorageDataToBqMessage(s"gs://${baseBucket.getName}/$filePath"))
        case Failure(t) ⇒
          log.error(t, "write data to storage fail")
          Try(baseBucket.get(filePath).delete())
          sender() ! Left(FailureMessage)
      }
    case DeleteStorageDataMessage(filePath) ⇒
      val PathRex = "gs://([a-zA-Z0-9_\\-]*)/([a-zA-Z0-9_\\-\\/]*)".r
      Try(
        filePath match {
          case PathRex(sourceBucket, path) ⇒
            if (storage.delete(BlobId.of(sourceBucket, path))) log.info(s"delete $filePath")
            else log.warning(s"delete $filePath error")
          case _ ⇒
            log.error(s"parse $filePath error")
        }
      )
      sender() ! FinishMessage
    case msg ⇒
      log.warning(s"${self.path.name} received unknown message $msg from $sender")
  }

  private[this] def getFilePath(lastTime: OffsetDateTime): String = {

    val time = lastTime.toLocalDateTime
    val yearMonth = s"${time.getYear}${time.getMonthValue.formatted("%02d")}"
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

    s"$schema/$name/$yearMonth/$name-${formatter.format(time)}"
  }

}
