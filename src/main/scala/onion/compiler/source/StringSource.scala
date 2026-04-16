package onion.compiler.source

import java.io.{Reader, StringReader}

class StringSource(input: String, val name: String = "<none>") extends SourceHandle {
  override def openReader(): Reader =
    new StringReader(input)
}
