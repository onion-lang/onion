package onion.tools

/**
 * Single source of truth for the Onion version reported by the CLI tools
 * (compiler, script runner, REPL). The value is read from the jar manifest's
 * `Implementation-Version`, which sbt-dynver derives from the git tag at build
 * time, so a release build reports e.g. `0.2.0`. Falls back to a default when
 * running without a packaged manifest (e.g. `sbt run`).
 */
object OnionVersion {

  private val Fallback = "0.2.0"

  val value: String =
    packageVersion.orElse(manifestVersion).getOrElse(Fallback)

  private def packageVersion: Option[String] =
    Option(this.getClass.getPackage).flatMap(p => Option(p.getImplementationVersion)).filter(_.nonEmpty)

  private def manifestVersion: Option[String] = {
    try {
      val resources = this.getClass.getClassLoader.getResources("META-INF/MANIFEST.MF")
      var found: Option[String] = None
      while (found.isEmpty && resources.hasMoreElements) {
        val url = resources.nextElement()
        val is = url.openStream()
        try {
          val attrs = new java.util.jar.Manifest(is).getMainAttributes
          if (attrs.getValue("Implementation-Title") == "onion") {
            found = Option(attrs.getValue("Implementation-Version")).filter(_.nonEmpty)
          }
        } finally is.close()
      }
      found
    } catch {
      case _: Throwable => None
    }
  }
}
