package onion.compiler
import java.io.{Reader, StringReader}

case class StringInputSource(input: String) extends InputSource {
  override def openReader: Reader = new StringReader(input)

  override def name: String = "<none>"
}
