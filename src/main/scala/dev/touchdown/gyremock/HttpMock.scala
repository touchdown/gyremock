package dev.touchdown.gyremock

import java.io.IOException
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
import scalapb.json4s.JsonFormat

import scala.concurrent.{ExecutionContext, Future}
import scala.compat.java8.FutureConverters._
import scala.util.Try

object HttpMock {
  private val config = wireMockConfig
    .notifier(new Slf4jNotifier(true))
    .maxRequestJournalEntries(1000)
    .usingFilesUnderDirectory("wiremock")
    .extensions(new ResponseTemplateTransformer(true))
    .port(8888)
  private val SERVER = new WireMockServer(config)
}

class HttpMock(implicit ec: ExecutionContext) extends StrictLogging {
  import dev.touchdown.gyremock.HttpMock.SERVER

  def init(): Unit = {
    if (!SERVER.isRunning) SERVER.start()
  }

  def destroy(): Unit = {
    if (SERVER.isRunning) SERVER.stop()
  }

  @throws[IOException]
  @throws[InterruptedException]
  def send[I <: GeneratedMessage, O <: GeneratedMessage with Message[O] : GeneratedMessageCompanion](message: I, path: String): Future[O] = {
    val json =  JsonFormat.toJsonString(message)
    logger.info("received raw message:\n{}\njson:\n{}", message.toProtoString, json)
    val request = HttpRequest.newBuilder
      .uri(URI.create(SERVER.baseUrl + path))
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build
    HttpClient
      .newHttpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString)
      .toScala
      .map{r =>
        val resp = Try(JsonFormat.fromJsonString[O](r.body()))
        resp.fold(
          ex => logger.error("failed to parse into protos", ex),
          m => logger.info("resp proto:\n{}", m.toProtoString)
        )
        resp.get
      }
  }
}