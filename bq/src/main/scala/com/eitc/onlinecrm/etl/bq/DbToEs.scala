package com.eitc.onlinecrm.etl.bq

import java.net.InetAddress
import java.time.{ LocalDateTime, OffsetDateTime }

import com.eitc.onlinecrm.etl.bq.db.FbTables
import com.eitc.onlinecrm.etl.bq.db.json.JsonRow
import com.fasterxml.jackson.databind.JsonNode
import org.elasticsearch.action.ActionFuture
import org.elasticsearch.action.explain.{ ExplainRequest, ExplainResponse }
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.{ MatchAllQueryBuilder, QueryBuilders }
import org.elasticsearch.transport.client.PreBuiltTransportClient
import org.graysurf.util.{ Mapper, Utils }
import org.graysurf.util.db.PostgresConfig
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }

class DbToEs {

  val log = LoggerFactory.getLogger("es")

  import scala.concurrent.ExecutionContext.Implicits.global
  import PostgresConfig.profile.api._

  private val config = PostgresConfig("db_target")
  private val db = config.db

  //
  def getRecords(table: String, startTime: OffsetDateTime, endTime: OffsetDateTime): Future[Seq[JsonRow]] = {
    log.info(s"fetch $table table, startTime: $startTime, endTime: $endTime")
    val _f = db.run(FbTables.query("fb", table, startTime, endTime))
    _f.onComplete {
      case Success(_) ⇒
      case Failure(t) ⇒ log.error("getRecords error", t)
    }
    _f
  }

  def main(args: Array[String]): Unit = {

    log.info("start")
    val settings = Settings
      .builder()
      .put("cluster.name", "production")
      .build()

    val client = new PreBuiltTransportClient(settings)
    client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("35.184.213.56"), 9300))

    println(client.listedNodes())

    val bulk = client.prepareBulk()

    List("about_education", "about_family", "about_place", "about_work", "about_basic").foreach {
      //List("about_basic").foreach {
      table ⇒
        log.info(s"start $table table")
        val f = getRecords(table, OffsetDateTime.parse("2017-05-04T14:00:00+08:00"), OffsetDateTime.parse("2017-05-04T15:00:00+08:00"))
        val data = Await.result(f, Duration.Inf)
        log.info(s"end get data: ${data.size} rows")

        log.info("start bulk insert")

        data.groupBy(_.id).foreach {
          case (id, rows) ⇒
            val node = Mapper.createObjectNode()
            val json = node
              .put("time_update", OffsetDateTime.now().toString)
              .set(table, Mapper.valueToTree(rows.map(_.json)))
            //log.info(s"$json")

            val upsertRequest = new UpdateRequest("jetcw", table, s"$table-$id")
              .parent(id)
              .doc(json.toString, XContentType.JSON)
              .docAsUpsert(true)
            bulk.add(upsertRequest)
        }
        bulk.execute().actionGet()

        //data.grouped(1000).foreach {
        //  d ⇒
        //    for (row ← d) {
        //      val node = Mapper.createObjectNode()
        //      val json = node
        //        .put("time_update", OffsetDateTime.now().toString)
        //        .set(table, row.json)
        //      //log.info(s"$json")
        //
        //      val upsertRequest = new UpdateRequest("jetcw", table, s"$table-${row.id}")
        //        .parent(row.id)
        //        .doc(json.toString, XContentType.JSON)
        //        .docAsUpsert(true)
        //      bulk.add(upsertRequest)
        //    }
        //    bulk.execute().actionGet()
        //    log.info("bulk insert 1000")
        //}

        //data.grouped(1000).foreach {
        //  d ⇒
        //    for (row ← d) {
        //      val node = Mapper.createObjectNode()
        //      val json = node
        //        .put("time_update", OffsetDateTime.now().toString)
        //        .set(table, row.json)
        //      //log.info(s"$json")
        //
        //      val upsertRequest = new UpdateRequest("jetcw", table, s"$table-${row.id}")
        //        .parent(row.id)
        //        .doc(json.toString, XContentType.JSON)
        //        .docAsUpsert(true)
        //      bulk.add(upsertRequest)
        //    }
        //    bulk.execute().actionGet()
        //    log.info("bulk insert 1000")
        //}

        log.info(s"end $table")
    }

    log.info("end")

    //val indexRequest = new IndexRequest("orders", "order", "89757")
    //  .source(Array[Byte](), XContentType.JSON)
    //val upsertRequest = new UpdateRequest("jetcw", "fb", "8975711")
    //  .doc(Map("car" → "tank").asJava)
    //  .docAsUpsert(true)
    //
    //bulk.add(upsertRequest)

    //client.update(upsertRequest).get()

    //// query
    //val query = client
    //  .prepareSearch("orders")
    //  .setQuery(QueryBuilders.matchAllQuery())
    //  //.setFetchSource(true)
    //  .setFetchSource(Array("ID", "a"), null)
    //  .execute()
    //  .actionGet()
    //
    //for (hit ← query.getHits.asScala) {
    //  println(hit.getId)
    //  println(hit.getSource)
    //  println(hit)
    //}

    client.close()
    log.info("end")

  }

}
