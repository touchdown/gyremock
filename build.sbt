import scalapb.compiler.Version

name := "gyremock"

version := "0.2.0"

scalaVersion := "2.12.11"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe" % "config" % "1.4.1",
  "com.github.tomakehurst" % "wiremock-standalone" % "2.27.2",
  // runtime classpath not found on scalapb/Message
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.10.2",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

fork in run := true

scalacOptions ++= Seq("-deprecation")

enablePlugins(AkkaGrpcPlugin)
akkaGrpcGeneratedSources in Compile := Seq(AkkaGrpc.Server)
akkaGrpcExtraGenerators in Compile := Seq(AkkaGrpcRedirectCodeGen, ServicesBuilderCodeGen)

PB.protoSources in Compile := Seq(baseDirectory.value / "proto")

resolvers += Resolver.bintrayRepo("akka", "snapshots")
