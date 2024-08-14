import scala.collection.Seq

val scala3Version = "3.4.3"

inThisBuild(
  List(
    organization := "in.rcard.sus4s",
    homepage     := Some(url("https://github.com/rcardin")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "rcardin",
        "Riccardo Cardin",
        "riccardo DOT cardin AT gmail.com",
        url("https://github.com/rcardin/sus4s")
      )
    )
  )
)

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository                 := "https://s01.oss.sonatype.org/service/local"
sonatypeProfileName                := "in.rcard"

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
    val scalatestVersion = "3.2.18"
    val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion
  }

lazy val commonDependencies = Seq(
  dependencies.scalatest % Test
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)
