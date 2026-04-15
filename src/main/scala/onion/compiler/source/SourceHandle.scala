package onion.compiler.source

import java.io.Reader

trait SourceHandle {
  def openReader(): Reader
  def name: String
}
