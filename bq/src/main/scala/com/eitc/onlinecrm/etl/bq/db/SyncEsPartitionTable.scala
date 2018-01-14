package com.eitc.onlinecrm.etl.bq.db

import java.time.OffsetDateTime

import com.eitc.onlinecrm.etl.bq.db.SyncEsPartitionTable.SyncEsPartitionRow
import org.graysurf.util.db.PostgresConfig.profile.api._
import slick.lifted.ProvenShape
import slick.sql.SqlAction

object SyncEsPartitionTable {

  import slick.jdbc.{ GetResult ⇒ GR }

  final case class SyncEsPartitionRow(name: String, partition: String, perFetch: Int, lastFetchSeqno: Long)

  implicit def GetResultSyncEPartitionsRow(implicit e0: GR[String], e1: GR[Int], e2: GR[Long]): GR[SyncEsPartitionRow] = GR {
    prs ⇒
      import prs._
      SyncEsPartitionRow.tupled((<<[String], <<[String], <<[Int], <<[Long]))
  }

  def insertIfNotExist(name: String, partition: String): SqlAction[Int, NoStream, Effect] =
    sqlu"""
           insert into bq.sync_es_partition
                                  (name, partition)
                              select $name, $partition
                              where
                                  not exists (
                                      select name from bq.sync_es_partition where name = $name and partition = $partition
                                  );
        """
}

class SyncEsPartitionTable(_tableTag: Tag) extends Table[SyncEsPartitionRow](_tableTag, Some("bq"), "sync_es_partition") {

  def * : ProvenShape[SyncEsPartitionRow] = (name, partition, perFetch, lastFetchSeqno) <> (SyncEsPartitionRow.tupled, SyncEsPartitionRow.unapply)

  /** Database column seqno SqlType(serial), AutoInc */
  val seqno: Rep[Int] = column[Int]("seqno", O.AutoInc)
  /** Database column table SqlType(int4), PrimaryKey */
  val name: Rep[String] = column[String]("name")
  /** Database column table SqlType(int4), PrimaryKey */
  val partition: Rep[String] = column[String]("partition")
  /** Database column time_last_fetch SqlType(timestamptz), Default(None) */
  val perFetch: Rep[Int] = column[Int]("per_fetch")
  /** Database column time_last_fetch SqlType(timestamptz), Default(None) */
  val lastFetchSeqno: Rep[Long] = column[Long]("last_fetch_seqno")
  /** Database column time_insert SqlType(timestamptz) */
  val timeInsert: Rep[OffsetDateTime] = column[OffsetDateTime]("time_insert")
  /** Database column time_last_update SqlType(timestamptz), Default(None) */
  val timeLastUpdate: Rep[Option[OffsetDateTime]] = column[Option[OffsetDateTime]]("time_last_update", O.Default(None))

  def pk = primaryKey("pk", (name, partition))

}