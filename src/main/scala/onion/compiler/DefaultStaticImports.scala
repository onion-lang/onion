package onion.compiler

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets

object DefaultStaticImports {
  private val ResourcePath = "onion/default-static-imports.txt"
  private val Fallback = Seq(
    "java.lang.System",
    "java.lang.Runtime",
    "java.lang.Math",
    "onion.IO"
  )

  lazy val classes: Seq[String] = {
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    val stream = Option(loader.getResourceAsStream(ResourcePath))
    stream match {
      case None =>
        Fallback
      case Some(input) =>
        val reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
        try {
          val entries = Iterator.continually(reader.readLine())
            .takeWhile(_ != null)
            .map(_.trim)
            .filter(t => t.nonEmpty && !t.startsWith("#") && !t.startsWith("//"))
            .toSeq
          if (entries.nonEmpty) entries else Fallback
        } finally {
          reader.close()
        }
    }
  }
}
