package dev.touchdown.gyremock

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.typesafe.scalalogging.StrictLogging
import org.json4s.JsonAST
import org.json4s.jackson.JsonMethods
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import scalapb.json4s.{Parser, Printer}

class HttpMock(settings: WiremockSettings, jsPrinter: Printer, jsParser: Parser) extends StrictLogging {
  private lazy val SERVER = new WireMockServer(
    wireMockConfig
      .notifier(new Slf4jNotifier(true))
      .maxRequestJournalEntries(1000)
      .usingFilesUnderDirectory("wiremock")
      .extensions(new ResponseTemplateTransformer(true))
      .port(settings.port)
  )
  if (settings.host.isEmpty && !SERVER.isRunning) SERVER.start()

  private val targetBaseUrl = settings.host.map(h => s"http://${h}:${settings.port}").getOrElse(SERVER.baseUrl)

  def destroy(): Unit = if (settings.host.isEmpty && SERVER.isRunning) SERVER.stop()

  def send[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: I, path: String)(implicit
    ec: ExecutionContext
  ): Future[O] = {
    val json = jsPrinter.print(message)
    logger.info("received raw message:\n{}\njson:\n{}", message.toProtoString, json)
    val request = HttpRequest.newBuilder
      .uri(URI.create(targetBaseUrl + path))
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build
    HttpClient.newHttpClient
      .sendAsync(request, HttpResponse.BodyHandlers.ofString)
      .toScala
      .map {
        r =>
          val resp = Try(jsParser.fromJsonString[O](r.body()))
          resp.fold(
            ex => logger.error("failed to parse into protos", ex),
            m => logger.info("resp proto:\n{}", m.toProtoString)
          )
          resp.get
      }
  }

  def sendStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: I, path: String)(
    implicit ec: ExecutionContext
  ): Future[List[O]] = {
    val json = jsPrinter.print(message)
    logger.info("received raw message:\n{}\njson:\n{}", message.toProtoString, json)
    val request = HttpRequest.newBuilder
      .uri(URI.create(targetBaseUrl + path))
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build
    HttpClient.newHttpClient
      .sendAsync(request, HttpResponse.BodyHandlers.ofString)
      .toScala
      .map {
        r =>
          val js = JsonMethods
            .parse(r.body()) match {
            case JsonAST.JArray(arr) => arr
            case j                   => List(j)
          }
          js.map {
            jValue =>
              val resp = Try(jsParser.fromJson[O](jValue))
              resp.fold(
                ex => logger.error("failed to parse into protos", ex),
                m => logger.info("resp proto:\n{}", m.toProtoString)
              )
              resp.get
          }
      }
  }
}
