import sbt.Keys._


name := """travel4me"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)

libraryDependencies += "com.evojam" %% "play-elastic4s" % "0.3.1"
libraryDependencies += "com.github.tototoshi" %% "play-json4s-jackson" % "0.5.0"
libraryDependencies += "com.github.tototoshi" %% "play-json4s-test-jackson" % "0.5.0" % "test"
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.3m"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.3.0"

herokuAppName in Compile := "peaceful-spire-72419"
