package onion.compiler.source

import onion.compiler.toolbox.Inputs

import java.io.Reader

class FileSource(val name: String) extends SourceHandle {
  override def openReader(): Reader =
    Inputs.newReader(name)
}
