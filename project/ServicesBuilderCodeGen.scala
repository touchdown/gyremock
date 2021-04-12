
import akka.grpc.gen.scaladsl.Service
import akka.grpc.gen.{CodeGenerator, Logger}
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import protocbridge.Artifact
import scalapb.compiler._

import scala.collection.immutable
import scala.jdk.CollectionConverters._

object ServicesBuilderCodeGen extends CodeGenerator {
  override val name: String = "services-builder"

  override def run(request: CodeGeneratorRequest, logger: Logger): CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.File.newBuilder()
    val services = getServices(request, logger)
    b.setContent(new ObjectCodeGenerator(services).run())
    b.setName("dev/touchdown/gyremock/ServicesBuilder.scala")
    logger.info("Generating services builder")
    CodeGeneratorResponse.newBuilder().addFile(b).build()
  }

  override def suggestedDependencies: CodeGenerator.ScalaBinaryVersion => scala.Seq[Artifact] = _ => Nil

  // code is copied and pasted from https://github.com/akka/akka-grpc/blob/master/codegen/src/main/scala/akka/grpc/gen/scaladsl/ScalaCodeGenerator.scala
  // will remove once its been fixed to be a protected method upstream
  private def getServices(request: CodeGeneratorRequest, logger: Logger) = {
    // Currently per-invocation options, intended to become per-service options eventually
    // https://github.com/akka/akka-grpc/issues/451
    val params = request.getParameter.toLowerCase
    // flags listed in akkaGrpcCodeGeneratorSettings's description
    val serverPowerApi = params.contains("server_power_apis") && !params.contains("server_power_apis=false")
    val usePlayActions = params.contains("use_play_actions") && !params.contains("use_play_actions=false")

    val codeGenRequest = protocgen.CodeGenRequest(request)
    (for {
      fileDesc <-     codeGenRequest.filesToGenerate
      serviceDesc <- fileDesc.getServices.asScala
    } yield Service(
      codeGenRequest,
      parseParameters(request.getParameter),
      fileDesc,
      serviceDesc,
      serverPowerApi,
      usePlayActions)
    ).toList
  }

  // flags listed in akkaGrpcCodeGeneratorSettings's description
  private def parseParameters(params: String): GeneratorParams =
    params.split(",").map(_.trim).filter(_.nonEmpty).foldLeft[GeneratorParams](GeneratorParams()) {
      case (p, "java_conversions")            => p.copy(javaConversions = true)
      case (p, "flat_package")                => p.copy(flatPackage = true)
      case (p, "single_line_to_string")       => p.copy(singleLineToProtoString = true) // for backward-compatibility
      case (p, "single_line_to_proto_string") => p.copy(singleLineToProtoString = true)
      case (p, "ascii_format_to_string")      => p.copy(asciiFormatToString = true)
      case (p, "no_lenses")                   => p.copy(lenses = false)
      case (p, "retain_source_code_info")     => p.copy(retainSourceCodeInfo = true)
      case (p, "grpc")                        => p.copy(grpc = true)
      case (x, _)                             => x
    }
}

final private class ObjectCodeGenerator(services: immutable.Seq[Service]) {

  services.foreach(println)

  def run(): String = {
    new FunctionalPrinter()
      .call(addPackageClause)
      .newline
      .call(addImportStatements)
      .newline
      .call(addObject)
      .newline
      .result
  }


  private def addPackageClause(printer: FunctionalPrinter): FunctionalPrinter = {
    printer.add("package dev.touchdown.gyremock")
  }

  private def addImportStatements(printer: FunctionalPrinter): FunctionalPrinter = {
    val libraryImports = Seq(
      "akka.actor.ActorSystem",
      "akka.grpc.ServiceDescription",
      "akka.http.scaladsl.model.{HttpRequest, HttpResponse}",
      "scala.collection.immutable",
      "scala.concurrent.Future"
    )

    printer.add(libraryImports.map(i => s"import $i"): _*)
  }

  private def addObject(printer: FunctionalPrinter): FunctionalPrinter = {
    printer.add("object ServicesBuilder {")
      .newline
      .addIndented("def build(httpMock: HttpMock)(implicit sys: ActorSystem): immutable.Seq[(ServiceDescription, PartialFunction[HttpRequest, Future[HttpResponse]])] = List(")
      .indent
      .print(services) { (p, s) =>
        p.addIndented(s"(${s.packageName}.${s.name}, ${s.packageName}.${s.name}Handler.partial(new ${s.packageName}.${s.name}Redirect(httpMock))),")
      }
      .add(")")
      .outdent
      .add("}")
      .newline
  }
}
