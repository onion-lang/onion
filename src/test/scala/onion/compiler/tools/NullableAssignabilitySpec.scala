package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A nullable value (`g: Foo?`, never null-checked) flowing into a non-nullable
 * expected type through `AssignabilitySupport.processAssignable` -- a `val`/`var`
 * declaration with an explicit type annotation, a `return` statement, a plain
 * local reassignment, or a field initializer -- reported the generic
 * "type Foo is expected, but type Foo? is used" (E0000) instead of the
 * null-safety error (E0070, NULLABLE_MEMBER_ACCESS) that every other
 * dereference-like/assignability use of a nullable value (member access,
 * indexing, operators, conditions, array size, throw, try-with-resources,
 * range bounds, select scrutinee) already reports.
 *
 * `processAssignable`'s terminal compatibility check delegates to
 * `TypeRelations.isAssignableWithBoxing` (non-generic `expected`) or
 * `structurallyAssignable` (generic `expected`), neither of which special-cases
 * a `NullableType` actual -- `TypeRules.isSuperType` explicitly disallows
 * `T <- T?` -- so it fell straight into the generic `INCOMPATIBLE_TYPE` report,
 * unlike the sibling receiver-nullability checks (`MethodTargetTypingSupport.
 * normalizeMethodCallTarget`, `MemberSelectionResolutionSupport.
 * normalizeMemberSelectionTarget`) which already special-case `NullableType`
 * up front. Fixed by special-casing a `NullableType` actual up front in
 * `processAssignable`, before the ordinary compatibility check, mirroring
 * those read-path fixes -- except a target that itself accepts null (a
 * `NullableType` expected, or a bare/platform type variable) is left alone.
 */
class NullableAssignabilitySpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  it("reports E0070, not the generic E0000 fallback, for a nullable val declaration initializer") {
    val codes = errorCodes(
      """
        |class Foo {}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val g: Foo? = null
        |    val f: Foo = g
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("reports E0070, not the generic E0000 fallback, for a nullable return value") {
    val codes = errorCodes(
      """
        |class Foo {}
        |class Test {
        |public:
        |  static def get(): Foo {
        |    val g: Foo? = null
        |    return g
        |  }
        |  static def main(args: String[]): Int {
        |    get()
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("reports E0070, not the generic E0000 fallback, for a nullable plain reassignment") {
    val codes = errorCodes(
      """
        |class Foo {}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val g: Foo? = null
        |    var f: Foo = new Foo()
        |    f = g
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("reports E0070, not the generic E0000 fallback, for a nullable field initializer") {
    val codes = errorCodes(
      """
        |class Foo {}
        |class Test {
        |  val f: Foo
        |public:
        |  def this(g: Foo?) {
        |    this.f = g
        |  }
        |  static def main(args: String[]): Int {
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0070"), s"expected E0070 in $codes")
    assert(!codes.contains("E0000"), s"must not fall back to the generic E0000 message: $codes")
  }

  it("rejects a nullable val declaration initializer at runtime-failure granularity via Shell.Failure(-1)") {
    val src =
      """
        |class Foo {}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val g: Foo? = null
        |    val f: Foo = g
        |    return 0
        |  }
        |}
        |""".stripMargin
    assert(Shell.Failure(-1) == shell.run(src, "None", Array()))
  }

  it("still reports the generic E0000 for a genuinely incompatible, non-nullable initializer") {
    val codes = errorCodes(
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val s: String = "3"
        |    val n: Int = s
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.contains("E0000"), s"expected E0000 for a non-nullable incompatible assignment: $codes")
    assert(!codes.contains("E0070"), s"must not claim nullability for a non-nullable incompatible assignment: $codes")
  }

  it("still compiles a genuinely non-null value typed as nullable after `!!`-asserting it") {
    val codes = errorCodes(
      """
        |class Foo {}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val g: Foo? = new Foo()
        |    val f: Foo = g!!
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors after `!!`-asserting non-null: $codes")
  }

  it("still compiles assigning a nullable value to a nullable-typed declaration") {
    val codes = errorCodes(
      """
        |class Foo {}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val g: Foo? = null
        |    val h: Foo? = g
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors assigning nullable to nullable: $codes")
  }

  it("still compiles assigning a nullable value to a bare (nullable) type parameter") {
    val codes = errorCodes(
      """
        |class Box[T] {
        |  var value: T
        |public:
        |  def this(v: T) { self.value = v }
        |  def put(v: T): void { self.value = v }
        |}
        |class Foo {}
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val g: Foo? = null
        |    val b: Box[Foo?] = new Box[Foo?](null)
        |    b.put(g)
        |    return 0
        |  }
        |}
        |""".stripMargin
    )
    assert(codes.isEmpty, s"expected no errors assigning nullable to a bare type parameter: $codes")
  }
}
