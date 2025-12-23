package onion.compiler

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer

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
        val entries = new ArrayBuffer[String]()
        try {
          var line = reader.readLine()
          while (line != null) {
            val trimmed = line.trim
            if (trimmed.nonEmpty && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
              entries += trimmed
            }
            line = reader.readLine()
          }
        } finally {
          reader.close()
        }
        if (entries.nonEmpty) entries.toSeq else Fallback
    }
  }
}
