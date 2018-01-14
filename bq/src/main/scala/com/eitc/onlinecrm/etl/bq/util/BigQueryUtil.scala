package com.eitc.onlinecrm.etl.bq.util

import java.time.{ LocalDateTime, ZoneOffset }
import java.util

import com.fasterxml.jackson.databind.node.{ ArrayNode, ObjectNode }
import com.google.cloud.bigquery.FieldValue.Attribute
import com.google.cloud.bigquery.TimePartitioning.Type
import com.google.cloud.bigquery._
import org.graysurf.util.Mapper
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Try }

object BigQueryUtil {

  private val log = LoggerFactory.getLogger("BigQueryUtil")

  val bq: BigQuery = BigQueryOptions.getDefaultInstance.getService

  def getMaxPartitionSeqno(dataSet: String, table: String, partition: scala.Option[String] = None): Long = {

    val sql = partition match {
      case Some(p) ⇒
        s"SELECT max(seqno_by_partition) FROM $dataSet.$table WHERE _PARTITIONTIME = TIMESTAMP('$p')"
      case None ⇒
        s"SELECT max(seqno_by_partition) FROM $dataSet.$table"
    }

    //log.info(sql)

    var result: Try[Long] = Failure(new Exception)
    while (result.isFailure) {
      val queryRequest = QueryRequest
        .newBuilder(sql)
        .setUseLegacySql(false)
        .setPageSize(1000L)
        .build()

      val request: QueryResponse = bq.query(queryRequest)
      val job = bq.getJob(request.getJobId)
      job.waitFor()
      if (job.getStatus.getError != null) throw new Exception(job.getStatus.getError.toString)

      result = Try(request.getResult
        .getValues
        .asScala
        .head
        .asScala
        .head
        .getLongValue)
    }

    result.get
  }

  def getMaxSeqno(dataSet: String, table: String, partition: scala.Option[String] = None): Long = {

    val sql = partition match {
      case Some(p) ⇒
        s"SELECT max(seqno) FROM $dataSet.$table WHERE _PARTITIONTIME = TIMESTAMP('$p')"
      case None ⇒
        s"SELECT max(seqno) FROM $dataSet.$table"
    }

    //log.info(sql)

    val queryRequest = QueryRequest
      .newBuilder(sql)
      .setUseLegacySql(false)
      .setPageSize(1000L)
      .build()

    val request: QueryResponse = bq.query(queryRequest)
    val job = bq.getJob(request.getJobId)
    job.waitFor()
    if (job.getStatus.getError != null) throw new Exception(job.getStatus.getError.toString)

    val maxSeqno = request.getResult
      .getValues
      .asScala
      .head
      .asScala
      .head
      .getLongValue

    maxSeqno
  }

  def getPartition(dataSet: String, table: String): Seq[String] = {

    val queryRequest = QueryRequest
      .newBuilder(s"SELECT DISTINCT _PARTITIONTIME AS pt FROM $dataSet.$table ORDER BY pt")
      .setUseLegacySql(false)
      .setPageSize(1000L)
      .build()

    val request: QueryResponse = bq.query(queryRequest)
    val job = bq.getJob(request.getJobId)
    job.waitFor()
    if (job.getStatus.getError != null) throw new Exception(job.getStatus.getError.toString)

    val partitions = request.getResult
      .getValues
      .asScala
      .map(_.asScala.head)
      .toList
      .map {
        v ⇒
          val second = v.getTimestampValue / 1000000
          val nanosecond = ((v.getTimestampValue % 1000000) * 1000).toInt
          LocalDateTime.ofEpochSecond(second, nanosecond, ZoneOffset.ofHours(0)).toLocalDate.toString
      }
    partitions
  }

  def getJsonDataByPartition(dataSet: String, table: String, perFetch: Int, lastFetchSeqno: Long, partition: scala.Option[String] = None): List[ObjectNode] = {

    val listBuffer = ListBuffer.empty[ObjectNode]

    val tableSchema = bq.getTable(TableId.of(dataSet, table))
      .getDefinition
      .asInstanceOf[StandardTableDefinition]
      .getSchema
      .getFields
      .asScala

    //log.info(tableSchema.toString())

    val fromIndex = lastFetchSeqno
    val toIndex = lastFetchSeqno + perFetch

    val sql = partition match {
      case Some(p) ⇒
        s"SELECT * FROM $dataSet.$table WHERE seqno_by_partition > $fromIndex AND seqno_by_partition <= $toIndex AND _PARTITIONTIME = TIMESTAMP('$p') "
      case None ⇒
        s"SELECT * FROM $dataSet.$table WHERE seqno > $fromIndex AND seqno <= $toIndex"
    }

    //log.info(sql)

    val queryRequest = QueryRequest
      .newBuilder(sql)
      .setUseLegacySql(false)
      .setPageSize(1000L)
      .build()

    val request = bq.query(queryRequest)
    val job = bq.getJob(request.getJobId)
    job.waitFor()
    if (job.getStatus.getError != null) throw new Exception(job.getStatus.getError.toString)

    var queryResult = request.getResult

    while (queryResult != null) {
      for (row ← queryResult.getValues.asScala) {
        val json = schemaToJson(row.asScala.toList, tableSchema)
        //log.info(json.toString)
        listBuffer += json
      }
      queryResult = queryResult.getNextPage
    }

    listBuffer.toList
  }

  def writeData(dataSet: String, tableName: String, sourcePath: String): Unit = {
    val tableId = TableId.of(dataSet, tableName)
    val table = bq.getTable(tableId)
    val job = table.load(FormatOptions.json(), sourcePath).waitFor()
    if (job.getStatus.getError != null) throw new Exception(job.getStatus.getError.toString)
  }

  def copyTable(name: String, fromDataSet: String, toDataSet: String): Table = {
    val fromTable = bq.getTable(TableId.of(fromDataSet, name))
    val schema = fromTable.getDefinition.asInstanceOf[StandardTableDefinition].getSchema
    val tableId = TableId.of(toDataSet, name)
    val tableDefinition = StandardTableDefinition
      .newBuilder()
      .setTimePartitioning(TimePartitioning.of(Type.DAY))
      .setSchema(schema)
      .build()
    val tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build()
    bq.create(tableInfo)
  }

  private def schemaToJson(data: Seq[FieldValue], schema: Seq[Field]): ObjectNode = {

    val json: ObjectNode = Mapper.createObjectNode()

    for ((row, field) ← data.zip(schema)) {

      val isNull = row.isNull
      val isRecord = field.getType.getValue == LegacySQLTypeName.RECORD
      val isRepeated = field.getMode == Field.Mode.REPEATED

      (isNull, isRecord, isRepeated) match {
        case (true, _, _) ⇒
          json.putNull(field.getName)
        case (false, false, _) ⇒
          writeColumnToJson(json, field.getName, row, field.getType.getValue)
        case (false, true, false) ⇒
          val subJson = schemaToJson(row.getRecordValue.asScala.toList, field.getFields.asScala)
          json.set(field.getName, subJson)
        case (false, true, true) ⇒
          val subJson = Mapper.createArrayNode()
          json.set(field.getName, subJson)
          for (fieldValue ← row.getRepeatedValue.asScala) {
            val subRepeatJson = schemaToJson(fieldValue.getRecordValue.asScala.toList, field.getFields.asScala)
            subJson.add(subRepeatJson)
          }
      }
    }
    json
  }

  private def writeColumnToJson(json: ObjectNode, columnName: String, fieldValue: FieldValue, format: LegacySQLTypeName): Unit = {

    if (fieldValue.getAttribute == Attribute.REPEATED) {
      val fieldValueList = fieldValue.getRepeatedValue.asScala
      if (fieldValueList.isEmpty) {
        json.putNull(columnName)
      } else {
        val arrayNode = json.putArray(columnName)
        for (value ← fieldValueList.map(_.getValue)) {
          putArrayValue(arrayNode, value)
        }
      }
    } else {
      putValue(json)
    }

    def putValue(json: ObjectNode): Unit = {
      format match {
        case LegacySQLTypeName.STRING  ⇒ json.put(columnName, fieldValue.getStringValue)
        case LegacySQLTypeName.BYTES   ⇒ json.put(columnName, fieldValue.getBytesValue)
        case LegacySQLTypeName.INTEGER ⇒ json.put(columnName, fieldValue.getLongValue)
        case LegacySQLTypeName.FLOAT   ⇒ json.put(columnName, fieldValue.getDoubleValue)
        case LegacySQLTypeName.BOOLEAN ⇒ json.put(columnName, fieldValue.getBooleanValue)
        case LegacySQLTypeName.TIMESTAMP ⇒
          val second = fieldValue.getTimestampValue / 1000000
          val nanosecond = ((fieldValue.getTimestampValue % 1000000L) * 1000).toInt
          val timestamp = LocalDateTime.ofEpochSecond(second, nanosecond, ZoneOffset.ofHours(0)).toString
          json.put(columnName, timestamp)
        case LegacySQLTypeName.DATE     ⇒ json.put(columnName, fieldValue.getStringValue)
        case LegacySQLTypeName.TIME     ⇒ json.put(columnName, fieldValue.getStringValue)
        case LegacySQLTypeName.DATETIME ⇒ json.put(columnName, fieldValue.getStringValue)
        //case LegacySQLTypeName.RECORD   ⇒ json.put(columnName, fieldValue.getValue.toString)
        case _                          ⇒ throw new Exception("unknown BigQueryColumnType")
      }
    }

    def putArrayValue(json: ArrayNode, value: Any): Unit = {
      format match {
        case LegacySQLTypeName.STRING  ⇒ value.asInstanceOf[String].split(", ").foreach(json.add)
        case LegacySQLTypeName.BYTES   ⇒ value.asInstanceOf[String].split(", ").foreach(v ⇒ json.add(v.getBytes))
        case LegacySQLTypeName.INTEGER ⇒ value.asInstanceOf[String].split(", ").foreach(v ⇒ json.add(v.toLong))
        case LegacySQLTypeName.FLOAT   ⇒ value.asInstanceOf[String].split(", ").foreach(v ⇒ json.add(v.toFloat))
        case LegacySQLTypeName.BOOLEAN ⇒ value.asInstanceOf[String].split(", ").foreach(v ⇒ json.add(v.toBoolean))
        case LegacySQLTypeName.TIMESTAMP ⇒
          value.asInstanceOf[String].split(", ").foreach {
            v ⇒
              val second = v.toLong / 1000000
              val nanosecond = ((v.toLong % 1000000) * 1000).toInt
              val timestamp = LocalDateTime.ofEpochSecond(second, nanosecond, ZoneOffset.ofHours(0)).toString
              json.add(timestamp)
          }
        case LegacySQLTypeName.DATE     ⇒ value.asInstanceOf[String].split(", ").foreach(json.add)
        case LegacySQLTypeName.TIME     ⇒ value.asInstanceOf[String].split(", ").foreach(json.add)
        case LegacySQLTypeName.DATETIME ⇒ value.asInstanceOf[String].split(", ").foreach(json.add)
        //case LegacySQLTypeName.RECORD   ⇒ value.asInstanceOf[String].split(", ").foreach(json.add)
        case _                          ⇒ throw new Exception("unknown BigQueryColumnType")
      }
    }

  }

}

