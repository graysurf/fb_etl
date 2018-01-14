package com.eitc.onlinecrm.etl.bq.db.json

import java.time.OffsetDateTime

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

case class JsonRow(id: String, json: JsonNode, timeInsert: OffsetDateTime, timeLastUpdate: Option[OffsetDateTime]) {
  def toJsonNode: JsonNode = {
    json.asInstanceOf[ObjectNode]
      .put("time_insert", timeInsert.toString)
      .put("time_last_update", if (timeLastUpdate.nonEmpty) timeLastUpdate.toString else null)
  }
}
