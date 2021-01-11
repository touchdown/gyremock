import scalapb.compiler.Version

name := "gyremock"

version := "0.1.1"

scalaVersion := "2.12.12"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe" % "config" % "1.4.1",
  "com.github.tomakehurst" % "wiremock-standalone" % "2.27.2",
  "io.grpc" % "protoc-gen-grpc-java" % Version.grpcJavaVersion asProtocPlugin(),
  "io.grpc" % "grpc-services" % Version.grpcJavaVersion,
  "io.grpc" % "grpc-netty"  % "1.21.0",
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.9.3",
  // remove java8-compat after update to scala 2.13
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

fork in run := true

PB.protoSources in Compile := Seq(baseDirectory.value / "proto")
PB.targets in Compile := Seq(
  scalapb.gen() -> ((sourceManaged in Compile).value),
  GrpcRedirectCodeGen -> ((sourceManaged in Compile).value)
)
