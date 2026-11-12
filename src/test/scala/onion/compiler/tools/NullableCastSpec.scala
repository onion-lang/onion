package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/** A cast to or from a nullable type is checked like the non-null form, and
 *  `null` still casts to any nullable type, primitive ones included (#2388). */
class NullableCastSpec extends AbstractShellSpec {
  private def compileErrors(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(e => s"${e.code} ${e.message}").mkString("\n")
      case _ => ""
    }
  }

  describe("a cast involving a nullable type") {
    it("accepts null cast to a nullable primitive type") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val a = (null as Int?)
          |  val b = (null as Long?)
          |  return if a == null && b == null { 1 } else { 0 }
          |}
          |""".stripMargin, "NullAsNullablePrimitive.on", Array())
      assert(Shell.Success(1) == r)
    }
    it("pins an overload with null cast to a nullable primitive type") {
      val r = shell.run(
        """def f(x: Int?): Int = 1
          |def f(x: String?): Int = 2
          |def main(args: String[]): Int { return f(null as Int?) * 10 + f(null as String?) }
          |""".stripMargin, "NullOverloadPin.on", Array())
      assert(Shell.Success(12) == r)
    }
    it("reports an invalid cast from a nullable source without the `!!` hint") {
      val msgs = compileErrors(
        """def main(args: String[]): void {
          |  val s: String? = "a"
          |  val n = (s as Integer)
          |}
          |""".stripMargin)
      assert(msgs.contains("E0000"), s"expected E0000, got: $msgs")
      assert(!msgs.contains("!!"), s"`!!` does not make this cast valid, got: $msgs")
    }
    it("still gives the `!!` hint where a nullable value meets a non-null type") {
      val msgs = compileErrors(
        """def main(args: String[]): void {
          |  val s: String? = "a"
          |  val t: String = s
          |}
          |""".stripMargin)
      assert(msgs.contains("!!"), s"expected the null-safety hint, got: $msgs")
    }
  }

  describe("a cast between two different nullable primitive types (#2542)") {
    it("rejects Int? as Long? and Long? as Int? at compile time") {
      for ((from, to) <- Seq(("Int", "Long"), ("Long", "Int"), ("Int", "Double"), ("Double", "Int"))) {
        val msgs = compileErrors(
          s"def main(args: String[]): void {\n  val i: $from? = null\n  val l: $to? = (i as $to?)\n}\n")
        assert(msgs.contains("E0000"), s"expected E0000 for $from? as $to?, got: $msgs")
      }
    }
    it("still accepts a nullable primitive cast to itself, to its box, or to Object") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val i: Int? = 3
          |  val a: Int? = (i as Int?)
          |  val b: Int = (i as Int)
          |  val c: Object = (i as Object)
          |  val d = (c as Int?)
          |  return (a as Int) + b + (d as Int)
          |}
          |""".stripMargin, "NullablePrimitiveSelfCast.on", Array())
      assert(Shell.Success(9) == r)
    }
  }

  describe("a cast from a box supertype or a numeric box to a primitive (#2551)") {
    it("narrows a nullable supertype of the box, then unboxes") {
      val r = shell.run(
        """import { java.io.Serializable }
          |def main(args: String[]): Int {
          |  val n: Number? = 5
          |  val s: Serializable? = 7
          |  val c: Comparable[JInteger]? = 9
          |  return (n as Int) + (s as Int) * 10 + (c as Int) * 100
          |}
          |""".stripMargin, "NullableSuperToPrimitive.on", Array())
      assert(Shell.Success(975) == r)
    }
    it("narrows a non-null supertype of the box, then unboxes") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val n: Number = 5
          |  val o: Object = 3
          |  return (n as Int) * 10 + (o as Int)
          |}
          |""".stripMargin, "SuperToPrimitive.on", Array())
      assert(Shell.Success(53) == r)
    }
    it("unboxes a numeric box to another numeric primitive") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val i: Integer? = 3
          |  val j: Integer = 4
          |  val d: JDouble = 2.9
          |  val l: Long = (i as Long) + (j as Long)
          |  val x: Double = (j as Double)
          |  return (l as Int) * 100 + (x as Int) * 10 + (d as Int)
          |}
          |""".stripMargin, "BoxToOtherPrimitive.on", Array())
      assert(Shell.Success(742) == r)
    }
    it("narrows an out-of-range value to Byte and Short, like the primitive cast") {
      // Each line must equal the primitive cast of the same value: (300 as Byte) is 44,
      // (70000 as Short) is 4464, (-129 as Byte) is 127.
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val i: JInteger = 300
          |  val n: Number = 300
          |  val o: Object = 300
          |  val s: JInteger = 70000
          |  val m: Number? = -129
          |  val b1: Byte = (i as Byte)
          |  val b2: Byte = (n as Byte)
          |  val b3: Byte = (o as Byte)
          |  val h: Short = (s as Short)
          |  val b4: Byte = (m as Byte)
          |  val ok = b1 == (300 as Byte) && b2 == (300 as Byte) && b3 == (300 as Byte) &&
          |    h == (70000 as Short) && b4 == (-129 as Byte)
          |  val x: Int = b1
          |  val y: Int = h
          |  val z: Int = b4
          |  return if ok { x + y + z } else { -1 }
          |}
          |""".stripMargin, "NarrowBoxToByteShort.on", Array())
      assert(Shell.Success(44 + 4464 + 127) == r)
    }
    it("still rejects a cast the boxed value can never satisfy") {
      for (src <- Seq(
        "val s: String? = \"1\"\n  val n = (s as Int)",
        "val b: JBoolean = true\n  val n = (b as Int)",
        "val i: Integer = 1\n  val b = (i as Boolean)",
        "val c: JCharacter = 'a'\n  val n = (c as Int)",
        "val i: Integer = 1\n  val c = (i as Char)",
        "val n: Number = 1\n  val c = (n as Char)",
        "val c: Comparable[String] = \"a\"\n  val n = (c as Int)"
      )) {
        val msgs = compileErrors(s"def main(args: String[]): void {\n  $src\n}\n")
        assert(msgs.contains("E0000"), s"expected E0000 for <$src>, got: $msgs")
      }
    }
  }

  describe("a cast from a primitive to an applied supertype of its box (#2561)") {
    it("rejects a type argument the box can never have") {
      for (src <- Seq(
        "val c = (5 as Comparable[String])",
        "val c = (5L as Comparable[JInteger])",
        "val c = (true as Comparable[String])",
        "val x: Int? = 5\n  val c = (x as Comparable[String])"
      )) {
        val msgs = compileErrors(s"def main(args: String[]): void {\n  $src\n}\n")
        assert(msgs.contains("E0000"), s"expected E0000 for <$src>, got: $msgs")
      }
    }
    it("still boxes to the box's own applied view and to raw supertypes") {
      val r = shell.run(
        """import { java.io.Serializable }
          |def main(args: String[]): Int {
          |  val c = (5 as Comparable[JInteger])
          |  val l = (7L as Comparable[JLong])
          |  val n = (3 as Number)
          |  val o = (2 as Object)
          |  val s = (1 as Serializable)
          |  val r = (4 as Comparable)
          |  return c.compareTo(4) + l.compareTo(7L) * 10 + n.intValue() * 100 +
          |    (if o.hashCode() + s.hashCode() + r.hashCode() == 7 { 1000 } else { 0 })
          |}
          |""".stripMargin, "PrimToAppliedBoxView.on", Array())
      assert(Shell.Success(1301) == r)
    }
  }

  describe("assigning a primitive to an applied supertype of its box (#2594)") {
    it("rejects a type argument the box can never have") {
      for (src <- Seq(
        "val y: Comparable[String] = 5",
        "val y: Comparable[JInteger] = 5L",
        "val y: Comparable[String] = true"
      )) {
        val msgs = compileErrors(s"def main(args: String[]): void {\n  $src\n}\n")
        assert(msgs.contains("E0000") || msgs.contains("E0001"), s"expected an error for <$src>, got: $msgs")
      }
    }
    it("still boxes to the box's own applied view and to plain supertypes") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val c: Comparable[JInteger] = 5
          |  val n: Number = 3
          |  val o: Object = 2
          |  return c.compareTo(4) + n.intValue() * 100 + (if o.hashCode() == 2 { 1000 } else { 0 })
          |}
          |""".stripMargin, "AssignToAppliedBoxView.on", Array())
      assert(Shell.Success(1301) == r)
    }
    it("accepts the box's own view with a nullable type argument") {
      val r = shell.run(
        """import { java.util.*; java.lang.Integer as JInteger }
          |def f(c: Comparable[JInteger?]): Int = c.compareTo(1)
          |def main(args: String[]): Int {
          |  val xs: List[Comparable[JInteger?]] = new ArrayList[Comparable[JInteger?]]()
          |  xs.add(5)
          |  val v: Comparable[JInteger?] = 7
          |  val w = 8 as Comparable[JInteger?]
          |  return f(5) + xs.size() * 10 + v.compareTo(7) * 100 + w.compareTo(1) * 1000
          |}
          |""".stripMargin, "AssignToNullableArgBoxView.on", Array())
      assert(Shell.Success(1011) == r)
    }
    it("binds an unpinned T from the box's own view of a primitive argument") {
      val r = shell.run(
        """def id[T](c: Comparable[T]): Comparable[T] = c
          |def f[T](c: Comparable[T]): Int = 1
          |def main(args: String[]): Int {
          |  val r = id(5)
          |  val l = id(9L)
          |  return r.compareTo(5) + l.compareTo(9L) + f(5) * 10 + id(3).compareTo(1) * 100
          |}
          |""".stripMargin, "BindTFromBoxView.on", Array())
      assert(Shell.Success(110) == r)
    }
    it("keeps T from the arguments when its return already fits a wider expected type") {
      val r = shell.run(
        """def first[T](c: Comparable[T]): T = c as T
          |def firstOrNull[T](c: Comparable[T]): T? = c as T
          |def id[T](x: T): T = x
          |def main(args: String[]): Int {
          |  val n: Number = first(5)
          |  val m: Number? = firstOrNull(6)
          |  val k: Number = id(7)
          |  val o: Object = first(8)
          |  return n.intValue() + (m?.intValue() ?: 0) * 10 + k.intValue() * 100 + (if o.hashCode() == 8 { 1000 } else { 0 })
          |}
          |""".stripMargin, "KeepArgTUnderWiderExpected.on", Array())
      assert(Shell.Success(1765) == r)
    }
    it("still rejects a use at a type argument the box does not have") {
      val msgs = compileErrors(
        "def id[T](c: Comparable[T]): Comparable[T] = c\ndef main(args: String[]): void {\n  val s: Comparable[String] = id(5)\n}\n")
      assert(msgs.contains("E0000") || msgs.contains("E0005"), s"expected an error, got: $msgs")
    }
    it("does not let T from a primitive argument reach an applied type its box lacks") {
      for (src <- Seq(
        "def id[T](x: T): T = x\ndef main(args: String[]): void {\n  val c: Comparable[String] = id(5)\n}\n",
        "def id[T](x: T): T = x\ndef main(args: String[]): void {\n  val c: Comparable[String]? = id(5)\n}\n",
        "def first[T](c: Comparable[T]): T = c as T\ndef main(args: String[]): void {\n  val c: Comparable[String] = first(5)\n}\n"
      )) {
        val msgs = compileErrors(src)
        assert(msgs.contains("E0000") || msgs.contains("E0005"), s"expected an error for <$src>, got: $msgs")
      }
    }
    it("still lets T from a primitive argument reach the box's own applied view") {
      val r = shell.run(
        """import { java.lang.Integer as JInteger }
          |def id[T](x: T): T = x
          |def main(args: String[]): Int {
          |  val c: Comparable[JInteger] = id(5)
          |  return c.compareTo(4)
          |}
          |""".stripMargin, "ArgTToOwnBoxView.on", Array())
      assert(Shell.Success(1) == r)
    }
  }

  describe("a reference whose own view contradicts an applied target (#2649)") {
    val imports = "import { java.util.*; java.lang.Integer as JInteger }\n"
    it("rejects a cast, an assignment and every other spelling at compile time") {
      for (body <- Seq(
        "val b: JInteger = 5\n  val y = (b as Comparable[String])",
        "val b: JInteger = 5\n  val y: Comparable[String] = b",
        "val b: JInteger? = 5\n  val y: Comparable[String]? = b",
        "val x: Comparable[JInteger] = \"s\"",
        "val x = (\"s\" as Comparable[JInteger])",
        "val b: JInteger = 5\n  val xs: List[Comparable[String]] = [b]",
        "val e: Comparable[String]? = 5",
        "val xs: List[Comparable[String]] = [5]",
        "val m: Map[String, Comparable[String]] = [\"a\": 5]",
        "val o: Comparable[String]? = null\n  val y: Comparable[String] = o ?: 5"
      )) {
        val msgs = compileErrors(s"${imports}def main(args: String[]): void {\n  $body\n}\n")
        assert(msgs.nonEmpty, s"expected a compile error for <$body>")
      }
    }
    it("rejects passing it to a parameter of the contradicted type") {
      val msgs = compileErrors(
        s"${imports}def f(c: Comparable[String]): Int = c.compareTo(\"x\")\ndef main(args: String[]): void {\n  val b: JInteger = 5\n  f(b)\n}\n")
      assert(msgs.nonEmpty)
    }
    it("rejects it as a constructor argument: class, record and primary super call") {
      for (decl <- Seq(
        "class D { public: def this(c: Comparable[String]) { } }\ndef main(args: String[]): void {\n  val b: JInteger = 5\n  new D(b)\n}\n",
        "record R(c: Comparable[String])\ndef main(args: String[]): void {\n  val b: JInteger = 5\n  new R(b)\n}\n",
        "class Base(c: Comparable[String])\nclass Sub(b: JInteger) extends Base(b)\ndef main(args: String[]): void {\n  new Sub(5)\n}\n"
      )) {
        val msgs = compileErrors(imports + decl)
        assert(msgs.contains("E0021"), s"expected E0021 for <$decl>, got $msgs")
      }
    }
    it("rejects an interface type argument that a final class's own argument can never meet") {
      for (body <- Seq(
        "val b: JInteger = 5\n  val s: Comparable[CharSequence] = b",
        "val b: JInteger = 5\n  val s = (b as Comparable[CharSequence])",
        "val s: Comparable[Runnable] = \"s\"",
        "val q: Comparable[Runnable] = new Q()"
      )) {
        val msgs = compileErrors(
          s"${imports}final class Q conforms Comparable[Q] { public: def compareTo(o: Q): Int = 0 }\ndef main(args: String[]): void {\n  $body\n}\n")
        assert(msgs.nonEmpty, s"expected a compile error for <$body>")
      }
    }
    it("still accepts an interface type argument against a non-final class's own argument") {
      val msgs = compileErrors(
        s"${imports}def main(args: String[]): void {\n  val c: Comparable[CharSequence] = new Date()\n}\n")
      assert(msgs.isEmpty, s"expected no compile error, got $msgs")
    }
    it("leaves related type arguments to the old rule (no break)") {
      val r = shell.run(
        imports +
        """class Names conforms Iterable[String] {
          |public:
          |  def iterator(): Iterator[String] = (["a", "b"] as List[String]).iterator()
          |}
          |def main(args: String[]): Int {
          |  val p = new Properties()
          |  p.setProperty("k", "v")
          |  val m: Map[String, String] = p
          |  val m2 = (p as Map[String, String])
          |  val it: Iterable[Object] = new Names()
          |  var n = 0
          |  foreach x in it { n = n + 1 }
          |  return m.size + m2.size * 10 + n * 100
          |}
          |""".stripMargin, "OwnViewRelated.on", Array())
      assert(Shell.Success(211) == r)
    }
    it("still accepts the class's own view, plain supertypes and casts that may succeed") {
      val r = shell.run(
        imports +
        """def main(args: String[]): Int {
          |  val b: JInteger = 5
          |  val c: Comparable[JInteger] = b
          |  val d = (b as Comparable[JInteger])
          |  val s: Comparable[String] = "a"
          |  val n: Number = b
          |  val o: Object = b
          |  val back = (o as Comparable[String])
          |  val e: Comparable[JInteger]? = b
          |  val xs: List[Comparable[JInteger]] = [b, 7]
          |  return c.compareTo(4) + d.compareTo(5) * 10 + s.compareTo("a") + n.intValue() * 100 +
          |    (if back === o && e != null { 1000 } else { 0 }) + xs.size * 10000
          |}
          |""".stripMargin, "OwnViewStillFits.on", Array())
      assert(Shell.Success(21501) == r)
    }
  }
}
