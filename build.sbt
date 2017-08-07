lazy val `example-aws-javasdk` =
  project.in(file("."))
  .aggregate(
    s3
  )
  .settings(commonSettings: _*)

lazy val s3 =
  project.in(file("s3"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies
      ++= commonLibraries
  )



/**
 *
 * Commons
 *
 */

lazy val commonSettings = Seq(
  organization := "com.github.jw3",
  version := "0.1",
  scalaVersion := "2.11.11",

  scalacOptions ++= Seq(
    "-encoding", "UTF-8",

    "-feature",
    "-unchecked",
    "-deprecation",

    "-language:postfixOps",
    "-language:implicitConversions",

    "-Ywarn-unused-import",
    "-Xfatal-warnings",
    "-Xlint:_"
  )
)

lazy val akkaVersion = "2.5.2"
lazy val akkaHttpVersion = "10.0.7"
lazy val scalatestVersion = "3.0.3"

lazy val commonLibraries = {
  Seq(
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.83",
    "com.iheart" %% "ficus" % "1.4.0",
    "com.elderresearch" %% "ssc" % "1.0.0",

    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

    "org.scalactic" %% "scalactic" % scalatestVersion % Test,
    "org.scalatest" %% "scalatest" % scalatestVersion % Test
  )
}
