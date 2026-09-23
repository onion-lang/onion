package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome, CompileError}
import onion.tools.Shell
import java.io.StringReader

/**
 * A bare type parameter `[T]` (or a bound one that stays nullable, e.g.
 * `[T extends B?]`) ranges over nullable types, so dereferencing a `T`-typed
 * value without a null check first is rejected with `E0057`
 * (TYPE_PARAMETER_MAY_BE_NULL) -- this is already enforced for every *read*
 * form: plain member access (`MemberSelectionResolutionSupport.
 * normalizeMemberSelectionTarget`), method-call targets
 * (`MethodTargetTypingSupport.normalizeMethodCallTarget`), and indexing reads
 * (`ConstructionTyping.typeIndexing`) all special-case a nullable
 * `TypeVariableType` before falling into their generic `ObjectType` handling.
 *
 * The *write* forms (`AssignmentTyping.processMemberAssign` and the
 * indexing-assignment branch of `AssignmentTyping.processArrayAssign`) never
 * got the same special case: `TypeVariableType` is itself an `ObjectType`, so
 * `this.item.x = v` / `this.item[i] = v` on a nullable `T` fell straight into
 * the ordinary field/indexed-assignment path with no null-safety diagnostic
 * at all, compiling clean and then NPEing at runtime the first time the
 * value is actually null -- a real hole in the "no miscompile" bar, distinct
 * from the `NullableType` (`T?`) receiver case already covered by
 * `NullableMemberAssignmentTargetSpec`.
 */
class TypeVariableNullableAssignmentTargetSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  private val shapeClass =
    """
      |class Shape {
      |public:
      |  var x: Int
      |  def this(x: Int) { this.x = x }
      |}
      |""".stripMargin

  private val cellClass =
    """
      |class Cell {
      |public:
      |  var v: Int
      |  def this(v: Int) { this.v = v }
      |  def set(i: Int, x: Int): void { this.v = x }
      |  def get(i: Int): Int { return this.v }
      |}
      |""".stripMargin

  describe("Assigning through a nullable bare-T target (E0057)") {
    it("rejects an unchecked field assignment through a nullable type variable, instead of compiling to a runtime NPE") {
      val src =
        shapeClass +
        """
          |class Box[T extends Shape?] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def poke(): void { this.item.x = 9 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box[Shape?](null)
          |    b.poke()
          |    return "done"
          |  }
          |}
          |""".stripMargin
      val codes = errors(src).flatMap(_.errorCode)
      assert(codes.contains("E0057"), s"expected E0057 in $codes")
    }

    it("rejects an unchecked indexed assignment through a nullable type variable") {
      val src =
        cellClass +
        """
          |class Box[T extends Cell?] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def poke(): void { this.item[0] = 9 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box[Cell?](null)
          |    b.poke()
          |    return "done"
          |  }
          |}
          |""".stripMargin
      val codes = errors(src).flatMap(_.errorCode)
      assert(codes.contains("E0057"), s"expected E0057 in $codes")
    }

    it("rejects the field-assignment case at runtime-failure granularity via Shell.Failure(-1)") {
      val src =
        shapeClass +
        """
          |class Box[T extends Shape?] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def poke(): void { this.item.x = 9 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box[Shape?](null)
          |    b.poke()
          |    return "done"
          |  }
          |}
          |""".stripMargin
      assert(Shell.Failure(-1) == shell.run(src, "TypeVarAssignTarget.on", Array()))
    }

    it("still allows the field assignment once T has a non-null bound") {
      val src =
        shapeClass +
        """
          |class Box[T extends Shape] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def poke(): void { this.item.x = 9 }
          |  def peek(): Int { return this.item.x }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box[Shape](new Shape(1))
          |    b.poke()
          |    return "" + b.peek()
          |  }
          |}
          |""".stripMargin
      assert(Shell.Success("9") == shell.run(src, "TypeVarAssignTargetBounded.on", Array()))
    }
  }
}
