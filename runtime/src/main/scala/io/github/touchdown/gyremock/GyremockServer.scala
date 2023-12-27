package io.github.touchdown.gyremock

import scala.collection.immutable
import scala.util.{Failure, Success}

import akka.actor.ActorSystem
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import com.typesafe.scalalogging.StrictLogging
import scalapb.json4s.{Parser, Printer}

class GyremockServer(settings: GyremockSettings, services: immutable.Seq[Service])(implicit sys: ActorSystem)
    extends StrictLogging {

  private val printer = new Printer().includingDefaultValueFields
  private val parser = new Parser()
  private val grpcFromToJson = new GrpcFromToJsonImpl(settings.wiremockBaseUrl, printer, parser)

  private val handlers = ServiceHandler.concatOrNotFound(
    services.map(_.handlerF(grpcFromToJson)) :+ ServerReflection.partial(services.map(_.desc).toList): _*
  )

  /** binds to designated host and port, and registers to shutdown if bind successful */
  def run(): Unit = {
    Http()
      .newServerAt(interface = settings.host, port = settings.port)
      .bind(handlers)
      .onComplete {
        case Success(binding) =>
          logger.info(s"gRPC mock server bound to: ${binding.localAddress}")
          binding.addToCoordinatedShutdown(settings.stopTimeout)
        case Failure(exception) =>
          logger.error(s"exception in binding to ${settings.host}:${settings.port}", exception)
      }(sys.dispatcher)
  }
}
