package org.graysurf.util.db

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.{ Config, ConfigFactory }
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcBackend

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object PostgresConfig {

  val profile = ExtendPostgresProfile

  private[this] val dbList = new ConcurrentHashMap[String, DatabaseConfig[ExtendPostgresProfile]].asScala

  def apply(dbName: String): DatabaseConfig[ExtendPostgresProfile] = synchronized {
    if (dbList.keys.toSet.contains(dbName)) {
      dbList(dbName)
    } else {
      val config: DatabaseConfig[ExtendPostgresProfile] = new PostgresConfig(dbName)
      dbList += dbName → config
      config
    }
  }

}

class PostgresConfig(dbName: String) extends DatabaseConfig[ExtendPostgresProfile] {

  private[this] val log = LoggerFactory.getLogger(this.getClass.getName)

  private[this] val _databaseConfig = {
    lazy val _config = DatabaseConfig.forConfig[ExtendPostgresProfile](dbName, ConfigFactory.load("database.conf"))
    try {
      _config
    } catch {
      case t: Throwable ⇒
        log.error("load db config error", t)
        System.exit(0)
    }
    _config
  }

  override val config: Config = _databaseConfig.config

  override val db: JdbcBackend#DatabaseDef = {
    lazy val _db = _databaseConfig.db
    try {
      val _connect = _db.run {
        import profile.api._
        sql"select version()".as[String].head
      }
      log.info(Await.result(_connect, 5 seconds))
    } catch {
      case t: Throwable ⇒
        log.error("connect db error", t)
        System.exit(0)
    }
    _db
  }

  override val profile: ExtendPostgresProfile = _databaseConfig.profile
  override val driver: ExtendPostgresProfile = _databaseConfig.profile

  override val profileName: String = _databaseConfig.profileName

  override val profileIsObject: Boolean = _databaseConfig.profileIsObject

}
