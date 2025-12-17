package onion.compiler

import _root_.scala.jdk.CollectionConverters._
import _root_.onion.compiler.toolbox.{Paths, Systems}
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.TypedAST._
import _root_.onion.compiler.SemanticError._
import collection.mutable.{Buffer, HashMap, Map, Set => MutableSet, Stack}
import onion.compiler.generics.Erasure
import onion.compiler.typing.{AppliedTypeViews, TypeSubstitution}

import scala.compiletime.uninitialized

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
      case unknown => throw new RuntimeException("Unknown type descriptor: " + unknown)
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
            var resolved: ClassType = null
            val it = imports.iterator
            while (resolved == null && it.hasNext) {
              it.next().matches(name) match {
                case Some(fqcn) =>
                  val mappedType = forName(fqcn, qualified = true)
                  if (mappedType != null) resolved = mappedType
                case None =>
              }
            }
            resolved
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
  private[compiler] val reporter_ : SemanticErrorReporter = new SemanticErrorReporter(config.maxErrorReports)
  def newEnvironment(source: Seq[AST.CompilationUnit]) = new TypingEnvironment
  def processBody(source: Seq[AST.CompilationUnit], environment: TypingEnvironment): Seq[ClassDefinition] = {
    for(unit <- source) processHeader(unit)
    for(unit <- source) processOutline(unit)
    for(unit <- source) processTyping(unit)
    for(unit <- source) processDuplication(unit)
    val problems = reporter_.getProblems
    if (problems.length > 0) throw new CompilationException(problems.toSeq)
    table_.classes.values.toSeq
  }

  def processHeader(unit: AST.CompilationUnit): Unit = {
    unit_ = unit
    val module = unit.module
    val moduleName = if (module != null) module.name else null
    val imports = Buffer[ImportItem](
      ImportItem("*", Seq("java", "lang", "*")),
      ImportItem("*", Seq("java", "io", "*")),
      ImportItem("*", Seq("java", "util", "*")),
      ImportItem("*", Seq("javax", "swing", "*")),
      ImportItem("*", Seq("java", "awt", "event", "*")),
      ImportItem("JByte", Seq("java", "lang", "Byte")),
      ImportItem("JShort", Seq("java", "lang", "Short")),
      ImportItem("JCharacter", Seq("java", "lang", "Character")),
      ImportItem("JInteger", Seq("java", "lang", "Integer")),
      ImportItem("JLong", Seq("java", "lang", "Long")),
      ImportItem("JFloat", Seq("java", "lang", "Float")),
      ImportItem("JDouble", Seq("java", "lang", "Double")),
      ImportItem("JBoolean", Seq("java", "lang", "Boolean")),
      ImportItem("*", Seq("onion", "*")),
      ImportItem("*", if (moduleName != null) moduleName.split("\\.").toIndexedSeq.appended("*")else Seq("*"))
    )
    if(unit.imports != null) {
      for((key, value) <- unit.imports.mapping) {
        imports.append(ImportItem(key, value.split("\\.").toIndexedSeq))
      }
    }
    val staticList = new StaticImportList
    staticList.add(new StaticImportItem("java.lang.System", true))
    staticList.add(new StaticImportItem("java.lang.Runtime", true))
    staticList.add(new StaticImportItem("java.lang.Math", true))
    staticImportedList_ = staticList
    var count = 0
    for(top <- unit.toplevels) top match {
      case declaration: AST.ClassDeclaration =>
        val node = ClassDefinition.newClass(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null, null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
        }else {
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(imports.toSeq))
        }
      case declaration: AST.InterfaceDeclaration =>
        val node = ClassDefinition.newInterface(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
        }else{
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(imports.toSeq))
        }
      case otherwise =>
        count += 1
    }
    if (count > 0) {
      val node = ClassDefinition.newClass(unit.location, 0, topClass, table_.rootClass, new Array[ClassType](0))
      node.setSourceFile(Paths.nameOf(unit_.sourceFile))
      node.setResolutionComplete(true)
      table_.classes.add(node)
      node.addDefaultConstructor
      put(unit, node)
      add(node.name, new NameMapper(imports.toSeq))
    }
  }
  def processOutline(unit: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingOutlinePass(this, unit).run()
  def processTyping(unit: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingBodyPass(this, unit).run()

  // Typing body pass moved to onion.compiler.typing.TypingBodyPass


  def processDuplication(node: AST.CompilationUnit): Unit =
    new onion.compiler.typing.TypingDuplicationPass(this, node).run()

  private[compiler] object DuplicationChecks {
    private val emptyMethodSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty

    private def erasedMethodDesc(method: Method): String =
      Erasure.methodDescriptor(method.returnType, method.arguments)

    private def erasedParamDescriptor(args: Array[Type]): String =
      args.map(Erasure.asmType).map(_.getDescriptor).mkString("(", "", ")")

    def checkOverrideContracts(clazz: ClassDefinition, fallback: Location): Unit =
      if clazz.isInterface then return
      val views = AppliedTypeViews.collectAppliedViewsFrom(clazz)
      if views.isEmpty then return

      val implByErasedParams: scala.collection.immutable.Map[(String, String), Method] =
        clazz.methods
          .filter(m => !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier))
          .map(m => ((m.name, erasedParamDescriptor(m.arguments)), m))
          .toMap

      for (view <- views.values) {
        val viewSubst: scala.collection.immutable.Map[String, Type] =
          view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap

        for (contract <- view.raw.methods) {
          if !Modifier.isStatic(contract.modifier) && !Modifier.isPrivate(contract.modifier) then
            val key = (contract.name, erasedParamDescriptor(contract.arguments))
            implByErasedParams.get(key).foreach { impl =>
              val specializedArgs = contract.arguments.map(tp => TypeSubstitution.substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
              val specializedRet = TypeSubstitution.substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)

              val implAst = lookupAST(impl.asInstanceOf[Node])
              val location = if implAst != null then implAst.location else fallback

              var i = 0
              while (i < specializedArgs.length && i < impl.arguments.length) {
                val arg = specializedArgs(i)
                val checkedArg = if (!impl.arguments(i).isBasicType && arg.isBasicType) boxedTypeArgument(arg) else arg
                if (!TypeRules.isSuperType(impl.arguments(i), checkedArg)) {
                  report(INCOMPATIBLE_TYPE, location, specializedArgs(i), impl.arguments(i))
                }
                i += 1
              }

              if (!TypeRules.isAssignable(specializedRet, impl.returnType)) {
                report(INCOMPATIBLE_TYPE, location, specializedRet, impl.returnType)
              }
            }
        }
      }

    def checkErasureSignatureCollisions(clazz: ClassDefinition, fallback: Location): Unit =
      val seen = HashMap[(String, String), Method]()
      for m <- clazz.methods do
        val key = (m.name, erasedMethodDesc(m))
        if seen.contains(key) then
          val ast = lookupAST(m.asInstanceOf[Node])
          val location = if ast != null then ast.location else fallback
          report(ERASURE_SIGNATURE_COLLISION, location, clazz, m.name, key._2)
        else
          seen(key) = m
  }

  def report(error: SemanticError, node: AST.Node, items: AnyRef*): Unit = {
    report(error, node.location, items*)
  }
  def report(error: SemanticError, location: Location, items: AnyRef*): Unit = {
    def report_(items: Array[AnyRef]): Unit = {
      reporter_.setSourceFile(unit_.sourceFile)
      reporter_.report(error, location, items)
    }
    report_(items.toArray)
  }
  def createFQCN(moduleName: String, simpleName: String): String =  (if (moduleName != null) moduleName + "." else "") + simpleName
  def load(name: String): ClassType = table_.load(name)
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
  private[compiler] def lookupKernelNode(astNode: AST.Node): Node = ast2ixt_.get(astNode).getOrElse(null)
  private[compiler] def add(className: String, mapper: NameMapper): Unit = mappers_(className) = mapper
  private[compiler] def find(className: String): NameMapper = mappers_.get(className).getOrElse(null)
  private def createName(moduleName: String, simpleName: String): String = (if (moduleName != null) moduleName + "." else "") + simpleName
  private def classpath(paths: Seq[String]): String = paths.foldLeft(new StringBuilder){(builder, path) => builder.append(Systems.pathSeparator).append(path)}.toString()
  private[compiler] def typesOf(arguments: List[AST.Argument]): Option[List[Type]] = {
    val result = arguments.map{arg => mapFrom(arg.typeRef)}
    if(result.forall(_ != null)) Some(result) else None
  }
  private[compiler] def mapFrom(typeNode: AST.TypeNode): Type = mapFrom(typeNode, mapper_)
  private[compiler] def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): Type = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString)
    else validateTypeApplication(typeNode, mappedType)
    mappedType
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
            var i = 0
            while (i < rawParams.length) {
              val upper = rawParams(i).upperBound.getOrElse(rootClass)
              val arg = applied.typeArguments(i)
              if (arg eq BasicType.VOID) {
                report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeNode, arg.name)
                return
              }
              val checkedArg = boxedTypeArgument(arg)
              if (!TypeRules.isAssignable(upper, checkedArg)) {
                report(INCOMPATIBLE_TYPE, typeNode, upper, arg)
                return
              }
              i += 1
            }
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
