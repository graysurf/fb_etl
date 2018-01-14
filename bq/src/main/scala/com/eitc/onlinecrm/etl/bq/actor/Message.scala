package com.eitc.onlinecrm.etl.bq.actor

import java.time.OffsetDateTime

case object FetchMessage

case class WriteMessage(time: OffsetDateTime, data: String)

case class UpdateFetchTimeMessage(time: OffsetDateTime)

case class WriteFetchTimeIfNotExistMessage(time: OffsetDateTime)

case class WriteStorageDataToBqMessage(source: String)

case class DeleteStorageDataMessage(source: String)

case object FailureMessage

case object FinishMessage
