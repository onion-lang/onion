/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import _root_.scala.jdk.CollectionConverters._
import _root_.onion.compiler.toolbox.{Paths, Systems}
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.typing.{NameResolver, TypeAliasEntry, TypeParam, TypeParamScope, TypingDiagnostics, TypingTypeSupport}
import _root_.onion.compiler.typing.session.{AstBindingIndex, ExtensionRegistry, TypeAliasRegistry, TypingGlobalState, TypingSession, TypingUnitContext}
import _root_.onion.compiler.TypedAST._
import _root_.onion.compiler.SemanticError._
import collection.mutable.{HashMap, Map}

/**
 * Type Checking Phase - Static Type Analysis and Type Inference
 *
 * This is the central phase of the Onion compiler, responsible for:
 *   - Type checking all expressions and statements
 *   - Type inference for local variables and closures
 *   - Method and constructor resolution with overloading
 *   - Access control verification
 *   - Symbol table construction
 *
 * == Four Sub-Phases ==
 *
 * The type checking is organized into four sequential sub-phases:
 *
 * '''1. Header Pass''' ([[onion.compiler.typing.TypingHeaderPass]])
 *   - Collects class/interface/record declarations
 *   - Builds the initial class table
 *   - Processes imports and module declarations
 *
 * '''2. Outline Pass''' ([[onion.compiler.typing.TypingOutlinePass]])
 *   - Processes class member declarations (fields, methods)
 *   - Resolves inheritance relationships
 *   - Builds method and field symbol tables
 *
 * '''3. Body Pass''' ([[onion.compiler.typing.TypingBodyPass]])
 *   - Type checks method and constructor bodies
 *   - Performs type inference
 *   - Resolves method calls and field accesses
 *   - Generates typed AST nodes
 *
 * '''4. Duplication Pass''' ([[onion.compiler.typing.TypingDuplicationPass]])
 *   - Detects duplicate declarations
 *   - Validates method signatures (erasure collision detection)
 *
 * == Type System Features ==
 *
 *   - '''Subtyping''': Class inheritance, interface implementation
 *   - '''Generics''': Parameterized types with type bounds
 *   - '''Wildcards''': `? extends T` and `? super T`
 *   - '''Boxing''': Automatic primitive-to-object conversion
 *   - '''Closures''': First-class functions with type inference
 *
 * == Key Components ==
 *
 *   - [[ClassTable]]: Symbol table for class definitions
 *   - [[onion.compiler.typing.NameResolver]]: Resolves type names to types
 *   - [[LocalContext]]: Tracks local variable scopes
 *   - [[SemanticErrorReporter]]: Collects type errors
 *
 * == Phase Position in Pipeline ==
 *
 * {{{
 * Parsing → Rewriting → '''Typing''' → Code Generation
 * }}}
 *
 * @param config Compiler configuration options
 *
 * @see [[onion.compiler.Rewriting]] for the previous phase
 * @see [[onion.compiler.AsmCodeGeneration]] for the next phase
 * @see [[onion.compiler.TypedAST]] for the output AST types
 *
 * @author Kota Mizushima
 */
object Typing {
  /** Pseudo receiver name for extension methods on array types. */
  final val ArrayExtensionReceiver = "<array>"
}

class Typing(config: CompilerConfig) extends AnyRef with Processor[Seq[AST.CompilationUnit], Seq[ClassDefinition]] {
  class TypingEnvironment
  private[compiler] val emptyTypeParams = TypeParamScope(Map.empty)

  private[compiler] def boxedClass(`type`: BasicType): ClassType =
    `type` match
      case BasicType.BOOLEAN => loadRequired("java.lang.Boolean")
      case BasicType.BYTE => loadRequired("java.lang.Byte")
      case BasicType.SHORT => loadRequired("java.lang.Short")
      case BasicType.CHAR => loadRequired("java.lang.Character")
      case BasicType.INT => loadRequired("java.lang.Integer")
      case BasicType.LONG => loadRequired("java.lang.Long")
      case BasicType.FLOAT => loadRequired("java.lang.Float")
      case BasicType.DOUBLE => loadRequired("java.lang.Double")
      case _ => throw new IllegalArgumentException(s"Not a boxable primitive type: ${`type`.name}")

  private[compiler] def boxedTypeArgument(arg: Type): Type =
    arg match
      case bt: BasicType if bt != BasicType.VOID => boxedClass(bt)
      case _ => arg

  type Continuable = Boolean
  type Environment = TypingEnvironment
  type Dimension = Int
  private[compiler] def split(descriptor: AST.TypeDescriptor): (AST.TypeDescriptor, Dimension) = {
    def loop(target: AST.TypeDescriptor, dimension: Int): (AST.TypeDescriptor, Int) = target match {
      case AST.ArrayType(component) => loop(component, dimension + 1)
      case otherwise => (otherwise, dimension)
    }
    loop(descriptor, 0)
  }
  private val globalState = TypingGlobalState(
    table = new ClassTable(classpath(config.classPath)),
    bindings = new AstBindingIndex,
    mappers = Map[String, NameResolver](),
    declaredTypeParams = HashMap[AST.Node, Seq[TypeParam]](),
    typeAliases = new TypeAliasRegistry,
    extensions = new ExtensionRegistry,
    diagnostics = new SemanticErrorReporter(config.maxErrorReports),
    warnings = new WarningReporter(config.warningLevel, config.suppressedWarnings)
  )
  private[compiler] val session = new TypingSession(config, globalState, emptyTypeParams)
  private val diagnostics = new TypingDiagnostics(this, session)
  private val typeSupport = new TypingTypeSupport(this)

  // Lazy so that compilations that never reach an extension lookup (parse
  // failures, most fuzz mutants) don't pay for scanning the builtin
  // containers; eager registration in the constructor caused GC thrashing
  // when thousands of compilers were constructed in one JVM.
  private var builtinExtensionsRegistered = false
  private def ensureBuiltinExtensions(): Unit = {
    if (!builtinExtensionsRegistered) {
      builtinExtensionsRegistered = true
      registerBuiltinExtensions()
    }
  }

  /**
   * Registers the static helpers of the bundled collection utilities as
   * extension methods on their first parameter's type, so scripts can write
   * `list.map { x => ... }` instead of `Colls::map(list) { x => ... }`.
   */
  private def registerBuiltinExtensions(): Unit = {
    for {
      containerName <- Seq("onion.Colls", "onion.Iterables")
      container <- load(containerName)
      method <- container.methods
      if Modifier.isStatic(method.modifier) && Modifier.isPublic(method.modifier) && method.arguments.nonEmpty
    } {
      val receiverName = method.arguments(0) match {
        case applied: AppliedClassType => applied.raw.name
        case ct: ClassType => ct.name
        case _: ArrayType => Typing.ArrayExtensionReceiver
        case _ => null
      }
      if (receiverName != null) {
        val extMethod = new ExtensionMethodDefinition(
          null,
          method.modifier,
          method.arguments(0),
          container,
          method.name,
          method.arguments.drop(1),
          method.returnType,
          null,
          method.typeParameters
        )
        registerExtensionMethod(receiverName, extMethod)
      }
    }
  }
  def newEnvironment(source: Seq[AST.CompilationUnit]) = new TypingEnvironment
  def processBody(source: Seq[AST.CompilationUnit], environment: TypingEnvironment): Seq[ClassDefinition] = {
    for(unit <- source) processHeader(unit)
    for(unit <- source) processOutline(unit)
    for(unit <- source) processTyping(unit)
    for(unit <- source) processDuplication(unit)
    diagnostics.finishOrThrow()
    table_.classes.values.toSeq
  }

  def processHeader(unit: AST.CompilationUnit): Unit = {
    val unitContext = session.activate(unit)
    new onion.compiler.typing.TypingHeaderPass(this, unitContext).run()
  }
  def processOutline(unit: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingOutlinePass(this, session.activate(unit)).run()
  def processTyping(unit: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingBodyPass(this, session.activate(unit)).run()

  // Typing body pass moved to onion.compiler.typing.TypingBodyPass


  def processDuplication(node: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingDuplicationPass(this, session.activate(node)).run()

  def withSuppressedReporting[A](block: => A): A = {
    val unitContext = currentUnitContext
    unitContext.reportingSuppressed += 1
    try block
    finally unitContext.reportingSuppressed -= 1
  }

  private[compiler] def reportingEnabled: Boolean = currentUnitContext.reportingSuppressed == 0

  def report(error: SemanticError, node: AST.Node, items: AnyRef*): Unit = {
    report(error, node.location, items*)
  }
  def report(error: SemanticError, location: Location, items: AnyRef*): Unit =
    diagnostics.report(error, location, items)

  def reportUnusedVariables(context: LocalContext): Unit =
    diagnostics.reportUnusedVariables(context)

  /**
   * Reports a variable shadowing warning if the given name shadows an outer variable.
   */
  def checkAndReportShadowing(name: String, location: Location, context: LocalContext): Unit =
    diagnostics.checkAndReportShadowing(name, location, context)

  def createFQCN(moduleName: String, simpleName: String): String =  (if (moduleName != null) moduleName + "." else "") + simpleName
  def load(name: String): Option[ClassType] = table_.load(name)
  /** Loads a class the compiler itself depends on; throws if the classpath is broken. */
  def loadRequired(name: String): ClassType = table_.loadRequired(name)
  /** The top-level container class; absent when the unit has only type declarations. */
  def loadTopClass: Option[ClassType] = table_.load(topClass)
  def loadArray(base: Type, dimension: Int): ArrayType = table_.loadArray(base, dimension)
  def rootClass: ClassType = table_.rootClass
  def problems: Array[CompileError] = diagnostics.problems
  def warnings: Seq[CompileWarning] = diagnostics.warnings
  def typedBindings: scala.collection.immutable.Map[AST.Node, TypedAST.Node] = session.global.bindings.allTypedBindings
  def sourceClasses: Array[ClassDefinition] = table_.classes.values.toArray
  def topClass: String = {
    val module = unit_.module
    val moduleName = if (module != null) module.name else null
    createName(moduleName, Paths.cutExtension(unit_.sourceFile) + "Main")
  }
  private[compiler] def put(astNode: AST.Node, kernelNode: Node): Unit = {
    session.global.bindings.bind(astNode, kernelNode)
  }
  private[compiler] def lookupAST(kernelNode: Node): Option[AST.Node] = session.global.bindings.astOf(kernelNode)
  private[compiler] def lookupKernelNode(astNode: AST.Node): Option[Node] = session.global.bindings.typedOf(astNode)
  /** Typed lookup: returns the typed node bound to `astNode` if it exists and has type `A`. */
  private[compiler] def kernelNodeOf[A <: Node : scala.reflect.ClassTag](astNode: AST.Node): Option[A] =
    lookupKernelNode(astNode).collect { case a: A => a }
  def typedNodeOf(astNode: AST.Node): Option[TypedAST.Node] = lookupKernelNode(astNode)
  private[compiler] def add(className: String, mapper: NameResolver): Unit = session.global.mappers(className) = mapper
  private[compiler] def find(className: String): Option[NameResolver] = session.global.mappers.get(className)
  // Extension method registration
  private[compiler] def registerExtensionDeclaration(decl: AST.ExtensionDeclaration, container: ClassDefinition): Unit = {
    session.global.extensions.registerDeclaration(decl, container)
  }
  private[compiler] def registerExtensionMethod(receiverFqcn: String, method: ExtensionMethodDefinition): Unit = {
    session.global.extensions.registerMethod(receiverFqcn, method)
  }
  private[compiler] def lookupExtensionMethods(receiverFqcn: String): Seq[ExtensionMethodDefinition] = {
    ensureBuiltinExtensions()
    session.global.extensions.methodsFor(receiverFqcn)
  }
  private def createName(moduleName: String, simpleName: String): String = (if (moduleName != null) moduleName + "." else "") + simpleName
  private def classpath(paths: Seq[String]): String = paths.foldLeft(new StringBuilder){(builder, path) => builder.append(Systems.pathSeparator).append(path)}.toString()
  private[compiler] def typesOf(arguments: List[AST.Argument]): Option[List[Type]] =
    typeSupport.typesOf(arguments)

  private[compiler] def mapFrom(typeNode: AST.TypeNode): Option[Type] =
    typeSupport.mapFrom(typeNode)

  private[compiler] def mapFrom(typeNode: AST.TypeNode, mapper: NameResolver): Option[Type] =
    typeSupport.mapFrom(typeNode, mapper)

  private[compiler] def openTypeParams[A](scope: TypeParamScope)(block: => A): A =
    typeSupport.openTypeParams(scope)(block)

  private[compiler] def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] =
    typeSupport.createTypeParams(nodes)

  // Accessors below read/write the *active* unit context (session.currentContext); they exist for
  // collaborators that must observe whichever unit is being processed at call time
  // (TypingTypeSupport, TypingDiagnostics, NameResolutionContext). Pass-local state is accessed
  // directly through each pass's TypingUnitContext instead.
  private[compiler] def table_ : ClassTable = session.global.table
  private[compiler] def mapper_ : NameResolver = currentUnitContext.currentMapper
  private[compiler] def unit_ : AST.CompilationUnit = currentUnitContext.unit
  private[compiler] def typeParams_ : TypeParamScope = currentUnitContext.currentTypeParams
  private[compiler] def setTypeParams(value: TypeParamScope): Unit = currentUnitContext.currentTypeParams = value
  private[compiler] def declaredTypeParams_ : collection.mutable.Map[AST.Node, Seq[TypeParam]] = session.global.declaredTypeParams
  private[compiler] def typeAliases_ : collection.mutable.Map[String, TypeAliasEntry] = session.global.typeAliases.entries
  private[compiler] def typeAliasResolutionStack_ : collection.mutable.Set[String] = session.global.typeAliases.resolutionStack
  private[compiler] def reporter_ : SemanticErrorReporter = diagnostics.reporter
  private[compiler] def warningReporter_ : WarningReporter = diagnostics.warningReporter

  private def currentUnitContext: TypingUnitContext = session.currentContext


}
