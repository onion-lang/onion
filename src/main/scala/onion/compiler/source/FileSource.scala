package onion.compiler.source

import onion.compiler.toolbox.Inputs

import java.io.Reader
import java.nio.charset.Charset

class FileSource(val name: String, val charset: Charset) extends SourceHandle {
  def this(name: String) = this(name, Charset.defaultCharset())

  override def openReader(): Reader =
    Inputs.newReader(name, charset.name())

  // One read and one decode, under the same charset `openReader()` uses; the reader path
  // decoded through a small buffer into a StringBuilder.
  override def readText(): String =
    new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(name)), charset)
}
