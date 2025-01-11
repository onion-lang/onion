package onion.compiler.tools

import onion.tools.Shell
import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

class AbstractShellSpec extends AnyFunSpec with Diagrams {
  val shell = Shell(Seq())
}