package dev.touchdown.gyremock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.scalalogging.StrictLogging
import scalapb.json4s.{Parser, Printer}

import scala.concurrent.{ExecutionContext, Future}

object GyreMockApp extends StrictLogging with App {
  // Important: enable HTTP/2 in ActorSystem's config
  // We do it here programmatically, but you can also set it in the application.conf
  val system = ActorSystem("GyreMockApp")
  val wiremockBaseUrl = Some(system.settings.config.getString("gyremock.wiremock.host")).filter(_.nonEmpty)
  val printer = new Printer().includingDefaultValueFields
  val parser = new Parser()
  val httpMock = new HttpMock(wiremockBaseUrl, printer, parser)
  new GyreMockApp(system, httpMock).run()
  if (wiremockBaseUrl.isEmpty){
    httpMock.init()
    system.registerOnTermination(httpMock.destroy())
  }
  // ActorSystem threads will keep the app alive until `system.terminate()` is called
}

class GyreMockApp(system: ActorSystem, httpMock: HttpMock) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    val services = ServicesBuilder.build(httpMock)
    val server = new AkkaGrpcServer(services).start(50000)
    // report successful binding
    server.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }
    server
  }
}
