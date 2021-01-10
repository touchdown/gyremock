package dev.touchdown.gyremock

import java.io.IOException

import com.typesafe.scalalogging.StrictLogging
import io.grpc.{Server, ServerBuilder, ServerServiceDefinition}
import io.grpc.protobuf.services.ProtoReflectionService

import scala.collection.JavaConverters._

class GrpcServer(val services: List[ServerServiceDefinition]) extends StrictLogging {
  @throws[IOException]
  def start(port: Int): Server = {
    val builder = ServerBuilder.forPort(port)
    builder.addService(ProtoReflectionService.newInstance)
    services.foreach(builder.addService)
    val server = builder.build.start
    logger.info(summary(server))
    server
  }

  private def summary(server: Server) = "Started " + server + "\nRegistered services:\n" + server.getServices.asScala.map(s => " * " + s.getServiceDescriptor.getName).mkString("\n")

}
