package onion.compiler.source

import onion.compiler.toolbox.Inputs

import java.io.Reader

class FileSource(val name: String) extends SourceHandle {
  override def openReader(): Reader =
    Inputs.newReader(name)

  // One read and one decode, under the same default charset `openReader()` (a FileReader)
  // uses; the reader path decoded through a small buffer into a StringBuilder.
  override def readText(): String =
    new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(name)), java.nio.charset.Charset.defaultCharset())
}
