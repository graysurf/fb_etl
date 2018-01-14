package com.eitc.onlinecrm.etl.bq.db.json

import java.time.OffsetDateTime

import com.fasterxml.jackson.databind.JsonNode
import org.graysurf.util.db.PostgresConfig.profile.api._
import slick.dbio.Effect.Read
import slick.lifted.{ ProvenShape, TableQuery }
import slick.sql.FixedSqlStreamingAction

object JsonTable {

  implicit class QueryImplicit(table: TableQuery[JsonTable]) {
    def query(start: OffsetDateTime, end: OffsetDateTime): FixedSqlStreamingAction[Seq[JsonRow], JsonRow, Read] = {
      table.filter(t ⇒ t.timeInsert >= start && t.timeInsert <= end).result
    }
  }

}

class JsonTable(_tableTag: Tag, _tableSchema: Option[String], _tableName: String) extends Table[JsonRow](_tableTag, _tableSchema, _tableName) {

  override def * : ProvenShape[JsonRow] = (id, json, timeInsert, timeLastUpdate) <> (JsonRow.tupled, JsonRow.unapply)

  lazy val id: Rep[String] = column[String]("fbid", O.PrimaryKey)
  lazy val json: Rep[JsonNode] = column[JsonNode]("json")
  lazy val timeInsert: Rep[OffsetDateTime] = column[OffsetDateTime]("time_insert")
  lazy val timeLastUpdate: Rep[Option[OffsetDateTime]] = column[Option[OffsetDateTime]]("time_last_update", O.Default(None))

}
