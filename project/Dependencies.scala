import sbt.{CrossVersion, compilerPlugin, _}

object Dependencies {

  object Versions {
    val circe = "0.12.3"
    val http4s = "0.20.23"
    val `cats-effect-scala-test` = "0.5.2"
    val logback = "1.2.3"
    val fs2 = "2.4.6"
    val pureconfig = "0.14.0"
  }

  val `cats-effect` = Seq(
    "org.typelevel" %% "cats-effect" % "2.2.0" withSources () withJavadoc (),
    "io.chrisdavenport" %% "cats-effect-time" % "0.1.2"
  )

  val `circe` = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % Versions.circe)

  val http4s = Seq(
    "org.http4s" %% "http4s-blaze-server",
    "org.http4s" %% "http4s-blaze-client",
    "org.http4s" %% "http4s-circe",
    "org.http4s" %% "http4s-dsl"
  ).map(_ % Versions.http4s)

  val log = Seq(
    "ch.qos.logback" % "logback-classic"
  ).map(_ % Versions.logback)

  val test = Seq(
    "com.codecommit" %% "cats-effect-testing-scalatest" % Versions.`cats-effect-scala-test` % "test"
  )

  val `pure-config` = Seq(
    "com.github.pureconfig" %% "pureconfig",
    "com.github.pureconfig" %% "pureconfig-cats-effect"
  ).map(_ % Versions.pureconfig)

  val fs2 = Seq(
    "co.fs2" %% "fs2-core",
    "co.fs2" %% "fs2-io",
    "co.fs2" %% "fs2-reactive-streams",
    "co.fs2" %% "fs2-experimental"
  ).map(_ % Versions.fs2)

  val `iso-country` = Seq(
    "com.vitorsvieira" %% "scala-iso" % "0.1.2"
  )

}
