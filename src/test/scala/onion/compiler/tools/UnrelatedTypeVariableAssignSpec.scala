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
    it("leaves an inherited member on a raw receiver at its bound, as before") {
      val r = shell.run(
        """class Box[T] {
          |  var value: T
          |public:
          |  def this(v: T) { value = v }
          |  def get(): T { return value }
          |  def set(v: T): void { value = v }
          |}
          |class Mid[U](v: U) extends Box[U](v) { }
          |def touch(o: Object): String = select o {
          |  case m is Mid: { m.set("new"); m.get().toString() }
          |  case b is Box: { b.set("new"); "" + b.get() }
          |  else: "?"
          |}
          |def main(args: String[]): Int {
          |  val o: Object = new Mid[String]("old")
          |  (o as Mid).set("cast")
          |  val viaCast = (o as Mid).get().toString()
          |  return touch(new Mid[String]("old")).length() * 100 + touch(new Box[String]("old")).length() * 10 + viaCast.length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(334) == r)
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

  describe("this reached from inside a lambda (#2328 review)") {
    val box =
      """class Box[T] {
        |protected:
        |  var value: T
        |public:
        |  def this(v: T) { value = v }
        |  def get(): T { return value }
        |}
        |""".stripMargin
    it("types this.get() inside a lambda as the subclass's U") {
      val r = shell.run(box +
        """class Mid[U](v: U) extends Box[U](v) {
          |public:
          |  def lam(): U {
          |    val f: () -> U = () -> this.get()
          |    return f()
          |  }
          |}
          |def main(args: String[]): Int { return new Mid[String]("hello").lam().length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
    it("types self.get() inside a block lambda as the subclass's U") {
      val r = shell.run(box +
        """class Mid[U](v: U) extends Box[U](v) {
          |public:
          |  def lam(): U {
          |    val f: () -> U = () -> { val x: U = self.get(); x }
          |    return f()
          |  }
          |}
          |def main(args: String[]): Int { return new Mid[String]("hello").lam().length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
    it("types a bare inherited field and a bare inherited call inside a lambda as U") {
      val r = shell.run(box +
        """class Mid[U](v: U) extends Box[U](v) {
          |public:
          |  def lam(): U {
          |    val f: () -> U = () -> value
          |    return f()
          |  }
          |  def lam2(): U {
          |    val f: () -> U = () -> get()
          |    return f()
          |  }
          |}
          |def main(args: String[]): Int { return new Mid[String]("hello").lam().length() * 10 + new Mid[String]("hi").lam2().length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(52) == r)
    }
  }

  describe("an if/select with a T branch, under a wider target (#2328 review)") {
    it("joins if c { t } else { \"...\" } to Object, not T") {
      val r = shell.run(
        """class Box[T] {
          |  val t: T
          |public:
          |  def this(x: T) { t = x }
          |  def show(full: Boolean): String {
          |    val o: Object = if full { t } else { "..." }
          |    return "" + o
          |  }
          |}
          |def main(args: String[]): Int { return new Box[Integer](7).show(true).length() * 10 + new Box[Integer](7).show(false).length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(13) == r)
    }
    it("returns a nullable V or an Object fallback as Object") {
      val r = shell.run(
        """class Cache[K, V] {
          |  val map: java.util.HashMap[K, V] = new java.util.HashMap[K, V]()
          |public:
          |  def lookup(k: K, fallback: Object): Object {
          |    val v = map.get(k)
          |    return if v != null { v } else { fallback }
          |  }
          |}
          |def main(args: String[]): Int { val c = new Cache[String, String](); return ("" + c.lookup("k", "none")).length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(4) == r)
    }
    it("joins the cases of select this in a generic enum to Object") {
      val r = shell.run(
        """enum Opt[T] { case Some(value: T); case Nothing
          |public:
          |  def orElse(d: Object): Object = select this { case s is Some: s.value(); case n is Nothing: d }
          |}
          |def main(args: String[]): Int { return ("" + new Nothing[String]().orElse("dflt")).length() * 10 + ("" + new Some[String]("v").orElse("d")).length() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(41) == r)
    }
    it("still rejects a joined value that is not a T, put into a T slot") {
      val src =
        """class Box[T, U] {
          |  var t: T
          |  var u: U
          |public:
          |  def this(a: T, b: U) { t = a; u = b }
          |  def g1(c: Boolean, o: Object): T { return if c { t } else { o } }
          |  def g2(c: Boolean): void { t = if c { t } else { u } }
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000", "E0000"))
    }
  }
}
