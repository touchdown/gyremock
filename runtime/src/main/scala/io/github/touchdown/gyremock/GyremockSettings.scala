package io.github.touchdown.gyremock

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

import com.typesafe.config.Config

object GyremockSettings {
  def apply(config: Config): GyremockSettings =
    GyremockSettings(
      host = config.getString("host"),
      port = config.getInt("port"),
      stopTimeout = config.getDuration("stop-timeout").toScala,
      wiremockBaseUrl = config.getString("wiremock-base-url")
    )
}

case class GyremockSettings(host: String, port: Int, stopTimeout: FiniteDuration, wiremockBaseUrl: String)
