# gyremock
grpc wrapper for wiremock

## Intro
This project aims to introduce a translation layer between grpc and json (wiremock). 
To do so, it relies on the `codegen` logic in the plugin to
1. have access to proto files
2. generate said translation layer for grpc <-> json
3. generate an aggregate of all the services generated above

Then, the `runtime` library provides the support needed to run the code generated above.

Lastly, the end project still need to write a few lines of code to actually run the service to make it useful and provide all the needed wiremock files.


## Getting Started
in your project's `plugins.sbt`
```sbt
addSbtPlugin("io.github.touchdown" % "sbt-gyremock" % "<version>")
```
then add these lines in your `build.sbt`
```sbt
import gyremock.gen.scaladsl._

Compile / akkaGrpcExtraGenerators := Seq(TranslatorCodeGen, ServicesBuilderCodeGen)
```
along with your usual protoc settings


finally, add a file like this to bootstrap the actual service
```scala
package xyz

import akka.actor.ActorSystem
import io.github.touchdown.gyremock._

object GyreMockApp {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("GyreMockApp")
    val settings = GyremockSettings(system.settings.config.getConfig("gyremock"))
    new GyremockServer(settings, ServicesBuilder.build).run()
  }

}
```
