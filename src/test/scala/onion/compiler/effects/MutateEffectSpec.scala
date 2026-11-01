package onion.compiler.effects

import java.io.StringReader
import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * The `mutate` effect (RFC #2557, design section 3.1).
 *
 * An empty effect set used to mean "no I/O". It did not mean "no mutation": the table
 * marked `List`, `ArrayList`, `HashMap` and `StringBuilder` as pure, so a method that
 * only adds to a list looked pure. A contract expression must not write, so the
 * inference now also says whether a body writes to memory.
 *
 * `mutate` is internal. It describes memory, not the outside world, so a `tool` never
 * declares it and never needs to: the capability layer ignores it.
 */
class MutateEffectSpec extends AnyFunSpec {
  import Effect._

  private def compile(source: String) =
    new OnionCompiler(new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false))
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Mutate.on")))

  private def infer(source: String): Map[String, Set[Effect]] = {
    val result = compile(source)
    assert(!result.hasErrors, s"compile failed: ${result.allErrors.map(_.message).mkString("; ")}")
    val typed = result.debugArtifacts.typedClasses.getOrElse(Seq.empty)
    EffectInference.infer(typed).map(me => s"${me.className}#${me.methodName}" -> me.effects).toMap
  }

  private def errorCodes(source: String): Seq[String] =
    compile(source).allErrors.flatMap(_.errorCode)

  describe("what counts as a mutation") {
    it("is a field write in a method") {
      val eff = infer(
        """class C {
          |  var n: Int
          |public:
          |  def set(v: Int): void { n = v }
          |}""".stripMargin)
      assert(eff("C#set") == Set(Mutate))
    }

    it("is an array element write") {
      val eff = infer(
        """class A {
          |public:
          |  static def f(a: Int[]): void { a[0] = 1 }
          |}""".stripMargin)
      assert(eff("A#f") == Set(Mutate))
    }

    it("is not a field read") {
      val eff = infer(
        """class C {
          |  var n: Int
          |public:
          |  def get(): Int = n
          |}""".stripMargin)
      assert(eff("C#get") == Set.empty[Effect])
    }

    it("does not count a constructor initialising its own object") {
      val eff = infer(
        """class C {
          |  var n: Int
          |public:
          |  def this(v: Int) { this.n = v }
          |}""".stripMargin)
      assert(eff("C#<init>") == Set.empty[Effect])
    }

    it("counts a constructor writing another object") {
      val eff = infer(
        """class C {
          |public:
          |  var n: Int
          |  def this(o: C) { o.n = 1 }
          |}""".stripMargin)
      assert(eff("C#<init>") == Set(Mutate))
    }

    it("passes through a call to a mutating method") {
      val eff = infer(
        """class C {
          |  var n: Int
          |public:
          |  def set(v: Int): void { n = v }
          |  def reset(): void { set(0) }
          |}""".stripMargin)
      assert(eff("C#reset") == Set(Mutate))
    }

    it("sits next to a world effect without hiding it") {
      val eff = infer(
        """class C {
          |  var n: Int
          |public:
          |  def log(v: Int): void { n = v; IO::println("set") }
          |}""".stripMargin)
      assert(eff("C#log") == Set(Mutate, Console))
    }
  }

  describe("library calls") {
    it("treats adding to a list as a mutation") {
      val eff = infer(
        """import { java.util.* }
          |class A {
          |public:
          |  static def f(xs: List[Int]): void { xs.add(1) }
          |}""".stripMargin)
      assert(eff("A#f") == Set(Mutate))
    }

    it("treats reading a list as pure") {
      val eff = infer(
        """import { java.util.* }
          |class A {
          |public:
          |  static def g(xs: List[Int]): Int = xs.size
          |}""".stripMargin)
      assert(eff("A#g") == Set.empty[Effect])
    }

    it("treats building a fresh collection as pure") {
      val eff = infer(
        """import { java.util.* }
          |class A {
          |public:
          |  static def h(): List[Int] = new ArrayList[Int]()
          |}""".stripMargin)
      assert(eff("A#h") == Set.empty[Effect])
    }

    it("treats appending to a StringBuilder as a mutation and printing it as pure") {
      val eff = infer(
        """class A {
          |public:
          |  static def a(sb: StringBuilder): void { sb.append("x") }
          |  static def b(sb: StringBuilder): String = sb.toString()
          |}""".stripMargin)
      assert(eff("A#a") == Set(Mutate))
      assert(eff("A#b") == Set.empty[Effect])
    }

    it("defaults an unlisted method of a mutable class to a mutation, not to pure") {
      assert(EffectTable.effectsOrUnknown("java.util.List", "aMethodAddedInSomeFutureJdk") == Set(Mutate))
      assert(EffectTable.effectsOrUnknown("java.util.List", "add") == Set(Mutate))
      assert(EffectTable.effectsOrUnknown("java.util.List", "size") == Set.empty[Effect])
      assert(EffectTable.effectsOrUnknown("java.util.ArrayList", "<init>") == Set.empty[Effect])
    }
  }

  describe("capabilities are about the world, not about memory") {
    it("lets a tool write to a list without declaring anything") {
      val src =
        """tool collect(n: Int): Int
          |{
          |  val xs: List[Int] = []
          |  xs.add(n)
          |  return xs.size
          |}
          |""".stripMargin
      val codes = errorCodes(src)
      assert(!codes.contains("E0077"), codes.toString)
      assert(codes.isEmpty, codes.toString)
    }

    it("rejects `mutate` as a declared capability") {
      val codes = errorCodes(
        """tool t(n: Int): Int
          |  requires { mutate }
          |{
          |  return n
          |}
          |""".stripMargin)
      assert(codes.contains("E0079"), codes.toString)
    }

    it("does not report a declared capability as unused because of mutation") {
      val codes = errorCodes(
        """tool t(n: Int): Int
          |  requires { console }
          |{
          |  val xs: List[Int] = []
          |  xs.add(n)
          |  IO::println("done")
          |  return xs.size
          |}
          |""".stripMargin)
      assert(codes.isEmpty, codes.toString)
    }
  }

  describe("the vocabulary") {
    it("keeps `mutate` out of the declarable effects") {
      assert(!Effect.all.contains(Mutate))
      assert(Effect.internal == Set[Effect](Mutate))
      assert(Effect.parse("mutate") == Some(Mutate))
      assert(Effect.parseCapability("mutate").isEmpty)
      assert(Effect.parseCapability("console") == Some(Console))
    }

    it("renders like any other effect") {
      assert(Effect.render(Set(Mutate)) == "mutate")
      assert(Effect.render(Set(Mutate, Console)) == "console,mutate")
    }
  }
}
