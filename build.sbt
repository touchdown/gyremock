import sbt.Keys.scalaVersion
import sbt.addSbtPlugin

ThisBuild / organization := "io.github.touchdown"

ThisBuild / dynverVTagPrefix := true
ThisBuild / dynverSeparator := "-"
// append -SNAPSHOT to version when isSnapshot
ThisBuild / dynverSonatypeSnapshots := true
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo := sonatypePublishToBundle.value

// enable scalafix
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / scalacOptions ++= Seq("-Ywarn-unused")

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / crossScalaVersions := Dependencies.Versions.CrossScalaForLib
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")

ThisBuild / githubWorkflowPublishTargetBranches := Seq()

val gyremockRuntimeName = "gyremock-runtime"
val akkaGrpcVersion = "2.1.6"

lazy val codegen = Project(id = "gyremock-codegen", base = file("codegen"))
  .settings(resolvers += Resolver.sbtPluginRepo("releases"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head,
    addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % akkaGrpcVersion),
    buildInfoKeys ++= Seq[BuildInfoKey](organization, name, version, scalaVersion, sbtVersion),
    buildInfoKeys += "runtimeArtifactName" -> gyremockRuntimeName,
    buildInfoPackage := "gyremock.gen"
  )

lazy val sbtPlugin = Project(id = "sbt-gyremock", base = file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForPlugin,
    scalaVersion := Dependencies.Versions.CrossScalaForPlugin.head
  )
  .dependsOn(codegen)

lazy val runtime = Project(id = gyremockRuntimeName, base = file("runtime"))
  .settings(
    crossScalaVersions := Dependencies.Versions.CrossScalaForLib,
    scalaVersion := Dependencies.Versions.CrossScalaForLib.head,
    libraryDependencies := Seq(
      "ch.qos.logback" % "logback-classic" % "1.4.14" % Runtime,
      "com.lightbend.akka.grpc" %% "akka-grpc-runtime" % akkaGrpcVersion,
      "com.typesafe.akka" %% "akka-actor" % "2.6.20",
      "com.typesafe.akka" %% "akka-http-core" % "10.2.9",
      "com.thesamet.scalapb" %% "scalapb-json4s" % "0.11.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    )
  )

lazy val root = Project(id = "gyremock", base = file("."))
  .aggregate(runtime, codegen, sbtPlugin)
  .settings(
    publish / skip := true,
    // https://github.com/sbt/sbt/issues/3465
    // Libs and plugins must share a version. The root project must use that
    // version (and set the crossScalaVersions as empty list) so each sub-project
    // can then decide which scalaVersion and crossCalaVersions they use.
    crossScalaVersions := Nil,
    scalaVersion := Dependencies.Versions.scala212
  )
