package onion.compiler.source

import onion.compiler.InputSource

object InputSourceAdapter {
  def fromInputSources(srcs: Seq[InputSource]): Seq[SourceHandle] =
    srcs

  def toInputSource(handle: SourceHandle): InputSource =
    handle match {
      case input: InputSource => input
      case other => new AdaptedInputSource(other)
    }

  private final class AdaptedInputSource(delegate: SourceHandle) extends InputSource {
    override def openReader(): java.io.Reader = delegate.openReader()
    override def name: String = delegate.name
  }
}
