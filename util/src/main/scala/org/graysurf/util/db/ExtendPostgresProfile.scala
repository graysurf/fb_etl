package org.graysurf.util.db

import com.github.tminglei.slickpg.{ PgArraySupport, _ }
import slick.basic.Capability
import slick.jdbc._

private[db] trait ExtendPostgresProfile extends ExPostgresProfile
    with PgArraySupport
    with PgDate2Support
    with PgJacksonSupport {

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api = ExtendAPI

  object ExtendAPI extends API with JsonImplicits with ArrayImplicits with DateTimeImplicits

  val plainApi = ExtendPlainAPI

  object ExtendPlainAPI extends API with SimpleJsonPlainImplicits with SimpleArrayPlainImplicits with Date2DateTimePlainImplicits

}

private[db] object ExtendPostgresProfile extends ExtendPostgresProfile