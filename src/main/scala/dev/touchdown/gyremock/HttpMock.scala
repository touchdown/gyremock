package dev.touchdown.gyremock

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.typesafe.scalalogging.StrictLogging
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import scalapb.json4s.{Parser, Printer}

import scala.concurrent.{ExecutionContext, Future}
import scala.compat.java8.FutureConverters._
import scala.util.Try

object HttpMock extends StrictLogging {
  private val port = 18080
  private val config = wireMockConfig
    .notifier(new Slf4jNotifier(true))
    .maxRequestJournalEntries(1000)
    .usingFilesUnderDirectory("wiremock")
    .extensions(new ResponseTemplateTransformer(true))
    .port(port)

  private lazy val SERVER = new WireMockServer(config)
}

class HttpMock(wiremockHost: Option[String], jsPrinter: Printer, jsParser: Parser)(implicit ec: ExecutionContext) extends StrictLogging {
  import HttpMock._

  if (wiremockHost.isEmpty && !SERVER.isRunning) SERVER.start()

  private val targetBaseUrl = wiremockHost.map(h => s"http://${h}:$port").getOrElse(SERVER.baseUrl)

  def destroy(): Unit = if (wiremockHost.isEmpty && SERVER.isRunning) SERVER.stop()

  def send[I <: GeneratedMessage, O <: GeneratedMessage with Message[O] : GeneratedMessageCompanion](message: I, path: String): Future[O] = {
    val json = jsPrinter.print(message)
    logger.info("received raw message:\n{}\njson:\n{}", message.toProtoString, json)
    val request = HttpRequest.newBuilder
      .uri(URI.create(targetBaseUrl + path))
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build
    HttpClient
      .newHttpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString)
      .toScala
      .map{r =>
        val resp = Try(jsParser.fromJsonString[O](r.body()))
        resp.fold(
          ex => logger.error("failed to parse into protos", ex),
          m => logger.info("resp proto:\n{}", m.toProtoString)
        )
        resp.get
      }
  }
}