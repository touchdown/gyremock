import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import com.google.protobuf.Descriptors.{FileDescriptor, MethodDescriptor, ServiceDescriptor}
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import protocbridge.{Artifact, ProtocCodeGenerator}
import scalapb.compiler._
import scalapb.compiler.Version.{scalapbVersion => ScalaPbVersion}
import scalapb.options.compiler.Scalapb

import scala.collection.immutable

object GrpcRedirectCodeGen extends ProtocCodeGenerator {

  override def run(input: Array[Byte]): Array[Byte] = {
    val registry = ExtensionRegistry.newInstance()
    Scalapb.registerAllExtensions(registry)
    val request = CodeGeneratorRequest.parseFrom(input, registry)
    val params = GeneratorParams() // we don't use these for anything here

    val filesByName: Map[String, FileDescriptor] = request.getProtoFileList.asScala
      .foldLeft(Map.empty[String, FileDescriptor])((acc, fdProto) => {
        val deps = fdProto.getDependencyList.asScala.map(acc)
        acc + (fdProto.getName -> FileDescriptor.buildFrom(fdProto, deps.toArray))
      })
    val implicits = new DescriptorImplicits(params, filesByName.values.toVector)
    val serviceDs = request.getFileToGenerateList.asScala.flatMap (name =>
      filesByName(name).getServices.asScala
    )

    val resp = generateServicesBuilder(serviceDs.toList, implicits)
    (if (serviceDs.nonEmpty){
      val services = serviceDs.map(sd => generateService(sd, implicits))
      val builder = CodeGeneratorResponse.newBuilder().addAllFile(services.asJava)
      builder
        .addFile(resp)
        .build()
    }else{
      CodeGeneratorResponse.newBuilder()
        .addFile(resp)
        .build()
    }).toByteArray
  }


  private def generateService(service: ServiceDescriptor, implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    import implicits._

    val p = new RpcLibCodeGenerator(service, implicits)
    val code = p.run()
    val b = CodeGeneratorResponse.File.newBuilder()
    val name = service.objectName + "Redirect"
    b.setName(service.getFile.scalaDirectory + "/" + name + ".scala")
    b.setContent(code)
    b.build()
  }

  private def generateServicesBuilder(services: immutable.Seq[ServiceDescriptor], implicits: DescriptorImplicits): CodeGeneratorResponse.File = {
    import implicits._

    val p = new ServicesBuilderCodeGenerator(services.map(s => s"${s.getFile.scalaPackageName}.${s.objectName}"))
    val code = p.run()
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName("io/touchdown/gyremock/ServicesBuilder.scala")
    b.setContent(code)
    b.build()
  }

  /** Transitive library dependencies.
   *
   *  This is a list of library dependencies that users of RPCLib will need.
   *  Instead of requiring users to manually add these libraries in their SBT
   *  build definitions, we define them here, and the user's SBT build will
   *  automatically pick them up as transitive dependencies.
   */
  override val suggestedDependencies: Seq[Artifact] = {
    Seq(
      Artifact("com.thesamet.scalapb", "scalapb-runtime-grpc", ScalaPbVersion, crossVersion = true)
    )
  }
}


final private class ServicesBuilderCodeGenerator(services: immutable.Seq[String]) {
  def run(): String = {
    new FunctionalPrinter()
      .call(addPackageClause)
      .newline
      .call(addImportStatements)
      .newline
      .call(addPackageObj)
      .newline
      .result
  }


  private def addPackageClause(printer: FunctionalPrinter): FunctionalPrinter = {
    printer.add("package dev.touchdown.gyremock")
  }

  private def addImportStatements(printer: FunctionalPrinter): FunctionalPrinter = {
    val libraryImports = Seq(
      "io.grpc.ServerServiceDefinition",
      "scala.concurrent.ExecutionContext"
    )

    printer.add(libraryImports.map(i => s"import $i"): _*)
  }

  private def addPackageObj(printer: FunctionalPrinter): FunctionalPrinter = {
    printer.add("object ServicesBuilder {")
      .newline
      .addIndented("def build(httpMock: HttpMock)(implicit ec: ExecutionContext): List[ServerServiceDefinition] = List(")
      .indent
      .print(services) { (p, s) =>
        p.addIndented(s"${s}.bindService(new ${s}Redirect(httpMock), ec),")
      }
      .add(")")
      .outdent
      .add("}")
  }
}


/** Anatomy of this class:
 * Routines that generate code for method definitions are written as methods.
 * Routines that generate classes/traits/objects are written within a nested object.
 *
 * Adhering to this consistency keeps the structure of this file in sync with that of the generated code,
 * which is especially helpful when viewing this file with code folding.
 * @param service
 * @param implicits
 */
final private class RpcLibCodeGenerator(service: ServiceDescriptor, implicits: DescriptorImplicits) {
  import implicits._

  def run(): String = {
    new FunctionalPrinter()
      .call(addPackageClause)
      .newline
      .call(addImportStatements)
      .newline
      .add(s"class ${service.objectName}Redirect(httpMock: HttpMock) extends ${service.getFile.scalaPackageName}.${service.objectName}.${service.name} {")
      .indent
      .newline
      .print(service.methods)(addMethodImpl)
      .outdent
      .add("}")
      .result
  }

  private def addPackageClause(printer: FunctionalPrinter): FunctionalPrinter = {
    printer.add(s"package ${service.getFile.scalaPackageName}")
  }

  private def addImportStatements(printer: FunctionalPrinter): FunctionalPrinter = {
    val libraryImports = Seq(
      "dev.touchdown.gyremock.HttpMock",
      "io.grpc._",
      "io.grpc.stub._",
      "scalapb.grpc._",
      "scala.concurrent.Future"
    )

    printer.add(libraryImports.map(i => s"import $i"): _*)
  }


  def addMethodImpl(printer: FunctionalPrinter, method: MethodDescriptor): FunctionalPrinter = {
    println(method.toProto.toString)
    method.streamType match {
      case StreamType.Unary =>
        printer
          .add(s"def ${method.name}(request: ${method.inputType.scalaType}): Future[${method.outputType.scalaType}] = ")
          .addIndented(s"""httpMock.send[${method.inputType.scalaType}, ${method.outputType.scalaType}](request, "/${service.name}/${method.name}")""")
      case StreamType.ServerStreaming => // NOTE: this isnt implemented yet
        printer
          .add(s"def ${method.name}(request: ${method.inputType.scalaType}, responseObserver: io.grpc.stub.StreamObserver[${method.outputType.scalaType}]): Unit = ???")
      case _ => // NOTE: this isnt implemented yet
        printer
          .add(s"def ${method.name}(responseObserver: io.grpc.stub.StreamObserver[${method.outputType.scalaType}]): io.grpc.stub.StreamObserver[${method.inputType.scalaType}] = ???")
    }
  }.addIndented("")
    .newline

  private implicit class RichFunctionalPrinter(printer: FunctionalPrinter) {
    def resultTrimmed: String = {
      printer.content
        .map(_.replaceAll("\\s+$", ""))
        .mkString("\n")
    }
  }

}


