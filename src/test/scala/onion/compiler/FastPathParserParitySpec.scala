package onion.compiler

import onion.compiler.parser.{JJOnionParser, OnionParser}

import java.io.{File, StringReader}
import org.scalatest.funspec.AnyFunSpec
import scala.io.Source

/**
 * The handwritten recursive-descent parser (`onion.compiler.parser.OnionParser`) is meant to
 * produce the identical AST to the JavaCC parser for every program the latter accepts, falling
 * back (`OnionParser.Fail`) on anything it cannot handle -- see CLAUDE.md's Parsing section.
 * Nothing else in the suite checks that claim directly: the default test run only exercises the
 * fast path (it is tried first and normally succeeds), and CI never sets
 * `-Donion.parser.javacc=true` to run the JavaCC parser alone. A fast-path bug that still
 * returns a *different* tree instead of failing would compile silently -- exactly the
 * miscompilation risk the project's quality bar treats as a blocker -- without this test.
 */
class FastPathParserParitySpec extends AnyFunSpec {

  private def samples: Seq[File] =
    Option(new File("run").listFiles()).getOrElse(Array.empty[File]).toSeq
      .filter(_.getName.endsWith(".on"))
      .sortBy(_.getName)

  describe("the handwritten fast-path parser matches the JavaCC parser") {
    val files = samples

    it("finds the sample programs") {
      assert(files.nonEmpty, "no .on samples found under run/")
    }

    files.foreach { f =>
      it(s"run/${f.getName}: fast-path AST equals the JavaCC AST when both accept it") {
        val code = Source.fromFile(f, "UTF-8").mkString
        val fast = try Some(OnionParser.parse(code)) catch { case _: OnionParser.Fail => None }
        fast.foreach { fastUnit =>
          val jjUnit = new JJOnionParser(new StringReader(code)).unit()
          assert(fastUnit == jjUnit, s"run/${f.getName}: fast-path AST diverged from the JavaCC AST")
        }
      }
    }
  }
}
