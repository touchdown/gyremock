package io.touchdown.gyremock

import com.typesafe.scalalogging.StrictLogging

object GrpcMockApp extends StrictLogging with App {

  val httpMock = new HttpMock()(scala.concurrent.ExecutionContext.global)

  val services = GyreMocks.build(httpMock)

  httpMock.init()
  private val server = new GrpcServer(services).start(50000)

  server.awaitTermination()

  server.shutdown().awaitTermination()
  httpMock.destroy()
}
