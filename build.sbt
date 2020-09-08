import sbt._

name := "coup-game-server"

version := "0.1"

scalaVersion := "2.12.4"

mainClass in Compile := Some("com.coupgame.server.GameServerMain")
enablePlugins(SbtTwirl)
enablePlugins(JavaAppPackaging)

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-core" % "10.0.5",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.5",
  "com.typesafe.akka" %% "akka-stream" % "2.4.17",
  "com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.google.inject" % "guice" % "4.1.0",
  "com.sendgrid" % "sendgrid-java" % "4.0.1"
)