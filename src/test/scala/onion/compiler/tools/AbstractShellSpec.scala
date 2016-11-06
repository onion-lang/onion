package onion.compiler.tools

import onion.tools.Shell
import org.scalatest._

class AbstractShellSpec extends FunSpec with DiagrammedAssertions {
  val shell = Shell(Seq())
}