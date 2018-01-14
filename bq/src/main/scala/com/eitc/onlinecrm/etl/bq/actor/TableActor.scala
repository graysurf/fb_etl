package com.eitc.onlinecrm.etl.bq.actor

import java.time.OffsetDateTime

import akka.actor.{ Actor, ActorLogging, Props }
import akka.pattern._
import akka.util.Timeout

import scala.annotation.tailrec
import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

class TableActor(schema: String, name: String) extends Actor with ActorLogging {

  import context.dispatcher

  implicit val timeout: Timeout = Timeout(30 days)

  private val fetchMinutes = 180

  private var errorCount = 0

  private val dbActor = context.actorOf(Props(classOf[DbActor], schema, name, fetchMinutes), s"db")
  private val storageActor = context.actorOf(Props(classOf[StorageActor], schema, name, "jetcw"), s"storage")
  private val bqActor = context.actorOf(Props(classOf[BqActor], schema, name), s"bq")

  def error: Receive = {
    case _ ⇒
      log.warning("error occurred 3 times, stop fetch data")
  }

  override def receive: Receive = {
    case _ if errorCount >= 3 ⇒
      context.become(error)
      self ! _
    case FetchMessage ⇒
      val sdr = sender()
      @tailrec
      def fetchDataFromDb(): Unit = Await.result((dbActor ? FetchMessage).mapTo[WriteMessage], Duration.Inf) match {
        case WriteMessage(time, data) if time.isBefore(OffsetDateTime.now().minusMinutes(fetchMinutes)) ⇒
          writeDate(time, data)
          if (data.nonEmpty) Thread.sleep(20 * 1000)
          fetchDataFromDb()
        case WriteMessage(time, data) ⇒
          writeDate(time, data)
          sdr ! FinishMessage
      }
      fetchDataFromDb()
    case msg ⇒
      log.warning(s"${self.path.name} received unknown message $msg from $sender")
  }

  private def writeDate(time: OffsetDateTime, data: String) = {
    def _writeToStorage = (storageActor ? WriteMessage(time, data)).mapTo[Either[FailureMessage.type, WriteStorageDataToBqMessage]]
    def _writeToBq(sourcePath: String) = (bqActor ? WriteStorageDataToBqMessage(sourcePath)).mapTo[Either[DeleteStorageDataMessage, FinishMessage.type]]
    def _updateDbFetchTime(time: OffsetDateTime) = dbActor ? UpdateFetchTimeMessage(time)
    def _deleteAbandonedStorageData(sourcePath: String) = storageActor ? DeleteStorageDataMessage(sourcePath)

    data match {
      case "" ⇒
        Await.result(_updateDbFetchTime(time), Duration.Inf)
        errorCount = 0
      case _ ⇒
        val _write = _writeToStorage.flatMap {
          case Right(WriteStorageDataToBqMessage(sourcePath)) ⇒
            _writeToBq(sourcePath).flatMap {
              case Right(_) ⇒
                _updateDbFetchTime(time)
              case Left(DeleteStorageDataMessage(path)) ⇒
                _deleteAbandonedStorageData(path)
            }
          case Left(_) ⇒
            Future.successful(())
        }
        _write.onComplete {
          case Success(_) ⇒
            errorCount = 0
          case Failure(t) ⇒
            log.error(t, s"write $name table error")
            errorCount += 1
        }
        Await.result(_write, Duration.Inf)
    }

    log.info(s"table $name finish")
  }

}
