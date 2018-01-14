package com.eitc.onlinecrm.etl.bq.db

import java.time.OffsetDateTime

import com.eitc.onlinecrm.etl.bq.db.SyncEsTable.SyncEsRow
import org.graysurf.util.db.PostgresConfig.profile.api._
import slick.lifted.ProvenShape

object SyncEsTable {

  import slick.jdbc.{ GetResult ⇒ GR }

  final case class SyncEsRow(name: String, perFetch: Int, lastFetchSeqno: Long)

  implicit def GetResultSyncEsRow(implicit e0: GR[String], e1: GR[Int], e2: GR[Long]): GR[SyncEsRow] = GR {
    prs ⇒
      import prs._
      SyncEsRow.tupled((<<[String], <<[Int], <<[Long]))
  }

}

class SyncEsTable(_tableTag: Tag) extends Table[SyncEsRow](_tableTag, Some("bq"), "sync_es") {

  def * : ProvenShape[SyncEsRow] = (name, perFetch, lastFetchSeqno) <> (SyncEsRow.tupled, SyncEsRow.unapply)

  /** Database column seqno SqlType(serial), AutoInc */
  val seqno: Rep[Int] = column[Int]("seqno", O.AutoInc)
  /** Database column table SqlType(int4), PrimaryKey */
  val name: Rep[String] = column[String]("name", O.PrimaryKey)
  /** Database column time_last_fetch SqlType(timestamptz), Default(None) */
  val perFetch: Rep[Int] = column[Int]("per_fetch")
  /** Database column time_last_fetch SqlType(timestamptz), Default(None) */
  val lastFetchSeqno: Rep[Long] = column[Long]("last_fetch_seqno")
  /** Database column time_insert SqlType(timestamptz) */
  val timeInsert: Rep[OffsetDateTime] = column[OffsetDateTime]("time_insert")
  /** Database column time_last_update SqlType(timestamptz), Default(None) */
  val timeLastUpdate: Rep[Option[OffsetDateTime]] = column[Option[OffsetDateTime]]("time_last_update", O.Default(None))

}