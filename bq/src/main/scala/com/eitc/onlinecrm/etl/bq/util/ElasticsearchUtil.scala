package com.eitc.onlinecrm.etl.bq.util

import java.net.InetAddress
import java.time.OffsetDateTime

import com.fasterxml.jackson.databind.node.ObjectNode
import org.elasticsearch.action.bulk.{ BackoffPolicy, BulkProcessor, BulkRequest, BulkResponse }
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.unit.{ ByteSizeUnit, ByteSizeValue, TimeValue }
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.transport.client.PreBuiltTransportClient
import org.graysurf.util.Mapper
import org.slf4j.LoggerFactory

object ElasticsearchUtil {

  private val log = LoggerFactory.getLogger("ElasticsearchUtil")

  private val settings = Settings
    .builder()
    .put("cluster.name", "cluster-2")
    .build()

  private val client = new PreBuiltTransportClient(settings)

  private val address = Seq(
    "10.128.1.18",
    "10.128.2.213",
    "10.128.0.67",
    "10.128.1.16",
    "10.128.2.212",
    "10.128.1.17",
    "10.128.1.15"
  )
  address.foreach(ip ⇒ client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(ip), 9300)))

  log.info(client.listedNodes().toString)

  private val bulk = BulkProcessor.builder(client, new BulkProcessor.Listener() {
    override def beforeBulk(executionId: Long, request: BulkRequest): Unit = Unit
    override def afterBulk(executionId: Long, request: BulkRequest, response: BulkResponse): Unit = Unit
    override def afterBulk(executionId: Long, request: BulkRequest, failure: Throwable): Unit = throw failure
  })
    //.setBulkActions(100000)
    .setBulkSize(new ByteSizeValue(15, ByteSizeUnit.MB))
    //.setFlushInterval(TimeValue.timeValueSeconds(180))
    .setConcurrentRequests(10)
    .setBackoffPolicy(
      BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3)
    )
    .build()

  def bulkInsert(index: String, typeName: String, data: Seq[ObjectNode]): Unit = synchronized {

    for (row ← data) {
      //val uuid = row.get("uuid").binaryValue()
      //val fbid = row.get("uuid").asText()
      //val seqno = row.get("seqno").asLong() % 10000

      row.put("time_update", OffsetDateTime.now().toString)
      //log.info(s"$row")

      val request = new IndexRequest(index, typeName)

        .source(row.toString, XContentType.JSON)

      bulk.add(request)
    }
  }

  def flush(): Unit = bulk.flush()

  bulk.flush()
}
