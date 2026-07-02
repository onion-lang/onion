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
    checkInstanceCoherence(unit.toplevels)
    val newToplevels = Buffer.empty[AST.Toplevel]
    for (top <- unit.toplevels) top match {
      case declaration: AST.ClassDeclaration =>
        newToplevels += rewriteClassDeclaration(declaration)
      case declaration: AST.InterfaceDeclaration =>
        newToplevels += rewriteInterfaceDeclaration(declaration)
      case declaration: AST.RecordDeclaration =>
        newToplevels += rewriteRecordDeclaration(declaration)
      case declaration: AST.InstanceDeclaration =>
        newToplevels += rewriteClassDeclaration(lowerInstanceDeclaration(declaration))
      case declaration: AST.FunctionDeclaration =>
        newToplevels += rewriteFunctionDeclaration(declaration)
      case declaration: AST.GlobalVariableDeclaration =>
        newToplevels += rewriteGlobalVariableDeclaration(declaration)
      case element: AST.BlockElement =>
        // Top-level statements need desugaring too (do-notation et al.);
        // they previously passed through untouched, so a DoExpression
        // reached typing and the whole statement vanished silently
        newToplevels += rewriteBlockElement(element)
      case otherwise =>
        newToplevels += otherwise
    }
    appendAutoCliCall(newToplevels)
    unit.copy(toplevels = newToplevels.toList)
  }

  /**
   * Auto-CLI: a top-level `def main(p1: T1, p2: T2 = default, ...)` whose
   * parameters are all CLI-convertible (String / Int / Long / Double / Float /
   * Boolean / Short / Byte) gets a synthesized trailing statement that parses
   * `args` via onion.Cli and calls main with the converted values. Required
   * params are positional; defaulted params become --name flags (Boolean
   * defaults become switches), with the default expression evaluated
   * in-language when the flag is absent.
   */
  /** Whether a parameter type is `String[]` (the rest collector for auto-CLI). */
  private def isStringArrayParam(typeRef: AST.TypeNode): Boolean =
    typeRef != null && (typeRef.desc match {
      case AST.ArrayType(AST.ReferenceType("String", _)) => true
      case AST.ArrayType(AST.ReferenceType("java.lang.String", _)) => true
      case _ => false
    })

  private def appendAutoCliCall(toplevels: Buffer[AST.Toplevel]): Unit = {
    val allScalar = (f: AST.FunctionDeclaration) => f.args.forall(a => cliKindOf(a.typeRef).isDefined)
    // A main with required leading scalar parameters and a trailing String[] that
    // collects the remaining arguments, e.g. `def main(cmd: String, files: String[])`.
    val restPattern = (f: AST.FunctionDeclaration) =>
      f.args.length >= 2 && isStringArrayParam(f.args.last.typeRef) && f.args.last.defaultValue == null &&
        f.args.init.forall(a => cliKindOf(a.typeRef).isDefined && a.defaultValue == null)

    toplevels.collectFirst {
      case f: AST.FunctionDeclaration if f.name == "main" && !allScalar(f) && restPattern(f) => f
    }.foreach { f =>
      val loc = f.location
      val k = f.args.length - 1
      val usage = f.args.init.map(a => "<" + a.name + ">").mkString(" ") + " <" + f.args.last.name + ">..."
      toplevels += AST.StaticMethodCall(
        loc,
        AST.TypeNode(loc, AST.ReferenceType("onion.Cli", true), false),
        "requireArgs",
        List(AST.Id(loc, "args"), AST.IntegerLiteral(loc, k), AST.StringLiteral(loc, usage))
      )
      val argExprs = f.args.zipWithIndex.map { case (a, i) =>
        if (i < k) convertCliValue(loc, cliKindOf(a.typeRef).get, a.name, AST.Indexing(loc, AST.Id(loc, "args"), AST.IntegerLiteral(loc, i)))
        else AST.StaticMethodCall(
          loc,
          AST.TypeNode(loc, AST.ReferenceType("onion.Cli", true), false),
          "rest",
          List(AST.Id(loc, "args"), AST.IntegerLiteral(loc, k))
        )
      }
      toplevels += AST.UnqualifiedMethodCall(loc, "main", argExprs)
      return
    }

    val mainFn = toplevels.collectFirst {
      case f: AST.FunctionDeclaration if f.name == "main" && allScalar(f) => f
    }
    mainFn.foreach { f =>
      val loc = f.location
      // Zero-arg main: emit a direct call with no CLI preamble
      if (f.args.isEmpty) {
        toplevels += AST.UnqualifiedMethodCall(loc, "main", Nil)
        return
      }
      val cliVar = "__cliArgs"
      val spec = f.args.map { a =>
        val kind = cliKindOf(a.typeRef).get
        if (a.defaultValue == null) a.name
        else if (kind == "Boolean") a.name + "?"
        else a.name + "="
      }.mkString(",")
      val parseCall = AST.StaticMethodCall(
        loc,
        AST.TypeNode(loc, AST.ReferenceType("onion.Cli", true), false),
        "parse",
        List(AST.Id(loc, "args"), AST.StringLiteral(loc, spec))
      )
      val decl = AST.LocalVariableDeclaration(loc, AST.M_FINAL, cliVar, null, parseCall)
      val argExprs = f.args.zipWithIndex.map { case (a, i) =>
        val raw = AST.Indexing(loc, AST.Id(loc, cliVar), AST.IntegerLiteral(loc, i))
        val converted = convertCliValue(loc, cliKindOf(a.typeRef).get, a.name, raw)
        if (a.defaultValue == null) converted
        else AST.IfExpression(
          loc,
          AST.Equal(loc, AST.Indexing(loc, AST.Id(loc, cliVar), AST.IntegerLiteral(loc, i)), AST.NullLiteral(loc)),
          AST.BlockExpression(loc, List(rewriteExpression(a.defaultValue))),
          AST.BlockExpression(loc, List(converted))
        )
      }
      toplevels += decl
      toplevels += AST.UnqualifiedMethodCall(loc, "main", argExprs)
    }
  }

  /** The CLI conversion kind for a parameter type, or None when unsupported. */
  private def cliKindOf(typeRef: AST.TypeNode): Option[String] = {
    if (typeRef == null) return None
    typeRef.desc match {
      case AST.PrimitiveType(AST.KInt) => Some("Int")
      case AST.PrimitiveType(AST.KLong) => Some("Long")
      case AST.PrimitiveType(AST.KDouble) => Some("Double")
      case AST.PrimitiveType(AST.KFloat) => Some("Float")
      case AST.PrimitiveType(AST.KBoolean) => Some("Boolean")
      case AST.PrimitiveType(AST.KShort) => Some("Short")
      case AST.PrimitiveType(AST.KByte) => Some("Byte")
      case AST.ReferenceType("String", _) => Some("String")
      case AST.ReferenceType("java.lang.String", _) => Some("String")
      case _ => None
    }
  }

  /** Builds the AST converting a raw CLI string to the parameter's type.
   *  For numeric types, delegates to onion.Cli.parseInt/parseLong/... which
   *  produce friendly error messages instead of leaking NumberFormatException.
   */
  private def convertCliValue(loc: Location, kind: String, paramName: String, raw: AST.Expression): AST.Expression = {
    val cliType = AST.TypeNode(loc, AST.ReferenceType("onion.Cli", true), false)
    def parseViaCli(method: String): AST.Expression =
      AST.StaticMethodCall(loc, cliType, method, List(AST.StringLiteral(loc, paramName), raw))
    kind match {
      case "String"  => raw
      case "Int"     => parseViaCli("parseInt")
      case "Long"    => parseViaCli("parseLong")
      case "Double"  => parseViaCli("parseDouble")
      case "Float"   => parseViaCli("parseFloat")
      case "Boolean" => parseViaCli("parseBoolean")
      case "Short"   => parseViaCli("parseShort")
      case "Byte"    => parseViaCli("parseByte")
      case _         => raw
    }
  }

  /** Builds the AST converting a regex capture (String) to a record component's
   *  type for `record ... from re""` synthesis. Unlike convertCliValue (which
   *  routes through onion.Cli helpers that print a message and exit), this uses
   *  the raw wrapper parse methods so a conversion failure throws
   *  NumberFormatException — which the synthesized parse() catches to return null.
   */
  private def convertCapturedValue(loc: Location, kind: String, raw: AST.Expression): AST.Expression = {
    def parseWith(wrapper: String, method: String): AST.Expression =
      AST.StaticMethodCall(loc, AST.TypeNode(loc, AST.ReferenceType(wrapper, true), false), method, List(raw))
    kind match {
      case "String"  => raw
      case "Int"     => parseWith("java.lang.Integer", "parseInt")
      case "Long"    => parseWith("java.lang.Long", "parseLong")
      case "Double"  => parseWith("java.lang.Double", "parseDouble")
      case "Float"   => parseWith("java.lang.Float", "parseFloat")
      case "Boolean" => parseWith("java.lang.Boolean", "parseBoolean")
      case "Short"   => parseWith("java.lang.Short", "parseShort")
      case "Byte"    => parseWith("java.lang.Byte", "parseByte")
      case _         => raw
    }
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

  /**
   * Lowers `instance Trait[Type] { methods }` to a public class implementing the
   * trait interface. The class name is derived from the trait application
   * (`Numeric[Integer]` -> `Numeric$$Integer`) so distinct instances get distinct
   * classes. Instance registration, coherence, and the dictionary singleton are
   * layered on in later stages; for now this just makes an instance a well-formed
   * implementing class.
   */
  /**
   * Type-class coherence: at most one `instance` per (trait, type) within a
   * compilation unit. Two instances for the same trait application would lower to
   * the same class; catching it here gives a clear message instead of leaking the
   * internal mangled name via a duplicate-class error. (Cross-unit duplicates are
   * still caught later, since they redefine the same class.)
   */
  private def checkInstanceCoherence(toplevels: List[AST.Toplevel]): Unit = {
    val seen = scala.collection.mutable.Map.empty[String, AST.InstanceDeclaration]
    toplevels.foreach {
      case inst: AST.InstanceDeclaration =>
        val key = mangleInstanceClassName(inst.traitType.desc)
        if (seen.contains(key)) {
          throw new CompilationException(Seq(CompileError("", inst.location,
            s"instance ${inst.traitType.desc} は既に定義されています（型クラスの instance は (trait, 型) ごとに1つまでです）")))
        }
        seen(key) = inst
      case _ =>
    }
  }

  private def lowerInstanceDeclaration(declaration: AST.InstanceDeclaration): AST.ClassDeclaration = {
    val section = AST.AccessSection(declaration.location, AST.M_PUBLIC, declaration.methods)
    AST.ClassDeclaration(
      declaration.location, declaration.modifiers, mangleInstanceClassName(declaration.traitType.desc),
      null, List(declaration.traitType), None, List(section), Nil
    )
  }

  /** Deterministic class name for the instance of a trait application, e.g.
    * `Numeric[Integer]` -> `Numeric$$Integer`. Must agree between the instance
    * lowering and the `Trait[..]::method` dictionary-access lowering. */
  private def mangleInstanceClassName(applied: AST.TypeDescriptor): String =
    applied.toString.replace("[", "$$").replace("]", "").replace(",", "$").replace(" ", "").replace(".", "_")

  /** `Trait[TypeArgs]::method(args)` -> `new <mangled instance>().method(args)`.
    * Works for ground type arguments (the instance class exists); an abstract type
    * parameter yields a "class not found" error until dictionary passing lands. */
  private def lowerTraitMethodCall(node: AST.TraitMethodCall): AST.Expression = {
    val applied = AST.ParameterizedType(node.traitType.desc, node.typeArgs.map(_.desc))
    val classNode = AST.TypeNode(node.location, AST.ReferenceType(mangleInstanceClassName(applied), false), false)
    AST.MethodCall(node.location, AST.NewObject(node.location, classNode, Nil), node.name, node.args.map(rewriteExpression), Nil)
  }

  def rewriteRecordDeclaration(declaration: AST.RecordDeclaration): RecordDeclaration = {
    // A componentless record has nothing to derive into, so don't synthesize methods.
    val fromMethods = declaration.fromPattern match {
      case Some(pattern) if declaration.args.nonEmpty => synthesizeFromMethods(declaration, pattern)
      case _ => Nil
    }
    val hasJson = declaration.derives.contains("Json")
    val hasYaml = declaration.derives.contains("Yaml")
    val derivable = declaration.args.nonEmpty
    // Format-agnostic core (toMap/fromMap) synthesized once when any data format is
    // requested, then thin per-format sugar layered on top.
    val dataMethods = if ((hasJson || hasYaml) && derivable) synthesizeDataMethods(declaration) else Nil
    val jsonMethods = if (hasJson && derivable) synthesizeFormatMethods(declaration, "Json", "Json") else Nil
    val yamlMethods = if (hasYaml && derivable) synthesizeFormatMethods(declaration, "Yaml", "Yaml") else Nil
    // law/example clauses (B3): each becomes a boolean static method the compiler runs at
    // build time (LawCheckPhase). No `derivable` guard — a componentless record can still
    // carry laws/examples (the law's own params drive generation).
    val lawMethods = declaration.laws.map(synthesizeLawMethod)
    val exampleMethods = declaration.examples.zipWithIndex.map { case (ex, i) => synthesizeExampleMethod(ex, i) }
    val all = fromMethods ++ dataMethods ++ jsonMethods ++ yamlMethods ++ lawMethods ++ exampleMethods
    if (all.isEmpty) declaration
    else declaration.copy(synthesizedMethods = all, laws = Nil, examples = Nil)
  }

  /** `law name(p: T) { expr }` -> `static def onion$$law$$name(p: T): boolean { return expr }`. */
  private def synthesizeLawMethod(law: AST.LawClause): AST.MethodDeclaration = {
    val boolType = AST.TypeNode(law.location, AST.PrimitiveType(AST.KBoolean), false)
    AST.MethodDeclaration(law.location, AST.M_PUBLIC | AST.M_STATIC,
      "onion$$law$$" + law.name, law.params, boolType, wrapReturningBoolean(law.body))
  }

  /** `example [name] { expr }` -> `static def onion$$example$$<name|idx>(): boolean { return expr }`. */
  private def synthesizeExampleMethod(ex: AST.ExampleClause, idx: Int): AST.MethodDeclaration = {
    val boolType = AST.TypeNode(ex.location, AST.PrimitiveType(AST.KBoolean), false)
    val suffix = ex.name.getOrElse(idx.toString)
    AST.MethodDeclaration(ex.location, AST.M_PUBLIC | AST.M_STATIC,
      "onion$$example$$" + suffix, Nil, boolType, wrapReturningBoolean(ex.body))
  }

  /** Make a `{ ...; lastExpr }` block return its last expression, so the synthesized boolean
   *  method yields a value. An already-`return`ed tail is left as-is. */
  private def wrapReturningBoolean(block: AST.BlockExpression): AST.BlockExpression =
    block.elements.reverse match {
      case (_: AST.ReturnExpression) :: _ => block
      case (last: AST.Expression) :: rest =>
        AST.BlockExpression(block.location, rest.reverse :+ AST.ReturnExpression(last.location, last))
      case _ => block
    }

  /**
   * Pattern-attached records: `record Name(c1: T1, ...) from re"..."` derives a typed
   * parser from the record shape. We synthesize two static methods as ordinary AST so
   * they go through normal typing (which also reuses the regex select-pattern's compile-time
   * E0059/E0060 checks):
   *
   *   static def parse(s: String): Name? {
   *     try {
   *       return select s {
   *         case re"<pattern>" (g0, g1, ...): new Name(convert(g0), convert(g1), ...)
   *         else: null
   *       }
   *     } catch __e: NumberFormatException { return null }
   *   }
   *
   *   static def parseAll(text: String): List[Name] {
   *     val __acc: ArrayList = new ArrayList
   *     foreach __line: String in Strings::lines(text) {
   *       val __r: Name? = Name::parse(__line)
   *       if __r != null { __acc.add(__r) }
   *     }
   *     return __acc
   *   }
   *
   * Conversions reuse the auto-CLI wrapper-parse helpers (Integer::parseInt, ...). Bad
   * component types and regex/group-count issues are reported at typing time, so any
   * record reaches typing intact even when its `from` clause is invalid.
   */
  private def synthesizeFromMethods(declaration: AST.RecordDeclaration, pattern: String): List[AST.MethodDeclaration] = {
    val loc = declaration.location
    val recordName = declaration.name
    val recordType = AST.TypeNode(loc, AST.ReferenceType(recordName, false), false)
    val nullableRecordType = AST.TypeNode(loc, AST.NullableType(AST.ReferenceType(recordName, false)), false)
    val stringType = AST.TypeNode(loc, AST.ReferenceType("String", false), false)

    // --- parse(s: String): Name? ---
    val groupNames = declaration.args.indices.map(i => s"__g$i").toList
    val regexPattern = AST.RegexPattern(loc, pattern, groupNames)
    val ctorArgs = declaration.args.zip(groupNames).map { case (arg, gName) =>
      val raw = AST.Id(loc, gName)
      cliKindOf(arg.typeRef) match {
        case Some(kind) => convertCapturedValue(loc, kind, raw)
        case None       => raw // unsupported component type; typing reports the error
      }
    }
    // Build to Name? so the select unifies cleanly with the `else: null` branch
    // (avoids a spurious "null assigned where non-nullable" warning at synthesis).
    val buildExpr = AST.Cast(loc, AST.NewObject(loc, recordType, ctorArgs), nullableRecordType)
    val selectExpr = AST.SelectExpression(
      loc,
      AST.Id(loc, "__s"),
      List((List(regexPattern), AST.BlockExpression(loc, List(buildExpr)))),
      AST.BlockExpression(loc, List(AST.NullLiteral(loc)))
    )
    val tryBody = AST.BlockExpression(loc, List(AST.ReturnExpression(loc, selectExpr)))
    val catchArg = AST.Argument(loc, "__nfe", AST.TypeNode(loc, AST.ReferenceType("NumberFormatException", false), false))
    val catchBody = AST.BlockExpression(loc, List(AST.ReturnExpression(loc, AST.NullLiteral(loc))))
    val parseTry = AST.TryExpression(loc, Nil, tryBody, List((catchArg, catchBody)), null)
    val parseBody = AST.BlockExpression(loc, List(parseTry))
    val parseMethod = AST.MethodDeclaration(
      loc, AST.M_PUBLIC | AST.M_STATIC, "parse",
      List(AST.Argument(loc, "__s", stringType)), nullableRecordType, parseBody
    )

    // --- parseAll(text: String): List[Name] ---
    val listType = AST.TypeNode(loc, AST.ParameterizedType(AST.ReferenceType("List", false), List(AST.ReferenceType(recordName, false))), false)
    val arrayListType = AST.TypeNode(loc, AST.ParameterizedType(AST.ReferenceType("ArrayList", false), List(AST.ReferenceType(recordName, false))), false)
    // Declare the accumulator with the List[Name] return type and widen the freshly
    // built ArrayList to it via a cast, so `return __acc` matches without E0000.
    val accInit = AST.Cast(loc, AST.NewObject(loc, arrayListType, Nil), listType)
    val accDecl = AST.LocalVariableDeclaration(loc, AST.M_FINAL, "__acc", listType, accInit)
    val linesCall = AST.StaticMethodCall(loc, AST.TypeNode(loc, AST.ReferenceType("Strings", false), false), "lines", List(AST.Id(loc, "__text")))
    val parseCall = AST.StaticMethodCall(loc, recordType, "parse", List(AST.Id(loc, "__line")))
    val rDecl = AST.LocalVariableDeclaration(loc, AST.M_FINAL, "__r", nullableRecordType, parseCall)
    val addCall = AST.MethodCall(loc, AST.Id(loc, "__acc"), "add", List(AST.Id(loc, "__r")))
    val guardedAdd = AST.IfExpression(
      loc,
      AST.NotEqual(loc, AST.Id(loc, "__r"), AST.NullLiteral(loc)),
      AST.BlockExpression(loc, List(addCall)),
      null
    )
    val foreachBody = AST.BlockExpression(loc, List(rDecl, guardedAdd))
    val foreach = AST.ForeachExpression(loc, AST.Argument(loc, "__line", stringType), linesCall, foreachBody)
    val parseAllBody = AST.BlockExpression(loc, List(accDecl, foreach, AST.ReturnExpression(loc, AST.Id(loc, "__acc"))))
    val parseAllMethod = AST.MethodDeclaration(
      loc, AST.M_PUBLIC | AST.M_STATIC, "parseAll",
      List(AST.Argument(loc, "__text", stringType)), listType, parseAllBody
    )

    // --- format(v: Name): String --- (the inverse direction, when invertible)
    // A pattern is invertible when it is purely literal text + flat top-level
    // capture groups: then format renders the record by dropping each component
    // string into its group's slot. parse(format(x)) == x for data that doesn't
    // collide with the literals. Non-literal separators (\s+, ., alternation,
    // quantified/nested/non-capturing groups) have no unique rendering, so we
    // skip format rather than guess (see formatSegments).
    val formatMethodOpt = formatSegments(pattern).flatMap { segs =>
      if (segs.count(_.isEmpty) != declaration.args.length) None
      else {
        var slot = 0
        val parts: List[AST.Expression] = segs.map {
          case Some(literal) => AST.StringLiteral(loc, literal)
          case None =>
            val comp = declaration.args(slot); slot += 1
            AST.MethodCall(loc, AST.Id(loc, "__v"), comp.name, Nil)
        }
        // Lead with "" so the whole chain is String-typed even when the first
        // segment is a slot or a non-String component.
        val chain = parts match {
          case (s: AST.StringLiteral) :: _ => parts
          case _ => AST.StringLiteral(loc, "") :: parts
        }
        val body = chain.reduceLeft((a, b) => AST.Addition(loc, a, b))
        Some(AST.MethodDeclaration(
          loc, AST.M_PUBLIC | AST.M_STATIC, "format",
          List(AST.Argument(loc, "__v", recordType)), stringType,
          AST.BlockExpression(loc, List(AST.ReturnExpression(loc, body)))
        ))
      }
    }

    List(parseMethod, parseAllMethod) ++ formatMethodOpt
  }

  /** Maps a record component kind (from cliKindOf) to its Json static getter. */
  private def jsonGetterOf(kind: String): String = kind match {
    case "Int"     => "getInt"
    case "Long"    => "getLong"
    case "Double"  => "getDouble"
    case "Float"   => "getFloat"
    case "Boolean" => "getBoolean"
    case "Short"   => "getShort"
    case "Byte"    => "getByte"
    case _         => "getString" // String, and the unsupported fallback (typing rejects bad components)
  }

  /**
   * `derive!(Json)` / `derive!(Yaml)` records: synthesize a format-agnostic core
   * (toMap/fromMap) once, plus thin per-format wrappers, so a record's shape yields
   * both directions of every requested format:
   *
   *   static def toMap(__v: User): Map {
   *     val __m: Map = Json::object()
   *     __m.put("name", __v.name()); __m.put("age", __v.age())
   *     return __m
   *   }
   *   static def fromMap(__m: Object): User? {
   *     try { return (new User(Json::getString(__m,"name"), Json::getInt(__m,"age")) as User?) }
   *     catch __e: Exception { return null }
   *   }
   *   static def fromJson(__s: String): User? {
   *     try { return User::fromMap(Json::parse(__s)) } catch __e: Exception { return null }
   *   }
   *   static def toJson(__v: User): String { return Json::stringify(User::toMap(__v)) }
   *   // ...and fromYaml/toYaml the same way over Yaml::parse / Yaml::stringify.
   *
   * `Json::object()` is a neutral LinkedHashMap factory and `Json::getXxx(obj, key)` reads
   * the shared Map intermediate, so both are reused for Yaml — the intermediate
   * representation is format-agnostic. Two-level catch: fromMap catches the unbox NPE from
   * a missing/wrong-typed numeric key, the fromXxx wrapper catches the parser's checked
   * exception. Both collapse to null (same "failure -> null" contract as parse()). A new
   * format needs only an stdlib type with parse/stringify over the Map — no macro change.
   * Unsupported component types are reported (E0062) at typing time, skipping registration.
   */
  private def synthesizeDataMethods(declaration: AST.RecordDeclaration): List[AST.MethodDeclaration] = {
    val loc = declaration.location
    val recordName = declaration.name
    val recordType = AST.TypeNode(loc, AST.ReferenceType(recordName, false), false)
    val nullableRecordType = AST.TypeNode(loc, AST.NullableType(AST.ReferenceType(recordName, false)), false)
    val objectType = AST.TypeNode(loc, AST.ReferenceType("Object", false), false)
    val mapType = AST.TypeNode(loc, AST.ParameterizedType(AST.ReferenceType("Map", false), List(AST.ReferenceType("String", false), AST.ReferenceType("Object", false))), false)
    val jsonType = AST.TypeNode(loc, AST.ReferenceType("Json", false), false)

    // --- toMap(v: Name): Map[String, Object] ---  (record -> shared Map intermediate)
    val objCall = AST.StaticMethodCall(loc, jsonType, "object", Nil)
    val mapDecl = AST.LocalVariableDeclaration(loc, AST.M_FINAL, "__m", mapType, objCall)
    val putCalls: List[AST.Expression] = declaration.args.map { arg =>
      AST.MethodCall(loc, AST.Id(loc, "__m"), "put",
        List(AST.StringLiteral(loc, arg.name), AST.MethodCall(loc, AST.Id(loc, "__v"), arg.name, Nil)))
    }
    val toMapElems: List[AST.BlockElement] = (mapDecl :: putCalls) :+ AST.ReturnExpression(loc, AST.Id(loc, "__m"))
    val toMapMethod = AST.MethodDeclaration(
      loc, AST.M_PUBLIC | AST.M_STATIC, "toMap",
      List(AST.Argument(loc, "__v", recordType)), mapType,
      AST.BlockExpression(loc, toMapElems)
    )

    // --- fromMap(m: Object): Name? ---  (shared Map intermediate -> record)
    val ctorArgs: List[AST.Expression] = declaration.args.map { arg =>
      val getter = cliKindOf(arg.typeRef).map(jsonGetterOf).getOrElse("getString")
      AST.StaticMethodCall(loc, jsonType, getter, List(AST.Id(loc, "__m"), AST.StringLiteral(loc, arg.name)))
    }
    val buildExpr = AST.Cast(loc, AST.NewObject(loc, recordType, ctorArgs), nullableRecordType)
    val tryBody = AST.BlockExpression(loc, List(AST.ReturnExpression(loc, buildExpr)))
    val catchArg = AST.Argument(loc, "__e", AST.TypeNode(loc, AST.ReferenceType("Exception", false), false))
    val catchBody = AST.BlockExpression(loc, List(AST.ReturnExpression(loc, AST.NullLiteral(loc))))
    val fromMapTry = AST.TryExpression(loc, Nil, tryBody, List((catchArg, catchBody)), null)
    val fromMapMethod = AST.MethodDeclaration(
      loc, AST.M_PUBLIC | AST.M_STATIC, "fromMap",
      List(AST.Argument(loc, "__m", objectType)), nullableRecordType,
      AST.BlockExpression(loc, List(fromMapTry))
    )

    List(toMapMethod, fromMapMethod)
  }

  /**
   * Per-format sugar over toMap/fromMap: `fromXxx(s) = fromMap(Xxx::parse(s))` and
   * `toXxx(v) = Xxx::stringify(toMap(v))`. `format` is the method-name suffix and
   * `typeName` the stdlib type (both "Json" or "Yaml"). The fromXxx wrapper carries its
   * own catch for the parser's checked exception (fromMap already catches unbox NPEs).
   */
  private def synthesizeFormatMethods(declaration: AST.RecordDeclaration, format: String, typeName: String): List[AST.MethodDeclaration] = {
    val loc = declaration.location
    val recordName = declaration.name
    val recordType = AST.TypeNode(loc, AST.ReferenceType(recordName, false), false)
    val nullableRecordType = AST.TypeNode(loc, AST.NullableType(AST.ReferenceType(recordName, false)), false)
    val stringType = AST.TypeNode(loc, AST.ReferenceType("String", false), false)
    val fmtType = AST.TypeNode(loc, AST.ReferenceType(typeName, false), false)

    // fromXxx(s: String): Name?  ==  fromMap(Xxx::parse(s))
    val parseCall = AST.StaticMethodCall(loc, fmtType, "parse", List(AST.Id(loc, "__s")))
    val fromMapCall = AST.StaticMethodCall(loc, recordType, "fromMap", List(parseCall))
    val fromTryBody = AST.BlockExpression(loc, List(AST.ReturnExpression(loc, fromMapCall)))
    val fromCatchArg = AST.Argument(loc, "__e", AST.TypeNode(loc, AST.ReferenceType("Exception", false), false))
    val fromCatchBody = AST.BlockExpression(loc, List(AST.ReturnExpression(loc, AST.NullLiteral(loc))))
    val fromTry = AST.TryExpression(loc, Nil, fromTryBody, List((fromCatchArg, fromCatchBody)), null)
    val fromMethod = AST.MethodDeclaration(
      loc, AST.M_PUBLIC | AST.M_STATIC, s"from$format",
      List(AST.Argument(loc, "__s", stringType)), nullableRecordType,
      AST.BlockExpression(loc, List(fromTry))
    )

    // toXxx(v: Name): String  ==  Xxx::stringify(toMap(v))
    val toMapCall = AST.StaticMethodCall(loc, recordType, "toMap", List(AST.Id(loc, "__v")))
    val stringifyCall = AST.StaticMethodCall(loc, fmtType, "stringify", List(toMapCall))
    val toMethod = AST.MethodDeclaration(
      loc, AST.M_PUBLIC | AST.M_STATIC, s"to$format",
      List(AST.Argument(loc, "__v", recordType)), stringType,
      AST.BlockExpression(loc, List(AST.ReturnExpression(loc, stringifyCall)))
    )

    List(fromMethod, toMethod)
  }

  /**
   * Decompose a regex into an ordered list of segments for `format` synthesis:
   * `Some(text)` is a literal fragment (unescaped), `None` is a capture-group
   * slot. Returns None — meaning "not invertible, don't synthesize format" —
   * when the pattern contains anything whose rendering isn't unique: a
   * character class / shorthand outside a group (`.`, `\d`, `[...]`), a
   * quantifier or alternation outside a group, a non-capturing/lookaround
   * group, a nested group, or a quantifier applied to a group.
   */
  private def formatSegments(pattern: String): Option[List[Option[String]]] = {
    val segs = scala.collection.mutable.ListBuffer[Option[String]]()
    val lit = new StringBuilder
    def flush(): Unit = { if (lit.nonEmpty) { segs += Some(lit.toString); lit.setLength(0) } }
    val literalEscapes = ".()[]{}+*?|^$\\/-"
    var i = 0
    val n = pattern.length
    while (i < n) {
      pattern.charAt(i) match {
        case '\\' =>
          if (i + 1 >= n) return None
          val nx = pattern.charAt(i + 1)
          if (literalEscapes.indexOf(nx.toInt) >= 0) { lit.append(nx); i += 2 }
          else return None // \d \w \s ... — a class, no unique rendering
        case '(' =>
          if (i + 1 < n && pattern.charAt(i + 1) == '?') return None // non-capturing / lookaround
          flush()
          var j = i + 1
          var closed = false
          while (j < n && !closed) {
            pattern.charAt(j) match {
              case '\\' => j += 2
              case '(' => return None // nested group breaks the flat slot model
              case ')' => closed = true
              case _ => j += 1
            }
          }
          if (!closed) return None
          segs += None
          i = j + 1
          if (i < n && "*+?{".indexOf(pattern.charAt(i).toInt) >= 0) return None // quantified group
        case '^' | '$' => i += 1 // zero-width anchor, contributes no text
        case c if ".[]{}*+?|".indexOf(c.toInt) >= 0 => return None // metachar outside a group
        case c => lit.append(c); i += 1
      }
    }
    flush()
    Some(segs.toList)
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
    // The initializer is optional (forward val n: List;) — don't rewrite null
    val newInit = if (field.init == null) null else rewriteExpression(field.init)
    field.copy(init = newInit)
  }

  def rewriteConstructorDeclaration(constructor: AST.ConstructorDeclaration): AST.ConstructorDeclaration = {
    val newSuperInits = constructor.superInits.map(rewriteExpression)
    val newBlock = rewriteBlockExpression(constructor.block)
    constructor.copy(superInits = newSuperInits, block = newBlock)
  }

  def rewriteBlockExpression(block: AST.BlockExpression): AST.BlockExpression = {
    if (block == null) return null
    val newElements = block.elements.map(rewriteBlockElement)
    block.copy(elements = newElements)
  }

  def rewriteBlockElement(element: AST.BlockElement): AST.BlockElement = element match {
    case AST.LocalVariableDeclaration(loc, modifiers, name, typeRef, init) =>
      AST.LocalVariableDeclaration(loc, modifiers, name, typeRef, Option(init).map(rewriteExpression).orNull)
    case AST.DestructuringDeclaration(loc, modifiers, names, init) =>
      AST.DestructuringDeclaration(loc, modifiers, names, rewriteExpression(init))
    case expr: AST.Expression =>
      rewriteExpression(expr)
  }

  def rewriteForInitializer(init: AST.ForInitializer): AST.ForInitializer = init match {
    case AST.ForInitDeclaration(declaration) =>
      AST.ForInitDeclaration(rewriteBlockElement(declaration).asInstanceOf[AST.LocalVariableDeclaration])
    case AST.ForInitExpression(expression) =>
      AST.ForInitExpression(rewriteExpression(expression))
    case empty: AST.ForInitEmpty =>
      empty
  }

  def rewriteExpression(expr: AST.Expression): AST.Expression = expr match {
    // Do notation desugaring - the main transformation
    case doExpr: AST.DoExpression => desugarDoExpression(doExpr)
    case AST.RetStatement(loc, e) => AST.RetStatement(loc, rewriteExpression(e))
    case b: AST.BlockExpression => rewriteBlockExpression(b)
    case _: AST.BreakExpression | _: AST.ContinueExpression => expr
    case AST.ForeachExpression(loc, arg, collection, statement) =>
      AST.ForeachExpression(loc, arg, rewriteExpression(collection), rewriteBlockExpression(statement))
    case AST.ForExpression(loc, init, condition, update, block) =>
      AST.ForExpression(loc, rewriteForInitializer(init),
        Option(condition).map(rewriteExpression).orNull,
        Option(update).map(rewriteExpression).orNull,
        rewriteBlockExpression(block))
    case AST.IfExpression(loc, condition, thenBlock, elseBlock) =>
      AST.IfExpression(loc, rewriteExpression(condition),
        rewriteBlockExpression(thenBlock),
        Option(elseBlock).map(rewriteBlockExpression).orNull)
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
        resources.map(r => rewriteBlockElement(r).asInstanceOf[AST.LocalVariableDeclaration]),
        rewriteBlockExpression(tryBlock),
        recClauses.map { case (arg, block) => (arg, rewriteBlockExpression(block)) },
        Option(finBlock).map(rewriteBlockExpression).orNull)
    case AST.WhileExpression(loc, condition, block) =>
      AST.WhileExpression(loc, rewriteExpression(condition), rewriteBlockExpression(block))
    case AST.DoWhileExpression(loc, block, condition) =>
      AST.DoWhileExpression(loc, rewriteBlockExpression(block), rewriteExpression(condition))
    case AST.LabeledLoop(loc, name, loop) =>
      AST.LabeledLoop(loc, name, rewriteExpression(loop))

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
    case AST.SafeIndexing(loc, lhs, rhs) => AST.SafeIndexing(loc, rewriteExpression(lhs), rewriteExpression(rhs))
    case AST.NotNullAssertion(loc, term) => AST.NotNullAssertion(loc, rewriteExpression(term))
    case AST.BitNot(loc, term) => AST.BitNot(loc, rewriteExpression(term))
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
    case AST.MapLiteral(loc, entries) =>
      AST.MapLiteral(loc, entries.map { case (k, v) => (rewriteExpression(k), rewriteExpression(v)) })
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
    case node: AST.TraitMethodCall =>
      lowerTraitMethodCall(node)
    case AST.StringInterpolation(loc, parts, expressions) =>
      AST.StringInterpolation(loc, parts, expressions.map(rewriteExpression))
    case AST.SuperMethodCall(loc, name, args, typeArgs) =>
      AST.SuperMethodCall(loc, name, args.map(rewriteExpression), typeArgs)

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

    // `ret` yields the do-block's final value and must be the last statement.
    // A `ret` in any earlier position (e.g. two `ret`s) is malformed and would
    // otherwise desugar into invalid bytecode, so reject it up front.
    val strayRet = statements.dropRight(1).collectFirst {
      case r: AST.RetStatement => r
    }
    strayRet.foreach { r =>
      throw new CompilationException(Seq(
        CompileError("", r.location, "ret must be the final statement of a do block")
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

      // Binding followed directly by the final ret: x <- e1; ret e2
      // => e1.map((x) -> e2). Using map instead of bind+M::successful means
      // a monad only needs map/bind instance (or extension) methods — no
      // static successful — which is what lets do[List] work over plain
      // java.util.List via the Colls extension methods.
      case (binding: AST.DoBinding) :: List(AST.RetStatement(_, retExpr)) =>
        val bindExpr = rewriteExpression(binding.expr)
        val rewrittenRet = rewriteExpression(retExpr)
        val arg = AST.Argument(binding.location, binding.name, null, null, false)
        val closureBody = AST.BlockExpression(binding.location, List(rewrittenRet))
        val closureType = AST.TypeNode(binding.location, AST.ReferenceType("onion.Function1", true), true)
        val closure = AST.ClosureExpression(binding.location, closureType, "call", List(arg), null, closureBody)
        AST.MethodCall(binding.location, bindExpr, "map", List(closure))

      // Binding: x <- e1; rest => e1.bind((x) -> { rest })
      case (binding: AST.DoBinding) :: rest =>
        val bindExpr = rewriteExpression(binding.expr)
        val restExpr = desugarDoStatements(binding.location, monadType, rest)

        // Create closure: (x) -> { restExpr }
        val arg = AST.Argument(binding.location, binding.name, null, null, false)
        val closureBody = AST.BlockExpression(binding.location, List(restExpr))
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
        val closureBody = AST.BlockExpression(loc, List(restExpr))
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
