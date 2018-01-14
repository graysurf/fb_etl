package org.graysurf.util.db

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.github.tminglei.slickpg.{ ExPostgresProfile, json, utils }
import slick.jdbc._
import slick.lifted.Rep

import scala.reflect.classTag

trait PgJacksonSupport extends json.PgJsonExtensions with utils.PgCommonJdbcTypes { driver: PostgresProfile ⇒

  val mapper = new ObjectMapper

  def pgjson = "jsonb"

  trait SimpleJsonCodeGenSupport {
    // register types to let `ExModelBuilder` find them
    driver match {
      case p: ExPostgresProfile ⇒
        p.bindPgTypeToScala("json", classTag[JsonNode])
        p.bindPgTypeToScala("jsonb", classTag[JsonNode])
      case _ ⇒
    }
  }

  /// alias
  trait JsonImplicits extends SimpleJsonImplicits

  trait SimpleJsonImplicits extends SimpleJsonCodeGenSupport {
    implicit val simpleJsonTypeMapper: JdbcType[JsonNode] =
      new GenericJdbcType[JsonNode](
        pgjson,
        (v) ⇒ mapper.readTree(v),
        (v) ⇒ v.toString,
        hasLiteralForm = false
      )

    implicit def simpleJsonColumnExtensionMethods(c: Rep[JsonNode]): JsonColumnExtensionMethods[JsonNode, JsonNode] = {
      new JsonColumnExtensionMethods[JsonNode, JsonNode](c)
    }

    implicit def simpleJsonOptionColumnExtensionMethods(c: Rep[Option[JsonNode]]): JsonColumnExtensionMethods[JsonNode, Option[JsonNode]] = {
      new JsonColumnExtensionMethods[JsonNode, Option[JsonNode]](c)
    }
  }

  trait SimpleJsonPlainImplicits extends SimpleJsonCodeGenSupport {

    import utils.PlainSQLUtils._

    implicit class PgJsonPositionedResult(r: PositionedResult) {
      def nextJson(): JsonNode = nextJsonOption().orNull

      def nextJsonOption(): Option[JsonNode] = r.nextStringOption().map(mapper.readTree)
    }

    implicit val getJson: GetResult[JsonNode] = mkGetResult(_.nextJson())
    implicit val getJsonOption: GetResult[Option[JsonNode]] = mkGetResult(_.nextJsonOption())
    implicit val setJson: SetParameter[JsonNode] = mkSetParameter[JsonNode](pgjson, _.toString)
    implicit val setJsonOption: SetParameter[Option[JsonNode]] = mkOptionSetParameter[JsonNode](pgjson, _.toString)
  }

}
