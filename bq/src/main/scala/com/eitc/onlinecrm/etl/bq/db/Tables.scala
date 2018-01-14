package com.eitc.onlinecrm.etl.bq.db

import slick.lifted.TableQuery

object Tables {

  //val Test: TableQuery[JsonTable] = TableQuery(new JsonTable(_, Some("fb"), "test"))

  def FetchTime(schema: String): TableQuery[BigQueryFetchTimeTable] = new TableQuery(tag ⇒ new BigQueryFetchTimeTable(tag, Some(schema)))

  def SyncEs(): TableQuery[SyncEsTable] = new TableQuery(tag ⇒ new SyncEsTable(tag))

  def SyncEsPartition(): TableQuery[SyncEsPartitionTable] = new TableQuery(tag ⇒ new SyncEsPartitionTable(tag))

}
