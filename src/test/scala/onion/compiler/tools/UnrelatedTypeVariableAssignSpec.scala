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

  private def errors(src: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors
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

  describe("a bounded type variable joined with another branch (#2328 review, round 3)") {
    it("joins if c { n } else { 3 } through N's bound, Number") {
      val r = shell.run(
        """class C[N extends Number] {
          |  val n: N
          |public:
          |  def this(x: N) { n = x }
          |  def pick(c: Boolean): Number = if c { n } else { 3 }
          |  def pickD(c: Boolean): Number = if c { n } else { 2.5 }
          |}
          |def main(args: String[]): Int {
          |  val c = new C[Integer](5)
          |  return c.pick(true).intValue() * 100 + c.pick(false).intValue() * 10 + c.pickD(false).intValue()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(532) == r)
    }
    it("keeps the bound's methods: (if c { s } else { \"abc\" }).length()") {
      val r = shell.run(
        """class C[S extends CharSequence] {
          |  val s: S
          |public:
          |  def this(x: S) { s = x }
          |  def len(c: Boolean): Int = (if c { s } else { "abc" }).length()
          |}
          |def main(args: String[]): Int { val c = new C[String]("hello"); return c.len(true) * 10 + c.len(false) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(53) == r)
    }
    it("still rejects a bounded join put back into the type variable") {
      val src =
        """class C[N extends Number] {
          |  val n: N
          |public:
          |  def this(x: N) { n = x }
          |  def bad(c: Boolean): N = if c { n } else { 3 }
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }
    it("joins a nullable T with null to T?") {
      val r = shell.run(
        """class Box[T] {
          |  var t: T?
          |public:
          |  def this(x: T) { t = x }
          |  def j(c: Boolean): T? = if c { t } else { null }
          |}
          |def main(args: String[]): Int { val b = new Box[String]("ab"); return if b.j(false) == null { b.j(true)?.length() ?: 0 } else { 9 } }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
  }

  describe("a Comparator[T]/Consumer[T]/Predicate[T] passed to a JDK ? super T parameter (#2328 review, round 3)") {
    it("accepts List.sort, forEach, removeIf and Collections::sort") {
      val r = shell.run(
        """import { java.util.*; java.util.function.* }
          |class Bag[T] {
          |  val xs: List[T] = new ArrayList[T]()
          |public:
          |  def add(x: T): void { xs.add(x) }
          |  def sortBy(c: Comparator[T]): void { xs.sort(c) }
          |  def each(k: Consumer[T]): void { xs.forEach(k) }
          |  def drop(p: Predicate[T]): void { xs.removeIf(p) }
          |  def sortAgain(c: Comparator[T]): void { Collections::sort(xs, c) }
          |  def first(): T = xs.get(0)
          |  def size(): Int = xs.size()
          |}
          |def main(args: String[]): Int {
          |  val b = new Bag[String]()
          |  b.add("b"); b.add("a"); b.add("cc")
          |  b.sortBy((p, q) -> p.compareTo(q))
          |  val sorted = b.first()
          |  b.drop((s) -> s.length() > 1)
          |  b.sortAgain((p, q) -> q.compareTo(p))
          |  val sb = new StringBuilder()
          |  b.each((s) -> { sb.append(s); return })
          |  return if sorted == "a" && sb.toString() == "ba" { b.size() } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
  }

  describe("an F-bounded type variable joined with another branch (#2328 review, round 4)") {
    it("joins T extends Comparable[T] with a String under an Object target") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def pick(c: Boolean, t: T): Object {
          |    val o: Object = if c { t } else { "none" }
          |    return o
          |  }
          |}
          |def main(args: String[]): Int {
          |  val a = new J[String]().pick(true, "t").toString()
          |  val b = new J[Integer]().pick(false, 1).toString()
          |  return if a == "t" && b == "none" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("joins a method's N extends Comparable[N] with an Int literal") {
      val r = shell.run(
        """def num[N extends Comparable[N]](c: Boolean, n: N): Object = if c { n } else { 0 }
          |def main(args: String[]): Int {
          |  val a = num(true, "x").toString()
          |  val b = num(false, "x").toString()
          |  return if a == "x" && b == "0" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("calls toString on the join and joins in a select") {
      val r = shell.run(
        """class K[T extends Comparable[T]] {
          |public:
          |  def s(c: Boolean, t: T): String = (if c { t } else { "none" }).toString()
          |  def sel(c: Boolean, t: T): Object = select c { case true: t; case false: 42 }
          |}
          |def main(args: String[]): Int {
          |  val k = new K[String]()
          |  return if k.s(true, "a") == "a" && k.s(false, "a") == "none" && k.sel(false, "a").toString() == "42" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
  }

  describe("a type variable bounded by a later one, and this, in a join (#2328 review, round 5)") {
    it("joins A extends B with B, in either branch order and in a select") {
      val r = shell.run(
        """class K[A extends B, B] {
          |  var a: A
          |  var b: B
          |public:
          |  def this(x: A, y: B) { a = x; b = y }
          |  def pick(c: Boolean): B = if c { a } else { b }
          |  def pick2(c: Boolean): B = if c { b } else { a }
          |  def sel(n: Int): B = select n {
          |    case 1: a
          |    else: b
          |  }
          |}
          |def main(args: String[]): Int {
          |  val k = new K[String, Object]("a", "b")
          |  val s = "" + k.pick(true) + k.pick2(false) + k.sel(1) + k.sel(2)
          |  return if s == "aaab" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("sees a later bound's members through A extends B, B extends CharSequence") {
      val r = shell.run(
        """class K[A extends B, B extends CharSequence] {
          |  var a: A
          |  var b: B
          |public:
          |  def this(x: A, y: B) { a = x; b = y }
          |  def pick(c: Boolean): B = if c { a } else { b }
          |  def len(): Int = a.length() + b.length()
          |}
          |def main(args: String[]): Int {
          |  val k = new K[String, String]("ab", "c")
          |  return if k.pick(true).toString() == "ab" && k.len() == 3 { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("still rejects a B put into an A slot") {
      val src =
        """class K[A extends B, B] {
          |public:
          |  def wrong(b: B): A = b
          |}
          |""".stripMargin
      assert(errorCodes(src).contains("E0000"))
    }
    it("joins this with an o: Mid[U] in either branch order") {
      val r = shell.run(
        """class Box[T] {
          |  val value: T
          |public:
          |  def this(v: T) { value = v }
          |  def get(): T = value
          |}
          |class Mid[U](v: U) extends Box[U](v) {
          |public:
          |  def pick(c: Boolean, o: Mid[U]): U = (if c { this } else { o }).get()
          |  def pick2(c: Boolean, o: Mid[U]): U = (if c { o } else { this }).get()
          |}
          |def main(args: String[]): Int {
          |  val m = new Mid[String]("a")
          |  val o = new Mid[String]("b")
          |  val s = m.pick(true, o) + m.pick(false, o) + m.pick2(true, o) + m.pick2(false, o)
          |  return if s == "abba" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
  }

  // Round 6 (#2328 panel at d1ae17a): a member reached through `super` more
  // than one level up, or through a receiver typed by a variable bounded by
  // an applied type, must have its type variables substituted along the
  // whole chain. On `main` the loose rule hid that this was missing.
  describe("a member reached through several substitution steps (#2328 review, round 6)") {
    it("types super.get() two levels up as the subclass's own variable") {
      val r = shell.run(
        """class Box[T](val x: T) {
          |public:
          |  def get(): T = x
          |}
          |class Mid[U](y: U) extends Box[U](y) {
          |}
          |class Leaf[V](z: V) extends Mid[V](z) {
          |public:
          |  override def get(): V = super.get()
          |}
          |def main(args: String[]): Int {
          |  val s: String = new Leaf[String]("hi").get()
          |  return if s == "hi" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("types b.get() on b: B extends Box[T] as the holder's T") {
      val r = shell.run(
        """class Holder[T, B extends Box[T]] {
          |  val b: B
          |public:
          |  def this(x: B) { b = x }
          |  def get(): T = b.get()
          |}
          |class Box[T](val x: T) {
          |public:
          |  def get(): T = x
          |}
          |def main(args: String[]): Int {
          |  return new Holder[String, Box[String]](new Box[String]("q")).get().length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("types l.get(0) on l: L extends List[T] as T") {
      val r = shell.run(
        """import { java.util.* }
          |class LT[T, L extends List[T]] {
          |  val l: L
          |public:
          |  def this(x: L) { l = x }
          |  def head(): T = l.get(0)
          |}
          |def main(args: String[]): Int {
          |  val xs = new ArrayList[Integer](); xs.add(7)
          |  val h: Integer = new LT[Integer, ArrayList[Integer]](xs).head()
          |  return h
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }
    it("accepts a static generic method reading through B extends Box[T]") {
      val src =
        """class Box[T](val x: T) {
          |public:
          |  def get(): T = x
          |}
          |class U {
          |public:
          |  static def unbox[T, B extends Box[T]](b: B): T = b.get()
          |}
          |""".stripMargin
      assert(errorCodes(src).isEmpty)
    }
    it("passes and reads a T through a bounded receiver and through super") {
      val r = shell.run(
        """class Box[T] {
          |  var x: T
          |public:
          |  def this(v: T) { x = v }
          |  def get(): T = x
          |  def set(v: T): void { x = v }
          |}
          |class Holder[T, B extends Box[T]] {
          |  val b: B
          |public:
          |  def this(x: B) { b = x }
          |  def put(t: T): T { b.set(t); return b.get() }
          |}
          |class Mid[U](y: U) extends Box[U](y) { }
          |class Leaf[V](z: V) extends Mid[V](z) {
          |public:
          |  def put(v: V): V { super.set(v); return super.get() }
          |}
          |def main(args: String[]): Int {
          |  val s = new Holder[String, Box[String]](new Box[String]("q")).put("r") + new Leaf[String]("a").put("b")
          |  return if s == "rb" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("still rejects a value that is not a T through those paths") {
      val src =
        """class Box[T] {
          |  var x: T
          |public:
          |  def this(v: T) { x = v }
          |  def get(): T = x
          |  def set(v: T): void { x = v }
          |}
          |class Holder[T, U, B extends Box[T]] {
          |  val b: B
          |public:
          |  def this(x: B) { b = x }
          |  def bad(o: Object): U = b.get()
          |}
          |class Mid[U](y: U) extends Box[U](y) { }
          |class Leaf[V, W](z: V) extends Mid[V](z) {
          |public:
          |  def bad(w: W): W = super.get()
          |}
          |""".stripMargin
      assert(errorCodes(src).count(_ == "E0000") == 2)
    }
  }

  describe("a self-referencing bound, a nullable join and a shadowing T (#2328 review, round 7)") {
    val animal =
      """abstract class Animal[A extends Animal[A]] {
        |public:
        |  abstract def me(): A
        |  def chain(): A = me().me()
        |  def chainVal(): A { val x: A = me(); return x.me() }
        |}
        |class Dog extends Animal[Dog] {
        |public:
        |  def me(): Dog = this
        |}
        |""".stripMargin
    it("passes a member reached through an F-bound back as the class's own A") {
      val r = shell.run(
        animal +
          """def main(args: String[]): Int {
            |  val d = new Dog()
            |  return if (d.chain() is Dog) && (d.chainVal() is Dog) { 1 } else { 0 }
            |}
            |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("still rejects another F-bounded variable's value in an A slot") {
      val src =
        """abstract class Animal[A extends Animal[A], B extends Animal[B]] {
          |public:
          |  abstract def me(): A
          |  def f(b: B): A = b.me()
          |}
          |""".stripMargin
      assert(errorCodes(src).contains("E0000"))
    }
    it("keeps a join through a bare T nullable, so dereferencing it is still rejected") {
      for (body <- Seq(
        "(if c { t } else { \"s\" }).toString()",
        "(if c { \"s\" } else { t }).toString()",
        "(select c {\n  case true: t\n  else: \"s\"\n  }).toString()"
      )) {
        val src =
          s"""class J[T](val t: T) {
             |public:
             |  def k(c: Boolean): String = $body
             |}
             |""".stripMargin
        assert(errorCodes(src).nonEmpty, body)
      }
    }
    it("still joins a non-null T's bound without a null check") {
      val r = shell.run(
        """class J[T extends Object](val t: T) {
          |public:
          |  def k(c: Boolean): String = (if c { t } else { "s" }).toString()
          |}
          |def main(args: String[]): Int {
          |  return if new J[String]("a").k(true) == "a" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("explains two different type parameters that share a name") {
      val src =
        """class K[T] {
          |  var t: T
          |public:
          |  def this(x: T) { t = x }
          |  def m[T](a: T): void { t = a }
          |}
          |""".stripMargin
      val es = errors(src)
      assert(es.flatMap(_.errorCode) == Seq("E0000"))
      assert(es.head.message.contains("`[T]`"))
    }
  }

  describe("a chain through an F-bound, and the hints on a T slot (#2328 review, round 8)") {
    it("runs a fluent builder's named(\"x\").named(\"y\").get()") {
      val r = shell.run(
        """abstract class Builder[B extends Builder[B]] {
          |  var name: String = ""
          |public:
          |  abstract def me(): B
          |  def named(n: String): B { name = n; return me() }
          |  def get(): String = name
          |  def go(): String = named("x").named("y").get()
          |}
          |class PB extends Builder[PB] {
          |public:
          |  def me(): PB = this
          |}
          |def main(args: String[]): Int {
          |  return if new PB().go() == "y" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("runs me().me().me(), a.me().me() and a five-step chain") {
      val r = shell.run(
        """abstract class Animal[A extends Animal[A]] {
          |public:
          |  abstract def me(): A
          |  def chain(): A = me().me().me()
          |  def chain2(a: A): A = a.me().me()
          |  def deep(): A = me().me().me().me().me()
          |}
          |class Cat extends Animal[Cat] {
          |public:
          |  def me(): Cat = this
          |}
          |def main(args: String[]): Int {
          |  val c = new Cat()
          |  return if c.chain() === c && c.chain2(c) === c && c.deep() === c { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("still rejects another F-bounded variable's value at the end of a chain") {
      // Compared against the same class without `other`, so the bound errors
      // a second class's `[A extends Animal[A]]` gets today do not count.
      def holder(extra: String): String =
        s"""abstract class Animal[A extends Animal[A]] {
           |public:
           |  abstract def me(): A
           |}
           |class Holder[A extends Animal[A], C extends Animal[C]] {
           |public:
           |  def same(a: A): A = a.me().me().me()
           |  $extra
           |}
           |""".stripMargin
      val base = errorCodes(holder(""))
      assert(errorCodes(holder("def other(c: C): A = c.me().me().me()")) == base :+ "E0000")
    }
    it("does not suggest !! for an Object? or a T/U join put into a bare T slot") {
      for (body <- Seq(
        "def setMaybe(o: Object?): void { t = o }",
        "def g(c: Boolean): T = if c { t } else { u }"
      )) {
        val src =
          s"""class S[T, U] {
             |  var t: T
             |  var u: U
             |public:
             |  def this(a: T, b: U) { t = a; u = b }
             |  $body
             |}
             |""".stripMargin
        val es = errors(src)
        assert(es.flatMap(_.errorCode) == Seq("E0000"), body)
        assert(!es.head.message.contains("!!"), body)
      }
    }
    it("still suggests !! (E0070) for a T? value put into a non-null T slot") {
      val src =
        """class N[T extends Object] {
          |  var t: T
          |public:
          |  def this(a: T) { t = a }
          |  def set(o: T?): void { t = o }
          |}
          |""".stripMargin
      val es = errors(src)
      assert(es.flatMap(_.errorCode) == Seq("E0070"))
      assert(es.head.message.contains("!!"))
    }
    it("compiles the shadow hint's fix: dropping the method's [T]") {
      val src =
        """class K[T] {
          |  var t: T
          |public:
          |  def this(x: T) { t = x }
          |  def m(a: T): void { t = a }
          |}
          |""".stripMargin
      assert(errorCodes(src).isEmpty)
    }
  }
}
