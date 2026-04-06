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
import _root_.onion.compiler.typing.{NameMapper, TypeAliasEntry, TypeParam, TypeParamScope, TypingDiagnostics, TypingTypeSupport}
import _root_.onion.compiler.TypedAST._
import _root_.onion.compiler.SemanticError._
import collection.mutable.{Buffer, HashMap, Map}

import scala.compiletime.uninitialized

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
 *   - [[NameMapper]]: Resolves type names to types
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
class Typing(config: CompilerConfig) extends AnyRef with Processor[Seq[AST.CompilationUnit], Seq[ClassDefinition]] {
  class TypingEnvironment
  private[compiler] val emptyTypeParams = TypeParamScope(Map.empty)

  private[compiler] def boxedClass(`type`: BasicType): ClassType =
    `type` match
      case BasicType.BOOLEAN => load("java.lang.Boolean")
      case BasicType.BYTE => load("java.lang.Byte")
      case BasicType.SHORT => load("java.lang.Short")
      case BasicType.CHAR => load("java.lang.Character")
      case BasicType.INT => load("java.lang.Integer")
      case BasicType.LONG => load("java.lang.Long")
      case BasicType.FLOAT => load("java.lang.Float")
      case BasicType.DOUBLE => load("java.lang.Double")
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
  private[compiler] val table_  = new ClassTable(classpath(config.classPath))
  private[compiler] val ast2ixt_ = Map[AST.Node, TypedAST.Node]()
  private[compiler] val ixt2ast_ = Map[TypedAST.Node, AST.Node]()
  private[compiler] val mappers_  = Map[String, NameMapper]()
  private[compiler] var access_ : Int = uninitialized
  private[compiler] var mapper_ : NameMapper = uninitialized
  private[compiler] var staticImportedList_ : StaticImportList = uninitialized
  private[compiler] var definition_ : ClassDefinition = uninitialized
  private[compiler] var unit_ : AST.CompilationUnit = uninitialized
  private[compiler] var typeParams_ : TypeParamScope = emptyTypeParams
  private[compiler] val declaredTypeParams_ : HashMap[AST.Node, Seq[TypeParam]] = HashMap()
  // Extension method support: maps extension declarations to their container classes
  private[compiler] val extensionDeclarations_ : Buffer[(AST.ExtensionDeclaration, ClassDefinition)] = Buffer()
  // Maps receiver type FQCN to extension methods - populated during OutlinePass
  private[compiler] val extensionMethods_ : HashMap[String, Buffer[ExtensionMethodDefinition]] = HashMap()
  // Type alias storage
  private[compiler] val typeAliases_ : HashMap[String, TypeAliasEntry] = HashMap()
  // Cyclic detection stack for type alias resolution
  private[compiler] val typeAliasResolutionStack_ : collection.mutable.Set[String] = collection.mutable.Set()
  private val diagnostics = new TypingDiagnostics(this, config)
  private val typeSupport = new TypingTypeSupport(this)
  private[compiler] val reporter_ : SemanticErrorReporter = diagnostics.reporter
  private[compiler] val warningReporter_ : WarningReporter = diagnostics.warningReporter
  private var suppressReporting: Int = 0
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
    new onion.compiler.typing.TypingHeaderPass(this, unit).run()
  }
  def processOutline(unit: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingOutlinePass(this, unit).run()
  def processTyping(unit: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingBodyPass(this, unit).run()

  // Typing body pass moved to onion.compiler.typing.TypingBodyPass


  def processDuplication(node: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingDuplicationPass(this, node).run()

  def withSuppressedReporting[A](block: => A): A = {
    suppressReporting += 1
    try block
    finally suppressReporting -= 1
  }

  private[compiler] def reportingEnabled: Boolean = suppressReporting == 0

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
  def load(name: String): ClassType = table_.load(name)
  /** Option-returning version of load for safer null handling */
  def loadOpt(name: String): Option[ClassType] = Option(table_.load(name))
  def loadTopClass: ClassType = table_.load(topClass)
  def loadArray(base: Type, dimension: Int): ArrayType = table_.loadArray(base, dimension)
  def rootClass: ClassType = table_.rootClass
  def problems: Array[CompileError] = diagnostics.problems
  def sourceClasses: Array[ClassDefinition] = table_.classes.values.toArray
  def topClass: String = {
    val module = unit_.module
    val moduleName = if (module != null) module.name else null
    createName(moduleName, Paths.cutExtension(unit_.sourceFile) + "Main")
  }
  private[compiler] def put(astNode: AST.Node, kernelNode: Node): Unit = {
    ast2ixt_(astNode) = kernelNode
    ixt2ast_(kernelNode) = astNode
  }
  private[compiler] def lookupAST(kernelNode: Node): AST.Node =  ixt2ast_.get(kernelNode).getOrElse(null)
  /** Option-returning version of lookupAST */
  private[compiler] def lookupASTOpt(kernelNode: Node): Option[AST.Node] = ixt2ast_.get(kernelNode)
  private[compiler] def lookupKernelNode(astNode: AST.Node): Node = ast2ixt_.get(astNode).getOrElse(null)
  /** Option-returning version of lookupKernelNode */
  private[compiler] def lookupKernelNodeOpt(astNode: AST.Node): Option[Node] = ast2ixt_.get(astNode)
  def typedNodeOf(astNode: AST.Node): Option[TypedAST.Node] = lookupKernelNodeOpt(astNode)
  private[compiler] def add(className: String, mapper: NameMapper): Unit = mappers_(className) = mapper
  private[compiler] def find(className: String): NameMapper = mappers_.get(className).getOrElse(null)
  // Extension method registration
  private[compiler] def registerExtensionDeclaration(decl: AST.ExtensionDeclaration, container: ClassDefinition): Unit = {
    extensionDeclarations_ += ((decl, container))
  }
  private[compiler] def registerExtensionMethod(receiverFqcn: String, method: ExtensionMethodDefinition): Unit = {
    extensionMethods_.getOrElseUpdate(receiverFqcn, Buffer()) += method
  }
  private[compiler] def lookupExtensionMethods(receiverFqcn: String): Seq[ExtensionMethodDefinition] = {
    extensionMethods_.getOrElse(receiverFqcn, Seq()).toSeq
  }
  /** Option-returning version of find */
  private[compiler] def findOpt(className: String): Option[NameMapper] = mappers_.get(className)
  private def createName(moduleName: String, simpleName: String): String = (if (moduleName != null) moduleName + "." else "") + simpleName
  private def classpath(paths: Seq[String]): String = paths.foldLeft(new StringBuilder){(builder, path) => builder.append(Systems.pathSeparator).append(path)}.toString()
  private[compiler] def typesOf(arguments: List[AST.Argument]): Option[List[Type]] =
    typeSupport.typesOf(arguments)

  private[compiler] def mapFrom(typeNode: AST.TypeNode): Type =
    typeSupport.mapFrom(typeNode)

  /** Option-returning version of mapFrom for safer null handling */
  private[compiler] def mapFromOpt(typeNode: AST.TypeNode): Option[Type] =
    typeSupport.mapFromOpt(typeNode)

  private[compiler] def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): Type =
    typeSupport.mapFrom(typeNode, mapper)

  /** Option-returning version of mapFrom for safer null handling */
  private[compiler] def mapFromOpt(typeNode: AST.TypeNode, mapper: NameMapper): Option[Type] =
    typeSupport.mapFromOpt(typeNode, mapper)

  private[compiler] def openTypeParams[A](scope: TypeParamScope)(block: => A): A =
    typeSupport.openTypeParams(scope)(block)

  private[compiler] def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] =
    typeSupport.createTypeParams(nodes)


}
