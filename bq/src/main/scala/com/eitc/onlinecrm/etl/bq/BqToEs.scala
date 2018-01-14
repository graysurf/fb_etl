package com.eitc.onlinecrm.etl.bq

import java.time.{ LocalDateTime, ZoneOffset }

import com.eitc.onlinecrm.etl.bq.util.{ BigQueryUtil, ElasticsearchUtil }
import com.eitc.onlinecrm.etl.bq.db.BigQueryFetchTimeTable.BigQueryFetchTimeRow
import com.eitc.onlinecrm.etl.bq.db.SyncEsTable.SyncEsRow
import com.eitc.onlinecrm.etl.bq.db.Tables
import com.fasterxml.jackson.databind.node.ObjectNode
import org.graysurf.util.db.PostgresConfig
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import scala.concurrent.duration.{ Duration, _ }

class BqToEs {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val log = LoggerFactory.getLogger("Test")

  def main(args: Array[String]): Unit = {

    import PostgresConfig.profile.api._

    val config = PostgresConfig("db_target")
    val db = config.db

    def FetchBigQueryToEs(index: String, bqTable: String, isParent: Boolean = false): Unit = {

      val sync = Tables.SyncEs()
      val _f = db.run(sync.filter(_.name === bqTable).result)
      val syncEsRow: Option[SyncEsRow] = Await.result(_f, Duration.Inf).headOption

      syncEsRow match {
        case Some(SyncEsRow(_, perFetch, lastFetchSeqno)) ⇒
          val dataset :: table :: Nil = bqTable.split('.').toList
          var curFetchSeqno = lastFetchSeqno

          while (curFetchSeqno < BigQueryUtil.getMaxSeqno(dataset, table)) {

            log.info(s"[$table] start fetch: current->$curFetchSeqno, per_fetch->$perFetch")

            // get data from BigQuery
            val startFetchBigQuery = System.nanoTime()
            val data = BigQueryUtil.getJsonDataByPartition(dataset, table, perFetch, curFetchSeqno)
            val endFetchBigQuery = System.nanoTime()
            log.info(s"[$table] get data from BigQuery duration: ${(endFetchBigQuery - startFetchBigQuery).nanos.toMillis} ms")
            curFetchSeqno = if (data.isEmpty) curFetchSeqno + perFetch else data.map(_.get("seqno").asLong()).max
            // write json to ES
            val startWriteElasticsearch = System.nanoTime()
            if (data.nonEmpty) ElasticsearchUtil.bulkInsert(index, table, data)
            val endWriteElasticsearch = System.nanoTime()
            log.info(s"[$table] write json to ES duration: ${(endWriteElasticsearch - startWriteElasticsearch).nanos.toMillis} ms")

            // update sync_es table
            val startWriteDb = System.nanoTime()
            val _update = db.run(sync.insertOrUpdate(SyncEsRow(bqTable, perFetch, curFetchSeqno)))
            Await.result(_update, Duration.Inf)
            val endWriteDb = System.nanoTime()
            log.info(s"[$table] update sync_es table duration: ${(endWriteDb - startWriteDb).nanos.toMillis} ms")

          }
        case None ⇒
          log.error(s"$bqTable is not exist")
      }
    }

    //FetchBigQueryToEs("jetcw-0830-test", "Normalization.NORM_page_like_0830")

    val f1 = Future {
      FetchBigQueryToEs("jetcw", "Normalization.NORM_about_cleaned_0828", isParent = true)
    }

    val f2 = Future {
      FetchBigQueryToEs("jetcw", "Normalization.NORM_page_like_0830")
    }

    val f = f1.flatMap(_ ⇒ f2)

    Await.result(f, Duration.Inf)

    println("finish")

  }
}
