package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `Class.member` where `Class` resolves to a real class is the Java/Kotlin habit of
 * using the instance-member operator for a static access -- the mirror image of
 * `s::m()` on a local variable (E0071, see StaticCallOnInstanceSpec). A bare class
 * name is never a value in Onion, so the target of the dot fails as an ordinary
 * unresolved identifier and is reported as the generic "local variable X is not
 * found" (E0002) with no mention of `::`. This hint replaces that with a dedicated
 * error naming the fix.
 */
class ClassUsedAsValueSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("fails for . on a class name (java.lang.System, default-imported)") {
    val result = shell.run(
      """
        | static def main(args: String[]): void {
        |   IO::println(System.currentTimeMillis())
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Failure(-1) == result)
  }

  it("reports a dedicated error code (not the generic E0002)") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): void {
        |    IO::println(System.currentTimeMillis())
        |  }
        |}
        |""".stripMargin
    )
    assert(!codes.contains("E0002"), s"expected the dedicated hint, not the generic not-found error, got: $codes")
    assert(codes.contains("E0091"), s"expected the dedicated CLASS_USED_AS_VALUE code E0091, got: $codes")
  }

  it("names the :: fix in the message") {
    val msgs = new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    IO::println(System.currentTimeMillis())
          |  }
          |}
          |""".stripMargin), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("System::"), s"expected the message to name the System:: fix, got: $msgs")
  }

  it("still allows a genuine instance member access via .") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int = "hi".length()
      """.stripMargin, "None", Array())
    assert(Shell.Success(2) == result)
  }

  it("a real local variable wins over a like-named type (no misfire)") {
    val result = shell.run(
      """
        | static def main(args: String[]): String {
        |   val System = "shadow"
        |   return System
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("shadow") == result)
  }

  it("an unresolvable capitalized name still gets the plain E0002") {
    assert(errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): void {
        |    IO::println(NoSuchThing.foo())
        |  }
        |}
        |""".stripMargin
    ).contains("E0002"))
  }

  it("also fires for a bare class name used as a select-case pattern") {
    val codes = errorCodes(
      """
        |class Animal {}
        |class Test {
        |public:
        |  static def main(args: String[]): void {
        |    val a: Animal = new Animal()
        |    select a {
        |    case Animal: IO::println("no")
        |    else: IO::println("yes")
        |    }
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0091"), s"expected E0091 for a bare class name as a case pattern, got: $codes")
  }

  it("names the `is` pattern fix too, for the select-case scenario") {
    val msgs = new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(
        """
          |class Animal {}
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    val a: Animal = new Animal()
          |    select a {
          |    case Animal: IO::println("no")
          |    else: IO::println("yes")
          |    }
          |  }
          |}
          |""".stripMargin), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("is Animal"), s"expected the message to also name the `is Animal` pattern fix, got: $msgs")
  }
}
