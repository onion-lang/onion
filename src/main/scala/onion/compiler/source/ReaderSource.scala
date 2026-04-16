package onion.compiler.source

import java.io.Reader

class ReaderSource(readerFactory: () => Reader, val name: String) extends SourceHandle {
  override def openReader(): Reader =
    readerFactory()
}
