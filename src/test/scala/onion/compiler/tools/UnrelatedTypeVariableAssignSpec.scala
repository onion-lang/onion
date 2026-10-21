package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A slot typed by a type parameter `T` accepts only a `T`: the same variable,
 * one whose bound chain reaches `T`, or `null`. It used to accept anything
 * that fit `T`'s bound, so `t = u` between unrelated `T` and `U` compiled and
 * then threw a ClassCastException on the next read (#2308). Java rejects all
 * of these at compile time.
 */
class UnrelatedTypeVariableAssignSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a value that is not a T, put into a T slot") {
    it("rejects assigning a sibling U to a T field (the issue's repro)") {
      val src =
        """class S[T, U] {
          |  var t: T
          |  var u: U
          |public:
          |  def this(x: T, y: U) { t = x; u = y }
          |  def f(): void { t = u }
          |  def getT(): T = t
          |}
          |def main(args: String[]): Int {
          |  val s = new S[String, Integer]("a", 1)
          |  s.f()
          |  val x: String = s.getT()
          |  return 0
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("rejects returning a U where T is declared") {
      val src =
        """class S[T, U] {
          |  var u: U
          |public:
          |  def this(y: U) { u = y }
          |  def g(): T { return u }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("rejects a U for a local declared as T") {
      val src =
        """class S[T, U] {
          |  var u: U
          |public:
          |  def this(y: U) { u = y }
          |  def g(): void { val t: T = u }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin
      assert(errorCodes(src).contains("E0000"))
    }
    it("rejects T = U when both share a non-Object bound") {
      val src =
        """class S[T extends CharSequence, U extends CharSequence] {
          |  var t: T
          |  var u: U
          |public:
          |  def this(x: T, y: U) { t = x; u = y }
          |  def f(): void { t = u }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("rejects an Object assigned to a T field") {
      val src =
        """class Box[T] {
          |  var item: T
          |public:
          |  def this(x: T) { item = x }
          |  def set(o: Object): void { item = o }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("rejects a B assigned to a T extends B field") {
      val src =
        """class Box[B, T extends B] {
          |  var t: T
          |  var b: B
          |public:
          |  def this(x: T, y: B) { t = x; b = y }
          |  def f(): void { t = b }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("rejects a concrete String assigned to a T field") {
      val src =
        """class Box[T] {
          |  var item: T
          |public:
          |  def this(x: T) { item = x }
          |  def f(): void { item = "oops" }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
  }

  describe("a value that is a T") {
    it("still compiles and runs: same variable, a chained variable, null, and an explicit cast") {
      val r = shell.run(
        """class Box[T, U extends T] {
          |  var t: T
          |  var u: U
          |public:
          |  def this(x: T, y: U) { t = x; u = y }
          |  def fromSame(x: T): void { t = x }
          |  def fromChain(): void { t = u }
          |  def clear(): void { t = null }
          |  def fromCast(o: Object): void { t = (o as T) }
          |  def get(): T = t
          |}
          |def main(args: String[]): Int {
          |  val b = new Box[String, String]("a", "bcd")
          |  b.fromSame("xy")
          |  val n1 = b.get().length()
          |  b.fromChain()
          |  val n2 = b.get().length()
          |  b.fromCast("hello")
          |  val n3 = b.get().length()
          |  b.clear()
          |  return n1 * 100 + n2 * 10 + n3
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(235) == r)
    }
    it("is what an inherited member returns on this, in a generic subclass") {
      val r = shell.run(
        """class Box[T] {
          |  val value: T
          |public:
          |  def this(v: T) { value = v }
          |  def get(): T { return value }
          |}
          |class Mid[U](v: U) extends Box[U](v) {
          |public:
          |  def implicitSelf(): U { return get() }
          |  def explicitSelf(): U { return this.get() }
          |}
          |def main(args: String[]): Int {
          |  val m = new Mid[String]("abc")
          |  return m.implicitSelf().length() * 10 + m.explicitSelf().length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(33) == r)
    }
    it("is what a case of select this binds, in a generic ADT enum") {
      val r = shell.run(
        """enum Lookup[T] {
          |  case Found(value: T)
          |  case Missing(key: String)
          |public:
          |  def getOrElse(default: T): T = select this {
          |    case f is Found: f.value()
          |    case m is Missing: default
          |  }
          |  def orElse(default: T): T = select this {
          |    case Found(v): v
          |    case Missing(k): default
          |  }
          |}
          |def main(args: String[]): Int {
          |  val a: Lookup[String] = new Found[String]("abcd")
          |  val b: Lookup[String] = new Missing[String]("k")
          |  return a.getOrElse("x").length() * 100 + b.getOrElse("xy").length() * 10 + a.orElse("").length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(424) == r)
    }
    it("still lets a generic method's type argument be inferred from a concrete argument") {
      val r = shell.run(
        """class Util {
          |public:
          |  static def id[T](x: T): T = x
          |}
          |class Holder[T] {
          |  var item: T
          |public:
          |  def this(x: T) { item = x }
          |  def wrap(): List[T] { val xs: List[T] = [item]; return xs }
          |}
          |def main(args: String[]): Int {
          |  val s: String = Util::id("abc")
          |  val h = new Holder[String]("de")
          |  return s.length() * 10 + h.wrap()[0].length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(32) == r)
    }
  }
}
