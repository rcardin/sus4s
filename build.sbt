import scala.collection.Seq

val scala3Version = "3.4.1"

lazy val core = project
  .settings(
    name := "core",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val root = (project in file("."))
  .aggregate(core)
  .settings(
    scalaVersion := scala3Version,
  )

lazy val dependencies =
  new {
    val scalatestVersion = "3.2.17"
    val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion
  }

lazy val commonDependencies = Seq(
  dependencies.scalatest % Test
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)
