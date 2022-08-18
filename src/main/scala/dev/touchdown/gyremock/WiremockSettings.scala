package dev.touchdown.gyremock

import com.typesafe.config.Config

object WiremockSettings {
  private val DefaultPort = 18080

  def apply(config: Config): WiremockSettings = WiremockSettings(
    host = Some(config.getString("host")).filter(_.nonEmpty),
    port = Some(config.getInt("port")).filter(_ > 0).getOrElse(DefaultPort)
  )
}

case class WiremockSettings(host: Option[String], port: Int)
