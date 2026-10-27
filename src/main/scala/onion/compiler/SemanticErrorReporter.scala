package onion.compiler

import SemanticErrorReporter.*

/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */

import java.text.MessageFormat
import scala.collection.mutable.Buffer
import onion.compiler.toolbox.{Message, Systems}
import onion.compiler.exceptions.CompilationException

/**
 * Semantic Error Reporter - Central Error Collection and Formatting
 *
 * This class collects and formats semantic errors during type checking.
 * It provides:
 *   - Data-driven error message formatting with i18n support
 *   - Compilation context tracking (class/method being compiled)
 *   - "Did you mean?" suggestions for common typos
 *   - Threshold-based early termination when too many errors occur
 *
 * == Error Reporting Architecture ==
 *
 * The reporter uses a data-driven approach for error formatting:
 *
 * '''1. ErrorDef''': Maps error types to message keys and argument extractors
 *
 * '''2. Special Handlers''': Some errors (METHOD_NOT_FOUND, CLASS_NOT_FOUND, etc.)
 * have custom handlers that provide additional suggestions
 *
 * '''3. Message Formatting''': Uses `java.text.MessageFormat` with i18n properties
 *
 * == Suggestion System ==
 *
 * For resolution errors, the reporter can suggest similar names:
 * {{{
 * error: variable 'naem' is not found
 *   did you mean: name
 * }}}
 *
 * Currently supports suggestions for:
 *   - VARIABLE_NOT_FOUND
 *   - METHOD_NOT_FOUND
 *   - FIELD_NOT_FOUND
 *   - CLASS_NOT_FOUND
 *   - CONSTRUCTOR_NOT_FOUND
 *   - UNKNOWN_PARAMETER_NAME
 *   - LABEL_NOT_FOUND
 *   - OVERRIDE_TARGET_NOT_FOUND
 *
 * == Compilation Context ==
 *
 * The reporter tracks the current compilation context (class, method, phase)
 * to provide more helpful error messages:
 * {{{
 * error: type mismatch (in class Foo, method bar)
 * }}}
 *
 * == Usage ==
 *
 * {{{
 * val reporter = new SemanticErrorReporter(maxErrors = 100)
 * reporter.setSourceFile("MyClass.on")
 * reporter.enterClass("MyClass")
 * reporter.enterMethod("myMethod")
 *
 * // Report an error
 * reporter.report(INCOMPATIBLE_TYPE, location, expectedType, actualType)
 *
 * // Get all collected errors
 * val errors = reporter.getProblems
 * }}}
 *
 * @param threshold Maximum number of errors before throwing CompilationException
 *
 * @see [[SemanticError]] for error type definitions
 * @see [[CompileError]] for error representation
 * @see [[CompilationContext]] for context tracking
 *
 * @author Kota Mizushima
 */
class SemanticErrorReporter(threshold: Int) {
  private val problems = Buffer[CompileError]()
  private var sourceFile: String = null
  private var errorCount: Int = 0
  private var currentError: SemanticError = null
  private var context: CompilationContext = CompilationContext.empty

  // ========== Type extractors ==========

  private def TemplateLiteralInName = SemanticErrorReporter.TemplateLiteralInName

  // A JS-style `console.log(...)`/`console.error(...)`/`console.warn(...)` call has no
  // `console` object in Onion, so `console` parses as a bare identifier reference and
  // the mistake surfaces as a VARIABLE_NOT_FOUND on exactly that name, with nothing
  // connecting it back to Onion's actual output API. Detect that exact identifier and
  // point at `IO::println` instead of a name-similarity guess.
  private val ConsoleObjectName = "console"

  /**
   * Handles VARIABLE_NOT_FOUND with optional suggestions.
   */
  private def reportVariableNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.variableNotFound"), Seq(name))

    // A third item is an explicit qualified-form hint (e.g. "this.x" / "C::x")
    // for a name that is actually a field; prefer it over name-similarity.
    val suggestion =
      if (TemplateLiteralInName.findFirstIn(name).isDefined) {
        Some(message("suggestion.jsTemplateLiteral"))
      } else if (name == ConsoleObjectName) {
        Some(message("suggestion.jsConsoleLog"))
      } else if (name == "ret") {
        // The pre-1.0 do-notation keyword: `ret e` now reads as an unknown variable `ret`
        // followed by `e`, so name the new spelling instead of a similar-name guess.
        Some(message("suggestion.retRenamedToYield"))
      } else if (items.length > 2 && items(2) != null) {
        Some(format(message("suggestion.didYouMean"), Seq(asString(items(2)))))
      } else if (items.length > 1) {
        val candidates = items(1).asInstanceOf[Array[String]]
        toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
      } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  private def reportMethodNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val targetType = items(0).asInstanceOf[TypedAST.Type]
    val name = asString(items(1))
    val argTypes = asTypeArray(items(2))
    val args = typeNames(argTypes)
    // Set only by an actually-unqualified call (UnqualifiedMethodCallSupport); every
    // other report site omits it, defaulting to false -- see FormerDefaultImportLookup.
    val isUnqualifiedCall = items.length > 3 && items(3) == java.lang.Boolean.TRUE
    // Set only by a `Type::name(...)` call (StaticMethodCallSupport); every instance-,
    // super- and unqualified-call site omits it, defaulting to false. Lets the suggestion
    // ranker tell a static member from an instance one, so a failed `a.zip(b)` is never
    // steered to a same-named-receiver static factory like `Option::of`, which
    // `a.zip(...)` syntax could not call even if the name were right.
    val calledStatically = items.length > 6 && items(6) == java.lang.Boolean.TRUE
    // Set only for a static call on a nested class whose enclosing class has a static method
    // of this name and arity (MethodCallReportingSupport): that class's simple name.
    val enclosingStaticOwner = if (items.length > 7 && items(7) != null) asString(items(7)) else null
    // A top-level function lives on the file's synthetic `<file>Main` class; name the
    // function alone rather than leak that owner (`mainMain.helper(Int, Int)`).
    val baseMessage =
      if (onion.compiler.toolbox.TypeFormatting.isTopLevelContainer(targetType))
        format(message("error.semantic.functionNotFound"), Seq(name, args))
      else format(message("error.semantic.methodNotFound"), Seq(typeName(targetType), name, args))
    // A method with that exact name exists: show its signatures instead of
    // a (possibly misleading) name-similarity suggestion
    val sameName = targetType match
      case obj: TypedAST.ObjectType => obj.methods.filter(_.name == name).toSeq
      case _ => Seq.empty
    // Set only for a bare call inside an `extension` block (UnqualifiedMethodCallSupport):
    // the receiver `self`/`this` is bound to, which `targetType` -- the synthetic
    // extension container, never the receiver -- cannot itself name a method on.
    val extensionReceiverCandidates =
      if (items.length > 4 && items(4) != null)
        items(4).asInstanceOf[TypedAST.ObjectType].methods.filter(_.name == name).toSeq
      else Seq.empty
    val suggestion =
      if (streamToListHint(targetType, name).isDefined) {
        streamToListHint(targetType, name)
      } else if (primitiveArrayPipelineHint(targetType, name).isDefined) {
        primitiveArrayPipelineHint(targetType, name)
      } else if (enclosingStaticOwner != null) {
        Some(format(message("suggestion.didYouMean"), Seq(s"$enclosingStaticOwner::$name")))
      } else if (!calledStatically && ForeignStringNames.contains(name) && isStringType(targetType)) {
        // The String counterpart of ForeignCollectionNames: Kotlin `reversed`/`padStart`, Python
        // `splitlines`/`title`/`find`, JavaScript `includes`/`trimStart` -- same operation, Onion's
        // name. Ahead of the same-name branch, which would otherwise answer `s.format(3)` with
        // the static `String.format` an instance call cannot reach.
        Some(format(message("suggestion.didYouMean"), Seq(ForeignStringNames(name))))
      } else if (sameName.nonEmpty) {
        val signatures = sameName.take(3).map { m =>
          s"${m.name}(${m.arguments.map(typeName).mkString(", ")})"
        }
        // A sibling whose name extends this one and takes these very arguments
        // (`Csv::stringify(mapRows)` -> `stringifyWithHeader`) is the likelier fix.
        val sibling = targetType match
          case obj: TypedAST.ObjectType =>
            obj.methods.find { m =>
              m.name != name && m.name.startsWith(name) && !Modifier.isPrivate(m.modifier) &&
                Modifier.isStatic(m.modifier) == Modifier.isStatic(sameName.head.modifier) &&
                m.arguments.length == argTypes.length &&
                m.arguments.zip(argTypes).forall { case (p, a) => argPassable(p, a) }
            }
          case _ => None
        sibling match
          case Some(m) =>
            Some(format(message("suggestion.didYouMean"), Seq(s"${m.name}(${m.arguments.map(typeName).mkString(", ")})")))
          case None =>
            Some(format(message("error.suggestion.candidates"), Seq(signatures.mkString(", "))))
      } else if (extensionReceiverCandidates.nonEmpty) {
        val signatures = extensionReceiverCandidates.take(3).map { m =>
          s"self.${m.name}(${m.arguments.map(typeName).mkString(", ")})"
        }
        Some(format(message("error.suggestion.candidates"), Seq(signatures.mkString(", "))))
      } else if (name == "f" && args == "String") {
        // A Python-style f-string prefix (`f"Hello {name}"`) is not a string in Onion --
        // any bare identifier directly before a string literal desugars to a call (the
        // scheme-literal sugar: `re"..."`/`file"..."`/a user-defined `id"raw"`), so this
        // reads as a call to a function named `f` that nothing defines. Point at Onion's
        // actual interpolation syntax instead of a name-similarity guess.
        Some(message("suggestion.pythonFString"))
      } else if (isUnqualifiedCall && name == "puts") {
        // A Ruby-style bare `puts(...)` call -- Onion has no default-imported `puts`
        // function, so this reads as an ordinary unresolved call on the enclosing
        // script's synthesized main class. Point at Onion's actual output API instead
        // of a name-similarity guess. Restricted to unqualified calls, same as
        // FormerDefaultImportLookup below, so a real method named `puts` on some other
        // type is unaffected.
        Some(message("suggestion.rubyPuts"))
      } else if ((fieldNamed(targetType, name) && !inaccessibleFieldNamed(items)) || isArrayLengthProperty(targetType, name)) {
        // `p.name()` where `name` is a field, not a method -- a common mix-up
        // with record component accessors (which really are methods). A
        // name-similarity hint would uselessly suggest the same spelling, so
        // point at the parentheses instead. `arr.length()`/`arr.size()` hits
        // the same mix-up, but arrays carry no real `length` field entry for
        // fieldNamed to match (their length is resolved specially, not as a
        // TypedAST field), so it needs its own check. A same-named field that
        // exists but isn't accessible from the caller (e.g. Concurrent.Lock's
        // private `lock`) is excluded: pointing at it would just trade this
        // error for an equally unhelpful FIELD_NOT_ACCESSIBLE.
        Some(format(message("suggestion.fieldNotMethod"), Seq(name)))
      } else if (isUnqualifiedCall && FormerDefaultImportLookup.find(name, argTypes.length).isDefined) {
        // A bare call to a method that used to be default-imported (v0.10 narrowed the
        // set to pure classes -- CLAUDE.md's own migration note). Restricted to
        // genuinely unqualified calls: at that report site every other resolution path
        // (current class, top-level functions, static imports, callable values) has
        // already failed, so a name+arity match here can only be this stdlib method.
        val className = FormerDefaultImportLookup.find(name, argTypes.length).get
        Some(format(message("suggestion.stdlibNoLongerDefaultImported"), Seq(className, name)))
      } else if (ForeignCollectionNames.contains(name) && isListType(targetType)) {
        // The name another language gives a List operation Onion also has, under its own
        // name (Scala `sortBy`/`foldLeft`/`forall`, Kotlin `joinToString`/`sumOf`, Ruby
        // `each`/`inject`/`collect`). Name similarity misses these or picks a wrong one
        // (`sortBy` -> `sort`, `forall` -> `addAll`), so name the real one.
        Some(format(message("suggestion.didYouMean"), Seq(ForeignCollectionNames(name))))
      } else if (name == "fold") {
        // `List` has the pipeline method `fold` (onion.Colls::fold(List, ...)), but every
        // other Iterable (Map::values(), a Set, ...) only has `foldl` (onion.Iterables::
        // foldl) -- the one pipeline method where map/filter's List-and-Iterable symmetry
        // breaks (#2053). Reaching METHOD_NOT_FOUND for "fold" already means it isn't
        // resolvable on this receiver, so a bare name-similarity guess would otherwise
        // pick an unrelated same-receiver method (e.g. Collection.add) instead of the one
        // rename that actually fixes the call. (argTypes can't be trusted to carry the
        // real arity here: an untyped trailing-closure call like `.fold(0) { a, x -> ... }`
        // reports with an empty array via the bidirectional-inference fallback.)
        Some(format(message("suggestion.didYouMean"), Seq("foldl")))
      } else {
        targetType match
          case obj: TypedAST.ObjectType => toolbox.Suggestions.formatSuggestion(name, rankedCallCandidates(obj, name, argTypes, if (isUnqualifiedCall) None else Some(calledStatically)))
          case _ => None
      }
    // Set only by MethodCallReportingSupport, when a hex literal ending in `B` is passed where
    // a same-named method takes a Byte: said after any other suggestion, which still applies.
    val hexDigitB = items.length > 8 && items(8) == SemanticErrorReporter.HexDigitBHint
    val withHexDigitB =
      if (!hexDigitB) suggestion
      else Some(suggestion.fold("")(_ + Systems.lineSeparator + "  ") + message("suggestion.hexDigitB"))
    problem(position, appendSuggestion(baseMessage, withHexDigitB))
  }

  /**
   * The names a failed call `name(argTypes)` on `obj` may be steered to.
   * Name similarity alone once answered `Format::grouped(1234567L)` with `group`, which is
   * `group(String, Boolean)` and could never take that call. So a method candidate is kept
   * only when some overload accepts that many arguments, and one whose parameters also take
   * the argument types is tried before one that only matches the count. Fields stay in the
   * second group, as they were before. Private members of a class compiled elsewhere are
   * left out: the program cannot call them.
   *
   * `calledStatically` excludes the other kind of member up front: a failed instance call
   * (`a.zip(b)`) never suggests a static method (`Option::of`), and a failed static call
   * never suggests an instance one (an unqualified call, `None`, may reach either, as a
   * top-level function is a static member) -- neither spelling could ever call the other kind, so
   * naming one is actively misleading, not merely a worse guess.
   *
   * The result is a single tier: the first tier with a name close enough to suggest. When
   * neither of those tiers has one, the methods that take no overload of that many
   * arguments form a last tier: `Strings::trimm(s, 1)` is a misspelled `trim` called with
   * one argument too many, and naming `trim` beats saying nothing. Library privates stay out of every tier.
   */
  private def rankedCallCandidates(obj: TypedAST.ObjectType, name: String, argTypes: Array[TypedAST.Type], calledStatically: Option[Boolean]): Seq[String] = {
    val argc = argTypes.length
    // A private member of a class compiled elsewhere (`Format.group`) can never be called
    // from the program, so it is no suggestion; a source class's own privates may be.
    val fromSource = obj.isInstanceOf[TypedAST.ClassDefinition]
    def visible(m: TypedAST.MemberRef): Boolean = fromSource || !Modifier.isPrivate(m.modifier)
    def sameCallKind(m: TypedAST.MemberRef): Boolean = calledStatically.forall(Modifier.isStatic(m.modifier) == _)
    val byName = obj.methods.toSeq.filter(visible).filter(sameCallKind).groupBy(_.name)
    val applicable = byName.collect {
      case (n, ms) if ms.exists(m => acceptsArity(m, argc) && acceptsTypes(m, argTypes)) => n
    }.toSeq
    val countOnly = byName.collect {
      case (n, ms) if !applicable.contains(n) && ms.exists(m => acceptsArity(m, argc)) => n
    }.toSeq
    val fields = obj.fields.toSeq.filter(visible).filter(sameCallKind).map(_.name).filterNot(byName.contains)
    // Arity misfits come last: only a fallback when nothing that fits is close by name.
    val arityMisfits = byName.keys.filterNot(n => applicable.contains(n) || countOnly.contains(n)).toSeq
    // findSimilar applies the distance cut-off; the first tier with a close name wins.
    val tiers = Seq(applicable.sorted, (countOnly ++ fields).distinct.sorted, arityMisfits.sorted)
    tiers.find(tier => toolbox.Suggestions.findSimilar(name, tier).isDefined).getOrElse(Seq.empty)
  }

  private def acceptsArity(m: TypedAST.Method, argc: Int): Boolean =
    if (m.isVararg) argc >= m.argumentCount - 1
    else argc >= m.minArguments && argc <= m.argumentCount

  /**
   * Whether each argument could be passed to `m`'s parameter in its position. Deliberately
   * lenient -- it only orders suggestions, never rejects a call: anything involving a type
   * variable, a function value (SAM conversion) or an unknown type counts as passable.
   */
  private def acceptsTypes(m: TypedAST.Method, argTypes: Array[TypedAST.Type]): Boolean = {
    val params = m.arguments
    argTypes.indices.forall { i =>
      val p =
        if (i < params.length && !(m.isVararg && i == params.length - 1)) params(i)
        else if (m.isVararg && params.nonEmpty) params.last match
          case arr: TypedAST.ArrayType if !(argTypes.length == params.length && argTypes(i).isArrayType) => arr.component
          case other => other
        else null
      p == null || argPassable(p, argTypes(i))
    }
  }

  private def argPassable(param: TypedAST.Type, arg: TypedAST.Type): Boolean = {
    def strip(t: TypedAST.Type): TypedAST.Type = t match
      case n: TypedAST.NullableType => n.innerType
      case other => other
    val p = strip(param)
    val a = strip(arg)
    def className(t: TypedAST.Type): String = t match
      case ap: TypedAST.AppliedClassType => ap.raw.name
      case ct: TypedAST.ClassType => ct.name
      case _ => null
    (p, a) match
      case (null, _) | (_, null) => true
      case (_: TypedAST.TypeVariableType, _) | (_, _: TypedAST.TypeVariableType) => true
      case (_, _) if a.isNullType || a.isBottomType => !p.isBasicType
      case (pb: TypedAST.BasicType, ab: TypedAST.BasicType) => TypedAST.TypeRules.isSuperType(pb, ab)
      case (pb: TypedAST.BasicType, _) => className(a) == boxedName(pb)
      case (_, ab: TypedAST.BasicType) =>
        val n = className(p)
        n == boxedName(ab) || BoxingSupertypes.contains(n)
      case (_, _) if className(a) != null && className(a).startsWith("onion.Function") => true
      case (pc: TypedAST.ClassType, ac: TypedAST.ClassType) =>
        TypedAST.TypeRules.isSuperType(pc, ac) || TypedAST.TypeRules.isSuperType(rawOf(pc), rawOf(ac))
      case _ => TypedAST.TypeRules.isSuperType(p, a)
  }

  /** Other languages' names for a `List` pipeline method -> Onion's name for it. */
  private val ForeignCollectionNames: Map[String, String] = Map(
    "sortBy" -> "sortedBy",
    "sortByDescending" -> "sortedByDescending",
    "forall" -> "all",
    "joinToString" -> "join",
    "foldLeft" -> "fold",
    "inject" -> "fold",
    "sumOf" -> "sumBy",
    "unique" -> "distinct",
    "nonEmpty" -> "isNotEmpty",
    "each" -> "forEach",
    "collect" -> "map",
    "firstOrNull" -> "first",
    "lastOrNull" -> "last",
    "zipWithIndex" -> "withIndex",
    "indexed" -> "withIndex"
  )

  /** Other languages' names for a String operation -> Onion's name for it. */
  private val ForeignStringNames: Map[String, String] = Map(
    "reversed" -> "reverse",
    "format" -> "formatted",
    "padStart" -> "padLeft",
    "padEnd" -> "padRight",
    "toUpper" -> "upper",
    "splitWhitespace" -> "words",
    "trimStart" -> "stripLeading",
    "trimEnd" -> "stripTrailing",
    "splitlines" -> "lines",
    "len" -> "length",
    "find" -> "indexOf",
    "includes" -> "contains",
    "title" -> "capitalizeWords"
  )

  private def isStringType(t: TypedAST.Type): Boolean = t match
    case n: TypedAST.NullableType => isStringType(n.innerType)
    case ct: TypedAST.ClassType => rawOf(ct).name == "java.lang.String"
    case _ => false

  /** List operations a Java Stream lacks, so `s.lines().size` means the user wanted a List. */
  private val ListOnlyNames: Set[String] = Set(
    "size", "first", "last", "join", "get", "isEmpty", "contains", "indexOf", "sortedBy",
    "sortedByDescending", "groupBy", "partition", "sumBy", "maxBy", "minBy", "take", "drop",
    "chunked", "windowed", "reversed", "withIndex", "mapIndexed", "associateBy", "fold"
  )

  /**
   * `s.lines()`/`s.chars()` are Java's String methods and return a Stream, not a List: a
   * List-style name on one says to convert first (`toList()`, or `boxed().toList()` for a
   * primitive stream), instead of a name-similarity guess such as `min` or `limit`.
   */
  private def streamToListHint(t: TypedAST.Type, name: String): Option[String] =
    if (!ListOnlyNames.contains(name)) None
    else t match
      case ct: TypedAST.ClassType if implementsType(ct, "java.util.stream.BaseStream") =>
        val primitive = Set("java.util.stream.IntStream", "java.util.stream.LongStream", "java.util.stream.DoubleStream")
          .exists(n => implementsType(ct, n))
        val conversion = if (primitive) "boxed().toList()" else "toList()"
        Some(format(message("suggestion.didYouMean"), Seq(s"$conversion.$name")))
      case _ => None

  /**
   * The builtin array pipelines take a `T[]`, which a primitive array (`Int[]`) is not (#2458).
   * Say so instead of a name-similarity guess such as `wait`. `Int[]`/`Long[]`/`Double[]` have an
   * `Arrays::stream`, so they get a one-line conversion; the other primitive arrays get a copy loop.
   */
  private def primitiveArrayPipelineHint(t: TypedAST.Type, name: String): Option[String] =
    t match
      case at: TypedAST.ArrayType if at.dimension == 1 && at.component.isBasicType &&
          Typing.builtinExtensions.getOrElse(Typing.ArrayExtensionReceiver, Nil).exists(_.name == name) =>
        if (Set[TypedAST.Type](TypedAST.BasicType.INT, TypedAST.BasicType.LONG, TypedAST.BasicType.DOUBLE).contains(at.component)) {
          val conversion = "java.util.Arrays::stream(xs).boxed().toList()"
          val snippet = if (name == "toList") conversion else s"$conversion.$name(...)"
          Some(format(message("suggestion.primitiveArrayPipeline"), Seq(typeName(at), snippet)))
        } else {
          Some(format(message("suggestion.primitiveArrayPipelineCopy"), Seq(typeName(at), typeName(at.component))))
        }
      case _ => None

  private def implementsType(ct: TypedAST.ClassType, target: String): Boolean = {
    def loop(c: TypedAST.ClassType, seen: Set[String]): Boolean =
      if (c == null) false
      else {
        val raw = rawOf(c)
        if (raw.name == target) true
        else if (seen.contains(raw.name)) false
        else (Option(raw.superClass).toSeq ++ raw.interfaces).exists(loop(_, seen + raw.name))
      }
    loop(ct, Set.empty)
  }

  /** Whether `t` is `java.util.List` or a class implementing it. */
  /** Whether `toArray(new C[0])` can store every element of the List `list` into `arrayType`
   *  (#2305): `List[Int]` into `String[]` compiles but throws ArrayStoreException. An element
   *  type that cannot be determined gives the snippet the benefit of the doubt. */
  private def listElementsFit(list: TypedAST.Type, arrayType: TypedAST.ArrayType): Boolean = {
    val element: Option[TypedAST.Type] = list match
      case n: TypedAST.NullableType => listElementType(n.innerType)
      case other => listElementType(other)
    element match
      case None => true
      case Some(e) if arrayType.dimension == 1 => argPassable(arrayType.component, e)
      case Some(e) =>
        def strip(t: TypedAST.Type): TypedAST.Type = t match
          case n: TypedAST.NullableType => n.innerType
          case other => other
        strip(e) match
          case ea: TypedAST.ArrayType if ea.dimension == arrayType.dimension - 1 =>
            if (arrayType.component.isBasicType || ea.component.isBasicType) ea.component == arrayType.component
            else TypedAST.TypeRules.isSuperType(arrayType.component, ea.component)
          case _ => false
  }

  /** Whether `toArray(new C[0])` for the array type is itself valid Onion (#2314). */
  private def snippetCompiles(list: TypedAST.Type, arrayType: TypedAST.ArrayType): Boolean = {
    val componentOk = arrayType.component match
      case _: TypedAST.AppliedClassType => false
      case ct: TypedAST.ClassType => ct.typeParameters.isEmpty
      case _ => true
    val element = list match
      case n: TypedAST.NullableType => listElementType(n.innerType)
      case other => listElementType(other)
    componentOk && !(arrayType.dimension == 1 && element.exists(_.isInstanceOf[TypedAST.TypeVariableType]))
  }

  private def listElementType(t: TypedAST.Type): Option[TypedAST.Type] = t match
    case ct: TypedAST.ClassType =>
      onion.compiler.typing.AppliedTypeViews.collectAppliedViewsFrom(ct).collectFirst {
        case (raw, applied) if raw.name == "java.util.List" && applied.typeArguments.length == 1 =>
          applied.typeArguments.head
      }
    case _ => None

  private def isListType(t: TypedAST.Type): Boolean = {
    def loop(ct: TypedAST.ClassType, seen: Set[String]): Boolean =
      if (ct == null) false
      else {
        val raw = rawOf(ct)
        val name = raw.name
        if (name == "java.util.List") true
        else if (seen.contains(name)) false
        else (Option(raw.superClass).toSeq ++ raw.interfaces).exists(loop(_, seen + name))
      }
    t match
      case n: TypedAST.NullableType => isListType(n.innerType)
      case ct: TypedAST.ClassType => loop(ct, Set.empty)
      case _ => false
  }

  private def rawOf(ct: TypedAST.ClassType): TypedAST.ClassType = ct match
    case ap: TypedAST.AppliedClassType => ap.raw
    case other => other

  private def boxedName(b: TypedAST.BasicType): String = b.name match
    case "int" => "java.lang.Integer"
    case "char" => "java.lang.Character"
    case other => "java.lang." + other.capitalize

  /**
   * `readText(p)`, `get(url)`, `now()`, `exit(1)` and similar calls used to resolve
   * bare: v0.10 narrowed the default static import set to pure classes, dropping
   * `System`, `Runtime`, `Files`, `Http` and `DateTime` (CLAUDE.md's own migration
   * note for that change). An unqualified call to one of their real static methods
   * then surfaces as an ordinary METHOD_NOT_FOUND with nothing connecting it back to
   * that change, so `reportMethodNotFound` points at the qualified form instead of a
   * name-similarity guess. Built by reflecting on the actual runtime classes -- shipped
   * with the compiler itself, so always on its classpath regardless of the program
   * being compiled -- rather than a hand-maintained list that could drift from the
   * real API surface.
   */
  private object FormerDefaultImportLookup {
    private val classes: Seq[Class[?]] = Seq(
      classOf[java.lang.System],
      classOf[java.lang.Runtime],
      classOf[onion.Files],
      classOf[onion.Http],
      classOf[onion.DateTime]
    )

    // (name, arity) -> simple class name. A name/arity pair shared by two of these
    // classes is dropped rather than guessed at.
    private lazy val index: Map[(String, Int), String] = {
      val entries = for {
        cls <- classes
        m <- cls.getMethods
        mods = m.getModifiers
        if java.lang.reflect.Modifier.isStatic(mods) && java.lang.reflect.Modifier.isPublic(mods)
        if m.getDeclaringClass == cls
      } yield (m.getName, m.getParameterCount) -> cls.getSimpleName
      entries.groupBy(_._1).collect {
        case (key, matches) if matches.map(_._2).distinct.size == 1 => key -> matches.head._2
      }
    }

    def find(name: String, arity: Int): Option[String] = index.get((name, arity))
  }

  private def fieldNamed(targetType: TypedAST.Type, name: String): Boolean =
    targetType match
      case obj: TypedAST.ObjectType => obj.fields.exists(_.name == name)
      case _ => false

  // Set by MethodCallReportingSupport.reportMethodNotFound (the sole producer of
  // METHOD_NOT_FOUND that routes through accessibility-aware code in the typing
  // package); absent (items.length <= 5) for the handful of direct `bodyContext.report`
  // call sites that report METHOD_NOT_FOUND without it, which keep the old behavior.
  private def inaccessibleFieldNamed(items: Array[AnyRef]): Boolean =
    items.length > 5 && items(5) == java.lang.Boolean.TRUE

  private def isArrayLengthProperty(targetType: TypedAST.Type, name: String): Boolean =
    targetType.isArrayType && (name == "length" || name == "size")

  private def reportFieldNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val targetType = items(0).asInstanceOf[TypedAST.Type]
    val name = asString(items(1))
    val baseMessage = format(message("error.semantic.fieldNotFound"), Seq(typeName(targetType), name))
    // A no-argument method is reachable with the same paren-less syntax as a
    // field (`xs.size`), so it is a valid fix for a misremembered field name --
    // `xs.length` on a List should suggest `size` even though List has no
    // fields at all. Without the methods here the candidate list was often
    // empty and the user got no guidance.
    val candidates = targetType match
      case obj: TypedAST.ObjectType =>
        (obj.fields.map(_.name) ++ obj.methods.filter(_.arguments.isEmpty).map(_.name)).distinct.toSeq
      case _ => Seq.empty
    val suggestion = streamToListHint(targetType, name).orElse(toolbox.Suggestions.formatSuggestion(name, candidates))
    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles LABEL_NOT_FOUND with optional suggestions for similar labels
   * among the enclosing labeled loops.
   */
  private def reportLabelNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.labelNotFound"), Seq(name))

    val suggestion = if (items.length > 1) {
      val candidates = items(1).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles CLASS_NOT_FOUND with optional suggestions for similar class names.
   */
  private def reportClassNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.classNotFound"), Seq(name))

    val suggestion = if (items.length > 1) {
      val candidates = items(1).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    // `extension List[T] { ... }` (TypingTypeSupport.mapFromExtensionReceiver):
    // `T` was meant as a fresh type parameter, mirroring `class Box[T]` syntax,
    // but `extension` has none -- the base type (`List`) plainly exists, so the
    // generic "check spelling or add an import" advice above is beside the point.
    val extensionHint =
      if (items.length > 3 && items(2) == "extensionTypeParam")
        Some(format(message("suggestion.extensionReceiverTypeParameter"), Seq(asString(items(3)))))
      else None

    problem(position, appendSuggestion(appendSuggestion(baseMessage, suggestion), extensionHint))
  }

  /**
   * Handles UNKNOWN_PARAMETER_NAME with optional suggestions for similar
   * parameter names.
   */
  private def reportUnknownParameterName(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.unknownParameterName"), Seq(name))

    val suggestion = if (items.length > 1) {
      val candidates = items(1).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles OVERRIDE_TARGET_NOT_FOUND with optional suggestions for similar
   * method names among the base class's/interfaces' overridable methods.
   */
  private def reportOverrideTargetNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val paramDescriptor = asString(items(1))
    val className = asString(items(2))
    val baseMessage = format(message("error.semantic.overrideTargetNotFound"), Seq(name, paramDescriptor, className))

    val suggestion = if (items.length > 3) {
      val candidates = items(3).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles CONSTRUCTOR_NOT_FOUND with available constructors display.
   */
  private def reportConstructorNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val typeRef = items(0).asInstanceOf[TypedAST.Type]
    val argTypes = asTypeArray(items(1))
    val baseMessage = format(message("error.semantic.constructorNotFound"), Seq(typeName(typeRef), typeNames(argTypes)))

    val suggestion = if (items.length > 2) {
      val constructors = items(2).asInstanceOf[Array[TypedAST.ConstructorRef]]
      if (constructors.nonEmpty) {
        val ctorSignatures = constructors.map { ctor =>
          s"  ${typeRef.name}(${ctor.getArgs.map(onion.compiler.toolbox.TypeFormatting.sourceForm).mkString(", ")})"
        }
        Some(s"${message("error.suggestion.availableConstructors")}${Systems.lineSeparator}${ctorSignatures.mkString(Systems.lineSeparator)}")
      } else None
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles AMBIGUOUS_METHOD with complex item structure.
   */
  private def reportAmbiguousMethod(position: Location, items: Array[AnyRef]): Unit = {
    val item1 = items(0).asInstanceOf[Array[AnyRef]]
    val item2 = items(1).asInstanceOf[Array[AnyRef]]
    val target1 = objectTypeName(item1(0))
    val name1 = asString(item1(1))
    val args1 = typeNames(asTypeArray(item1(2)))
    val target2 = objectTypeName(item2(0))
    val name2 = asString(item2(1))
    val args2 = typeNames(asTypeArray(item2(2)))
    problem(position, format(message("error.semantic.ambiguousMethod"),
      Seq(target1, name1, args1, target2, name2, args2)))
  }

  /**
   * Handles AMBIGUOUS_CONSTRUCTOR with complex item structure.
   */
  private def reportAmbiguousConstructor(position: Location, items: Array[AnyRef]): Unit = {
    val item1 = items(0).asInstanceOf[Array[AnyRef]]
    val item2 = items(1).asInstanceOf[Array[AnyRef]]
    val target1 = objectTypeName(item1(0))
    val args1 = typeNames(asTypeArray(item1(1)))
    val target2 = objectTypeName(item2(0))
    val args2 = typeNames(asTypeArray(item2(1)))
    problem(position, format(message("error.semantic.ambiguousConstructor"),
      Seq(target1, args1, target2, args2)))
  }

  /**
   * Handles NON_EXHAUSTIVE_PATTERN_MATCH with type array formatting.
   * Uses displayName to show generic type arguments in error messages.
   */
  private def reportNonExhaustivePatternMatch(position: Location, items: Array[AnyRef]): Unit = {
    val sealedType = items(0).asInstanceOf[TypedAST.Type]
    // Missing cases are subtypes for sealed hierarchies, constant names for enums
    val missingNames = items(1).asInstanceOf[Array[?]].map {
      case t: TypedAST.Type => onion.compiler.toolbox.TypeFormatting.sourceForm(t)
      case other => String.valueOf(other)
    }.mkString(", ")
    problem(position, format(message("error.semantic.nonExhaustivePatternMatch"),
      Seq(onion.compiler.toolbox.TypeFormatting.sourceForm(sealedType), missingNames)))
  }

  /**
   * Handles INCOMPATIBLE_TYPE with a null-safety hint when a nullable value
   * is used where a non-null type is expected.
   */
  private def reportIncompatibleType(position: Location, items: Array[AnyRef]): Unit = {
    val expected = items(0).asInstanceOf[TypedAST.Type]
    val actual = items(1).asInstanceOf[TypedAST.Type]
    // `(list as String[])`/`(arr as List[String])`: a List can never be an array at
    // runtime (#2258), so the generic message's "consider a cast" is actively wrong here.
    val listArrayCast: Option[(TypedAST.ArrayType, Boolean)] =
      if (expected.isInstanceOf[TypedAST.ArrayType] && isListType(actual))
        Some((expected.asInstanceOf[TypedAST.ArrayType], true))
      else if (isListType(expected) && actual.isInstanceOf[TypedAST.ArrayType])
        Some((actual.asInstanceOf[TypedAST.ArrayType], false))
      else None
    def written(t: TypedAST.Type): String = t match
      case a: TypedAST.ArrayType if listArrayCast.isDefined => onion.compiler.toolbox.TypeFormatting.arrayWrittenForm(a)
      case other => typeName(other)
    val baseMessage = format(
      message(if (listArrayCast.isDefined) "error.semantic.incompatibleType.noCast" else "error.semantic.incompatibleType"),
      Seq(written(expected), written(actual))
    )
    val hint =
      if (items.length > 2 && items(2) == SemanticErrorReporter.HexDigitBHint) {
        Some(message("suggestion.hexDigitB"))
      } else if (expected != null && actual != null && !expected.isNullable && actual.isNullable) {
        Some(format(message("suggestion.nullableToNonNull"), Seq()))
      } else if (expected != null && actual.isInstanceOf[TypedAST.TypeVariableType] && !expected.isInstanceOf[TypedAST.TypeVariableType]) {
        // `(x as Outcome.Ok).value()` yields the raw type variable: a cast to a generic
        // nested class does not restore the scrutinee's type arguments, a pattern does.
        Some(format(message("suggestion.castLosesTypeArguments"), Seq(typeName(actual))))
      } else listArrayCast match {
        case Some((arrayType, isListToArray)) =>
          // `toArray`/`Colls::toList` are generic over a reference-typed array -- a true
          // 1-D primitive array (`Int[]`) has no such overload, so naming either call
          // would hand back a different error (#2260 review). An outer dimension's
          // elements are themselves a reference type (arrays are objects), so only the
          // 1-D primitive case has no one-line fix.
          if (arrayType.dimension == 1 && arrayType.component.isBasicType) None
          else if (isListToArray && !listElementsFit(actual, arrayType)) None
          // `new C[0]` must compile when typed in: a generic component is raw there (E0066),
          // and a bare type parameter's elements guarantee no more than Object does (#2314).
          else if (isListToArray && !snippetCompiles(actual, arrayType)) None
          else if (isListToArray) {
            // Onion has no jagged-array syntax (`new Int[0][]` is a syntax error); a fully
            // sized `new Int[0][0]` compiles and is just as empty, since the outer (size-0)
            // dimension is the only one that matters to `toArray`.
            val emptyArrayLiteral = onion.compiler.toolbox.TypeFormatting.componentWrittenForm(arrayType.component) + ("[0]" * arrayType.dimension)
            Some(format(message("suggestion.listAsArrayCast"), Seq(emptyArrayLiteral)))
          } else {
            Some(message("suggestion.arrayAsListCast"))
          }
        case None => None
      }
    problem(position, appendSuggestion(baseMessage, hint))
  }

  /**
   * Handles METHOD_NOT_ACCESSIBLE (also covers constructors, reported as method `new`)
   * with a hint that an unmarked -- or explicitly `private:` -- member needs a
   * `public:`/`protected:` section to be reachable from outside its class. Without
   * it, "is not accessible" reads as a deliberate restriction rather than a missing
   * section a newcomer forgot to write.
   */
  private def reportMethodNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    val baseMessage = format(message("error.semantic.methodNotAccessible"),
      Seq(objectTypeName(items(0)), asString(items(1)), typeNames(asTypeArray(items(2))), classTypeName(items(3))))
    problem(position, appendSuggestion(baseMessage, Some(message("suggestion.privateAccessHint"))))
  }

  /**
   * Handles FIELD_NOT_ACCESSIBLE with the same hint as [[reportMethodNotAccessible]].
   */
  private def reportFieldNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    val baseMessage = format(message("error.semantic.fieldNotAccessible"),
      Seq(classTypeName(items(0)), asString(items(1)), classTypeName(items(2))))
    problem(position, appendSuggestion(baseMessage, Some(message("suggestion.privateAccessHint"))))
  }

  /**
   * Handles MISSING_RETURN with a hint when the declared return type is the
   * capitalized `Void` class rather than Onion's value-less `void`.
   */
  private def reportMissingReturn(position: Location, items: Array[AnyRef]): Unit = {
    val returnType = items(1).asInstanceOf[TypedAST.Type]
    val baseMessage = format(message("error.semantic.missingReturn"),
      Seq(asString(items(0)), typeName(returnType), indefiniteArticled(typeName(returnType))))
    val hint = if (isJavaLangVoidClass(returnType)) Some(message("suggestion.voidClassReturnType")) else None
    problem(position, appendSuggestion(baseMessage, hint))
  }

  /**
   * Handles CANNOT_RETURN_VALUE with the same `Void`-vs-`void` hint as
   * MISSING_RETURN, for the `return;`/`return <void-expr>` shape of the
   * same underlying mistake.
   */
  private def reportCannotReturnValue(position: Location, items: Array[AnyRef]): Unit = {
    val baseMessage = message("error.semantic.cannotReturnValue")
    val hint =
      if (items.nonEmpty && isJavaLangVoidClass(items(0).asInstanceOf[TypedAST.Type])) Some(message("suggestion.voidClassReturnType"))
      else None
    problem(position, appendSuggestion(baseMessage, hint))
  }

  /**
   * Handles LVALUE_REQUIRED with a hint when the rejected target is a safe
   * navigation (`obj?.field = value` or `obj?[index] = value`), a mistake
   * distinct enough from the generic non-lvalue case (e.g. `null = expr`) to
   * earn its own guidance.
   */
  private def reportLvalueRequired(position: Location, items: Array[AnyRef]): Unit = {
    val baseMessage = message("error.semantic.lValueRequired")
    val hint = items.headOption match {
      case Some(java.lang.Boolean.TRUE) => Some(message("suggestion.safeNavAssignmentTarget"))
      case Some("indexing") => Some(message("suggestion.safeNavIndexingAssignmentTarget"))
      case _ => None
    }
    problem(position, appendSuggestion(baseMessage, hint))
  }

  /**
   * Handles NULLABLE_MEMBER_ACCESS with wording that matches how the
   * receiver was dereferenced: dot access (`b.field`/`b.method()`) hints at
   * `?.`, indexing (`b[i]`) hints at `?[` instead -- `?.` would be the wrong
   * operator to suggest for the indexing form.
   */
  private def reportNullableMemberAccess(position: Location, items: Array[AnyRef]): Unit = {
    val typeName = asString(items(0))
    val key =
      if (items.length > 1 && items(1) == "indexing") "error.semantic.nullableIndexingAccess"
      else if (items.length > 1 && items(1) == "operator") "error.semantic.nullableOperatorOperand"
      else if (items.length > 1 && items(1) == "condition") "error.semantic.nullableCondition"
      else if (items.length > 1 && items(1) == "arraySize") "error.semantic.nullableArraySize"
      else if (items.length > 1 && items(1) == "resource") "error.semantic.nullableTryResource"
      else if (items.length > 1 && items(1) == "throw") "error.semantic.nullableThrow"
      else if (items.length > 1 && items(1) == "select") "error.semantic.nullableSelectScrutinee"
      else if (items.length > 1 && items(1) == "rangeBound") "error.semantic.nullableRangeBound"
      else if (items.length > 1 && items(1) == "assignment") "error.semantic.nullableAssignment"
      else if (items.length > 1 && items(1) == "catchType") "error.semantic.nullableCatchType"
      else if (items.length > 1 && items(1) == "synchronized") "error.semantic.nullableSynchronizedLock"
      else "error.semantic.nullableMemberAccess"
    // A top-level script `var` is never flow-narrowed by a null check (#2026, RFC
    // #2031 still open on whether it should be); the generic "...or check for null
    // first" advice in the base message is actively misleading there. Add a hint
    // naming the actual workaround instead (RFC #2031 "option 1").
    val hint =
      if (items.length > 1 && items(1) == "topLevelVar") Some(message("suggestion.topLevelVarNotNarrowed"))
      else None
    problem(position, appendSuggestion(format(message(key), Seq(typeName)), hint))
  }

  // ========== Main report method ==========

  /** Lambdas already reported as LAMBDA_ARITY_MISMATCH. Overload resolution can type
   *  the same lambda once per candidate (`map(List)` and `map(Iterable)`), and its
   *  parameter count is wrong against every one of them alike, so one report is enough. */
  private val arityMismatchPositions = scala.collection.mutable.Set.empty[(String, Location)]

  def report(error: SemanticError, position: Location, items: Array[AnyRef]): Unit = {
    if (error == SemanticError.LAMBDA_ARITY_MISMATCH && position != null && !arityMismatchPositions.add((sourceFile, position))) return
    errorCount += 1
    currentError = error

    error match {
      // Special cases that need custom handling
      case SemanticError.VARIABLE_NOT_FOUND =>
        reportVariableNotFound(position, items)
      case SemanticError.METHOD_NOT_FOUND =>
        reportMethodNotFound(position, items)
      case SemanticError.FIELD_NOT_FOUND =>
        reportFieldNotFound(position, items)
      case SemanticError.LABEL_NOT_FOUND =>
        reportLabelNotFound(position, items)
      case SemanticError.CLASS_NOT_FOUND =>
        reportClassNotFound(position, items)
      case SemanticError.CONSTRUCTOR_NOT_FOUND =>
        reportConstructorNotFound(position, items)
      case SemanticError.UNKNOWN_PARAMETER_NAME =>
        reportUnknownParameterName(position, items)
      case SemanticError.OVERRIDE_TARGET_NOT_FOUND =>
        reportOverrideTargetNotFound(position, items)
      case SemanticError.AMBIGUOUS_METHOD =>
        reportAmbiguousMethod(position, items)
      case SemanticError.AMBIGUOUS_CONSTRUCTOR =>
        reportAmbiguousConstructor(position, items)
      case SemanticError.NON_EXHAUSTIVE_PATTERN_MATCH =>
        reportNonExhaustivePatternMatch(position, items)
      case SemanticError.INCOMPATIBLE_TYPE =>
        reportIncompatibleType(position, items)
      case SemanticError.MISSING_RETURN =>
        reportMissingReturn(position, items)
      case SemanticError.CANNOT_RETURN_VALUE =>
        reportCannotReturnValue(position, items)
      case SemanticError.LVALUE_REQUIRED =>
        reportLvalueRequired(position, items)
      case SemanticError.NULLABLE_MEMBER_ACCESS =>
        reportNullableMemberAccess(position, items)
      case SemanticError.METHOD_NOT_ACCESSIBLE =>
        reportMethodNotAccessible(position, items)
      case SemanticError.FIELD_NOT_ACCESSIBLE =>
        reportFieldNotAccessible(position, items)

      // Data-driven cases
      case _ =>
        errorDefs.get(error) match {
          case Some(defn) =>
            val args = defn.extractors.map(_.apply(items))
            problem(position, format(message(defn.messageKey), args))
          case None =>
            // Fallback for any unmapped error
            problem(position, s"Unknown error: $error")
        }
    }

    if (errorCount >= threshold) {
      throw new CompilationException(problems.toSeq)
    }
  }

  def getProblems: Array[CompileError] = problems.toArray

  /** Number of errors collected so far. Lets callers checkpoint before/after a
   *  speculative typing attempt (e.g. a trailing-closure body) to detect whether
   *  it reported a fresh error, so a redundant outer method-not-found can be
   *  suppressed (issue #316). */
  def problemCount: Int = problems.length

  def setSourceFile(sourceFile: String): Unit = {
    this.sourceFile = sourceFile
  }

  // ========== Context management ==========

  /** Sets the current compilation context */
  def setContext(ctx: CompilationContext): Unit = {
    this.context = ctx
  }

  /** Gets the current compilation context */
  def getContext: CompilationContext = context

  /** Enters a class context */
  def enterClass(className: String): Unit = {
    context = context.withClass(className)
  }

  /** Enters a method context */
  def enterMethod(methodName: String): Unit = {
    context = context.withMethod(methodName)
  }

  /** Leaves the current method context */
  def leaveMethod(): Unit = {
    context = context.clearMethod
  }

  /** Leaves the current class context */
  def leaveClass(): Unit = {
    context = context.clearClass
  }

  /** Sets the compilation phase */
  def setPhase(phase: String): Unit = {
    context = context.withPhase(phase)
  }

  /** Resets the context to empty */
  def resetContext(): Unit = {
    context = CompilationContext.empty
  }

  private def problem(position: Location, message: String): Unit = {
    val errorCode = Option(currentError).map(_.errorCode)
    val ctx = if (context == CompilationContext.empty) None else Some(context)
    // Closure bodies are typed more than once (return-type inference,
    // bidirectional trials); drop exact duplicates so each problem is
    // reported a single time
    val duplicate = problems.exists(p =>
      p.sourceFile == sourceFile && p.location == position && p.message == message
    )
    if (!duplicate) {
      problems.append(CompileError(sourceFile, position, message, errorCode, ctx))
    }
  }
}

/**
 * The error-definition table and its pure helpers live on the companion object: they
 * carry no per-compilation state, and as class members the ~90-entry map with a closure
 * per entry was rebuilt for every `new SemanticErrorReporter` -- once per compilation.
 * The reporter's constructor showed up in profiles of a one-line program for that reason.
 */
object SemanticErrorReporter {
  /**
   * An extra item on INCOMPATIBLE_TYPE (third) or METHOD_NOT_FOUND (ninth): the failing value
   * is a hex literal ending in `B`, which was a Byte before #2324, so the error says why.
   */
  private[compiler] case object HexDigitBHint
  // Compiled once: a reporter is created per compilation, and a regex in its body was too.
  private val TemplateLiteralInName = """\$\{.*\}""".r
  // The reference types a primitive argument boxes into, for ranking call suggestions.
  private val BoxingSupertypes =
    Set("java.lang.Object", "java.lang.Number", "java.lang.Comparable", "java.io.Serializable", "java.lang.constant.Constable")

  private def typeName(item: AnyRef): String =
    if (item == null) "<unknown>" else onion.compiler.toolbox.TypeFormatting.sourceForm(item.asInstanceOf[TypedAST.Type])
  private def classTypeName(item: AnyRef): String =
    if (item == null) "<unknown>" else onion.compiler.toolbox.TypeFormatting.sourceForm(item.asInstanceOf[TypedAST.ClassType])
  private def objectTypeName(item: AnyRef): String =
    if (item == null) "<unknown>" else onion.compiler.toolbox.TypeFormatting.sourceForm(item.asInstanceOf[TypedAST.ObjectType])
  private def asString(item: AnyRef): String = item.asInstanceOf[String]
  // English indefinite article for a noun starting with a vowel sound ("enum" -> "an
  // enum") vs. one that doesn't ("record" -> "a record"). Only used where the same
  // message key must grammatically agree with more than one substituted noun -- a fixed
  // noun's article belongs directly in the .properties text instead, as everywhere else.
  private def indefiniteArticled(noun: String): String = {
    val article = noun.headOption.map(_.toLower) match {
      case Some(c) if "aeiou".contains(c) => "an"
      case _ => "a"
    }
    s"$article $noun"
  }
  private def asInt(item: AnyRef): String = item.asInstanceOf[Int].toString
  // English count + noun agreement ("1 field" / "2 fields"). Only used where the same
  // message key's Japanese counterpart uses a bare number with a counter word (e.g. "{1}
  // 個の...") and has no plural form to agree -- English gets its own pre-pluralized
  // argument instead of reusing the raw count, same pattern as `indefiniteArticled`.
  private def pluralizeCount(count: Int, noun: String): String =
    if (count == 1) s"1 $noun" else s"$count ${noun}s"
  // Same, but also carries the trailing verb so subject-verb number agreement is
  // captured too ("1 binding was specified" / "2 bindings were specified").
  private def pluralizeCountVerb(count: Int, noun: String, verbSingular: String, verbPlural: String): String =
    if (count == 1) s"1 $noun $verbSingular" else s"$count ${noun}s $verbPlural"
  // Same idea, but for a clause with no noun of its own ("1 is supplied" / "2 are
  // supplied") -- the noun was already named earlier in the sentence.
  private def pluralizeVerbOnly(count: Int, verbSingular: String, verbPlural: String): String =
    if (count == 1) s"1 $verbSingular" else s"$count $verbPlural"
  private def typeNames(types: Array[TypedAST.Type]): String = {
    if (types.isEmpty) "" else types.map(onion.compiler.toolbox.TypeFormatting.sourceForm).mkString(", ")
  }
  private def asTypeArray(item: AnyRef): Array[TypedAST.Type] = item.asInstanceOf[Array[TypedAST.Type]]
  // Detects the easy-to-typo `Void` (capitalized) return type -- the ordinary
  // boxed reference type `java.lang.Void`, not Onion's value-less `void` --
  // so MISSING_RETURN/CANNOT_RETURN_VALUE can hint at the likely fix.
  private def isJavaLangVoidClass(tp: TypedAST.Type): Boolean = tp match {
    case null => false
    case n: TypedAST.NullableType => isJavaLangVoidClass(n.innerType)
    case ct: TypedAST.ClassType => ct.name == "java.lang.Void"
    case _ => false
  }

  // ========== Message formatting ==========

  private def message(property: String): String = Message(property)

  private def format(template: String, args: Seq[String]): String = {
    if (args.isEmpty) template
    else MessageFormat.format(template, args.asInstanceOf[Seq[AnyRef]]: _*)
  }

  private def appendSuggestion(baseMessage: String, suggestion: Option[String]): String =
    suggestion match {
      case Some(text) => s"$baseMessage${Systems.lineSeparator}  $text"
      case None => baseMessage
    }


  // ========== Error definitions ==========

  /**
   * Error definition with message key and argument extractors.
   * Each extractor function takes the items array and returns a format argument.
   */

  private case class ErrorDef(
    messageKey: String,
    extractors: Seq[Array[AnyRef] => String]
  )

  /**
   * Error definitions for standard errors.
   * Special cases (with suggestions, complex formatting) are handled separately.
   */

  private val errorDefs: Map[SemanticError, ErrorDef] = Map(
    // Type errors
    SemanticError.ILLEGAL_METHOD_CALL -> ErrorDef(
      "error.semantic.illegalMethodCall",
      Seq(items => classTypeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.INCOMPATIBLE_TYPE -> ErrorDef(
      "error.semantic.incompatibleType",
      Seq(items => typeName(items(0)), items => typeName(items(1)))
    ),
    SemanticError.INCOMPATIBLE_OPERAND_TYPE -> ErrorDef(
      "error.semantic.incompatibleOperandType",
      Seq(items => asString(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.CANNOT_ASSIGN_TO_VAL -> ErrorDef(
      "error.semantic.cannotAssignToVal",
      Seq(items => asString(items(0)))
    ),
    SemanticError.VAL_REQUIRES_INITIALIZER -> ErrorDef(
      "error.semantic.valRequiresInitializer",
      Seq(items => asString(items(0)))
    ),
    SemanticError.CANNOT_RETURN_VALUE -> ErrorDef(
      "error.semantic.cannotReturnValue",
      Seq()
    ),
    SemanticError.IS_NOT_BOXABLE_TYPE -> ErrorDef(
      "error.semantic.isNotBoxableType",
      Seq(items => typeName(items(0)))
    ),

    // Resolution errors - CLASS_NOT_FOUND handled specially with suggestions
    SemanticError.FIELD_NOT_FOUND -> ErrorDef(
      "error.semantic.fieldNotFound",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.METHOD_NOT_FOUND -> ErrorDef(
      "error.semantic.methodNotFound",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),
    // CONSTRUCTOR_NOT_FOUND handled specially with available constructors
    SemanticError.CANNOT_CALL_METHOD_ON_PRIMITIVE -> ErrorDef(
      "error.semantic.cannotCallMethodOnPrimitive",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.INVALID_METHOD_CALL_TARGET -> ErrorDef(
      "error.semantic.invalidMethodCallTarget",
      Seq(items => typeName(items(0)))
    ),

    // Duplication errors
    SemanticError.DUPLICATE_LOCAL_VARIABLE -> ErrorDef(
      "error.semantic.duplicatedVariable",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_CLASS -> ErrorDef(
      "error.semantic.duplicatedClass",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_FIELD -> ErrorDef(
      "error.semantic.duplicatedField",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.DUPLICATE_METHOD -> ErrorDef(
      "error.semantic.duplicatedMethod",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),
    SemanticError.DUPLICATE_GLOBAL_VARIABLE -> ErrorDef(
      "error.semantic.duplicatedGlobalVariable",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_FUNCTION -> ErrorDef(
      "error.semantic.duplicatedFunction",
      Seq(items => asString(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.DUPLICATE_CONSTRUCTOR -> ErrorDef(
      "error.semantic.duplicatedConstructor",
      Seq(items => typeName(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.DUPLICATE_TYPE_PARAMETER -> ErrorDef(
      "error.semantic.duplicatedTypeParameter",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_GENERATED_METHOD -> ErrorDef(
      "error.semantic.duplicateGeneratedMethod",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),
    SemanticError.DUPLICATE_EXTENSION_METHOD -> ErrorDef(
      "error.semantic.duplicateExtensionMethod",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),

    // Access errors
    SemanticError.METHOD_NOT_ACCESSIBLE -> ErrorDef(
      "error.semantic.methodNotAccessible",
      Seq(
        items => objectTypeName(items(0)),
        items => asString(items(1)),
        items => typeNames(asTypeArray(items(2))),
        items => classTypeName(items(3))
      )
    ),
    SemanticError.FIELD_NOT_ACCESSIBLE -> ErrorDef(
      "error.semantic.fieldNotAccessible",
      Seq(items => classTypeName(items(0)), items => asString(items(1)), items => classTypeName(items(2)))
    ),
    SemanticError.CLASS_NOT_ACCESSIBLE -> ErrorDef(
      "error.semantic.classNotAccessible",
      Seq(items => classTypeName(items(0)), items => classTypeName(items(1)))
    ),

    // Inheritance errors
    SemanticError.CYCLIC_INHERITANCE -> ErrorDef(
      "error.semantic.cyclicInheritance",
      Seq(items => asString(items(0)))
    ),
    SemanticError.ILLEGAL_INHERITANCE -> ErrorDef(
      "error.semantic.illegalInheritance",
      Seq(items => asString(items(0)), items => message(s"error.semantic.illegalInheritanceReason.${asString(items(1))}"))
    ),
    SemanticError.INTERFACE_REQUIRED -> ErrorDef(
      "error.semantic.interfaceRequired",
      Seq(items => typeName(items(0)))
    ),
    SemanticError.UNIMPLEMENTED_ABSTRACT_METHOD -> ErrorDef(
      "error.semantic.unimplementedAbstractMethod",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.ABSTRACT_CLASS_INSTANTIATION -> ErrorDef(
      "error.semantic.abstractClassInstantiation",
      Seq(items => typeName(items(0)))
    ),
    SemanticError.FINAL_METHOD_OVERRIDE -> ErrorDef(
      "error.semantic.finalMethodOverride",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    // OVERRIDE_TARGET_NOT_FOUND handled specially with suggestions

    // Generic type errors
    SemanticError.TYPE_NOT_GENERIC -> ErrorDef(
      "error.semantic.typeNotGeneric",
      Seq(items => asString(items(0)))
    ),
    SemanticError.RAW_TYPE_NOT_ALLOWED -> ErrorDef(
      "error.semantic.rawTypeNotAllowed",
      Seq(items => asString(items(0)))
    ),
    // {1} is the raw type name (used by the Japanese template, which has no
    // article to agree). {2} is the English-only "a X"/"an X" phrase, same
    // pattern as `indefiniteArticled`'s use for CONSTRUCTOR_IN_RECORD_OR_ENUM.
    SemanticError.MISSING_RETURN -> ErrorDef(
      "error.semantic.missingReturn",
      Seq(
        items => asString(items(0)),
        items => typeName(items(1)),
        items => indefiniteArticled(typeName(items(1)))
      )
    ),
    // {1}/{2} are the raw counts (used by the Japanese template, which counts with
    // "個" and has no plural form to agree). {3}/{4} are English-only pre-pluralized
    // clauses for the same counts, same pattern as WRONG_BINDING_COUNT above.
    SemanticError.TYPE_ARGUMENT_ARITY_MISMATCH -> ErrorDef(
      "error.semantic.typeArgumentArityMismatch",
      Seq(
        items => asString(items(0)),
        items => items(1).toString,
        items => items(2).toString,
        items => pluralizeCount(items(1).asInstanceOf[Int], "type argument"),
        items => pluralizeVerbOnly(items(2).asInstanceOf[Int], "is supplied", "are supplied")
      )
    ),
    SemanticError.TYPE_ARGUMENT_MUST_BE_REFERENCE -> ErrorDef(
      "error.semantic.typeArgumentMustBeReference",
      Seq(items => asString(items(0)))
    ),
    SemanticError.METHOD_NOT_GENERIC -> ErrorDef(
      "error.semantic.methodNotGeneric",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    // {2}/{3} are the raw counts (used by the Japanese template, which counts with
    // "個" and has no plural form to agree). {4}/{5} are English-only pre-pluralized
    // clauses for the same counts, same pattern as TYPE_ARGUMENT_ARITY_MISMATCH above.
    SemanticError.METHOD_TYPE_ARGUMENT_ARITY_MISMATCH -> ErrorDef(
      "error.semantic.methodTypeArgumentArityMismatch",
      Seq(
        items => asString(items(0)),
        items => asString(items(1)),
        items => items(2).toString,
        items => items(3).toString,
        items => pluralizeCount(items(2).asInstanceOf[Int], "type argument"),
        items => pluralizeVerbOnly(items(3).asInstanceOf[Int], "is supplied", "are supplied")
      )
    ),
    SemanticError.ERASURE_SIGNATURE_COLLISION -> ErrorDef(
      "error.semantic.erasureSignatureCollision",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),

    // Pattern matching errors
    SemanticError.DUPLICATE_ARGUMENT -> ErrorDef(
      "error.semantic.duplicateArgument",
      Seq(items => asString(items(0)))
    ),
    SemanticError.POSITIONAL_AFTER_NAMED -> ErrorDef(
      "error.semantic.positionalAfterNamed",
      Seq()
    ),
    // {1}/{2} are the raw counts (used by the Japanese template, which counts with
    // "個" and has no plural form to agree). {3}/{4} are English-only pre-pluralized
    // clauses for the same counts, same pattern as CONSTRUCTOR_IN_RECORD_OR_ENUM's
    // `indefiniteArticled` below: the English template uses {3}/{4} instead of the
    // bare numbers so "1 fields"/"1 bindings were specified" can't happen.
    SemanticError.WRONG_BINDING_COUNT -> ErrorDef(
      "error.semantic.wrongBindingCount",
      Seq(
        items => asString(items(2)),
        items => asInt(items(0)),
        items => asInt(items(1)),
        items => pluralizeCount(items(0).asInstanceOf[Int], "field"),
        items => pluralizeCountVerb(items(1).asInstanceOf[Int], "binding", "was specified", "were specified")
      )
    ),
    SemanticError.NOT_A_RECORD_TYPE -> ErrorDef(
      "error.semantic.notARecordType",
      Seq(items => asString(items(0)))
    ),

    SemanticError.BREAK_OUTSIDE_LOOP -> ErrorDef(
      "error.semantic.breakOutsideLoop",
      Seq()
    ),
    SemanticError.CONTINUE_OUTSIDE_LOOP -> ErrorDef(
      "error.semantic.continueOutsideLoop",
      Seq()
    ),
    SemanticError.CURRENT_INSTANCE_NOT_AVAILABLE -> ErrorDef(
      "error.semantic.currentInstanceNotAvailable",
      Seq()
    ),
    SemanticError.RETURN_TYPE_REQUIRED -> ErrorDef(
      "error.semantic.returnTypeRequired",
      Seq(items => asString(items(0)))
    ),
    SemanticError.LAMBDA_PARAM_TYPE_REQUIRED -> ErrorDef(
      "error.semantic.lambdaParamTypeRequired",
      Seq(items => asString(items(0)))
    ),
    SemanticError.FUNCTION_BODY_REQUIRED -> ErrorDef(
      "error.semantic.functionBodyRequired",
      Seq(items => asString(items(0)))
    ),
    SemanticError.TYPE_PARAMETER_MAY_BE_NULL -> ErrorDef(
      "error.semantic.typeParameterMayBeNull",
      Seq(items => asString(items(0)))
    ),
    SemanticError.STATIC_CALL_ON_INSTANCE -> ErrorDef(
      "error.semantic.staticCallOnInstance",
      Seq(items => asString(items(0)))
    ),
    SemanticError.CANNOT_INSTANTIATE_PRIMITIVE_TYPE -> ErrorDef(
      "error.semantic.cannotInstantiatePrimitiveType",
      Seq(items => typeName(items(0)))
    ),
    SemanticError.CLASS_USED_AS_VALUE -> ErrorDef(
      "error.semantic.classUsedAsValue",
      Seq(items => asString(items(0)))
    ),
    SemanticError.ABSTRACT_METHOD_WITH_BODY -> ErrorDef(
      "error.semantic.abstractMethodWithBody",
      Seq(items => asString(items(0)))
    ),
    SemanticError.MAP_NOT_DIRECTLY_ITERABLE -> ErrorDef(
      "error.semantic.mapNotDirectlyIterable",
      Seq(items => typeName(items(0)))
    ),
    // LABEL_NOT_FOUND handled specially with suggestions
    SemanticError.REGEX_PATTERN_INVALID -> ErrorDef(
      "error.semantic.regexPatternInvalid",
      Seq(items => asString(items(0)))
    ),
    // {0}/{1} are the raw counts (used by the Japanese template, which counts with
    // "個" and has no plural form to agree). {2}/{3} are English-only pre-pluralized
    // clauses for the same counts, same pattern as WRONG_BINDING_COUNT above.
    SemanticError.REGEX_GROUP_MISMATCH -> ErrorDef(
      "error.semantic.regexGroupMismatch",
      Seq(
        items => asString(items(0)),
        items => asString(items(1)),
        items => pluralizeCount(asString(items(0)).toInt, "capture group"),
        items => pluralizeCountVerb(asString(items(1)).toInt, "binding", "was given", "were given")
      )
    ),
    // {3} is the clause as written: `from re"..."`, or `shape doc = json` for a shape.
    SemanticError.RECORD_FROM_COMPONENT_UNSUPPORTED -> ErrorDef(
      "error.semantic.recordFromComponentUnsupported",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)),
        items => asString(items(3)))
    ),
    SemanticError.RECORD_DERIVE_COMPONENT_UNSUPPORTED -> ErrorDef(
      "error.semantic.recordDeriveComponentUnsupported",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.RECORD_DERIVE_UNKNOWN_MARKER -> ErrorDef(
      "error.semantic.recordDeriveUnknownMarker",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.SHAPE_FORMAT_UNKNOWN -> ErrorDef(
      "error.semantic.shapeFormatUnknown",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.TOOL_UNDECLARED_EFFECT -> ErrorDef(
      "error.semantic.toolUndeclaredEffect",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.TOOL_UNUSED_CAPABILITY -> ErrorDef(
      "error.semantic.toolUnusedCapability",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.TOOL_BAD_CAPABILITY -> ErrorDef(
      "error.semantic.toolBadCapability",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.DUPLICATE_TOOL_NAME -> ErrorDef(
      "error.semantic.duplicateToolName",
      Seq(items => asString(items(0)))
    ),
    SemanticError.UNREACHABLE_CATCH_CLAUSE -> ErrorDef(
      "error.semantic.unreachableCatchClause",
      Seq(items => typeName(items(0)), items => typeName(items(1)))
    ),
    SemanticError.SHAPE_INSTANCE_WITHOUT_LAW -> ErrorDef(
      "error.semantic.shapeInstanceWithoutLaw",
      Seq(items => asString(items(0)))
    ),
    SemanticError.STATIC_METHOD_WITHOUT_BODY -> ErrorDef(
      "error.semantic.staticMethodWithoutBody",
      Seq(items => asString(items(0)))
    ),
    SemanticError.PRIVATE_ABSTRACT_METHOD -> ErrorDef(
      "error.semantic.privateAbstractMethod",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_RECORD_COMPONENT -> ErrorDef(
      "error.semantic.duplicateRecordComponent",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    // {0} = the class, {1} = the primary constructor's parameter types, so the fix is
    // copy-pasteable: `def this(...) : this(<{1}>) { ... }`.
    SemanticError.SECONDARY_CONSTRUCTOR_MUST_DELEGATE -> ErrorDef(
      "error.semantic.secondaryConstructorMustDelegate",
      Seq(items => typeName(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.CONSTRUCTOR_DELEGATION_CYCLE -> ErrorDef(
      "error.semantic.constructorDelegationCycle",
      Seq(items => typeName(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    // {2} is derived from {0} (not a separate call-site argument): the English
    // template needs the article to agree with whichever noun ("record" or "enum")
    // was passed, while the Japanese template uses the bare {0} directly (no
    // articles in Japanese), so both are extracted from the same source value.
    SemanticError.CONSTRUCTOR_IN_RECORD_OR_ENUM -> ErrorDef(
      "error.semantic.constructorInRecordOrEnum",
      Seq(items => asString(items(0)), items => typeName(items(1)), items => indefiniteArticled(asString(items(0))))
    ),
    SemanticError.THIS_BEFORE_CONSTRUCTOR_DELEGATION -> ErrorDef(
      "error.semantic.thisBeforeConstructorDelegation",
      Seq(items => typeName(items(0)))
    ),
    // Reported through the normal reporter path (NameResolution / TypingHeaderPass)
    // but never wired here, so both rendered as "Unknown error: <NAME>" (found while
    // sweeping E-code coverage for 0.10.1).
    SemanticError.CYCLIC_TYPE_ALIAS -> ErrorDef(
      "error.semantic.cyclicTypeAlias",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_TYPE_ALIAS -> ErrorDef(
      "error.semantic.duplicateTypeAlias",
      Seq(items => asString(items(0)))
    ),
    SemanticError.MULTIPLE_BINDING_PATTERNS_IN_CASE -> ErrorDef(
      "error.semantic.multipleBindingPatternsInCase",
      Seq()
    ),
    SemanticError.LAMBDA_ARITY_MISMATCH -> ErrorDef(
      "error.semantic.lambdaArityMismatch",
      Seq(items => items(0).toString, items => typeName(items(1).asInstanceOf[TypedAST.Type]), items => items(2).toString)
    )
  )

  // ========== Special case handlers ==========

  // A JS/TS-style backtick template literal (`` `Hello ${name}` ``) isn't a string in
  // Onion -- backticks only escape a reserved word into an identifier, so the whole
  // `${...}` text becomes the literal name of a local variable reference, and the
  // mistake surfaces as a VARIABLE_NOT_FOUND whose message repeats the entire template
  // text back as if it were an identifier. Detect that shape in the unresolved name
  // and point at Onion's actual interpolation syntax instead of a name-similarity guess.
}
