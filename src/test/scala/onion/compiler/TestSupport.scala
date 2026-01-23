package onion.compiler

import java.io.StringReader
import onion.compiler.parser.JJOnionParser
import org.scalatest.Assertions

/**
 * Test support utilities for Onion compiler unit tests.
 * Provides helper methods for AST construction, parsing, type building, and error collection.
 */
object TestSupport extends Assertions {

  // ============================================
  // Location Helpers
  // ============================================

  /** Default location for test AST nodes */
  val defaultLoc: Location = Location(1, 1)

  /** Create a location with specific line and column */
  def loc(line: Int, column: Int): Location = Location(line, column)

  /** Create a location with span information */
  def locWithSpan(line: Int, column: Int, endLine: Int, endColumn: Int): Location =
    Location(line, column, Some(endLine), Some(endColumn))

  // ============================================
  // Parsing Helpers
  // ============================================

  /** Parse a complete compilation unit from source code */
  def parseUnit(source: String): AST.CompilationUnit = {
    new JJOnionParser(new StringReader(source)).unit()
  }

  /** Parse an expression from source code */
  def parseExpression(source: String): AST.Expression = {
    // Wrap expression in a function to parse it
    val wrapped = s"""class _Test_ { public: def _test_(): Object = $source }"""
    val unit = parseUnit(wrapped)
    val clazz = unit.toplevels.head.asInstanceOf[AST.ClassDeclaration]
    val method = clazz.sections.head.members.head.asInstanceOf[AST.MethodDeclaration]
    method.block.elements.head match {
      case AST.ExpressionBox(_, body) => body
      case AST.ReturnExpression(_, result) => result
      case other => other.asInstanceOf[AST.Expression]
    }
  }

  /** Parse a block expression from source code */
  def parseBlock(source: String): AST.BlockExpression = {
    val wrapped = s"""class _Test_ { public: def _test_(): Void $source }"""
    val unit = parseUnit(wrapped)
    val clazz = unit.toplevels.head.asInstanceOf[AST.ClassDeclaration]
    val method = clazz.sections.head.members.head.asInstanceOf[AST.MethodDeclaration]
    method.block
  }

  // ============================================
  // Type Node Helpers
  // ============================================

  /** Create a simple reference type node */
  def refType(name: String, qualified: Boolean = false): AST.TypeNode =
    AST.TypeNode(defaultLoc, AST.ReferenceType(name, qualified), isRelaxed = true)

  /** Create a primitive type node */
  def primType(kind: AST.PrimitiveTypeKind): AST.TypeNode =
    AST.TypeNode(defaultLoc, AST.PrimitiveType(kind), isRelaxed = false)

  /** Commonly used type nodes */
  val intType: AST.TypeNode = primType(AST.KInt)
  val longType: AST.TypeNode = primType(AST.KLong)
  val doubleType: AST.TypeNode = primType(AST.KDouble)
  val booleanType: AST.TypeNode = primType(AST.KBoolean)
  val voidType: AST.TypeNode = primType(AST.KVoid)
  val stringType: AST.TypeNode = refType("String")
  val objectType: AST.TypeNode = refType("Object")

  // ============================================
  // Expression Construction Helpers
  // ============================================

  /** Create an integer literal */
  def intLit(value: Int): AST.IntegerLiteral =
    AST.IntegerLiteral(defaultLoc, value)

  /** Create a long literal */
  def longLit(value: Long): AST.LongLiteral =
    AST.LongLiteral(defaultLoc, value)

  /** Create a double literal */
  def doubleLit(value: Double): AST.DoubleLiteral =
    AST.DoubleLiteral(defaultLoc, value)

  /** Create a boolean literal */
  def boolLit(value: Boolean): AST.BooleanLiteral =
    AST.BooleanLiteral(defaultLoc, value)

  /** Create a string literal */
  def stringLit(value: String): AST.StringLiteral =
    AST.StringLiteral(defaultLoc, value)

  /** Create an identifier reference */
  def id(name: String): AST.Id =
    AST.Id(defaultLoc, name)

  /** Create a null literal */
  def nullLit: AST.NullLiteral =
    AST.NullLiteral(defaultLoc)

  /** Create an addition expression */
  def add(lhs: AST.Expression, rhs: AST.Expression): AST.Addition =
    AST.Addition(defaultLoc, lhs, rhs)

  /** Create a subtraction expression */
  def sub(lhs: AST.Expression, rhs: AST.Expression): AST.Subtraction =
    AST.Subtraction(defaultLoc, lhs, rhs)

  /** Create a multiplication expression */
  def mul(lhs: AST.Expression, rhs: AST.Expression): AST.Multiplication =
    AST.Multiplication(defaultLoc, lhs, rhs)

  /** Create an assignment expression */
  def assign(lhs: AST.Expression, rhs: AST.Expression): AST.Assignment =
    AST.Assignment(defaultLoc, lhs, rhs)

  /** Create a method call */
  def methodCall(target: AST.Expression, name: String, args: AST.Expression*): AST.MethodCall =
    AST.MethodCall(defaultLoc, target, name, args.toList)

  /** Create an unqualified method call */
  def call(name: String, args: AST.Expression*): AST.UnqualifiedMethodCall =
    AST.UnqualifiedMethodCall(defaultLoc, name, args.toList)

  /** Create a static method call */
  def staticCall(typeRef: AST.TypeNode, name: String, args: AST.Expression*): AST.StaticMethodCall =
    AST.StaticMethodCall(defaultLoc, typeRef, name, args.toList)

  // ============================================
  // Compound Expression Helpers
  // ============================================

  /** Create a block expression */
  def block(elements: AST.CompoundExpression*): AST.BlockExpression =
    AST.BlockExpression(defaultLoc, elements.toList)

  /** Create an expression box (wraps expression in compound context) */
  def exprBox(expr: AST.Expression): AST.ExpressionBox =
    AST.ExpressionBox(defaultLoc, expr)

  /** Create an if expression */
  def ifExpr(
    condition: AST.Expression,
    thenBlock: AST.BlockExpression,
    elseBlock: AST.BlockExpression = null
  ): AST.IfExpression =
    AST.IfExpression(defaultLoc, condition, thenBlock, elseBlock)

  /** Create a while expression */
  def whileExpr(condition: AST.Expression, body: AST.BlockExpression): AST.WhileExpression =
    AST.WhileExpression(defaultLoc, condition, body)

  /** Create a for expression */
  def forExpr(
    init: AST.CompoundExpression,
    condition: AST.Expression,
    update: AST.Expression,
    body: AST.BlockExpression
  ): AST.ForExpression =
    AST.ForExpression(defaultLoc, init, condition, update, body)

  /** Create a local variable declaration */
  def localVar(
    name: String,
    typeRef: AST.TypeNode = null,
    init: AST.Expression = null,
    modifiers: Int = 0
  ): AST.LocalVariableDeclaration =
    AST.LocalVariableDeclaration(defaultLoc, modifiers, name, typeRef, init)

  /** Create a return expression */
  def returnExpr(result: AST.Expression = null): AST.ReturnExpression =
    AST.ReturnExpression(defaultLoc, result)

  /** Create a break expression */
  def breakExpr: AST.BreakExpression =
    AST.BreakExpression(defaultLoc)

  /** Create a continue expression */
  def continueExpr: AST.ContinueExpression =
    AST.ContinueExpression(defaultLoc)

  /** Create an empty expression */
  def emptyExpr: AST.EmptyExpression =
    AST.EmptyExpression(defaultLoc)

  // ============================================
  // Do Notation Helpers
  // ============================================

  /** Create a do binding: x <- expr */
  def doBinding(name: String, expr: AST.Expression): AST.DoBinding =
    AST.DoBinding(defaultLoc, name, expr)

  /** Create a ret statement: ret expr */
  def retStmt(expr: AST.Expression): AST.RetStatement =
    AST.RetStatement(defaultLoc, expr)

  /** Create a do expression */
  def doExpr(monadType: AST.TypeNode, statements: Any*): AST.DoExpression =
    AST.DoExpression(defaultLoc, monadType, statements.toList)

  // ============================================
  // Declaration Helpers
  // ============================================

  /** Create an argument */
  def arg(name: String, typeRef: AST.TypeNode, defaultValue: AST.Expression = null, isVararg: Boolean = false): AST.Argument =
    AST.Argument(defaultLoc, name, typeRef, defaultValue, isVararg)

  /** Create a method declaration */
  def methodDecl(
    name: String,
    args: List[AST.Argument],
    returnType: AST.TypeNode,
    body: AST.BlockExpression,
    modifiers: Int = 0,
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.MethodDeclaration =
    AST.MethodDeclaration(defaultLoc, modifiers, name, args, returnType, body, typeParameters)

  /** Create a field declaration */
  def fieldDecl(
    name: String,
    typeRef: AST.TypeNode,
    init: AST.Expression = null,
    modifiers: Int = 0
  ): AST.FieldDeclaration =
    AST.FieldDeclaration(defaultLoc, modifiers, name, typeRef, init)

  /** Create a class declaration */
  def classDecl(
    name: String,
    sections: List[AST.AccessSection] = Nil,
    superClass: AST.TypeNode = null,
    interfaces: List[AST.TypeNode] = Nil,
    modifiers: Int = 0,
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.ClassDeclaration =
    AST.ClassDeclaration(defaultLoc, modifiers, name, superClass, interfaces, None, sections, typeParameters)

  /** Create an access section (public, private, protected) */
  def accessSection(modifier: Int, members: AST.MemberDeclaration*): AST.AccessSection =
    AST.AccessSection(defaultLoc, modifier, members.toList)

  /** Create a compilation unit */
  def compilationUnit(
    toplevels: List[AST.Toplevel],
    sourceFile: String = "test.on",
    module: AST.ModuleDeclaration = null
  ): AST.CompilationUnit =
    AST.CompilationUnit(defaultLoc, sourceFile, module, AST.ImportClause(defaultLoc, Nil), toplevels)

  // ============================================
  // Closure Helpers
  // ============================================

  /** Create a closure expression */
  def closure(
    args: List[AST.Argument],
    body: AST.BlockExpression,
    returnType: AST.TypeNode = null
  ): AST.ClosureExpression = {
    val funcType = refType(s"onion.Function${args.length}", qualified = true)
    AST.ClosureExpression(defaultLoc, funcType, "call", args, returnType, body)
  }

  // ============================================
  // Rewriting Helpers
  // ============================================

  /** Create a Rewriting instance with default config */
  def createRewriting: Rewriting = {
    val config = CompilerConfig(
      classPath = Nil,
      superClass = "java.lang.Object",
      encoding = "UTF-8",
      outputDirectory = ".",
      maxErrorReports = 10
    )
    new Rewriting(config)
  }

  /** Rewrite a compilation unit */
  def rewrite(unit: AST.CompilationUnit): AST.CompilationUnit = {
    createRewriting.rewrite(unit)
  }

  /** Rewrite an expression */
  def rewriteExpression(expr: AST.Expression): AST.Expression = {
    createRewriting.rewriteExpression(expr)
  }

  /** Rewrite a block expression */
  def rewriteBlock(block: AST.BlockExpression): AST.BlockExpression = {
    createRewriting.rewriteBlockExpression(block)
  }

  // ============================================
  // Assertion Helpers
  // ============================================

  /** Assert that two AST expressions are structurally equal (ignoring locations) */
  def assertStructurallyEqual(expected: AST.Expression, actual: AST.Expression): Unit = {
    def normalize(expr: AST.Expression): String = {
      // Simple string representation for comparison
      expr.toString.replaceAll("Location\\([^)]+\\)", "Location()")
    }
    assert(normalize(expected) == normalize(actual),
      s"Expected:\n${expected}\nActual:\n${actual}")
  }

  /** Assert that an expression parses and rewrites without error */
  def assertRewriteSucceeds(source: String): AST.Expression = {
    val expr = parseExpression(source)
    rewriteExpression(expr)
  }
}
