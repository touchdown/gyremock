package dev.touchdown.gyremock

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContext.Implicits.global

object GyreMockApp extends StrictLogging with App {

  val httpMock = new HttpMock()(scala.concurrent.ExecutionContext.global)

  val services = ServicesBuilder.build(httpMock)

  httpMock.init()
  private val server = new GrpcServer(services).start(50000)

  server.awaitTermination()

  server.shutdown().awaitTermination()
  httpMock.destroy()
}
