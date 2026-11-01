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
}
