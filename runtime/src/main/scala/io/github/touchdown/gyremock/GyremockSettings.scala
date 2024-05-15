package io.github.touchdown.gyremock

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
      wiremockBaseUrl = Option.when(config.hasPath("wiremock-base-url"))(config.getString("wiremock-base-url"))
    )
}

/**
 *
 * @param host default host is localhost
 * @param port default port is 0; when the port is 0, we will use dynamic port to setup gyremock server
 * @param wiremockBaseUrl wiremock url since gyremock will ask wiremock to handle request
 */
case class GyremockSettings(host: String = localhost, port: Int = dynamicPort, wiremockBaseUrl: Option[String])
