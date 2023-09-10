package dev.touchdown.gyremock

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.scalalogging.StrictLogging
import scalapb.json4s.{Parser, Printer}

object GyreMockApp extends App {
  private val system = ActorSystem("GyreMockApp")
  private val settings =
    WiremockSettings(system.settings.config.getConfig("gyremock.wiremock"))
  private val printer = new Printer().includingDefaultValueFields
  private val parser = new Parser()
  private val httpMock = new HttpMockImpl(settings, printer, parser)
  new GyreMockApp(system, httpMock).run()
  system.registerOnTermination(httpMock.destroy())
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
