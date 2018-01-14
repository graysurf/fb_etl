package com.eitc.onlinecrm.etl.bq

import com.eitc.onlinecrm.etl.bq.db.SyncEsPartitionTable.SyncEsPartitionRow
import com.eitc.onlinecrm.etl.bq.db.{ SyncEsPartitionTable, Tables }
import com.eitc.onlinecrm.etl.bq.util.{ BigQueryUtil, ElasticsearchUtil }
import org.graysurf.util.ThreadUtils
import org.graysurf.util.db.PostgresConfig
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.Random

class BqToEsByPartition {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val log = LoggerFactory.getLogger("BqToEsByPartition")

  private val executionContext = ExecutionContext.fromExecutor(ThreadUtils.newDaemonFixedThreadPool(18, "partition"))

  def main(args: Array[String]): Unit = {

    import PostgresConfig.profile.api._

    val config = PostgresConfig("db_target")
    val db = config.db

    def FetchBigQueryToEs(index: String, bqTable: String, typeName: Option[String] = None, isParent: Boolean = false): Unit = {

      val dataset :: table :: Nil = bqTable.split('.').toList

      // insert table process to db
      val _writePartition = BigQueryUtil.getPartition(dataset, table)
        .map(p ⇒ db.run(SyncEsPartitionTable.insertIfNotExist(bqTable, p)))
      Await.result(Future.sequence(_writePartition), Duration.Inf)

      val sync = Tables.SyncEsPartition()
      val _sync = db.run(sync.filter(_.name === bqTable).result)
      val syncEsPartitionRows = Await.result(_sync, Duration.Inf)

      val _f = syncEsPartitionRows.map {
        case SyncEsPartitionRow(_, partition, perFetch, lastFetchSeqno) ⇒
          Future {
            Thread.sleep(Random.nextInt(5) * 1000)
            val dataset :: table :: Nil = bqTable.split('.').toList
            var curFetchSeqno = lastFetchSeqno

            while (curFetchSeqno < BigQueryUtil.getMaxPartitionSeqno(dataset, table, Some(partition))) {
              log.info(s"[$table$$$partition] start fetch: current->$curFetchSeqno, per_fetch->$perFetch")
              // get data from BigQuery
              val startFetchBigQuery = System.nanoTime()
              var data = BigQueryUtil.getJsonDataByPartition(dataset, table, perFetch, curFetchSeqno, Some(partition))
              while (data.isEmpty) {
                log.info(s"[$table$$$partition] BigQuery get ${data.size} rows")
                data = BigQueryUtil.getJsonDataByPartition(dataset, table, perFetch, curFetchSeqno, Some(partition))
              }
              val endFetchBigQuery = System.nanoTime()
              log.info(s"[$table$$$partition] BigQuery duration: ${(endFetchBigQuery - startFetchBigQuery).nanos.toMillis} ms, get ${data.size} rows")
              curFetchSeqno = if (data.isEmpty) curFetchSeqno + perFetch else data.map(_.get("seqno_by_partition").asLong()).max

              // write json to ES
              val startWriteElasticsearch = System.nanoTime()
              val t = typeName match {
                case Some(v) ⇒ v
                case None    ⇒ table
              }
              if (data.nonEmpty) ElasticsearchUtil.bulkInsert(s"$index", t, data)
              val endWriteElasticsearch = System.nanoTime()
              log.info(s"[$table$$$partition] ES duration: ${(endWriteElasticsearch - startWriteElasticsearch).nanos.toMillis} ms")

              // update sync_es table
              val startWriteDb = System.nanoTime()
              val _update = db.run(sync.insertOrUpdate(SyncEsPartitionRow(bqTable, partition, perFetch, curFetchSeqno)))
              Await.result(_update, Duration.Inf)
              val endWriteDb = System.nanoTime()
              log.info(s"[$table$$$partition] DB duration: ${(endWriteDb - startWriteDb).nanos.toMillis} ms")
            }
          }(executionContext)
      }
      Await.result(Future.sequence(_f), Duration.Inf)
    }

    //FetchBigQueryToEs("onlinecrm-like-170913", "Normalization.NORM_about_without_like_0912", typeName = Some("like-170913"))

    FetchBigQueryToEs("onlinecrm-like-171001", "Normalization.NORM_page_like_0930",Some("like"))

    ElasticsearchUtil.flush()
    println("finish")

  }

}
