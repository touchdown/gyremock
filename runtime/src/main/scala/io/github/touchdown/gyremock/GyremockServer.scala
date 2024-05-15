package io.github.touchdown.gyremock

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.typesafe.scalalogging.StrictLogging
import scalapb.json4s.{Parser, Printer}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

class GyremockServer(settings: GyremockSettings, services: immutable.Seq[Service])(implicit sys: ActorSystem)
    extends StrictLogging {

  private val printer = new Printer().includingDefaultValueFields
  private val parser = new Parser()

  val wiremockBaseUrl: String = settings.wiremockBaseUrl.getOrElse {
    val server = new WireMockServer(
      wireMockConfig
        .notifier(new Slf4jNotifier(true))
        .maxRequestJournalEntries(20) // to save some memory
        .usingFilesUnderDirectory("/home/wiremock")
        .globalTemplating(true)
        .dynamicPort()
    )
    server.start()
    logger.info(s"internal wiremock http server started at ${server.baseUrl}")
    CoordinatedShutdown(sys).addTask(
      CoordinatedShutdown.PhaseServiceRequestsDone,
      "shutting down internal wiremock server"
    )(() => Future.fromTry(Try(server.stop())).map(_ => Done)(sys.dispatcher))
    server.baseUrl
  }

  private val grpcFromToJson = new GrpcFromToJsonImpl(wiremockBaseUrl, printer, parser)

  private val handlers = ServiceHandler.concatOrNotFound(
    services.map(_.handlerF(grpcFromToJson)) :+ ServerReflection.partial(services.map(_.desc).toList): _*
  )

  /** binds to designated host and port, and registers to shutdown if bind successful */
  def run(): Future[Http.ServerBinding] = {
    Http()
      .newServerAt(interface = settings.host, port = settings.port)
      .bind(handlers)
  }
}
