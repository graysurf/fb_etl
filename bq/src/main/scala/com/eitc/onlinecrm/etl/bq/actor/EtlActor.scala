package com.eitc.onlinecrm.etl.bq.actor

import akka.actor.{ Actor, ActorLogging, Props }
import akka.pattern._
import akka.util.Timeout
import org.graysurf.util.QuartzScheduler

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

class EtlActor extends Actor with ActorLogging {

  import context.dispatcher

  implicit val timeout: Timeout = Timeout(30 days)

  case class Table(schema: String, name: String)

  private[this] val fbTables = Seq(
    "about_basic",
    "about_education",
    "about_family",
    "about_place",
    "about_work",
    "allfbid",
    "checkin",
    "fbid_apifbid",
    "friend",
    "page",
    "pagelike",
    "uuid"
  )
    .map(t ⇒ Table("fb", t))

  private[this] val fbapiTables = Seq(
    "page",
    "page_post",
    "page_post_comment",
    "page_post_comment_like",
    "page_post_reaction",
    "post_process"
  )
    .map(t ⇒ Table("fbapi", t))

  override def preStart(): Unit = {
    super.preStart()
    // (fbapiTables ++ fbTables)
    fbTables
      .map { case Table(schema, name) ⇒ context.actorOf(Props(classOf[TableActor], schema, name), s"$schema.$name@table") }
  }

  override def receive: Receive = {
    case FetchMessage ⇒
      log.info("start ETL")
      context.children.foreach {
        table ⇒
          (table ? FetchMessage).onComplete {
            case Success(_) ⇒
              QuartzScheduler.newJob(table.path.name, "0 0/10 * * * ?") {
                val _f = (table ? FetchMessage).mapTo[FinishMessage.type]
                _f.onComplete {
                  case Success(_) ⇒
                  case Failure(t) ⇒
                    log.error(t, "")
                }
                Await.result(_f, Duration.Inf)
              }
            case Failure(t) ⇒
              log.error(t, "")
          }

      }

    case msg ⇒
      log.warning(s"${self.path.name} received unknown message $msg from $sender")
  }

}
