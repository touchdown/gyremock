package dev.touchdown.gyremock

import com.typesafe.scalalogging.StrictLogging
import scalapb.json4s.{Parser, Printer}

import scala.concurrent.ExecutionContext.Implicits.global

object GyreMockApp extends StrictLogging with App {

  val printer = new Printer().includingDefaultValueFields
  val parser = new Parser()
  val httpMock = new HttpMock(printer, parser)(scala.concurrent.ExecutionContext.global)

  val services = ServicesBuilder.build(httpMock)

  httpMock.init()
  private val server = new GrpcServer(services).start(50000)

  server.awaitTermination()

  server.shutdown().awaitTermination()
  httpMock.destroy()
}
