
import akka.grpc.gen.scaladsl.{Method, ScalaCodeGenerator, Service}

import akka.grpc.gen.Logger
import scalapb.compiler._
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse

import scala.collection.immutable

object AkkaGrpcRedirectCodeGen extends ScalaCodeGenerator {
  override val name: String = "akka-grpc-redirect"

  override def perServiceContent = super.perServiceContent + generateRedirectImpl

  private val generateRedirectImpl: (Logger, Service) => immutable.Seq[CodeGeneratorResponse.File] = (logger, service) => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(new RedirectCodeGenerator(service).run())
    b.setName(s"${service.packageDir}/${service.name}Redirect.scala")
    logger.info(s"Generating Akka gRPC redirect service impl for ${service.packageName}.${service.name}")
    immutable.Seq(b.build)
  }
}

final private class RedirectCodeGenerator(service: Service) {
  def run(): String = {
    new FunctionalPrinter()
      .add(s"package ${service.packageName}")
      .newline
      .call(addImports)
      .newline
      .add(s"class ${service.name}Redirect(httpMock: HttpMock)(implicit sys: ActorSystem) extends ${service.name} {")
      .indent
      .add("private implicit val ec = sys.dispatcher")
      .newline
      .print(service.methods)(addMethodImpl)
      .outdent
      .add("}")
      .result
  }

  private def addImports(printer: FunctionalPrinter): FunctionalPrinter = {
    val libraryImports = immutable.Seq(
      "dev.touchdown.gyremock.HttpMock",
      "akka.NotUsed",
      "akka.actor.ActorSystem",
      "akka.stream.scaladsl._"
    )
    printer.add(libraryImports.map(i => s"import $i"): _*)
  }

  private def addMethodImpl(printer: FunctionalPrinter, method: Method): FunctionalPrinter = {
    printer
      .add(s"def ${method.nameSafe}(in: ${method.parameterType}): ${method.returnType} = {")
      .addIndented(method.methodType match {
        case akka.grpc.gen.Unary =>
          s"""httpMock.send[${method.parameterType}, ${method.outputTypeUnboxed}](in, "/${service.name}/${method.name}")"""
        case akka.grpc.gen.ServerStreaming =>
          s"""Source.future(httpMock.send[${method.parameterType}, ${method.outputTypeUnboxed}](in, "/${service.name}/${method.name}"))"""
        case akka.grpc.gen.ClientStreaming =>
          s"""in.runWith(Sink.last).flatMap(e => httpMock.send[${method.inputTypeUnboxed}, ${method.outputTypeUnboxed}](e, "/${service.name}/${method.name}"))"""
        case akka.grpc.gen.BidiStreaming =>
          s"""in.mapAsync(1)(e => httpMock.send[${method.inputTypeUnboxed}, ${method.outputTypeUnboxed}](e, "/${service.name}/${method.name}"))"""
      })
      .add("}")
      .newline
  }
}

