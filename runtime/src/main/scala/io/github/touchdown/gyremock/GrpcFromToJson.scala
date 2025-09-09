package io.github.touchdown.gyremock

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import com.typesafe.scalalogging.StrictLogging
import org.json4s.{JsonAST, JValue}
import org.json4s.jackson.JsonMethods
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import scalapb.json4s.{Parser, Printer}

trait GrpcFromToJson {
  def unary[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: I, path: String)(implicit
    ec: ExecutionContext
  ): Future[O]

  def sStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: I, path: String)(
    implicit ec: ExecutionContext
  ): Future[Seq[O]]

  def cStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](message: Seq[I], path: String)(
    implicit ec: ExecutionContext
  ): Future[O]
}

class GrpcFromToJsonImpl(wiremockBaseUrl: String, jsPrinter: Printer, jsParser: Parser)
    extends GrpcFromToJson
    with StrictLogging {

  val httpClient = HttpClient.newHttpClient

  private def translate(json: String, path: String) = {
    val request = HttpRequest.newBuilder
      .header("Content-Type", "application/json")
      .uri(URI.create(wiremockBaseUrl + path))
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build

    httpClient
      .sendAsync(request, HttpResponse.BodyHandlers.ofString)
      .toScala
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
    logger.debug("received raw message:\n{}\n", message.toProtoString)
    translate(jsPrinter.print(message), path).map(parseResp(_).head)
  }

  override def sStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](
    message: I,
    path: String
  )(implicit ec: ExecutionContext): Future[Seq[O]] = {
    logger.debug("received raw message:\n{}\n", message.toProtoString)
    translate(jsPrinter.print(message), path).map(parseResp[O](_))
  }

  override def cStream[I <: GeneratedMessage, O <: GeneratedMessage: GeneratedMessageCompanion](
    messages: Seq[I],
    path: String
  )(implicit ec: ExecutionContext): Future[O] = {
    logger.debug("received raw message:\n{}\n", messages.map(_.toProtoString).mkString("[", ",", "]"))
    translate(messages.map(jsPrinter.print).mkString("[", ",", "]"), path).map(parseResp(_).head)
  }
}
