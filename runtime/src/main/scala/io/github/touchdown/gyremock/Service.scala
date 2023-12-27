package io.github.touchdown.gyremock

import scala.concurrent.Future

import akka.grpc.ServiceDescription
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

/** @param desc      we will use description to do reflection
  * @param handlerF  these will be the actual handlers when called with [[GrpcFromToJson]]
  */
final case class Service(
  desc: ServiceDescription,
  handlerF: GrpcFromToJson => PartialFunction[HttpRequest, Future[HttpResponse]]
)
