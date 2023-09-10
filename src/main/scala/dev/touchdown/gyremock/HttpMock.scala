package dev.touchdown.gyremock

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.typesafe.scalalogging.StrictLogging
import org.json4s.{JsonAST, JValue}
import org.json4s.jackson.JsonMethods
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import scalapb.json4s.{Parser, Printer}

trait HttpMock {
  def unary[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: I, path: String)(implicit
    ec: ExecutionContext
  ): Future[O]

  def sStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: I, path: String)(
    implicit ec: ExecutionContext
  ): Future[List[O]]

  def cStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: Seq[I], path: String)(
    implicit ec: ExecutionContext
  ): Future[O]

  def bdStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: Seq[I], path: String)(
    implicit ec: ExecutionContext
  ): Future[List[O]]
}

class HttpMockImpl(settings: WiremockSettings, jsPrinter: Printer, jsParser: Parser)
    extends HttpMock
    with StrictLogging {
  private val server = new WireMockServer(
    wireMockConfig
      .notifier(new Slf4jNotifier(true))
      .maxRequestJournalEntries(1000)
      .usingFilesUnderDirectory("wiremock")
      .extensions(new ResponseTemplateTransformer(true))
      .port(settings.port)
  )
  if (settings.host.isEmpty && !server.isRunning) server.start()

  private val targetBaseUrl = settings.host.map(host => s"http://$host:${settings.port}").getOrElse(server.baseUrl)

  def destroy(): Unit = if (settings.host.isEmpty && server.isRunning) server.stop()

  private def redirect(json: String, path: String) = {
    val request = HttpRequest.newBuilder
      .header("Content-Type", "application/json")
      .uri(URI.create(targetBaseUrl + path))
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build
    HttpClient.newHttpClient
      .sendAsync(request, HttpResponse.BodyHandlers.ofString)
      .toScala
  }

  private def parseMsgs[I <: GeneratedMessage](messages: Seq[I]) = {
    val json = messages.map(jsPrinter.print).mkString("[", ",", "]")
    logger.info("received raw message:\n{}\njson:\n{}", messages.map(_.toProtoString), json)
    json
  }

  private def parseJson[O <: GeneratedMessage: GeneratedMessageCompanion](json: JValue) = {
    val r = Try(jsParser.fromJson[O](json))
    r.foreach(m => logger.info("resp proto:\n{}", m.toProtoString))
    r.failed.foreach(ex => logger.error("failed to parse into protos", ex))
    r.get // todo fix to either
  }

  private def parseResp[O <: GeneratedMessage: GeneratedMessageCompanion](resp: HttpResponse[String]) = {
    JsonMethods.parseOpt(resp.body()) match {
      case Some(JsonAST.JArray(arr)) => arr.map(parseJson[O](_))
      case Some(j)                   => List(parseJson[O](j))
      case None =>
        logger.error(s"couldnt parse ${resp.body()} to json")
        Nil
    }
  }

  override def unary[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: I, path: String)(
    implicit ec: ExecutionContext
  ): Future[O] = {
    val json = jsPrinter.print(message)
    logger.info("received raw message:\n{}\njson:\n{}", message.toProtoString, json)
    redirect(json, path).map(parseResp(_).head)
  }

  override def sStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](
    message: I,
    path: String
  )(implicit ec: ExecutionContext): Future[List[O]] = {
    val json = jsPrinter.print(message)
    logger.info("received raw message:\n{}\njson:\n{}", message.toProtoString, json)
    redirect(json, path).map(parseResp[O](_))
  }

  override def cStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](
    messages: Seq[I],
    path: String
  )(implicit ec: ExecutionContext): Future[O] = {
    redirect(parseMsgs(messages), path).map(parseResp(_).head)
  }

  override def bdStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](
    messages: Seq[I],
    path: String
  )(implicit ec: ExecutionContext): Future[List[O]] = {
    redirect(parseMsgs(messages), path).map(parseResp[O](_))
  }
}
