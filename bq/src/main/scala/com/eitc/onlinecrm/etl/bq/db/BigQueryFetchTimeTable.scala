package com.eitc.onlinecrm.etl.bq.db

import java.time.OffsetDateTime

import com.eitc.onlinecrm.etl.bq.db.BigQueryFetchTimeTable._
import org.graysurf.util.db.PostgresConfig.profile.api._
import slick.lifted.ProvenShape

object BigQueryFetchTimeTable {

  import slick.jdbc.{ GetResult ⇒ GR }

  /**
   * Entity class storing rows of table Fetchtime
   *
   * @param table         Database column table SqlType(text), PrimaryKey
   * @param timeLastFetch Database column time_last_fetch SqlType(timestamptz), Default(None)
   */
  final case class BigQueryFetchTimeRow(table: String, timeLastFetch: Option[OffsetDateTime] = None)

  /** GetResult implicit for fetching FetchtimeRow objects using plain SQL queries */
  implicit def GetResultFetchTimeRow(implicit e0: GR[Int], e1: GR[Option[OffsetDateTime]], e2: GR[OffsetDateTime]): GR[BigQueryFetchTimeRow] = GR {
    prs ⇒
      import prs._
      BigQueryFetchTimeRow.tupled((<<[String], <<?[OffsetDateTime]))
  }
}

/** Table description of table fetchtime. Objects of this class serve as prototypes for rows in queries. */
class BigQueryFetchTimeTable(_tableTag: Tag, _tableSchema: Option[String]) extends Table[BigQueryFetchTimeRow](_tableTag, _tableSchema, "fetchtime") {
  def * : ProvenShape[BigQueryFetchTimeRow] = (table, timeLastFetch) <> (BigQueryFetchTimeRow.tupled, BigQueryFetchTimeRow.unapply)

  /** Database column seqno SqlType(serial), AutoInc */
  val seqno: Rep[Int] = column[Int]("seqno", O.AutoInc)
  /** Database column table SqlType(int4), PrimaryKey */
  val table: Rep[String] = column[String]("table", O.PrimaryKey)
  /** Database column time_last_fetch SqlType(timestamptz), Default(None) */
  val timeLastFetch: Rep[Option[OffsetDateTime]] = column[Option[OffsetDateTime]]("time_last_fetch", O.Default(None))
  /** Database column time_insert SqlType(timestamptz) */
  val timeInsert: Rep[OffsetDateTime] = column[OffsetDateTime]("time_insert")
  /** Database column time_last_update SqlType(timestamptz), Default(None) */
  val timeLastUpdate: Rep[Option[OffsetDateTime]] = column[Option[OffsetDateTime]]("time_last_update", O.Default(None))
}

