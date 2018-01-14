package com.eitc.onlinecrm.etl.bq.actor

import java.time.OffsetDateTime

import akka.actor.{ Actor, ActorLogging }
import com.eitc.onlinecrm.etl.bq.db.BigQueryFetchTimeTable.BigQueryFetchTimeRow
import com.eitc.onlinecrm.etl.bq.db.json.JsonRow
import com.eitc.onlinecrm.etl.bq.db.{ FbTables, Tables }
import org.graysurf.util.db.PostgresConfig

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

class DbActor(schema: String, name: String, fetchMinutes: Int) extends Actor with ActorLogging {

  import PostgresConfig.profile.api._
  import context.dispatcher

  private val config = PostgresConfig("db_target")
  private val db = config.db

  override def receive: Receive = {
    case FetchMessage ⇒
      log.info("start fetch data from db")

      val startTime = getLastFetchTime
      val nowTime = OffsetDateTime.now()
      if (nowTime.isBefore(startTime.plusMinutes(fetchMinutes))) {
        log.info(s"skip fetch, lastFetchTime: $startTime,fetchMinutes: $fetchMinutes ")
        sender() ! WriteMessage(startTime, "")
      } else {
        val endTime = if (nowTime.isBefore(startTime.plusMinutes(fetchMinutes))) nowTime else startTime.plusMinutes(fetchMinutes)
        val startFetchTime = System.nanoTime()
        val rows: Seq[JsonRow] = Await.result(getRecords(startTime, endTime), Duration.Inf)
        val endFetchTIme = System.nanoTime()
        val fetchTime = s"duration: ${(endFetchTIme - startFetchTime).nanos.toMillis} ms"
        log.info(s"get $name table ${rows.size} rows, $fetchTime")
        val rowString = rows.map(_.json).mkString("\n")
        sender() ! WriteMessage(endTime, rowString)
      }

    case UpdateFetchTimeMessage(time) ⇒
      val _f = db.run(Tables.FetchTime(schema).insertOrUpdate(BigQueryFetchTimeRow(name, Some(time))))
      Await.result(_f, Duration.Inf)
      sender() ! FinishMessage
    case WriteFetchTimeIfNotExistMessage(time) ⇒
      val _f = db.run(Tables.FetchTime(schema).insertOrUpdate(BigQueryFetchTimeRow(name, Some(time))))
      Await.result(_f, Duration.Inf)
      sender() ! FinishMessage
    case msg ⇒
      log.warning(s"${self.path.name} received unknown message $msg from $sender")
  }

  def getRecords(startTime: OffsetDateTime, endTime: OffsetDateTime): Future[Seq[JsonRow]] = {
    log.info(s"fetch $name table, startTime: $startTime, endTime: $endTime")

    val _f = if (name == "uuid")
               db.run(FbTables.query(schema, name, startTime, endTime, "uuid"))
             else
               db.run(FbTables.query(schema, name, startTime, endTime))


    _f.onComplete {
      case Success(_) ⇒
      case Failure(t) ⇒ log.error(t, "getRecords error")
    }
    _f
  }

  def getLastFetchTime: OffsetDateTime = {
    val _lastFetchTime = db.run(Tables.FetchTime(schema).filter(_.table === name).map(_.timeLastFetch).result.headOption)
      .map(_.flatten)
      .map(_.getOrElse(OffsetDateTime.parse("2017-04-15T00:00:00+08:00")))
    _lastFetchTime.onComplete {
      case Success(_) ⇒
      case Failure(t) ⇒ log.error(t, "getRecords error")
    }
    Await.result(_lastFetchTime, Duration.Inf)
  }

}
