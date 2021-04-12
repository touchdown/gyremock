import scalapb.compiler.Version

name := "gyremock"

version := "0.2.1"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe" % "config" % "1.4.1",
  "com.github.tomakehurst" % "wiremock-standalone" % "2.27.2",
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.10.3",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

fork in run := true

scalacOptions ++= Seq("-deprecation")

enablePlugins(AkkaGrpcPlugin)
akkaGrpcGeneratedSources in Compile := Seq(AkkaGrpc.Server)
akkaGrpcExtraGenerators in Compile := Seq(AkkaGrpcRedirectCodeGen, ServicesBuilderCodeGen)

PB.protoSources in Compile := Seq(baseDirectory.value / "proto")
