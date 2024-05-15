package io.github.touchdown.gyremock

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.DurationConverters._
import com.typesafe.config.Config
import io.github.touchdown.gyremock.GyremockSettings.{dynamicPort, localhost}

object GyremockSettings {
  private val localhost = "127.0.0.1"

  // when we set port = 0, that means we will use dynamic port
  private val dynamicPort = 0

  def apply(config: Config): GyremockSettings =
    GyremockSettings(
      host = config.getString("host"),
      port = config.getInt("port"),
      stopTimeout = config.getDuration("stop-timeout").toScala,
      wiremockBaseUrl = Option.when(config.hasPath("wiremock-base-url"))(config.getString("wiremock-base-url"))
    )
}

case class GyremockSettings(host: String = localhost, port: Int = dynamicPort, stopTimeout: FiniteDuration = 10.seconds, wiremockBaseUrl: Option[String])
