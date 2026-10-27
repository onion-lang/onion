package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * The builtin array pipelines (`map`, `filter`, `toList`, ...) take a `T[]`, which a
 * primitive array (`Int[]`) cannot be, as in Java. A call on one is a single E0005,
 * not an E0000 reading "type Int[] is expected, but type Int[] is used" (#2458).
 */
class PrimitiveArrayPipelineSpec extends AbstractShellSpec {
  private def errors(src: String) = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private def errorCodes(src: String): Seq[Option[String]] = errors(src).map(_.errorCode)

  describe("a builtin pipeline call on a primitive array") {
    it("map with a lambda is one E0005") {
      assert(errorCodes("val b = new Int[2]\nIO::println(b.map { x -> x })\n") == Seq(Some("E0005")))
    }

    it("filter with a lambda is one E0005") {
      assert(errorCodes("val b = new Double[2]\nIO::println(b.filter { x -> x > 0.0 })\n") == Seq(Some("E0005")))
    }

    it("toList is one E0005") {
      assert(errorCodes("val b = new Int[2]\nIO::println(b.toList())\n") == Seq(Some("E0005")))
    }
  }

  describe("the hint") {
    it("shows a conversion that compiles, for Int[]") {
      val msg = errors("val b = new Int[2]\nIO::println(b.map { x -> x })\n").map(_.message).mkString
      assert(msg.contains("java.util.Arrays::stream(xs).boxed().toList().map(...)"))
      assert(!msg.contains("wait"))
    }

    it("the conversion it shows runs") {
      assert(Shell.Success("[0, 14]") == shell.run(
        "def main(args: String[]): String {\n" +
        "  val xs = new Int[2]\n" +
        "  xs[1] = 7\n" +
        "  return java.util.Arrays::stream(xs).boxed().toList().map { x -> x * 2 }.toString()\n" +
        "}\n",
        "None", Array()))
    }

    it("is left out for Char[], which has no Arrays::stream") {
      val msg = errors("val b = new Char[2]\nIO::println(b.toList())\n").map(_.message).mkString
      assert(!msg.contains("Arrays::stream"))
    }
  }

  describe("arrays the pipelines do take") {
    it("a String[] still maps and converts") {
      assert(Shell.Success("[a!, b!]|2") == shell.run(
        "def main(args: String[]): String {\n" +
        "  val ss = Strings::split(\"a,b\", \",\").toArray(new String[0])\n" +
        "  return ss.map { s -> s + \"!\" }.toString() + \"|\" + ss.toList().size\n" +
        "}\n",
        "None", Array()))
    }

    it("an Int[][] is an array of references, so it still maps") {
      assert(Shell.Success("[3, 3]") == shell.run(
        "def main(args: String[]): String {\n" +
        "  val c = new Int[2][3]\n" +
        "  return c.map { r -> r.length }.toString()\n" +
        "}\n",
        "None", Array()))
    }
  }
}
