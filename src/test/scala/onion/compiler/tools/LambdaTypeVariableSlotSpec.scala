package onion.compiler.tools

import onion.tools.Shell

/**
 * #3018: `new Lazy[T](() -> 5)` inside a generic class, and `extends Lazy[T](() -> 5)`, were
 * accepted for a `() -> T` slot, then threw ClassCastException at the first read. An
 * assignment to the same slot, `val f: () -> T = () -> 5`, was already an error.
 */
class LambdaTypeVariableSlotSpec extends AbstractShellSpec {
  private def run(source: String): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", Array()) } }
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, new String(buf.toByteArray, "UTF-8"))
  }

  private val lazyClass =
    """class Lazy[T] {
      |public:
      |  val f: () -> T
      |  def this(f: () -> T) { this.f = f }
      |}
      |""".stripMargin

  describe("a lambda for a `() -> T` constructor slot") {
    it("rejects a lambda whose result is not T in `new Lazy[T](..)`") {
      val (result, out) = run(lazyClass +
        """class Mk[T] {
          |public:
          |  def mk(): Lazy[T] = new Lazy[T](() -> 5)
          |}
          |IO::println("ran")
          |""".stripMargin)
      assert(result == Shell.Failure(-1), out)
      assert(!out.contains("ClassCastException") && !out.contains("cannot be cast"), out)
    }

    it("rejects a lambda whose result is not T in `extends Lazy[T](..)`") {
      val (result, out) = run(lazyClass +
        """class Bad[T] extends Lazy[T](() -> 5)
          |val s: String = new Bad[String]().f.call()
          |IO::println(s)
          |""".stripMargin)
      assert(result == Shell.Failure(-1), out)
      assert(!out.contains("ClassCastException") && !out.contains("cannot be cast"), out)
    }

    it("still accepts a lambda that returns a T, a concrete type, or null for T?") {
      val (result, out) = run(lazyClass +
        """class Keep[T] {
          |public:
          |  def mk(v: T): Lazy[T] = new Lazy[T](() -> v)
          |  def none(): Lazy[T?] = new Lazy[T?](() -> null)
          |}
          |class Sub[T] extends Lazy[T?](() -> null)
          |val a: String = new Keep[String]().mk("hi").f.call()
          |val b: Int = new Lazy[Int](() -> 7).f.call()
          |val c: String? = new Sub[String]().f.call()
          |IO::println(a + b + c)
          |""".stripMargin)
      assert(result.isInstanceOf[Shell.Success], out)
      assert(out.contains("hi7null"), out)
    }

    it("still accepts a lambda that never returns, as a not-yet-initialised slot") {
      val (result, out) = run(lazyClass +
        """class B[T] extends Lazy[T](() -> throw new RuntimeException("sup"))
          |class Mk[T] {
          |public:
          |  def mk(): Lazy[T] = new Lazy[T](() -> throw new RuntimeException("mk"))
          |  def both(c: Boolean): Lazy[T] =
          |    new Lazy[T](() -> if c { throw new RuntimeException("a") } else { throw new RuntimeException("b") })
          |}
          |try { new B[String]().f.call() } catch e: RuntimeException { IO::println(e.getMessage()) }
          |try { new Mk[String]().mk().f.call() } catch e: RuntimeException { IO::println(e.getMessage()) }
          |try { new Mk[String]().both(false).f.call() } catch e: RuntimeException { IO::println(e.getMessage()) }
          |""".stripMargin)
      assert(result.isInstanceOf[Shell.Success], out)
      assert(out.contains("sup") && out.contains("mk") && out.contains("b"), out)
    }

    it("still accepts a block lambda whose only value is null") {
      val (result, out) = run(lazyClass +
        """class Mk[T] {
          |public:
          |  def mk(): Lazy[T] = new Lazy[T](() -> { IO::println("side"); null })
          |}
          |class Sub[T] extends Lazy[T](() -> { IO::println("sub"); null })
          |val a: String? = new Mk[String]().mk().f.call()
          |val b: String? = new Sub[String]().f.call()
          |IO::println("got " + a + b)
          |""".stripMargin)
      assert(result.isInstanceOf[Shell.Success], out)
      assert(out.contains("side") && out.contains("sub") && out.contains("got nullnull"), out)
    }
  }
}
