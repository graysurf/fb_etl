import com.eitc.onlinecrm.Dependencies._
import com.eitc.onlinecrm.Settings
import sbt.Package.ManifestAttributes


lazy val root = (project in file("."))
  .aggregate(bq)
  .settings(Settings.testedBaseSettings: _*)
  .settings(
    name := "etl",
    fork in run := true,
    version := "1.0"
  )


lazy val bq = (project in file("bq"))
  .dependsOn(util % "test->test;compile->compile")
  .settings(Settings.testedBaseSettings: _*)
  .settings(
    name := "bq",
    fork in run := true,
    version := "1.0",
    libraryDependencies++= ElasticsearchProject
  )

lazy val util = (project in file("util"))
  .settings(Settings.testedBaseSettings: _*)
  .settings(
    name := "scala_util",
    fork in run := true,
    version := "1.0",
    libraryDependencies ++= AkkaProject ++ DbProject ++ Gcp ++ Scopt ++ ScalaUtil.value
  )
