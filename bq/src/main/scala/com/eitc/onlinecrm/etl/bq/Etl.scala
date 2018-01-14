package com.eitc.onlinecrm.etl.bq

import akka.actor.{ ActorSystem, Props }
import com.eitc.onlinecrm.etl.bq.actor.{ EtlActor, FetchMessage }
import org.slf4j.{ Logger, LoggerFactory }


/*
postgres -> Google Colud Storage -> BigQuery -> Elasticsearch
 */

object Etl {

  val log: Logger = LoggerFactory.getLogger("BqToEsByPartition")

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("bq_etl")
    val etlActor = system.actorOf(Props(classOf[EtlActor]), "etl")
    etlActor ! FetchMessage
  }
}
