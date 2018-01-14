package com.eitc.onlinecrm.etl.bq.db

import java.time.OffsetDateTime

import com.eitc.onlinecrm.etl.bq.db.json.JsonRow
import com.fasterxml.jackson.databind.JsonNode

object FbTables {

  import org.graysurf.util.db.PostgresConfig.profile.plainApi._
  import scala.concurrent.ExecutionContext.Implicits.global

  def query(schema: String, table: String, start: OffsetDateTime, end: OffsetDateTime, pk: String = "fbid"): DBIOAction[Vector[JsonRow], NoStream, Effect] = {
    sql"""
         |select #$pk, row_to_json(#$table) as json, time_insert, time_last_update
         |from #$schema.#$table
         |where time_insert > $start and time_insert <= $end
"""
      .stripMargin
      .as[(String, JsonNode, OffsetDateTime, Option[OffsetDateTime])]
      .map(_.map(JsonRow.tupled))
  }
}
