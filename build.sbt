import Dependencies.{test, _}

name := "aggregate-service"

version := "0.1"

scalaVersion := "2.12.4"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++=
      `cats-effect` ++
        circe ++
        http4s ++
        fs2 ++
        `iso-country` ++
        log ++
        `pure-config` ++
        test
  )

scalacOptions in ThisBuild ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-encoding",
  "UTF-8",
  "-Xfatal-warnings",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Ypartial-unification"
)
