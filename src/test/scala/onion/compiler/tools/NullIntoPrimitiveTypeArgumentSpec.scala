package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** `null` never fills a slot a primitive type argument leaves (#2991): a constructor,
  * a generic method with the argument written out, and `xs[i] = v` / `m[k] = v` are E0000
  * with the `Int?` hint, as `xs.add(null)` is (#2872). */
class NullIntoPrimitiveTypeArgumentSpec extends AbstractShellSpec {
  private def outcome(src: String): Seq[(String, String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => (e.errorCode.getOrElse(""), e.message))
      case _ => Seq.empty
    }
  }
  private def codes(src: String): Seq[String] = outcome(src).map(_._1)

  private val box =
    """class Box[T] {
      |public:
      |  var v: T
      |  def this(v: T) { this.v = v }
      |}
      |""".stripMargin

  describe("null into a primitive type argument") {
    it("is E0000 for a generic class's constructor") {
      assert(codes(box + "val b = new Box[Int](null)\n") == Seq("E0000"))
    }
    it("is E0000 for a generic record's constructor") {
      assert(codes("record R[T](v: T)\nval r = new R[Int](null)\n") == Seq("E0000"))
    }
    it("is E0000 for a named constructor argument") {
      assert(codes(box + "val b = new Box[Int](v = null)\n") == Seq("E0000"))
    }
    it("is E0000 for a List element assignment") {
      assert(codes("val xs: List[Int] = [1]\nxs[0] = null\n") == Seq("E0000"))
    }
    it("is E0000 for a Map value assignment") {
      assert(codes("val m: Map[String, Int] = [\"a\": 1]\nm[\"b\"] = null\n") == Seq("E0000"))
    }
    it("hints at the nullable type argument, with no cast advice") {
      val messages = outcome(box + "val b = new Box[Long](null)\n").map(_._2)
      assert(messages.size == 1)
      assert(messages.head.contains("`Long?`"), messages.head)
      assert(!messages.head.contains("cast"), messages.head)
    }
  }

  describe("what still compiles and runs") {
    it("null for an Int? type argument, in every form") {
      val src =
        box +
          """record R[T](v: T)
            |def id[T](x: T): T = x
            |def main(args: String[]): Int {
            |  val b = new Box[Int?](null)
            |  val r = new R[Int?](null)
            |  val n: Int? = id[Int?](null)
            |  val o: Option[Int] = Option::of(null)
            |  val xs: List[Int?] = [1]
            |  xs[0] = null
            |  val m: Map[String, Int?] = ["a": 1]
            |  m["b"] = null
            |  return if b.v == null && r.v() == null && n == null && o.isEmpty() && xs[0] == null && m["b"] == null { 0 } else { 1 }
            |}
            |""".stripMargin
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
    it("null for a String type argument, and an Int stored through each form") {
      val src =
        box +
          """def main(args: String[]): Int {
            |  val s = new Box[String](null)
            |  val b = new Box[Int](4)
            |  val xs: List[Int] = [1]
            |  xs[0] = 5
            |  return b.v + xs[0] - 9
            |}
            |""".stripMargin
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
  }
}
