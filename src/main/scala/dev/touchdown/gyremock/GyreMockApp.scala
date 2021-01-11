package dev.touchdown.gyremock

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import scalapb.json4s.{Parser, Printer}

import scala.concurrent.ExecutionContext.Implicits.global

object GyreMockApp extends StrictLogging with App {

  val config = ConfigFactory.load()
  val wiremockHost = Some(config.getString("gyremock.wiremock.host")).filter(_.nonEmpty)

  val printer = new Printer().includingDefaultValueFields
  val parser = new Parser()
  val httpMock = new HttpMock(wiremockHost, printer, parser)

  val services = ServicesBuilder.build(httpMock)
  val server = new GrpcServer(services).start(50000)

  httpMock.init()
  server.awaitTermination()
  server.shutdown().awaitTermination()
  httpMock.destroy()

}
