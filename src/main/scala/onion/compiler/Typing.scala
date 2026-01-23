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
import _root_.onion.compiler.TypedAST._
import _root_.onion.compiler.SemanticError._
import collection.mutable.{Buffer, HashMap, Map, Set => MutableSet}

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
  private[compiler] case class TypeParam(name: String, variableType: TypedAST.TypeVariableType, upperBound: ClassType)
  private[compiler] case class TypeParamScope(params: Map[String, TypeParam]) {
    def get(name: String): Option[TypeParam] = params.get(name)
    def ++(ps: Seq[TypeParam]): TypeParamScope = copy(params ++ ps.map(p => p.name -> p))
  }
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
  private def split(descriptor: AST.TypeDescriptor): (AST.TypeDescriptor, Dimension) = {
    def loop(target: AST.TypeDescriptor, dimension: Int): (AST.TypeDescriptor, Int) = target match {
      case AST.ArrayType(component) => loop(component, dimension + 1)
      case otherwise => (otherwise, dimension)
    }
    loop(descriptor, 0)
  }
  private[compiler] class NameMapper(imports: Seq[ImportItem]) {
    def resolveNode(typeNode: AST.TypeNode): Type = map(typeNode.desc)

    /** Get candidate class names for suggestions (non-on-demand imports only) */
    def getCandidateClassNames: Array[String] = {
      val localClasses = table_.classes.values.map(_.name).toSeq
      val importedClasses = imports.filterNot(_.isOnDemand).map(_.simpleName)
      (localClasses ++ importedClasses).distinct.toArray
    }
    def map(descriptor : AST.TypeDescriptor): Type = descriptor match {
      case AST.PrimitiveType(AST.KChar)       => BasicType.CHAR
      case AST.PrimitiveType(AST.KByte)       => BasicType.BYTE
      case AST.PrimitiveType(AST.KShort)      => BasicType.SHORT
      case AST.PrimitiveType(AST.KInt)        => BasicType.INT
      case AST.PrimitiveType(AST.KLong)       => BasicType.LONG
      case AST.PrimitiveType(AST.KFloat)      => BasicType.FLOAT
      case AST.PrimitiveType(AST.KDouble)     => BasicType.DOUBLE
      case AST.PrimitiveType(AST.KBoolean)    => BasicType.BOOLEAN
      case AST.PrimitiveType(AST.KVoid)       => BasicType.VOID
      case AST.ReferenceType(name, qualified) => forName(name, qualified)
      case AST.ParameterizedType(base, params) =>
        val raw = map(base)
        if (raw == null) return null
        raw match {
          case clazz: ClassType =>
            val mappedArgs = params.map(map)
            if (mappedArgs.exists(_ == null)) return null
            TypedAST.AppliedClassType(clazz, mappedArgs)
          case _ =>
            raw
        }
      case AST.FunctionType(params, result) =>
        val mappedParams = params.map(map)
        val mappedResult = map(result)
        if (mappedParams.exists(_ == null) || mappedResult == null) return null
        val arity = mappedParams.length
        val functionType = table_.load(s"onion.Function$arity")
        if (functionType == null) return null
        TypedAST.AppliedClassType(functionType, (mappedParams :+ mappedResult).toList)
      case AST.ArrayType(component)           =>  val (base, dimension) = split(descriptor); table_.loadArray(map(base), dimension)
      case AST.WildcardType(upperBound, lowerBound) =>
        val mappedUpper = upperBound.map(map).getOrElse(rootClass)
        val mappedLower = lowerBound.map(map)
        new TypedAST.WildcardType(mappedUpper, mappedLower)
      case AST.NullableType(inner) =>
        val mappedInner = map(inner)
        if (mappedInner == null) null
        else new TypedAST.NullableType(mappedInner)
      case _ => null
    }
    private def forName(name: String, qualified: Boolean): ClassType = {
      if (qualified) {
        table_.load(name)
      } else {
        typeParams_.get(name).map(_.variableType).getOrElse {
          val module = unit_.module
          val moduleName = if (module != null) module.name else null
          val local = table_.lookup(createFQCN(moduleName, name))
          if (local != null) {
            local
          } else {
            imports.iterator
              .flatMap(_.matches(name))
              .map(fqcn => forName(fqcn, qualified = true))
              .find(_ != null)
              .orNull
          }
        }
      }
    }
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
  private[compiler] val reporter_ : SemanticErrorReporter = new SemanticErrorReporter(config.maxErrorReports)
  private[compiler] val warningReporter_ : WarningReporter = new WarningReporter(config.warningLevel, config.suppressedWarnings)
  private var suppressReporting: Int = 0
  def newEnvironment(source: Seq[AST.CompilationUnit]) = new TypingEnvironment
  def processBody(source: Seq[AST.CompilationUnit], environment: TypingEnvironment): Seq[ClassDefinition] = {
    for(unit <- source) processHeader(unit)
    for(unit <- source) processOutline(unit)
    for(unit <- source) processTyping(unit)
    for(unit <- source) processDuplication(unit)
    val problems = reporter_.getProblems
    if (problems.length > 0) throw new CompilationException(problems.toSeq)

    // Print warnings
    warningReporter_.printWarnings()

    // If warnings are treated as errors and there are warnings, fail compilation
    if (warningReporter_.treatAsErrors && warningReporter_.hasWarnings) {
      throw new CompilationException(Seq(
        CompileError("", null, s"${warningReporter_.warningCount} warning(s) treated as errors")
      ))
    }

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

  private def reportingEnabled: Boolean = suppressReporting == 0

  def report(error: SemanticError, node: AST.Node, items: AnyRef*): Unit = {
    report(error, node.location, items*)
  }
  def report(error: SemanticError, location: Location, items: AnyRef*): Unit = {
    if (!reportingEnabled) return
    def report_(items: Array[AnyRef]): Unit = {
      reporter_.setSourceFile(unit_.sourceFile)
      reporter_.report(error, location, items)
    }
    report_(items.toArray)
  }

  def reportUnusedVariables(context: LocalContext): Unit = {
    if (!reportingEnabled) return
    warningReporter_.setSourceFile(unit_.sourceFile)
    for (v <- context.unusedLocalVariables) {
      warningReporter_.unusedVariable(v.location, v.name)
    }
    for (p <- context.unusedParameters) {
      warningReporter_.unusedParameter(p.location, p.name)
    }
  }

  /**
   * Reports a variable shadowing warning if the given name shadows an outer variable.
   */
  def checkAndReportShadowing(name: String, location: Location, context: LocalContext): Unit = {
    if (!reportingEnabled) return
    // Skip synthetic/generated names
    if (name.startsWith("symbol#") || name.startsWith("$")) return

    warningReporter_.setSourceFile(unit_.sourceFile)
    context.checkShadowing(name) match {
      case Some(originalLocation) =>
        warningReporter_.shadowedVariable(location, name, originalLocation)
      case None => // No shadowing
    }
  }

  def createFQCN(moduleName: String, simpleName: String): String =  (if (moduleName != null) moduleName + "." else "") + simpleName
  def load(name: String): ClassType = table_.load(name)
  /** Option-returning version of load for safer null handling */
  def loadOpt(name: String): Option[ClassType] = Option(table_.load(name))
  def loadTopClass: ClassType = table_.load(topClass)
  def loadArray(base: Type, dimension: Int): ArrayType = table_.loadArray(base, dimension)
  def rootClass: ClassType = table_.rootClass
  def problems: Array[CompileError] = reporter_.getProblems
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
  private[compiler] def typesOf(arguments: List[AST.Argument]): Option[List[Type]] = {
    val result = arguments.map { arg =>
      val baseType = mapFrom(arg.typeRef)
      if (baseType != null && arg.isVararg) {
        // Convert vararg type to array type
        table_.loadArray(baseType, 1)
      } else {
        baseType
      }
    }
    if(result.forall(_ != null)) Some(result) else None
  }
  private[compiler] def mapFrom(typeNode: AST.TypeNode): Type = mapFrom(typeNode, mapper_)
  /** Option-returning version of mapFrom for safer null handling */
  private[compiler] def mapFromOpt(typeNode: AST.TypeNode): Option[Type] = mapFromOpt(typeNode, mapper_)
  private[compiler] def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): Type = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString, mapper.getCandidateClassNames)
    else validateTypeApplication(typeNode, mappedType)
    mappedType
  }
  /** Option-returning version of mapFrom for safer null handling */
  private[compiler] def mapFromOpt(typeNode: AST.TypeNode, mapper: NameMapper): Option[Type] = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) {
      report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString, mapper.getCandidateClassNames)
      None
    } else {
      validateTypeApplication(typeNode, mappedType)
      Some(mappedType)
    }
  }

  private def validateTypeApplication(typeNode: AST.TypeNode, mappedType: Type): Unit = {
    typeNode.desc match {
      case AST.ParameterizedType(_, _) | AST.FunctionType(_, _) =>
        mappedType match {
          case applied: TypedAST.AppliedClassType =>
            val rawParams = applied.raw.typeParameters
            if (rawParams.isEmpty) {
              report(TYPE_NOT_GENERIC, typeNode, applied.raw.name)
              return
            }
            if (rawParams.length != applied.typeArguments.length) {
              report(TYPE_ARGUMENT_ARITY_MISMATCH, typeNode, applied.raw.name, Integer.valueOf(rawParams.length), Integer.valueOf(applied.typeArguments.length))
              return
            }
            val hasError = rawParams.indices.exists { i =>
              val upper = rawParams(i).upperBound.getOrElse(rootClass)
              val arg = applied.typeArguments(i)
              if (arg eq BasicType.VOID) {
                report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeNode, arg.name)
                true
              } else {
                arg match {
                  case w: TypedAST.WildcardType =>
                    // For wildcards, check that the wildcard's upper bound is assignable to the type param's upper bound
                    val wildcardUpper = w.upperBound
                    if (!TypeRules.isAssignable(upper, wildcardUpper)) {
                      report(INCOMPATIBLE_TYPE, typeNode, upper, wildcardUpper)
                      true
                    } else false
                  case _ =>
                    val checkedArg = boxedTypeArgument(arg)
                    if (!TypeRules.isAssignable(upper, checkedArg)) {
                      report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                      true
                    } else false
                }
              }
            }
            if (hasError) return
          case _ =>
        }
      case _ =>
    }
  }

  private[compiler] def openTypeParams[A](scope: TypeParamScope)(block: => A): A = {
    val prev = typeParams_
    typeParams_ = scope
    try block
    finally typeParams_ = prev
  }

  private[compiler] def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] = {
    val seen = MutableSet[String]()
    val result = Buffer[TypeParam]()
    for (tp <- nodes) {
      if (seen.contains(tp.name)) {
        report(DUPLICATE_TYPE_PARAMETER, tp, tp.name)
      } else {
        seen += tp.name
        val upper = tp.upperBound match {
          case Some(boundNode) =>
            val mapped = mapFrom(boundNode)
            mapped match {
              case ct: ClassType => ct
              case _ =>
                report(INCOMPATIBLE_TYPE, boundNode, rootClass, mapped)
                rootClass
            }
          case None =>
            rootClass
        }
        val variableType = new TypedAST.TypeVariableType(tp.name, upper)
        result += TypeParam(tp.name, variableType, upper)
      }
    }
    result.toSeq
  }


}
