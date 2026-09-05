package onion.compiler

import onion.compiler.AST.*
import onion.compiler.exceptions.CompilationException
import org.scalatest.funsuite.AnyFunSuite

/** Full-AST characterization of the ADT expansion and its position in Rewriting. */
class AdtEnumRewritingSpec extends AnyFunSuite {
  private val loc = Location(3, 5, 12, 6)
  private val fieldLoc = Location(4, 17)
  private val intType = TypeNode(fieldLoc, PrimitiveType(KInt), false)
  private val field = Argument(fieldLoc, "value", intType, IntegerLiteral(fieldLoc, 7))
  private val declaration = EnumDeclaration(loc, M_PUBLIC, "Choice", Nil, List(
    EnumConstant(Location(4, 7), "Value", Nil, Some(List(field))),
    EnumConstant(Location(5, 7), "Empty", Nil, Some(Nil))
  ))

  private def unit(tops: List[Toplevel], rewriteBodies: Boolean = true): CompilationUnit =
    CompilationUnit(Location(1, 1), "choices.on", ModuleDeclaration(Location(1, 1), "demo"),
      ImportClause(Location(2, 1), List("List" -> "java.util.List")), tops,
      needsBodyRewrite = rewriteBodies, topLevelAssignedNames = Set("existing"))

  test("expand products and singleton cases in place, retaining fields, spans and unit metadata") {
    val before = IntegerLiteral(Location(2, 8), 1)
    val after = StringLiteral(Location(14, 1), "after")
    val input = unit(List(before, declaration, after), rewriteBodies = false)
    val parent = TypeNode(loc, ReferenceType("Choice", false), false)
    val expected = input.copy(toplevels = List(
      before,
      InterfaceDeclaration(loc, M_PUBLIC | M_SEALED, "Choice", Nil, Nil),
      RecordDeclaration(loc, M_PUBLIC, "Value", Nil, List(field), List(parent)),
      RecordDeclaration(loc, M_PUBLIC, "Empty", Nil, Nil, List(parent)),
      after
    ))
    assert(TestSupport.rewrite(input) == expected)
  }

  test("propagate ordered type parameters and bounds to every case and apply the parent type") {
    val params = List(
      TypeParameter(Location(3, 17), "T", Some(TypeNode(loc, ReferenceType("Object", false), false))),
      TypeParameter(Location(3, 20), "U")
    )
    val genericField = field.copy(typeRef = TypeNode(fieldLoc, ReferenceType("T", false), false))
    val generic = declaration.copy(typeParameters = params, constants = List(
      EnumConstant(fieldLoc, "Value", Nil, Some(List(genericField))),
      EnumConstant(Location(5, 7), "Empty", Nil, Some(Nil))))
    val input = unit(List(generic))
    val parent = TypeNode(loc, ParameterizedType(ReferenceType("Choice", false),
      List(ReferenceType("T", false), ReferenceType("U", false))), false)
    assert(TestSupport.rewrite(input) == input.copy(toplevels = List(
      InterfaceDeclaration(loc, M_PUBLIC | M_SEALED, "Choice", Nil, Nil, params),
      RecordDeclaration(loc, M_PUBLIC, "Value", params, List(genericField), List(parent)),
      RecordDeclaration(loc, M_PUBLIC, "Empty", params, Nil, List(parent))
    )))
  }

  test("preserve section method order and rewrite generated interface method bodies") {
    val bodyLoc = Location(8, 9)
    val monad = TypeNode(bodyLoc, ReferenceType("Future", false), false)
    val body = BlockExpression(bodyLoc, List(DoExpression(bodyLoc, monad,
      List(IntegerLiteral(bodyLoc, 42)))))
    val method = MethodDeclaration(bodyLoc, M_PUBLIC, "answer", Nil, monad, body)
    val abstractMethod = method.copy(name = "other", block = null)
    val input = unit(List(declaration.copy(sections = List(
      AccessSection(loc, M_PUBLIC, List(method,
        FieldDeclaration(fieldLoc, M_PUBLIC, "ignored", intType, null))),
      AccessSection(bodyLoc, M_PUBLIC, List(abstractMethod))
    ))))
    val loweredMethod = method.copy(block = BlockExpression(bodyLoc,
      List(StaticMethodCall(bodyLoc, monad, "successful", List(IntegerLiteral(bodyLoc, 42))))))
    val parent = TypeNode(loc, ReferenceType("Choice", false), false)
    assert(TestSupport.rewrite(input) == input.copy(toplevels = List(
      InterfaceDeclaration(loc, M_PUBLIC | M_SEALED, "Choice", Nil, List(loweredMethod, abstractMethod)),
      RecordDeclaration(loc, M_PUBLIC, "Value", Nil, List(field), List(parent)),
      RecordDeclaration(loc, M_PUBLIC, "Empty", Nil, Nil, List(parent))
    )))
  }

  test("leave homogeneous enums untouched and only lower case constants in mixed AST input") {
    val plain = EnumConstant(loc, "PLAIN", Nil)
    val homogeneous = declaration.copy(constants = List(plain))
    val input = unit(List(homogeneous))
    assert(TestSupport.rewrite(input) == input)
    val mixed = declaration.copy(constants = plain :: declaration.constants)
    assert(TestSupport.rewrite(unit(List(mixed))) == TestSupport.rewrite(unit(List(declaration))))
  }

  test("reject shared parameters with the original diagnostic and attach the unit source file") {
    val input = unit(List(declaration.copy(params = List(field))))
    val rewriter = TestSupport.createRewriting
    val expected = CompileError("", loc,
      "enum Choice mixes shared parameters with `case` cases; ADT enums carry per-case fields only")
    val direct = intercept[CompilationException](rewriter.rewrite(input))
    assert(direct.problems.toList == List(expected))
    val processed = intercept[CompilationException](
      rewriter.processBody(Seq(input), rewriter.newEnvironment(Seq(input))))
    assert(processed.problems.toList == List(expected.copy(sourceFile = "choices.on")))
  }
}
