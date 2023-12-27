object Dependencies {
  object Versions {
    val scala212 = "2.12.18"
    val scala213 = "2.13.12"
    val scala3 = "3.3.1"

    // the order in the list is important because the head will be considered the default.
    val CrossScalaForLib: Seq[String] = Seq(scala213, scala3)
    val CrossScalaForPlugin: Seq[String] = Seq(scala212)
  }
}
