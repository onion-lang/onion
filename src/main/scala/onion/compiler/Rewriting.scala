/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.{TreeSet => JTreeSet}

import _root_.onion.compiler.TypedAST.BinaryTerm.Kind._
import _root_.onion.compiler.TypedAST.UnaryTerm.Kind._
import _root_.onion.compiler.TypedAST._
import _root_.onion.compiler.SemanticError._
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.toolbox.{Boxing, Classes, Paths, Systems}
import onion.compiler.AST.{ClassDeclaration, InterfaceDeclaration, RecordDeclaration}

import _root_.scala.jdk.CollectionConverters._
import scala.collection.mutable.{Buffer, Map, Set => MutableSet}

/**
 * AST Rewriting Phase - Syntax Sugar Desugaring
 *
 * This compiler phase transforms the parsed AST by desugaring syntactic constructs
 * into more fundamental forms before type checking. This simplifies the type checker
 * by reducing the variety of constructs it needs to handle.
 *
 * == Main Transformations ==
 *
 * '''Do Notation Desugaring''': The primary transformation in this phase.
 * Do notation provides Haskell-like monadic syntax sugar:
 *
 * {{{
 * // Original do notation
 * do[Future] {
 *   x <- readFile("a.txt")
 *   y <- readFile("b.txt")
 *   ret x + y
 * }
 *
 * // Desugared form
 * readFile("a.txt").bind((x) -> {
 *   readFile("b.txt").bind((y) -> {
 *     Future::successful(x + y)
 *   })
 * })
 * }}}
 *
 * == Desugaring Rules ==
 *
 *   - `do[M] { x <- e1; rest }` → `e1.bind((x) -> { do[M] { rest } })`
 *   - `do[M] { ret e }` → `M::successful(e)`
 *   - `do[M] { e }` (final) → `M::successful(e)`
 *
 * == Phase Position in Pipeline ==
 *
 * {{{
 * Parsing → '''Rewriting''' → Typing → Code Generation
 * }}}
 *
 * This phase runs after parsing and before type checking. It transforms
 * `Seq[AST.CompilationUnit]` to `Seq[AST.CompilationUnit]` with desugared constructs.
 *
 * == Implementation Notes ==
 *
 * The rewriting is implemented as a recursive traversal of the AST. Most AST nodes
 * are passed through unchanged (but with their children recursively rewritten).
 * The primary transformation is `desugarDoExpression`, which handles do notation.
 *
 * @param config Compiler configuration options
 *
 * @see [[onion.compiler.Processor]] for the processor trait interface
 * @see [[onion.compiler.AST.DoExpression]] for the do notation AST node
 * @see [[onion.compiler.Typing]] for the next phase in the pipeline
 *
 * @author Kota Mizushima
 */
class Rewriting(config: CompilerConfig) extends AnyRef with Processor[Seq[AST.CompilationUnit], Seq[AST.CompilationUnit]] {

  /** Environment type for this phase (currently unused but required by Processor trait) */
  class TypingEnvironment

  type Environment = TypingEnvironment

  /** Creates a new environment for processing */
  def newEnvironment(source: Seq[AST.CompilationUnit]) = new TypingEnvironment

  /**
   * Main processing method that rewrites all compilation units.
   *
   * @param source The compilation units from the parsing phase
   * @param environment The typing environment (unused in this phase)
   * @return Rewritten compilation units with desugared constructs
   */
  def processBody(source: Seq[AST.CompilationUnit], environment: TypingEnvironment): Seq[AST.CompilationUnit] = {
    val rewritten = Buffer.empty[AST.CompilationUnit]
    for (unit <- source) {
      rewritten += rewrite(unit)
    }
    rewritten.toSeq
  }

  def rewrite(unit: AST.CompilationUnit): AST.CompilationUnit = {
    val newToplevels = Buffer.empty[AST.Toplevel]
    for (top <- unit.toplevels) top match {
      case declaration: AST.ClassDeclaration =>
        newToplevels += rewriteClassDeclaration(declaration)
      case declaration: AST.InterfaceDeclaration =>
        newToplevels += rewriteInterfaceDeclaration(declaration)
      case declaration: AST.RecordDeclaration =>
        newToplevels += rewriteRecordDeclaration(declaration)
      case declaration: AST.FunctionDeclaration =>
        newToplevels += rewriteFunctionDeclaration(declaration)
      case declaration: AST.GlobalVariableDeclaration =>
        newToplevels += rewriteGlobalVariableDeclaration(declaration)
      case otherwise =>
        newToplevels += otherwise
    }
    unit.copy(toplevels = newToplevels.toList)
  }

  def rewriteClassDeclaration(declaration: ClassDeclaration): ClassDeclaration = {
    val newDefaultSection = declaration.defaultSection.map(rewriteAccessSection)
    val newSections = declaration.sections.map(rewriteAccessSection)
    declaration.copy(defaultSection = newDefaultSection, sections = newSections)
  }

  def rewriteInterfaceDeclaration(declaration: AST.InterfaceDeclaration): InterfaceDeclaration = {
    val newMethods = declaration.methods.map(rewriteMethodDeclaration)
    declaration.copy(methods = newMethods)
  }

  def rewriteRecordDeclaration(declaration: AST.RecordDeclaration): RecordDeclaration = {
    declaration
  }

  def rewriteFunctionDeclaration(declaration: AST.FunctionDeclaration): AST.FunctionDeclaration = {
    val newBlock = rewriteBlockExpression(declaration.block)
    declaration.copy(block = newBlock)
  }

  def rewriteGlobalVariableDeclaration(declaration: AST.GlobalVariableDeclaration): AST.GlobalVariableDeclaration = {
    val newInit = Option(declaration.init).map(rewriteExpression).orNull
    declaration.copy(init = newInit)
  }

  def rewriteAccessSection(section: AST.AccessSection): AST.AccessSection = {
    val newMembers = section.members.map(rewriteMemberDeclaration)
    section.copy(members = newMembers)
  }

  def rewriteMemberDeclaration(member: AST.MemberDeclaration): AST.MemberDeclaration = member match {
    case m: AST.MethodDeclaration => rewriteMethodDeclaration(m)
    case f: AST.FieldDeclaration => rewriteFieldDeclaration(f)
    case d: AST.DelegatedFieldDeclaration => rewriteDelegatedFieldDeclaration(d)
    case c: AST.ConstructorDeclaration => rewriteConstructorDeclaration(c)
  }

  def rewriteMethodDeclaration(method: AST.MethodDeclaration): AST.MethodDeclaration = {
    val newBlock = if (method.block != null) rewriteBlockExpression(method.block) else null
    method.copy(block = newBlock)
  }

  def rewriteFieldDeclaration(field: AST.FieldDeclaration): AST.FieldDeclaration = {
    val newInit = Option(field.init).map(rewriteExpression).orNull
    field.copy(init = newInit)
  }

  def rewriteDelegatedFieldDeclaration(field: AST.DelegatedFieldDeclaration): AST.DelegatedFieldDeclaration = {
    val newInit = rewriteExpression(field.init)
    field.copy(init = newInit)
  }

  def rewriteConstructorDeclaration(constructor: AST.ConstructorDeclaration): AST.ConstructorDeclaration = {
    val newSuperInits = constructor.superInits.map(rewriteExpression)
    val newBlock = rewriteBlockExpression(constructor.block)
    constructor.copy(superInits = newSuperInits, block = newBlock)
  }

  def rewriteBlockExpression(block: AST.BlockExpression): AST.BlockExpression = {
    if (block == null) return null
    val newElements = block.elements.map(rewriteCompoundExpression)
    block.copy(elements = newElements)
  }

  def rewriteCompoundExpression(expr: AST.CompoundExpression): AST.CompoundExpression = expr match {
    case b: AST.BlockExpression => rewriteBlockExpression(b)
    case AST.BreakExpression(loc) => expr
    case AST.ContinueExpression(loc) => expr
    case AST.EmptyExpression(loc) => expr
    case AST.ExpressionBox(loc, body) => AST.ExpressionBox(loc, rewriteExpression(body))
    case AST.ForeachExpression(loc, arg, collection, statement) =>
      AST.ForeachExpression(loc, arg, rewriteExpression(collection), rewriteBlockExpression(statement))
    case AST.ForExpression(loc, init, condition, update, block) =>
      AST.ForExpression(loc, rewriteCompoundExpression(init),
        Option(condition).map(rewriteExpression).orNull,
        Option(update).map(rewriteExpression).orNull,
        rewriteBlockExpression(block))
    case AST.IfExpression(loc, condition, thenBlock, elseBlock) =>
      AST.IfExpression(loc, rewriteExpression(condition),
        rewriteBlockExpression(thenBlock),
        Option(elseBlock).map(rewriteBlockExpression).orNull)
    case AST.LocalVariableDeclaration(loc, modifiers, name, typeRef, init) =>
      AST.LocalVariableDeclaration(loc, modifiers, name, typeRef, Option(init).map(rewriteExpression).orNull)
    case AST.ReturnExpression(loc, result) =>
      AST.ReturnExpression(loc, Option(result).map(rewriteExpression).orNull)
    case AST.SelectExpression(loc, condition, cases, elseBlock) =>
      AST.SelectExpression(loc, rewriteExpression(condition),
        cases.map { case (patterns, block) => (patterns, rewriteBlockExpression(block)) },
        Option(elseBlock).map(rewriteBlockExpression).orNull)
    case AST.SynchronizedExpression(loc, condition, block) =>
      AST.SynchronizedExpression(loc, rewriteExpression(condition), rewriteBlockExpression(block))
    case AST.ThrowExpression(loc, target) =>
      AST.ThrowExpression(loc, rewriteExpression(target))
    case AST.TryExpression(loc, resources, tryBlock, recClauses, finBlock) =>
      AST.TryExpression(loc,
        resources.map(r => rewriteCompoundExpression(r).asInstanceOf[AST.LocalVariableDeclaration]),
        rewriteBlockExpression(tryBlock),
        recClauses.map { case (arg, block) => (arg, rewriteBlockExpression(block)) },
        Option(finBlock).map(rewriteBlockExpression).orNull)
    case AST.WhileExpression(loc, condition, block) =>
      AST.WhileExpression(loc, rewriteExpression(condition), rewriteBlockExpression(block))
  }

  def rewriteExpression(expr: AST.Expression): AST.Expression = expr match {
    // Do notation desugaring - the main transformation
    case doExpr: AST.DoExpression => desugarDoExpression(doExpr)
    case AST.RetStatement(loc, e) => AST.RetStatement(loc, rewriteExpression(e))

    // Binary expressions
    case AST.Addition(loc, lhs, rhs) => AST.Addition(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.AdditionAssignment(loc, lhs, rhs) => AST.AdditionAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Assignment(loc, lhs, rhs) => AST.Assignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.BitAnd(loc, lhs, rhs) => AST.BitAnd(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.BitAndAssignment(loc, lhs, rhs) => AST.BitAndAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.BitOr(loc, lhs, rhs) => AST.BitOr(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.BitOrAssignment(loc, lhs, rhs) => AST.BitOrAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Division(loc, lhs, rhs) => AST.Division(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.DivisionAssignment(loc, lhs, rhs) => AST.DivisionAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Elvis(loc, lhs, rhs) => AST.Elvis(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Equal(loc, lhs, rhs) => AST.Equal(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.GreaterOrEqual(loc, lhs, rhs) => AST.GreaterOrEqual(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.GreaterThan(loc, lhs, rhs) => AST.GreaterThan(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Indexing(loc, lhs, rhs) => AST.Indexing(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.LessOrEqual(loc, lhs, rhs) => AST.LessOrEqual(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.LeftShiftAssignment(loc, lhs, rhs) => AST.LeftShiftAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.LessThan(loc, lhs, rhs) => AST.LessThan(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.LogicalAnd(loc, lhs, rhs) => AST.LogicalAnd(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.LogicalOr(loc, lhs, rhs) => AST.LogicalOr(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.LogicalRightShift(loc, lhs, rhs) => AST.LogicalRightShift(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.LogicalRightShiftAssignment(loc, lhs, rhs) => AST.LogicalRightShiftAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.MathLeftShift(loc, lhs, rhs) => AST.MathLeftShift(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.MathRightShift(loc, lhs, rhs) => AST.MathRightShift(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.MathRightShiftAssignment(loc, lhs, rhs) => AST.MathRightShiftAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Modulo(loc, lhs, rhs) => AST.Modulo(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.ModuloAssignment(loc, lhs, rhs) => AST.ModuloAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Multiplication(loc, lhs, rhs) => AST.Multiplication(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.MultiplicationAssignment(loc, lhs, rhs) => AST.MultiplicationAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.NotEqual(loc, lhs, rhs) => AST.NotEqual(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.ReferenceEqual(loc, lhs, rhs) => AST.ReferenceEqual(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.ReferenceNotEqual(loc, lhs, rhs) => AST.ReferenceNotEqual(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.Subtraction(loc, lhs, rhs) => AST.Subtraction(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.SubtractionAssignment(loc, lhs, rhs) => AST.SubtractionAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.XOR(loc, lhs, rhs) => AST.XOR(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.XorAssignment(loc, lhs, rhs) => AST.XorAssignment(loc, rewriteExpression(lhs), rewriteExpression(rhs))

    // Unary expressions
    case AST.Negate(loc, term) => AST.Negate(loc, rewriteExpression(term))
    case AST.Not(loc, term) => AST.Not(loc, rewriteExpression(term))
    case AST.Posit(loc, term) => AST.Posit(loc, rewriteExpression(term))
    case AST.PostDecrement(loc, term) => AST.PostDecrement(loc, rewriteExpression(term))
    case AST.PostIncrement(loc, term) => AST.PostIncrement(loc, rewriteExpression(term))

    // Literals (no rewriting needed)
    case _: AST.BooleanLiteral | _: AST.ByteLiteral | _: AST.CharacterLiteral |
         _: AST.DoubleLiteral | _: AST.FloatLiteral | _: AST.IntegerLiteral |
         _: AST.LongLiteral | _: AST.ShortLiteral | _: AST.StringLiteral |
         _: AST.NullLiteral => expr

    // Other expressions
    case AST.Cast(loc, src, to) => AST.Cast(loc, rewriteExpression(src), to)
    case AST.ClosureExpression(loc, typeRef, mname, args, returns, body) =>
      AST.ClosureExpression(loc, typeRef, mname, args, returns, rewriteBlockExpression(body))
    case AST.CurrentInstance(loc) => expr
    case AST.Id(loc, name) => expr
    case AST.IsInstance(loc, target, typeRef) => AST.IsInstance(loc, rewriteExpression(target), typeRef)
    case AST.ListLiteral(loc, elements) => AST.ListLiteral(loc, elements.map(rewriteExpression))
    case AST.MemberSelection(loc, target, name) =>
      AST.MemberSelection(loc, Option(target).map(rewriteExpression).orNull, name)
    case AST.MethodCall(loc, target, name, args, typeArgs) =>
      AST.MethodCall(loc, Option(target).map(rewriteExpression).orNull, name, args.map(rewriteExpression), typeArgs)
    // Safe call (null-safe)
    case AST.SafeMemberSelection(loc, target, name) =>
      AST.SafeMemberSelection(loc, rewriteExpression(target), name)
    case AST.SafeMethodCall(loc, target, name, args, typeArgs) =>
      AST.SafeMethodCall(loc, rewriteExpression(target), name, args.map(rewriteExpression), typeArgs)
    case AST.NewArray(loc, typeRef, args) => AST.NewArray(loc, typeRef, args.map(rewriteExpression))
    case AST.NewArrayWithValues(loc, typeRef, values) => AST.NewArrayWithValues(loc, typeRef, values.map(rewriteExpression))
    case AST.NewObject(loc, typeRef, args) => AST.NewObject(loc, typeRef, args.map(rewriteExpression))
    case AST.UnqualifiedFieldReference(loc, name) => expr
    case AST.UnqualifiedMethodCall(loc, name, args, typeArgs) =>
      AST.UnqualifiedMethodCall(loc, name, args.map(rewriteExpression), typeArgs)
    case AST.NamedArgument(loc, name, value) => AST.NamedArgument(loc, name, rewriteExpression(value))
    case AST.StaticMemberSelection(loc, typeRef, name) => expr
    case AST.StaticMethodCall(loc, typeRef, name, args, typeArgs) =>
      AST.StaticMethodCall(loc, typeRef, name, args.map(rewriteExpression), typeArgs)
    case AST.StringInterpolation(loc, parts, expressions) =>
      AST.StringInterpolation(loc, parts, expressions.map(rewriteExpression))
    case AST.SuperMethodCall(loc, name, args, typeArgs) =>
      AST.SuperMethodCall(loc, name, args.map(rewriteExpression), typeArgs)

    // Compound expressions that can appear in expression context
    case compound: AST.CompoundExpression => rewriteCompoundExpression(compound)
  }

  /**
   * Desugars do notation into explicit monadic operations.
   *
   * Do notation provides a clean syntax for chaining monadic operations.
   * This method transforms the syntactic sugar into explicit method calls.
   *
   * == Transformation Rules ==
   *
   * {{{
   * do[M] { x <- e1; rest }  =>  e1.bind((x) -> { do[M] { rest } })
   * do[M] { ret e }          =>  M::successful(e)
   * do[M] { e }              =>  M::successful(e)  (final expression)
   * }}}
   *
   * == Requirements ==
   *
   * The monad type `M` must provide:
   *   - A static method `successful(value)` that wraps a value in the monad
   *   - An instance method `bind(f)` that chains operations
   *
   * @param doExpr The do expression AST node to desugar
   * @return The desugared expression using explicit bind/successful calls
   * @throws CompilationException if the do expression is empty or malformed
   */
  def desugarDoExpression(doExpr: AST.DoExpression): AST.Expression = {
    val monadType = doExpr.monadType
    val statements = doExpr.statements

    if (statements.isEmpty) {
      throw new CompilationException(Seq(
        CompileError("", doExpr.location, "Do expression cannot be empty")
      ))
    }

    desugarDoStatements(doExpr.location, monadType, statements)
  }

  private def desugarDoStatements(loc: Location, monadType: AST.TypeNode, statements: List[Any]): AST.Expression = {
    statements match {
      // Final expression: ret e => M::successful(e)
      case List(AST.RetStatement(_, expr)) =>
        val rewritten = rewriteExpression(expr)
        AST.StaticMethodCall(loc, monadType, "successful", List(rewritten))

      // Final expression without ret: e => M::successful(e)
      case List(expr: AST.Expression) =>
        val rewritten = rewriteExpression(expr)
        AST.StaticMethodCall(loc, monadType, "successful", List(rewritten))

      // Binding: x <- e1; rest => e1.bind((x) -> { rest })
      case (binding: AST.DoBinding) :: rest =>
        val bindExpr = rewriteExpression(binding.expr)
        val restExpr = desugarDoStatements(binding.location, monadType, rest)

        // Create closure: (x) -> { restExpr }
        val arg = AST.Argument(binding.location, binding.name, null, null, false)
        val closureBody = AST.BlockExpression(binding.location, List(AST.ExpressionBox(binding.location, restExpr)))
        val closureType = AST.TypeNode(binding.location, AST.ReferenceType("onion.Function1", true), true)
        val closure = AST.ClosureExpression(binding.location, closureType, "call", List(arg), null, closureBody)

        // e1.bind(closure)
        AST.MethodCall(binding.location, bindExpr, "bind", List(closure))

      // Intermediate expression (side effect): e; rest => e.bind((_) -> { rest })
      case (expr: AST.Expression) :: rest =>
        val rewrittenExpr = rewriteExpression(expr)
        val restExpr = desugarDoStatements(loc, monadType, rest)

        // Create closure: (_) -> { restExpr }
        val arg = AST.Argument(loc, "_unused", null, null, false)
        val closureBody = AST.BlockExpression(loc, List(AST.ExpressionBox(loc, restExpr)))
        val closureType = AST.TypeNode(loc, AST.ReferenceType("onion.Function1", true), true)
        val closure = AST.ClosureExpression(loc, closureType, "call", List(arg), null, closureBody)

        // expr.bind(closure)
        AST.MethodCall(loc, rewrittenExpr, "bind", List(closure))

      case _ =>
        throw new CompilationException(Seq(
          CompileError("", loc, s"Invalid do notation statement")
        ))
    }
  }
}
