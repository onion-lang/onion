package onion.compiler.source

import java.io.Reader

trait SourceHandle {
  def openReader(): Reader
  def name: String

  /** The whole text. The default drains `openReader()`; a file-backed source decodes its bytes in one step. */
  def readText(): String = {
    val reader = openReader()
    val sb = new java.lang.StringBuilder
    val buf = new Array[Char](1 << 16)
    try {
      var n = reader.read(buf)
      while (n != -1) { sb.append(buf, 0, n); n = reader.read(buf) }
    } finally reader.close()
    sb.toString
  }
}
