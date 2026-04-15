package onion.compiler
import onion.compiler.source.StringSource

case class StringInputSource(input: String, override val name: String = "<none>")
  extends StringSource(input, name)
  with InputSource
