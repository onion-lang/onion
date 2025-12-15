package onion.compiler

import onion.compiler.AST.AccessSection

import _root_.scala.jdk.CollectionConverters._
import _root_.onion.compiler.toolbox.{Boxing, Classes, Paths, Systems}
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.TypedAST._
import _root_.onion.compiler.TypedAST.BinaryTerm.Constants._
import _root_.onion.compiler.TypedAST.UnaryTerm.Constants._
import _root_.onion.compiler.SemanticError._
import collection.mutable.{Stack, Buffer, Map, HashMap, Set => MutableSet}
import java.util.{TreeSet => JTreeSet}
import onion.compiler.generics.Erasure

import scala.compiletime.uninitialized

class Typing(config: CompilerConfig) extends AnyRef with Processor[Seq[AST.CompilationUnit], Seq[ClassDefinition]] {
  class TypingEnvironment
  private[compiler] case class TypeParam(name: String, variableType: TypedAST.TypeVariableType, upperBound: ClassType)
  private[compiler] case class TypeParamScope(params: Map[String, TypeParam]) {
    def get(name: String): Option[TypeParam] = params.get(name)
    def ++(ps: Seq[TypeParam]): TypeParamScope = copy(params ++ ps.map(p => p.name -> p))
  }
  private[compiler] val emptyTypeParams = TypeParamScope(Map.empty)
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
                if (!TypeRules.isSuperType(impl.arguments(i), specializedArgs(i))) {
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
              if (arg.isBasicType) {
                report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeNode, arg.name)
                return
              }
              if (!TypeRules.isAssignable(upper, arg)) {
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

  private[compiler] object TypeSubstitution {
    def classSubstitution(tp: Type): scala.collection.immutable.Map[String, Type] = tp match {
      case applied: TypedAST.AppliedClassType =>
        val rawParams = applied.raw.typeParameters
        val mapping = HashMap[String, Type]()
        var i = 0
        while (i < rawParams.length && i < applied.typeArguments.length) {
          mapping += rawParams(i).name -> applied.typeArguments(i)
          i += 1
        }
        mapping.toMap
      case _ =>
        scala.collection.immutable.Map.empty
    }

    def substituteType(
      tp: Type,
      classSubst: scala.collection.immutable.Map[String, Type],
      methodSubst: scala.collection.immutable.Map[String, Type],
      defaultToBound: Boolean
    ): Type = {
      def lookup(name: String): Option[Type] = methodSubst.get(name).orElse(classSubst.get(name))
      tp match {
        case tv: TypedAST.TypeVariableType =>
          lookup(tv.name).getOrElse(if (defaultToBound) tv.upperBound else tv)
        case applied: TypedAST.AppliedClassType =>
          val newArgs = applied.typeArguments.map(arg => substituteType(arg, classSubst, methodSubst, defaultToBound))
          if (newArgs.sameElements(applied.typeArguments)) applied
          else TypedAST.AppliedClassType(applied.raw, newArgs.toList)
        case at: ArrayType =>
          val newComponent = substituteType(at.component, classSubst, methodSubst, defaultToBound)
          if (newComponent eq at.component) at
          else loadArray(newComponent, at.dimension)
        case w: TypedAST.WildcardType =>
          val newUpper = substituteType(w.upperBound, classSubst, methodSubst, defaultToBound)
          val newLower = w.lowerBound.map(lb => substituteType(lb, classSubst, methodSubst, defaultToBound))
          if ((newUpper eq w.upperBound) && newLower == w.lowerBound) w
          else new TypedAST.WildcardType(newUpper, newLower)
        case other =>
          other
      }
    }
  }

  private[compiler] object GenericMethodTypeArguments {
    def infer(
      callNode: AST.Node,
      method: Method,
      args: Array[Term],
      classSubst: scala.collection.immutable.Map[String, Type]
    ): scala.collection.immutable.Map[String, Type] =
      infer(callNode, method, args, classSubst, null)

    def explicit(
      callNode: AST.Node,
      method: Method,
      typeArgs: List[AST.TypeNode],
      classSubst: scala.collection.immutable.Map[String, Type]
    ): Option[scala.collection.immutable.Map[String, Type]] = {
      val typeParams = method.typeParameters
      if (typeParams.isEmpty) {
        report(METHOD_NOT_GENERIC, callNode, method.affiliation.name, method.name)
        return None
      }
      if (typeParams.length != typeArgs.length) {
        report(
          METHOD_TYPE_ARGUMENT_ARITY_MISMATCH,
          callNode,
          method.affiliation.name,
          method.name,
          Integer.valueOf(typeParams.length),
          Integer.valueOf(typeArgs.length)
        )
        return None
      }

      val mappedArgs = new Array[Type](typeArgs.length)
      var i = 0
      while (i < typeArgs.length) {
        val mapped = mapFrom(typeArgs(i))
        if (mapped == null) return None
        if (mapped.isBasicType) {
          report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeArgs(i), mapped.name)
          return None
        }
        mappedArgs(i) = mapped
        i += 1
      }

      var subst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
      i = 0
      while (i < typeParams.length) {
        subst = subst.updated(typeParams(i).name, mappedArgs(i))
        i += 1
      }

      i = 0
      while (i < typeParams.length) {
        val upper0 = typeParams(i).upperBound.getOrElse(rootClass)
        val upper = TypeSubstitution.substituteType(upper0, classSubst, subst, defaultToBound = true)
        val arg = mappedArgs(i)
        if (!TypeRules.isAssignable(upper, arg)) {
          report(INCOMPATIBLE_TYPE, typeArgs(i), upper, arg)
          return None
        }
        i += 1
      }

      Some(subst)
    }

    def infer(
      callNode: AST.Node,
      method: Method,
      args: Array[Term],
      classSubst: scala.collection.immutable.Map[String, Type],
      expectedReturn: Type
    ): scala.collection.immutable.Map[String, Type] = {
      val typeParams = method.typeParameters
      if (typeParams.isEmpty) return scala.collection.immutable.Map.empty

      val bounds = HashMap[String, Type]()
      for (tp <- typeParams) {
        val upper = tp.upperBound.getOrElse(rootClass)
        bounds += tp.name -> TypeSubstitution.substituteType(upper, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
      }

      val inferred = HashMap[String, Type]()
      val upperConstraints = HashMap[String, Type]()
      val lowerConstraints = HashMap[String, Type]()
      val paramNames = typeParams.map(_.name).toSet

      def addUpper(name: String, bound: Type, position: AST.Node): Unit = {
        if (bound == null || bound.isNullType) return
        upperConstraints.get(name) match
          case None =>
            upperConstraints += name -> bound
          case Some(prev) =>
            if (TypeRules.isSuperType(prev, bound)) upperConstraints += name -> bound
            else if (TypeRules.isSuperType(bound, prev)) ()
            else report(INCOMPATIBLE_TYPE, position, prev, bound)
      }

      def addLower(name: String, bound: Type): Unit = {
        if (bound == null || bound.isNullType) return
        lowerConstraints.get(name) match
          case None =>
            lowerConstraints += name -> bound
          case Some(prev) =>
            if (TypeRules.isSuperType(prev, bound)) ()
            else if (TypeRules.isSuperType(bound, prev)) lowerConstraints += name -> bound
            else lowerConstraints += name -> rootClass
      }

      def unify(formal: Type, actual: Type, position: AST.Node): Unit = {
        if (actual.isNullType) return
        formal match {
          case w: TypedAST.WildcardType =>
            w.lowerBound match
              case Some(lb) =>
                lb match
                  case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                    addUpper(tv.name, actual, position)
                  case _ =>
                    unify(lb, actual, position)
              case None =>
                w.upperBound match
                  case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                    addLower(tv.name, actual)
                  case _ =>
                    unify(w.upperBound, actual, position)
          case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
            inferred.get(tv.name) match {
              case Some(prev) =>
                if (!(prev eq actual)) report(INCOMPATIBLE_TYPE, position, prev, actual)
              case None =>
                inferred += tv.name -> actual
            }
          case apf: TypedAST.AppliedClassType =>
            def unifyWithApplied(apa: TypedAST.AppliedClassType): Unit =
              if (apf.raw eq apa.raw) && apf.typeArguments.length == apa.typeArguments.length then
                var i = 0
                while (i < apf.typeArguments.length) {
                  unify(apf.typeArguments(i), apa.typeArguments(i), position)
                  i += 1
                }

            actual match
              case apa: TypedAST.AppliedClassType =>
                if (apf.raw eq apa.raw) then unifyWithApplied(apa)
                else
                  val views = AppliedTypeViews.collectAppliedViewsFrom(apa)
                  views.get(apf.raw) match
                    case Some(view) => unifyWithApplied(view)
                    case None =>
              case ct: ClassType =>
                val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
                views.get(apf.raw) match
                  case Some(view) => unifyWithApplied(view)
                  case None =>
              case _ =>
          case aft: ArrayType =>
            actual match {
              case aat: ArrayType if aft.dimension == aat.dimension =>
                unify(aft.component, aat.component, position)
              case _ =>
            }
          case _ =>
        }
      }

      val formalArgs = method.arguments.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
      var i = 0
      while (i < formalArgs.length && i < args.length) {
        unify(formalArgs(i), args(i).`type`, callNode)
        i += 1
      }

      if (expectedReturn != null) {
        val formalReturn =
          TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
        unify(formalReturn, expectedReturn, callNode)
      }

      for (tp <- typeParams) {
        val name = tp.name
        val bound0 = bounds(name)
        val bound =
          upperConstraints.get(name) match
            case None => bound0
            case Some(upper) =>
              if (TypeRules.isSuperType(bound0, upper)) upper
              else if (TypeRules.isSuperType(upper, bound0)) bound0
              else {
                report(INCOMPATIBLE_TYPE, callNode, bound0, upper)
                bound0
              }

        val inferredType0 =
          inferred.get(name)
            .orElse(lowerConstraints.get(name))
            .getOrElse(bound)

        val inferredType =
          if (!TypeRules.isAssignable(bound, inferredType0)) {
            report(INCOMPATIBLE_TYPE, callNode, bound, inferredType0)
            bound
          } else {
            inferredType0
          }

        inferred += name -> inferredType
      }

      inferred.toMap
    }
  }
  private[compiler] def createEquals(kind: Int, lhs: Term, rhs: Term): Term = {
    val params = Array[Term](new AsInstanceOf(rhs, rootClass))
    val target = lhs.`type`.asInstanceOf[ObjectType]
    val methods = target.findMethod("equals", params)
    var node: Term = new Call(lhs, methods(0), params)
    if (kind == BinaryTerm.Constants.NOT_EQUAL) {
      node = new UnaryTerm(NOT, BasicType.BOOLEAN, node)
    }
    node
  }
  private[compiler] def indexref(bind: ClosureLocalBinding, value: Term): Term =
    new RefArray(new RefLocal(bind), value)
  private[compiler] def assign(bind: ClosureLocalBinding, value: Term): ActionStatement =
    new ExpressionActionStatement(new SetLocal(bind, value))
  private[compiler] def ref(bind: ClosureLocalBinding): Term =
    new RefLocal(bind)
  private[compiler] def findMethod(node: AST.Node, target: ObjectType, name: String): Method =
    findMethod(node, target, name, new Array[Term](0))
  private[compiler] def findMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Method = {
    val methods = MethodResolution.findMethods(target, name, params)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, target, name, params.map{param => param.`type`})
      return null
    }
    methods(0)
  }
  private[compiler] object MemberAccess {
    private def hasSamePackage(a: ClassType, b: ClassType): Boolean = {
      var name1 = a.name
      var name2 = b.name
      var index: Int = 0
      index = name1.lastIndexOf(".")
      if (index >= 0) name1 = name1.substring(0, index)
      else name1 = ""
      index = name2.lastIndexOf(".")
      name2 = if (index >= 0) name2.substring(0, index) else ""
      name1 == name2
    }

    def isTypeAccessible(target: ClassType, context: ClassType): Boolean = {
      if (hasSamePackage(target, context)) true else (target.modifier & AST.M_INTERNAL) == 0
    }

    def isMemberAccessible(member: MemberRef, context: ClassType): Boolean = {
      val targetType = member.affiliation
      val modifier = member.modifier
      if (targetType == context) {
        true
      } else if (TypeRules.isSuperType(targetType, context)) {
        (modifier & AST.M_PROTECTED) != 0 || (modifier & AST.M_PUBLIC) != 0
      } else {
        (AST.M_PUBLIC & modifier) != 0
      }
    }

    def findField(target: ObjectType, name: String): FieldRef = {
      if (target == null) {
        null
      } else {
        val direct = target.field(name)
        if (direct != null) {
          direct
        } else {
          val fromSuper = findField(target.superClass, name)
          if (fromSuper != null) {
            fromSuper
          } else {
            target.interfaces.iterator
              .map(findField(_, name))
              .find(_ != null)
              .getOrElse(null)
          }
        }
      }
    }

    def ensureTypeAccessible(node: AST.Node, target: ObjectType, context: ClassType): Boolean = {
      if (target.isArrayType) {
        val component = target.asInstanceOf[ArrayType].component
        if (!component.isBasicType) {
          if (!isTypeAccessible(component.asInstanceOf[ClassType], definition_)) {
            report(CLASS_NOT_ACCESSIBLE, node, target, context)
            return false
          }
        }
      } else {
        if (!isTypeAccessible(target.asInstanceOf[ClassType], context)) {
          report(CLASS_NOT_ACCESSIBLE, node, target, context)
          return false
        }
      }
      true
    }
  }
  private[compiler] def hasNumericType(term: Term): Boolean = numeric(term.`type`)
  private[compiler] def numeric(symbol: Type): Boolean = {
    symbol.isBasicType && (symbol == BasicType.BYTE || symbol == BasicType.SHORT || symbol == BasicType.CHAR || symbol == BasicType.INT || symbol == BasicType.LONG || symbol == BasicType.FLOAT || symbol == BasicType.DOUBLE)
  }
  private def doCastInsertion(arguments: Array[Type], params: Array[Term]): Array[Term] = {
    for(i <- 0 until params.length) {
      if (arguments(i) != params(i).`type`) params(i) = new AsInstanceOf(params(i), arguments(i))
    }
    params
  }
  private[compiler] def types(terms: Array[Term]): Array[Type] = terms.map(term => term.`type`)
  private[compiler] def typeNames(types: Array[Type]): Array[String] = types.map(_.name)
  private[compiler] def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Continuable, Method] = {
    val methods = MethodResolution.findMethods(target, name, params)
    if (methods.length > 0) {
      if (methods.length > 1) {
        report(AMBIGUOUS_METHOD, node, Array[AnyRef](methods(0).affiliation, name, methods(0).arguments), Array[AnyRef](methods(1).affiliation, name, methods(1).arguments))
        Left(false)
      } else if (!MemberAccess.isMemberAccessible(methods(0), definition_)) {
        report(METHOD_NOT_ACCESSIBLE, node, methods(0).affiliation, name, methods(0).arguments, definition_)
        Left(false)
      } else {
        Right(methods(0))
      }
    }else {
      Left(true)
    }
  }

  private[compiler] object MethodResolution {
    def findMethods(target: ObjectType, name: String, params: Array[Term]): Array[Method] =
      target match
        case ct: ClassType =>
          val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
          if views.isEmpty then target.findMethod(name, params)
          else findMethodsWithViews(ct, name, params, views)
        case _ =>
          target.findMethod(name, params)

    private def findMethodsWithViews(
      target: ObjectType,
      name: String,
      params: Array[Term],
      views: scala.collection.immutable.Map[ClassType, TypedAST.AppliedClassType]
    ): Array[Method] =
      val candidates = new JTreeSet[Method](new MethodComparator)

      def collectMethods(tp: ObjectType): Unit =
        if tp == null then return
        tp.methods(name).foreach(candidates.add)
        collectMethods(tp.superClass)
        tp.interfaces.foreach(collectMethods)

      collectMethods(target)
      val specializedArgsCache = HashMap[Method, Array[Type]]()

      def ownerViewSubst(method: Method): scala.collection.immutable.Map[String, Type] =
        val owner0 = method.affiliation match
          case ap: TypedAST.AppliedClassType => ap.raw
          case ct: ClassType => ct
        views.get(owner0) match
          case Some(view) =>
            view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap
          case None =>
            scala.collection.immutable.Map.empty

      def specializedArgs(method: Method): Array[Type] =
        specializedArgsCache.getOrElseUpdate(
          method,
          method.arguments.map(tp => TypeSubstitution.substituteType(tp, ownerViewSubst(method), scala.collection.immutable.Map.empty, defaultToBound = true))
        )

      def applicable(method: Method): Boolean =
        val expected = specializedArgs(method)
        if expected.length != params.length then return false
        var i = 0
        while i < expected.length do
          if !TypeRules.isSuperType(expected(i), params(i).`type`) then return false
          i += 1
        true

      val applicableMethods = candidates.asScala.filter(applicable).toList
      if applicableMethods.isEmpty then return new Array[Method](0)
      if applicableMethods.length == 1 then return Array(applicableMethods.head)

      def isAllSuperType(a: Array[Type], b: Array[Type]): Boolean =
        var i = 0
        while i < a.length do
          if !TypeRules.isSuperType(a(i), b(i)) then return false
          i += 1
        true

      val sorter: java.util.Comparator[Method] = new java.util.Comparator[Method] {
        def compare(m1: Method, m2: Method): Int =
          val a1 = specializedArgs(m1)
          val a2 = specializedArgs(m2)
          if isAllSuperType(a2, a1) then -1
          else if isAllSuperType(a1, a2) then 1
          else 0
      }

      val selected = new java.util.ArrayList[Method]()
      selected.addAll(applicableMethods.asJava)
      java.util.Collections.sort(selected, sorter)
      if selected.size < 2 then
        selected.toArray(new Array[Method](0))
      else
        val m1 = selected.get(0)
        val m2 = selected.get(1)
        if sorter.compare(m1, m2) >= 0 then
          selected.toArray(new Array[Method](0))
        else
          Array[Method](m1)
  }

  private[compiler] object AppliedTypeViews {
    def collectAppliedViewsFrom(target: ClassType): scala.collection.immutable.Map[ClassType, TypedAST.AppliedClassType] =
      val views = HashMap[ClassType, TypedAST.AppliedClassType]()
      val visited = MutableSet[String]()

      def keyOf(ap: TypedAST.AppliedClassType): String =
        ap.raw.name + ap.typeArguments.map(_.name).mkString("[", ",", "]")

      def traverse(tp: ClassType, subst: scala.collection.immutable.Map[String, Type]): Unit =
        if tp == null then return
        tp match
          case ap: TypedAST.AppliedClassType =>
            val specializedArgs = ap.typeArguments.map(arg => TypeSubstitution.substituteType(arg, subst, scala.collection.immutable.Map.empty, defaultToBound = false))
            val specialized = TypedAST.AppliedClassType(ap.raw, specializedArgs.toList)
            val k = keyOf(specialized)
            if visited.contains(k) then return
            visited += k
            views += specialized.raw -> specialized
            val nextSubst: scala.collection.immutable.Map[String, Type] =
              specialized.raw.typeParameters.map(_.name).zip(specialized.typeArguments).toMap
            traverse(specialized.raw.superClass, nextSubst)
            specialized.raw.interfaces.foreach(traverse(_, nextSubst))
          case raw =>
            traverse(raw.superClass, subst)
            raw.interfaces.foreach(traverse(_, subst))

      traverse(target, scala.collection.immutable.Map.empty)
      views.toMap
  }
  private[compiler] def sameTypes(left: Array[Type], right: Array[Type]): Boolean = {
    if (left.length != right.length) return false
    (for (i <- 0 until left.length) yield (left(i), right(i))).forall { case (l, r) => l eq r }
  }
  private[compiler] def getter(name: String): String =
    "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private[compiler] def getterBoolean(name: String): String =
    "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private[compiler] def setter(name: String): String =
    "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private[compiler] def promote(left: Type, right: Type): Type = {
    if (!numeric(left) || !numeric(right)) return null
    if ((left eq BasicType.DOUBLE) || (right eq BasicType.DOUBLE)) {
      return BasicType.DOUBLE
    }
    if ((left eq BasicType.FLOAT) || (right eq BasicType.FLOAT)) {
      return BasicType.FLOAT
    }
    if ((left eq BasicType.LONG) || (right eq BasicType.LONG)) {
      return BasicType.LONG
    }
    BasicType.INT
  }
  private[compiler] def processNumericExpression(kind: Int, node: AST.BinaryExpression, lt: Term, rt: Term): Term = {
    var left = lt
    var right = rt
    if ((!hasNumericType(left)) || (!hasNumericType(right))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val resultType = promote(left.`type`, right.`type`)
    if (left.`type` != resultType) left = new AsInstanceOf(left, resultType)
    if (right.`type` != resultType) right = new AsInstanceOf(right, resultType)
    new BinaryTerm(kind, resultType, left, right)
  }
  private[compiler] def promoteInteger(typeRef: Type): Type = {
    if (typeRef == BasicType.BYTE || typeRef == BasicType.SHORT || typeRef == BasicType.CHAR || typeRef == BasicType.INT) {
      return BasicType.INT
    }
    if (typeRef == BasicType.LONG) {
      return BasicType.LONG
    }
    null
  }
  private[compiler] def addArgument(arg: AST.Argument, context: LocalContext): Type = {
    val name = arg.name
    val binding = context.lookupOnlyCurrentScope(name)
    if (binding != null) {
      report(DUPLICATE_LOCAL_VARIABLE, arg, name)
      return null
    }
    val argType = mapFrom(arg.typeRef, mapper_)
    if(argType == null) return null
    context.add(name, argType)
    argType
  }
}
