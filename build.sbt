name := "gyremock"

version := "0.3.1"

scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe" % "config" % "1.4.1",
  "com.github.tomakehurst" % "wiremock-jre8-standalone" % "2.33.1",
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.11.1",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

run / fork := true

scalacOptions ++= Seq("-deprecation")

// these run codegen first before compiling
enablePlugins(AkkaGrpcPlugin)
Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server)
Compile / akkaGrpcExtraGenerators := Seq(AkkaGrpcRedirectCodeGen, ServicesBuilderCodeGen)

Compile / PB.protocOptions += "--experimental_allow_proto3_optional"

Compile / PB.protoSources += baseDirectory.value / "proto"

githubWorkflowTargetBranches := Seq("main")
githubWorkflowGeneratedCI := Workflow(scalaVersion.value, version.value)
