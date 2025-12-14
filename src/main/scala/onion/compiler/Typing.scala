package onion.compiler

import onion.compiler.AST.AccessSection

import _root_.scala.jdk.CollectionConverters._
import _root_.onion.compiler.toolbox.{Boxing, Classes, Paths, Systems}
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.IRT._
import _root_.onion.compiler.IRT.BinaryTerm.Constants._
import _root_.onion.compiler.IRT.UnaryTerm.Constants._
import _root_.onion.compiler.SemanticError._
import collection.mutable.{Stack, Buffer, Map, HashMap, Set => MutableSet}
import java.util.{Arrays, TreeSet => JTreeSet}
import onion.compiler.generics.Erasure

class Typing(config: CompilerConfig) extends AnyRef with Processor[Seq[AST.CompilationUnit], Seq[ClassDefinition]] {
  class TypingEnvironment
  private case class TypeParam(name: String, variableType: TypedAST.TypeVariableType, upperBound: ClassType)
  private case class TypeParamScope(params: Map[String, TypeParam]) {
    def get(name: String): Option[TypeParam] = params.get(name)
    def ++(ps: Seq[TypeParam]): TypeParamScope = copy(params ++ ps.map(p => p.name -> p))
  }
  private val emptyTypeParams = TypeParamScope(Map.empty)
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
  private class NameMapper(imports: Seq[ImportItem]) {
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
      case AST.ArrayType(component)           =>  val (base, dimension) = split(descriptor); table_.loadArray(map(base), dimension)
      case unknown => throw new RuntimeException("Unknown type descriptor: " + unknown)
    }
    private def forName(name: String, qualified: Boolean): ClassType = {
      if(qualified) {
        table_.load(name)
      }else {
        typeParams_.get(name).map(_.variableType).getOrElse {
          val module = unit_.module
          val moduleName = if (module != null) module.name else null
          val local = table_.lookup(createFQCN(moduleName, name))
          if (local != null) return local
          for(item <- imports) {
            val qname = item matches name
            if(qname.isDefined) {
              val mappedType = forName(qname.get, true)
              if(mappedType != null) return mappedType
            }
          }
          null
        }
      }
    }
  }
  private val table_  = new ClassTable(classpath(config.classPath))
  private val ast2ixt_ = Map[AST.Node, IRT.Node]()
  private val ixt2ast_ = Map[IRT.Node, AST.Node]()
  private val mappers_  = Map[String, NameMapper]()
  private var access_ : Int = _
  private var mapper_ : NameMapper = _
  private var staticImportedList_ : StaticImportList = _
  private var definition_ : ClassDefinition = _
  private var unit_ : AST.CompilationUnit = _
  private var typeParams_ : TypeParamScope = emptyTypeParams
  private val declaredTypeParams_ : HashMap[AST.Node, Seq[TypeParam]] = HashMap()
  private val reporter_ : SemanticErrorReporter = new SemanticErrorReporter(config.maxErrorReports)
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
  def processOutline(unit: AST.CompilationUnit): Unit = {
    var nconstructors = 0
    unit_ = unit
    def processClassDeclaration(node: AST.ClassDeclaration): Unit = {
      nconstructors = 0
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
      val classTypeParams = createTypeParams(node.typeParameters)
      declaredTypeParams_(node) = classTypeParams
      definition_.setTypeParameters(classTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray)
      constructTypeHierarchy(definition_, MutableSet[ClassType]())
      if (cyclic(definition_)) report(CYCLIC_INHERITANCE, node, definition_.name)
      openTypeParams(emptyTypeParams ++ classTypeParams) {
        for(defaultSection <- node.defaultSection) {
          access_ = defaultSection.modifiers
          for(member <- defaultSection.members) member match {
            case node: AST.FieldDeclaration => processFieldDeclaration(node)
            case node: AST.MethodDeclaration => processMethodDeclaration(node)
            case node: AST.ConstructorDeclaration => processConstructorDeclaration(node)
            case node: AST.DelegatedFieldDeclaration => processDelegatedFieldDeclaration(node)
          }
        }
        for(section <- node.sections; member <- section.members) {
          access_ = section.modifiers
          member match {
            case node: AST.FieldDeclaration => processFieldDeclaration(node)
            case node: AST.MethodDeclaration => processMethodDeclaration(node)
            case node: AST.ConstructorDeclaration => processConstructorDeclaration(node)
            case node: AST.DelegatedFieldDeclaration => processDelegatedFieldDeclaration(node)
          }
        }
      }
      if (nconstructors == 0) definition_.addDefaultConstructor
    }
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration): Unit = {
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
      val interfaceTypeParams = createTypeParams(node.typeParameters)
      declaredTypeParams_(node) = interfaceTypeParams
      definition_.setTypeParameters(interfaceTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray)
      constructTypeHierarchy(definition_, MutableSet[ClassType]())
      if (cyclic(definition_)) report(CYCLIC_INHERITANCE, node, definition_.name)
      openTypeParams(emptyTypeParams ++ interfaceTypeParams) {
        for(method <- node.methods) processInterfaceMethodDeclaration(method)
      }
    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration): Unit = {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return
      val modifier = node.modifiers | AST.M_PUBLIC
      val classType = loadTopClass.asInstanceOf[ClassDefinition]
      val name = node.name
      val field = new FieldDefinition(node.location, modifier, classType, name, typeRef)
      put(node, field)
      classType.add(field)
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration): Unit = {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if(node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
      for(args <- argsOption; returnType <- returnTypeOption) {
        val classType= loadTopClass.asInstanceOf[ClassDefinition]
        val modifier = node.modifiers | AST.M_PUBLIC
        val name = node.name
        val method = new MethodDefinition(node.location, modifier, classType, name, args.toArray, returnType, null)
        put(node, method)
        classType.add(method)
      }
    }
    def processFieldDeclaration(node: AST.FieldDeclaration): Unit = {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return; val modifier = node.modifiers | access_
      val name = node.name
      val field = new FieldDefinition(node.location, modifier, definition_, name, typeRef)
      put(node, field)
      definition_.add(field)
    }
    def processMethodDeclaration(node: AST.MethodDeclaration): Unit = {
      val methodTypeParams = createTypeParams(node.typeParameters)
      declaredTypeParams_(node) = methodTypeParams
      openTypeParams(typeParams_ ++ methodTypeParams) {
        val argsOption = typesOf(node.args)
        val returnTypeOption = Option(if (node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
        for(args <- argsOption; returnType <- returnTypeOption) {
          var modifier = node.modifiers | access_
          if (node.block == null) modifier |= AST.M_ABSTRACT
          val name = node.name
          val method = new MethodDefinition(
            node.location,
            modifier,
            definition_,
            name,
            args.toArray,
            returnType,
            null,
            methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray
          )
          put(node, method)
          definition_.add(method)
        }
      }
    }
    def processInterfaceMethodDeclaration(node: AST.MethodDeclaration): Unit = {
      val methodTypeParams = createTypeParams(node.typeParameters)
      declaredTypeParams_(node) = methodTypeParams
      openTypeParams(typeParams_ ++ methodTypeParams) {
        val argsOption = typesOf(node.args)
        val returnTypeOption = Option(if(node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
        for(args <- argsOption; returnType <- returnTypeOption) {
          val modifier = AST.M_PUBLIC | AST.M_ABSTRACT
          val name = node.name
          val method = new MethodDefinition(
            node.location,
            modifier,
            definition_,
            name,
            args.toArray,
            returnType,
            null,
            methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray
          )
          put(node, method)
          definition_.add(method)
        }
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit = {
      nconstructors += 1
      val argsOption = typesOf(node.args)
      for(args <- argsOption) {
        val modifier = node.modifiers | access_
        val constructor = new ConstructorDefinition(node.location, modifier, definition_, args.toArray, null, null)
        put(node, constructor)
        definition_.add(constructor)
      }
    }
    def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration): Unit = {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return
      if (!(typeRef.isObjectType && (typeRef.asInstanceOf[ObjectType]).isInterface)) {
        report(INTERFACE_REQUIRED, node, typeRef)
        return
      }
      val modifier = node.modifiers | access_ | AST.M_FORWARDED
      var name = node.name
      var field = new FieldDefinition(node.location, modifier, definition_, name, typeRef)
      put(node, field)
      definition_.add(field)
    }
    def cyclic(start: ClassDefinition): Boolean = {
      def loop(node: ClassType, visit: Set[ClassType]): Boolean = {
        if(node == null) return false
        if(visit.contains(node)) return true
        val newVisit = visit + node
        if(loop(node.superClass, newVisit)) return true
        for(interface <- node.interfaces) if(loop(interface, newVisit)) return true
        false
      }
      loop(start, Set[ClassType]())
    }
    def validateSuperType(node: AST.TypeNode, mustBeInterface: Boolean, mapper: NameMapper): ClassType = {
      if (node == null) {
        return if (mustBeInterface) null else table_.rootClass
      }
      val mapped = mapFrom(node, mapper)
      if (mapped == null) return if (mustBeInterface) null else table_.rootClass
      val typeRef = mapped match {
        case ct: ClassType => ct
        case _ =>
          report(INCOMPATIBLE_TYPE, node, table_.rootClass, mapped)
          return if (mustBeInterface) null else table_.rootClass
      }
      val isInterface = typeRef.isInterface
      if (((!isInterface) && mustBeInterface) || (isInterface && (!mustBeInterface))) {
        var location: Location = null
        if (typeRef.isInstanceOf[ClassDefinition]) {
          location = typeRef.asInstanceOf[ClassDefinition].location
        }
        report(ILLEGAL_INHERITANCE, location, typeRef.name)
      }
      typeRef
    }
    def constructTypeHierarchy(node: ClassType, visit: MutableSet[ClassType]): Unit = {
      if(node == null || visit.contains(node)) return
      visit += node
      node match {
        case node: ClassDefinition =>
          if (node.isResolutionComplete) return
          val interfaces = Buffer[ClassType]()
          val resolver = find(node.name)
          var superClass: ClassType = null
          if (node.isInterface) {
            val ast = lookupAST(node).asInstanceOf[AST.InterfaceDeclaration]
            superClass = rootClass
            for (typeSpec <- ast.superInterfaces) {
              val superType = validateSuperType(typeSpec, true, resolver)
              if (superType != null) interfaces += superType
            }
          }else {
            val ast = lookupAST(node).asInstanceOf[AST.ClassDeclaration]
            superClass = validateSuperType(ast.superClass, false, resolver)
            for (typeSpec <- ast.superInterfaces) {
              var superType = validateSuperType(typeSpec, true, resolver)
              if (superType != null) interfaces += superType
            }
          }
          constructTypeHierarchy(superClass, visit)
          for (superType <- interfaces)  constructTypeHierarchy(superType, visit)
          node.setSuperClass(superClass)
          node.setInterfaces(interfaces.toArray)
          node.setResolutionComplete(true)
        case _ =>
          constructTypeHierarchy(node.superClass, visit)
          for (interface<- node.interfaces)  constructTypeHierarchy(interface, visit)
      }
    }
    for(top <- unit.toplevels) {
      mapper_ = find(topClass)
      top match {
        case node : AST.ClassDeclaration => processClassDeclaration(node)
        case node : AST.InterfaceDeclaration => processInterfaceDeclaration(node)
        case node : AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node)
        case node : AST.FunctionDeclaration => processFunctionDeclaration(node)
        case _ =>
      }
    }
  }
  def processTyping(node: AST.CompilationUnit): Unit = {
    def processNodes(nodes: Array[AST.Expression], typeRef: Type, bind: ClosureLocalBinding, context: LocalContext): Term = {
      val expressions = new Array[Term](nodes.length)
      var error: Boolean = false
      for(i <- 0 until nodes.length){
        val expressionOpt = typed(nodes(i), context)
        expressions(i) = expressionOpt.getOrElse(null)
        if(expressions(i) == null) {
          error = true
        } else if (!TypeRules.isAssignable(typeRef, expressions(i).`type`)) {
          report(INCOMPATIBLE_TYPE, nodes(i), typeRef, expressions(i).`type`)
          error = true
        } else {
          if (expressions(i).isBasicType && expressions(i).`type` != typeRef) expressions(i) = new AsInstanceOf(expressions(i), typeRef)
          if (expressions(i).isReferenceType && expressions(i).`type` != rootClass) expressions(i) = new AsInstanceOf(expressions(i), rootClass)
        }
      }
      if (!error) {
        var node: Term = if(expressions(0).isReferenceType) {
          createEquals(BinaryTerm.Constants.EQUAL, new RefLocal(bind), expressions(0))
        } else {
          new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), expressions(0))
        }
        for(i <- 1 until expressions.length) {
          node = new BinaryTerm(LOGICAL_OR, BasicType.BOOLEAN, node, new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), expressions(i)))
        }
        node
      } else {
        null
      }
    }
    def processAssignable(node: AST.Node, a: Type, b: Term): Term = {
      if (b == null) return null
      if (a == b.`type`) return b
      if (!TypeRules.isAssignable(a, b.`type`)) {
        report(INCOMPATIBLE_TYPE, node, a, b.`type`)
        return null
      }
      new AsInstanceOf(node.location, b, a)
    }
    def openClosure[A](context: LocalContext)(block: => A): A = {
      val tmp = context.isClosure
      try {
        context.setClosure(true)
        block
      }finally{
        context.setClosure(tmp)
      }
    }
    def openFrame[A](context: LocalContext)(block: => A): A = context.openFrame(block)
    def processMethodDeclaration(node: AST.MethodDeclaration): Unit = {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (node.block == null) return
      val methodTypeParams = declaredTypeParams_.getOrElse(node, Seq())
      openTypeParams(typeParams_ ++ methodTypeParams) {
        val context = new LocalContext
        if((method.modifier & AST.M_STATIC) != 0) {
          context.setStatic(true)
        }
        context.setMethod(method)
        val arguments = method.arguments
        for(i <- 0 until arguments.length) {
          context.add(node.args(i).name, arguments(i))
        }
        val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], method.returnType)
        method.setBlock(block)
        method.setFrame(context.getContextFrame)
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit = {
      val constructor = lookupKernelNode(node).asInstanceOf[ConstructorDefinition]
      if (constructor == null) return
      val context = new LocalContext
      context.setConstructor(constructor)
      val args = constructor.getArgs
      for(i <- 0 until args.length) {
        context.add(node.args(i).name, args(i))
      }
      val params = typedTerms(node.superInits.toArray, context)
      val currentClass = definition_
      val superClass = currentClass.superClass
      val matched = superClass.findConstructor(params)
      if (matched.length == 0) {
        report(CONSTRUCTOR_NOT_FOUND, node, superClass, types(params))
      }else if (matched.length > 1) {
        report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](superClass, types(params)), Array[AnyRef](superClass, types(params)))
      }else {
        val init = new Super(superClass, matched(0).getArgs, params)
        val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], IRT.BasicType.VOID)
        constructor.superInitializer = init
        constructor.block = block
        constructor.frame = context.getContextFrame
      }
    }
    def processClassDeclaration(node: AST.ClassDeclaration, context: LocalContext): Unit = {
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
      val classTypeParams = declaredTypeParams_.getOrElse(node, Seq())
      openTypeParams(emptyTypeParams ++ classTypeParams) {
        for(section <- node.defaultSection; member <- section.members) {
          member match {
            case member: AST.FieldDeclaration =>
            case member: AST.MethodDeclaration =>
              processMethodDeclaration(member)
            case member: AST.ConstructorDeclaration =>
              processConstructorDeclaration(member)
            case member: AST.DelegatedFieldDeclaration =>
          }
        }
        for(section <- node.sections; member <- section.members) {
          member match {
            case member: AST.FieldDeclaration =>
            case member: AST.MethodDeclaration =>
              processMethodDeclaration(member)
            case member: AST.ConstructorDeclaration =>
              processConstructorDeclaration(member)
            case member: AST.DelegatedFieldDeclaration =>
          }
        }
      }
    }
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration, context: LocalContext): Unit = { () }
    def processFunctionDeclaration(node: AST.FunctionDeclaration, context: LocalContext): Unit = {
      val function = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (function == null) return
      val context = new LocalContext
      if ((function.modifier & AST.M_STATIC) != 0) {
        context.setStatic(true)
      }
      context.setMethod(function)
      val arguments = function.arguments
      for(i <- 0 until arguments.length) {
        context.add(node.args(i).name, arguments(i))
      }
      val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], function.returnType)
      function.setBlock(block)
      function.setFrame(context.getContextFrame)
    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration, context: LocalContext): Unit = {()}
    def processLocalAssign(node: AST.Assignment, context: LocalContext): Term = {
      var value: Term = typed(node.rhs, context).getOrElse(null)
      if (value == null) return null
      val id = node.lhs.asInstanceOf[AST.Id]
      val bind = context.lookup(id.name)
      var frame = 0
      var index = 0
      var leftType: Type = null
      val rightType: Type = value.`type`
      if (bind != null) {
        frame = bind.frameIndex
        index = bind.index
        leftType = bind.tp
      } else {
        frame = 0
        if (rightType.isNullType) {
          leftType = rootClass
        } else {
          leftType = rightType
        }
        index = context.add(id.name, leftType)
      }
      value = processAssignable(node.rhs, leftType, value)
      if (value != null) new SetLocal(frame, index, leftType, value) else null
    }
    // Removed: processThisFieldAssign - use this.field or self.field instead
    def processArrayAssign(node: AST.Assignment, context: LocalContext): Term = {
      var value = typed(node.rhs, context).getOrElse(null)
      val indexing = node.lhs.asInstanceOf[AST.Indexing]
      val target = typed(indexing.lhs, context).getOrElse(null)
      val index = typed(indexing.rhs, context).getOrElse(null)
      if (value == null || target == null || index == null) return null
      if (target.isBasicType) {
        report(INCOMPATIBLE_TYPE, indexing.lhs, rootClass, target.`type`)
        return null
      }
      if (target.isArrayType) {
        val targetType = target.`type`.asInstanceOf[ArrayType]
        if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
          report(INCOMPATIBLE_TYPE, indexing.rhs, IRT.BasicType.INT, index.`type`)
          return null
        }
        value = processAssignable(node.rhs, targetType.base, value)
        if (value == null) return null
        new SetArray(target, index, value)
      }else {
        val params = Array[Term](index, value)
        tryFindMethod(node, target.`type`.asInstanceOf[ObjectType], "set", Array[Term](index, value)) match {
          case Left(_) =>
            report(METHOD_NOT_FOUND, node, target.`type`, "set", types(params))
            null
          case Right(method) =>
            new Call(target, method, params)
        }
      }
    }
    def processMemberAssign(node: AST.Assignment, context: LocalContext): Term = {
      node match {
        case target@AST.Assignment(loc, node@AST.MemberSelection(_, _, _), expression) =>
          val contextClass = definition_
          val target = typed(node.target, context).getOrElse(null)
          if (target == null) return null
          if (target.`type`.isBasicType || target.`type`.isNullType) {
            report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
            return null
          }
          val targetType = target.`type`.asInstanceOf[ObjectType]
          if (!isAccessible(node, targetType, contextClass)) return null
          val name = node.name
          val field: FieldRef = findField(targetType, name)
          val value: Term = typed(expression, context).getOrElse(null)
          if (field != null && isAccessible(field, definition_)) {
            val classSubst = classSubstitution(target.`type`)
            val expected = substituteType(field.`type`, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
            val term = processAssignable(expression, expected, value)
            if (term == null) return null
            return new SetField(target, field, term)
          }
          tryFindMethod(node, targetType, setter(name), Array[Term](value)) match {
            case Right(method) =>
              new Call(target, method, Array[Term](value))
            case Left(_) =>
              null
          }
        case _ =>
          report(UNIMPLEMENTED_FEATURE, node)
          null
      }
    }
    def processEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
      var left: Term = typed(node.lhs, context).getOrElse(null)
      var right: Term = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return null
      val leftType: Type = left.`type`
      val rightType: Type = right.`type`
      if ((left.isBasicType && (!right.isBasicType)) || ((!left.isBasicType) && (right.isBasicType))) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      }
      if (left.isBasicType && right.isBasicType) {
        if (hasNumericType(left) && hasNumericType(right)) {
          val resultType = promote(leftType, rightType)
          if (resultType != left.`type`) left = new AsInstanceOf(left, resultType)
          if (resultType != right.`type`) right = new AsInstanceOf(right, resultType)
        }
        else if (leftType != IRT.BasicType.BOOLEAN || rightType != IRT.BasicType.BOOLEAN) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
          return null
        }
      }
      else if (left.isReferenceType && right.isReferenceType) {
        return createEquals(kind, left, right)
      }
      new BinaryTerm(kind, IRT.BasicType.BOOLEAN, left, right)
    }
    def processShiftExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
      var left: Term = typed(node.lhs, context).getOrElse(null)
      var right: Term = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return null
      if (!left.`type`.isBasicType) {
        val params = Array[Term](right)
        tryFindMethod(node, left.`type`.asInstanceOf[ObjectType], "add", params) match {
          case Left(_) =>
            report(METHOD_NOT_FOUND, node, left.`type`, "add", types(params))
            return null
          case Right(method) =>
            return new Call(left, method, params)
        }
      }
      if (!right.`type`.isBasicType) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
        return null
      }
      val leftType: BasicType = left.`type`.asInstanceOf[BasicType]
      val rightType: BasicType = right.`type`.asInstanceOf[BasicType]
      if ((!leftType.isInteger) || (!rightType.isInteger)) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
        return null
      }
      val leftResultType = promoteInteger(leftType)
      if (leftResultType != leftType) {
        left = new AsInstanceOf(left, leftResultType)
      }
      if (rightType != IRT.BasicType.INT) {
        right = new AsInstanceOf(right, IRT.BasicType.INT)
      }
      new BinaryTerm(kind, IRT.BasicType.BOOLEAN, left, right)
    }
    def processComparableExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] = {
      val left = typed(node.lhs, context).getOrElse(null)
      val right = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return null
      val leftType = left.`type`
      val rightType = right.`type`
      if ((!numeric(left.`type`)) || (!numeric(right.`type`))) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
        null
      } else {
        val resultType = promote(leftType, rightType)
        val newLeft = if (leftType != resultType)  new AsInstanceOf(left, resultType) else left
        val newRight = if(rightType != resultType) new AsInstanceOf(right, resultType) else right
        Array[Term](newLeft, newRight)
      }
    }
    def processBitExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
      val left = typed(node.lhs, context).getOrElse(null)
      val right = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return null
      if ((!left.isBasicType) || (!right.isBasicType)) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
        return null
      }
      val leftType = left.`type`.asInstanceOf[BasicType]
      val rightType = right.`type`.asInstanceOf[BasicType]
      var resultType: Type = null
      if (leftType.isInteger && rightType.isInteger) {
        resultType = promote(leftType, rightType)
      } else if (leftType.isBoolean && rightType.isBoolean) {
        resultType = IRT.BasicType.BOOLEAN
      } else {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      }
      val newLeft = if (left.`type` != resultType) new AsInstanceOf(left, resultType) else left
      val newRight = if (right.`type` != resultType) new AsInstanceOf(right, resultType) else right
      new BinaryTerm(kind, resultType, newLeft, newRight)
    }
    def processLogicalExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] = {
      val left = typed(node.lhs, context).getOrElse(null)
      val right = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return null
      val leftType: Type = left.`type`
      val rightType: Type = right.`type`
      if ((leftType != IRT.BasicType.BOOLEAN) || (rightType != IRT.BasicType.BOOLEAN)) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
        null
      } else {
        Array[Term](left, right)
      }
    }
    def processRefEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
      var left = typed(node.lhs, context).getOrElse(null)
      var right = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return null
      val leftType = left.`type`
      val rightType = right.`type`
      if ((left.isBasicType && (!right.isBasicType)) || ((!left.isBasicType) && (right.isBasicType))) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      }
      if (left.isBasicType && right.isBasicType) {
        if (hasNumericType(left) && hasNumericType(right)) {
          val resultType: Type = promote(leftType, rightType)
          if (resultType != left.`type`) {
            left = new AsInstanceOf(left, resultType)
          }
          if (resultType != right.`type`) {
            right = new AsInstanceOf(right, resultType)
          }
        } else if (leftType != IRT.BasicType.BOOLEAN || rightType != IRT.BasicType.BOOLEAN) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
          return null
        }
      }
      new BinaryTerm(kind, IRT.BasicType.BOOLEAN, left, right)
    }
    def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] = {
      var failed = false
      val result = nodes.map{node => typed(node, context).getOrElse{failed = true; null}}
      if(failed) null else result
    }
    def typed(node: AST.Expression, context: LocalContext): Option[Term] = node match {
      case node@AST.Addition(loc, _, _) =>
        var left = typed(node.lhs, context).getOrElse(null)
        var right = typed(node.rhs, context).getOrElse(null)
        if (left == null || right == null) return None
        if (left.isBasicType && right.isBasicType) {
          return Option(processNumericExpression(ADD, node, left, right))
        }
        if (left.isBasicType) {
          if (left.`type` == IRT.BasicType.VOID) {
            report(IS_NOT_BOXABLE_TYPE, node.lhs, left.`type`)
            return None
          } else {
            left = Boxing.boxing(table_, left)
          }
        }
        if (right.isBasicType) {
          if (right.`type` == IRT.BasicType.VOID) {
            report(IS_NOT_BOXABLE_TYPE, node.rhs, right.`type`)
            return None
          }
          else {
            right = Boxing.boxing(table_, right)
          }
        }
        val toStringL = findMethod(node.lhs, left.`type`.asInstanceOf[ObjectType], "toString")
        val toStringR = findMethod(node.rhs, right.`type`.asInstanceOf[ObjectType], "toString")
        left = new Call(left, toStringL, new Array[Term](0))
        right = new Call(right, toStringR, new Array[Term](0))
        val concat: Method = findMethod(node, left.`type`.asInstanceOf[ObjectType], "concat", Array[Term](right))
        Some(new Call(left, concat, Array[Term](right)))
      case node@AST.Subtraction(loc, left, right) =>
        val left = typed(node.lhs, context).getOrElse(null)
        val right = typed(node.rhs, context).getOrElse(null)
        if (left == null || right == null) None else Option(processNumericExpression(SUBTRACT, node, left, right))
      case node@AST.Multiplication(loc, left, right) =>
        val left = typed(node.lhs, context).getOrElse(null)
        val right = typed(node.rhs, context).getOrElse(null)
        if (left == null || right == null) None else Option(processNumericExpression(MULTIPLY, node, left, right))
      case node@AST.Division(loc, left, right) =>
        val left = typed(node.lhs, context).getOrElse(null)
        val right = typed(node.rhs, context).getOrElse(null)
        if (left == null || right == null) None else Option(processNumericExpression(DIVIDE, node, left, right))
      case node@AST.Modulo(loc, left, right) =>
        val left = typed(node.lhs, context).getOrElse(null)
        val right = typed(node.rhs, context).getOrElse(null)
        if (left == null || right == null) None else Option(processNumericExpression(MOD, node, left, right))
      case node@AST.Assignment(loc, l, r) =>
        node.lhs match {
          case _ : AST.Id =>
            Option(processLocalAssign(node, context))
          // Removed: UnqualifiedFieldReference case - use this.field or self.field instead
          case _ : AST.Indexing =>
            Option(processArrayAssign(node, context))
          case _ : AST.MemberSelection =>
            Option(processMemberAssign(node, context))
          case _ =>
            None
        }
      case node@AST.LogicalAnd(loc, left, right) =>
        val ops = processLogicalExpression(node, context)
        if (ops == null) None else Some(new BinaryTerm(LOGICAL_AND, IRT.BasicType.BOOLEAN, ops(0), ops(1)))
      case node@AST.LogicalOr(loc, left, right) =>
        val ops = processLogicalExpression(node, context)
        if (ops == null) None else Some(new BinaryTerm(LOGICAL_OR, IRT.BasicType.BOOLEAN, ops(0), ops(1)))
      case node@AST.BitAnd(loc, l, r) =>
        Option(processBitExpression(BIT_AND, node, context))
      case node@AST.BitOr(loc, l, r) =>
        Option(processBitExpression(BIT_OR, node, context))
      case node@AST.XOR(loc, left, right) =>
        Option(processBitExpression(XOR, node, context))
      case node@AST.LogicalRightShift(loc, left, right) =>
        Option(processShiftExpression(BIT_SHIFT_R3, node, context))
      case node@AST.MathLeftShift(loc, left, right) =>
        Option(processShiftExpression(BIT_SHIFT_L2, node, context))
      case node@AST.MathRightShift(loc, left, right) =>
        Option(processShiftExpression(BIT_SHIFT_R2, node, context))
      case node@AST.GreaterOrEqual(loc, left, right) =>
        val ops = processComparableExpression(node, context)
        if (ops == null) None else Some(new BinaryTerm(GREATER_OR_EQUAL, IRT.BasicType.BOOLEAN, ops(0), ops(1)))
      case node@AST.GreaterThan(loc, left, right) =>
        val ops = processComparableExpression(node, context)
        if (ops == null) None else Some(new BinaryTerm(GREATER_THAN, IRT.BasicType.BOOLEAN, ops(0), ops(1)))
      case node@AST.LessOrEqual(loc, left, right) =>
        val ops = processComparableExpression(node, context)
        if (ops == null) None else Some(new BinaryTerm(LESS_OR_EQUAL, IRT.BasicType.BOOLEAN, ops(0), ops(1)))
      case node@AST.LessThan(loc, left, right) =>
        val ops = processComparableExpression(node, context)
        if (ops == null) None else Some(new BinaryTerm(LESS_THAN, IRT.BasicType.BOOLEAN, ops(0), ops(1)))
      case node@AST.Equal(loc, left, right) =>
        Option(processEquals(EQUAL, node, context))
      case node@AST.NotEqual(loc, left, right) =>
        Option(processEquals(NOT_EQUAL, node, context))
      case node@AST.ReferenceEqual(loc, left, right) =>
        Option(processRefEquals(EQUAL, node, context))
      case node@AST.ReferenceNotEqual(loc, left, right) =>
        Option(processRefEquals(NOT_EQUAL, node, context))
      case node@AST.Elvis(loc, _, _) =>
        val left = typed(node.lhs, context).getOrElse(null)
        val right = typed(node.rhs, context).getOrElse(null)
        if(left == null || right == null) return None
        if (left.isBasicType || right.isBasicType || !TypeRules.isAssignable(left.`type`, right.`type`)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
          None
        }else {
          Some(new BinaryTerm(ELVIS, left.`type`, left, right))
        }
      case node@AST.Indexing(loc, left, right) =>
        val target = typed(node.lhs, context).getOrElse(null)
        val index = typed(node.rhs, context).getOrElse(null)
        if (target == null || index == null) return None
        if (target.isArrayType) {
          if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
            report(INCOMPATIBLE_TYPE, node, BasicType.INT, index.`type`)
            return None
          }
          return Some(new RefArray(target, index))
        }
        if (target.isBasicType) {
          report(INCOMPATIBLE_TYPE, node.lhs, rootClass, target.`type`)
          return None
        }
        if (target.isArrayType) {
          if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
            report(INCOMPATIBLE_TYPE, node.rhs, BasicType.INT, index.`type`)
            return None
          }
          return new Some(new RefArray(target, index))
        }
        val params = Array(index)
        tryFindMethod(node, target.`type`.asInstanceOf[ObjectType], "get", Array[Term](index)) match {
          case Left(_) =>
            report(METHOD_NOT_FOUND, node, target.`type`, "get", types(params))
            None
          case Right(method) =>
            Some(new Call(target, method, params))
        }
      case node@AST.AdditionAssignment(loc, left, right) =>
        typed(left, context)
        typed(right, context)
        report(UNIMPLEMENTED_FEATURE, node)
        None
      case node@AST.SubtractionAssignment(loc, left, right) =>
        typed(left, context)
        typed(right, context)
        report(UNIMPLEMENTED_FEATURE, node)
        None
      case node@AST.MultiplicationAssignment(loc, left, right) =>
        typed(left, context)
        typed(right, context)
        report(UNIMPLEMENTED_FEATURE, node)
        None
      case node@AST.DivisionAssignment(loc, left, right) =>
        typed(left, context)
        typed(right, context)
        report(UNIMPLEMENTED_FEATURE, node)
        null
      case node@AST.ModuloAssignment(loc, left, right) =>
        typed(left, context)
        typed(right, context)
        report(UNIMPLEMENTED_FEATURE, node)
        None
      case node@AST.CharacterLiteral(loc, v) =>
        Some(new CharacterValue(loc, v))
      case node@AST.IntegerLiteral(loc, v) =>
        Some(new IntValue(loc, v))
      case node@AST.LongLiteral(loc, v) =>
        Some(new LongValue(loc, v))
      case node@AST.FloatLiteral(loc, v) =>
        Some(new FloatValue(loc, v))
      case node@AST.DoubleLiteral(loc, v) =>
        Some(new DoubleValue(loc, v))
      case node@AST.BooleanLiteral(loc, v) =>
        Some(new BoolValue(loc, v))
      case node@AST.ListLiteral(loc, elements) =>
        Some(new ListLiteral(elements.map{e => typed(e, context).getOrElse(null)}.toArray, load("java.util.List")))
      case node@AST.NullLiteral(loc) =>
        Some(new NullValue(loc))
      case node@AST.Cast(loc, src, to) =>
        val term = typed(node.src, context).getOrElse(null)
        if(term == null) None
        else {
          val destination = mapFrom(node.to, mapper_)
          if(destination == null) None
          else Some(new AsInstanceOf(term, destination))
        }
      case node@AST.ClosureExpression(loc, _, _, _, _, _) =>
        val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
        val args = node.args
        val name = node.mname
        openFrame(context){
          openClosure(context) {
            val argTypes = args.map{arg => addArgument(arg, context)}.toArray
            val error = argTypes.exists(_ == null)
            if (error) return None
            if (typeRef == null) return None
            if (!typeRef.isInterface) {
              report(INTERFACE_REQUIRED, node.typeRef, typeRef)
              return None
            }
            val methods = typeRef.methods
            val method = matches(argTypes, name, methods)
            if (method == null) {
              report(METHOD_NOT_FOUND, node, typeRef, name, argTypes)
              return None
            }
            context.setMethod(method)
            context.getContextFrame.parent.setAllClosed(true)
            var block = translate(node.body, context)
            block = addReturnNode(block, method.returnType)
            val result = new NewClosure(typeRef, method, block)
            result.frame_=(context.getContextFrame)
            Some(result)
          }
        }
      case node@AST.CurrentInstance(loc) =>
        if(context.isStatic) None else Some(new This(loc, definition_))
      case node@AST.Id(loc, name) =>
        val bind = context.lookup(name)
        if (bind == null) {
          report(VARIABLE_NOT_FOUND, node, node.name)
          None
        }else {
          Some(new RefLocal(bind))
        }
      case node@AST.IsInstance(loc, _, _) =>
        val target = typed(node.target, context).getOrElse(null)
        val destinationType = mapFrom(node.typeRef, mapper_)
        if (target == null || destinationType == null) None
        else  Some(new InstanceOf(target, destinationType))
      case node@AST.MemberSelection(loc, _, _) =>
        val contextClass = definition_
        val target = typed(node.target, context).getOrElse(null)
        if (target == null) return None
        if (target.`type`.isBasicType || target.`type`.isNullType) {
          report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
          return None
        }
        val targetType = target.`type`.asInstanceOf[ObjectType]
        if (!isAccessible(node, targetType, contextClass)) return None
        val name = node.name
        if (target.`type`.isArrayType) {
          if (name.equals("length") || name.equals("size")) {
            return Some(new ArrayLength(target))
          } else {
            return None
          }
        }
        val field = findField(targetType, name)
        if (field != null && isAccessible(field, definition_)) {
          val ref = new RefField(target, field)
          val castType = substituteType(ref.`type`, classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
          return Some(if (castType eq ref.`type`) ref else new AsInstanceOf(ref, castType))
        }

        tryFindMethod(node, targetType, name, new Array[Term](0)) match {
          case Right(method) =>
            val call = new Call(target, method, new Array[Term](0))
            val castType = substituteType(method.returnType, classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
            return Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
          case Left(continuable) =>
            if(!continuable) return None
        }
        tryFindMethod(node, targetType, getter(name), new Array[Term](0)) match {
          case Right(method) =>
            val call = new Call(target, method, new Array[Term](0))
            val castType = substituteType(method.returnType, classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
            return Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
          case Left(continuable) =>
            if(!continuable) return None
        }
        tryFindMethod(node, targetType, getterBoolean(name), new Array[Term](0)) match {
          case Right(method) =>
            val call = new Call(target, method, new Array[Term](0))
            val castType = substituteType(method.returnType, classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
            Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
          case Left(_) =>
            if (field == null) {
              report(FIELD_NOT_FOUND, node, targetType, node.name)
            } else {
              report(FIELD_NOT_ACCESSIBLE, node, targetType, node.name, definition_)
            }
            None
        }

      case node@AST.MethodCall(loc, target, name, args, typeArgs) =>
        val target = typed(node.target, context).getOrElse(null)
        if (target == null) return None
        val params = typedTerms(node.args.toArray, context)
        if (params == null) return None
        val targetType = target.`type`.asInstanceOf[ObjectType]
        val name = node.name
        val methods = MethodResolution.findMethods(targetType, name, params)
        if (methods.length == 0) {
          report(METHOD_NOT_FOUND, node, targetType, name, types(params))
          None
        } else if (methods.length > 1) {
          report(AMBIGUOUS_METHOD, node, Array[AnyRef](methods(0).affiliation, name, methods(0).arguments), Array[AnyRef](methods(1).affiliation, name, methods(1).arguments))
          None
        } else if ((methods(0).modifier & AST.M_STATIC) != 0) {
          report(ILLEGAL_METHOD_CALL, node, methods(0).affiliation, name, methods(0).arguments)
          None
        } else {
          val method = methods(0)
          val classSubst = classSubstitution(target.`type`)
          val methodSubst =
            if (typeArgs.nonEmpty) {
              GenericMethodTypeArguments.explicit(node, method, typeArgs, classSubst).getOrElse(return None)
            } else {
              GenericMethodTypeArguments.infer(node, method, params, classSubst)
            }

          val expectedArgs = method.arguments.map(tp => substituteType(tp, classSubst, methodSubst, defaultToBound = true))
          var i = 0
          while (i < params.length) {
            params(i) = processAssignable(node.args(i), expectedArgs(i), params(i))
            if (params(i) == null) return None
            i += 1
          }

          val call = new Call(target, method, params)
          val castType = substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        }
      case node@AST.Negate(loc, target) =>
        val term = typed(node.term, context).getOrElse(null)
        if (term == null) return None
        if (!hasNumericType(term)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "-", Array[Type](term.`type`))
          return None
        }
        Some(new UnaryTerm(MINUS, term.`type`, term))
      case node@AST.NewArray(loc, _, _) =>
        val typeRef = mapFrom(node.typeRef, mapper_)
        val parameters = typedTerms(node.args.toArray, context)
        if(typeRef == null || parameters == null) return None
        val resultType = loadArray(typeRef, parameters.length)
        Some(new NewArray(resultType, parameters))
      case node@AST.NewObject(loc, _, _) =>
        val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
        val parameters = typedTerms(node.args.toArray, context)
        if (parameters == null || typeRef == null) return None
        val constructors = typeRef.findConstructor(parameters)
        if (constructors.length == 0) {
          report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(parameters))
          None
        }else if (constructors.length > 1) {
          report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](constructors(0).affiliation, constructors(0).getArgs), Array[AnyRef](constructors(1).affiliation, constructors(1).getArgs))
          None
        }else {
          typeRef match {
            case applied: TypedAST.AppliedClassType =>
              val appliedCtor = new TypedAST.ConstructorRef {
                def modifier: Int = constructors(0).modifier
                def affiliation: TypedAST.ClassType = applied
                def name: String = constructors(0).name
                def getArgs: Array[TypedAST.Type] = constructors(0).getArgs
              }
              Some(new NewObject(appliedCtor, parameters))
            case _ =>
              Some(new NewObject(constructors(0), parameters))
          }
        }
      case node@AST.Not(loc, target) =>
        val term = typed(node.term, context).getOrElse(null)
        if (term == null) return None
        if (term.`type` != IRT.BasicType.BOOLEAN) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "!", Array[Type](term.`type`))
          return None
        }
        Some(new UnaryTerm(NOT, BasicType.BOOLEAN, term))
      case node@AST.Posit(loc, target) =>
        val term = typed(node.term, context).getOrElse(null)
        if (term == null) return None
        if (!hasNumericType(term)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "+", Array[Type](term.`type`))
          return None
        }
        Some(new UnaryTerm(PLUS, term.`type`, term))
      case node@AST.PostDecrement(loc, target) =>
        val operand = typed(node.term, context).getOrElse(null)
        if (operand == null) return None
        if ((!operand.isBasicType) || !hasNumericType(operand)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "--", Array[Type](operand.`type`))
          return None
        }
        Option(operand match {
          case ref: RefLocal =>
            val varIndex = context.add(context.newName, operand.`type`)
            new Begin(new SetLocal(0, varIndex, operand.`type`, operand), new SetLocal(ref.frame, ref.index, ref.`type`, new BinaryTerm(SUBTRACT, operand.`type`, new RefLocal(0, varIndex, operand.`type`), new IntValue(1))), new RefLocal(0, varIndex, operand.`type`))
          case ref: RefField =>
            val varIndex = context.add(context.newName, ref.target.`type`)
            new Begin(new SetLocal(0, varIndex, ref.target.`type`, ref.target), new SetField(new RefLocal(0, varIndex, ref.target.`type`), ref.field, new BinaryTerm(SUBTRACT, operand.`type`, new RefField(new RefLocal(0, varIndex, ref.target.`type`), ref.field), new IntValue(1))))
          case _ =>
            report(LVALUE_REQUIRED, target)
            null
        })
      case node@AST.PostIncrement(loc, target) =>
        val operand = typed(node.term, context).getOrElse(null)
        if (operand == null) return None
        if ((!operand.isBasicType) || !hasNumericType(operand)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "++", Array[Type](operand.`type`))
          return None
        }
        Option(operand match {
          case ref: RefLocal =>
            val varIndex = context.add(context.newName, operand.`type`)
            new Begin(new SetLocal(0, varIndex, operand.`type`, operand), new SetLocal(ref.frame, ref.index, ref.`type`, new BinaryTerm(ADD, operand.`type`, new RefLocal(0, varIndex, operand.`type`), new IntValue(1))), new RefLocal(0, varIndex, operand.`type`))
          case ref: RefField =>
            val varIndex = context.add(context.newName, ref.target.`type`)
            new Begin(new SetLocal(0, varIndex, ref.target.`type`, ref.target), new SetField(new RefLocal(0, varIndex, ref.target.`type`), ref.field, new BinaryTerm(ADD, operand.`type`, new RefField(new RefLocal(0, varIndex, ref.target.`type`), ref.field), new IntValue(1))))
          case _ =>
            report(LVALUE_REQUIRED, target);
            null
        })
      // Removed: UnqualifiedFieldReference - use this.field or self.field instead
      case node@AST.UnqualifiedMethodCall(loc, name, args, typeArgs) =>
        var params = typedTerms(node.args.toArray, context)
        if (params == null) return None
        val targetType = definition_
        val methods = targetType.findMethod(node.name, params)
        if (methods.length == 0) {
          report(METHOD_NOT_FOUND, node, targetType, node.name, types(params))
          None
        } else if (methods.length > 1) {
          report(AMBIGUOUS_METHOD, node, Array[AnyRef](methods(0).affiliation, node.name, methods(0).arguments), Array[AnyRef](methods(1).affiliation, node.name, methods(1).arguments))
          None
        } else {
          val method = methods(0)
          val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
          val methodSubst =
            if (typeArgs.nonEmpty) {
              GenericMethodTypeArguments.explicit(node, method, typeArgs, classSubst).getOrElse(return None)
            } else {
              GenericMethodTypeArguments.infer(node, method, params, classSubst)
            }

          val expectedArgs = method.arguments.map(tp => substituteType(tp, classSubst, methodSubst, defaultToBound = true))
          var i = 0
          while (i < params.length) {
            params(i) = processAssignable(node.args(i), expectedArgs(i), params(i))
            if (params(i) == null) return None
            i += 1
          }

          if ((methods(0).modifier & AST.M_STATIC) != 0) {
            val call = new CallStatic(targetType, method, params)
            val castType = substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
            Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
          } else {
            if(context.isClosure) {
              val call = new Call(new OuterThis(targetType), method, params)
              val castType = substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
              Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
            }else {
              val call = new Call(new This(targetType), method, params)
              val castType = substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
              Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
            }
          }
        }
      case node@AST.StaticMemberSelection(loc, _, _) =>
        val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
        if (typeRef == null) return None
        val field = findField(typeRef, node.name)
        if (field == null) {
          report(FIELD_NOT_FOUND, node, typeRef, node.name)
          None
        }else {
          Some(new RefStaticField(typeRef,field))
        }
      case node@AST.StaticMethodCall(loc, _, _, _, typeArgs) =>
        val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
        val parameters = typedTerms(node.args.toArray, context)
        if (typeRef == null || parameters == null) {
          None
        } else {
          val methods = typeRef.findMethod(node.name, parameters)
          if (methods.length == 0) {
            report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
            None
          } else if (methods.length > 1) {
            report(AMBIGUOUS_METHOD, node, node.name, typeNames(methods(0).arguments), typeNames(methods(1).arguments))
            None
          } else {
            val method = methods(0)
            val classSubst = classSubstitution(typeRef)
            val methodSubst =
              if (typeArgs.nonEmpty) {
                GenericMethodTypeArguments.explicit(node, method, typeArgs, classSubst).getOrElse(return None)
              } else {
                GenericMethodTypeArguments.infer(node, method, parameters, classSubst)
              }

            val expectedArgs = method.arguments.map(tp => substituteType(tp, classSubst, methodSubst, defaultToBound = true))
            var i = 0
            while (i < parameters.length) {
              parameters(i) = processAssignable(node.args(i), expectedArgs(i), parameters(i))
              if (parameters(i) == null) return None
              i += 1
            }

            val call = new CallStatic(typeRef, method, parameters)
            val castType = substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
            Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
          }
        }
      case node@AST.StringLiteral(loc, value) =>
        Some(new StringValue(loc, value, load("java.lang.String")))
      case node@AST.StringInterpolation(loc, parts, expressions) =>
        // Type check all interpolated expressions
        val typedExprs = expressions.map(e => typed(e, context).getOrElse(null))
        if (typedExprs.contains(null)) return None
        
        // Build string concatenation using StringBuilder
        val stringType = load("java.lang.String")
        val sbType = load("java.lang.StringBuilder")
        
        // Find StringBuilder no-arg constructor
        val constructors = sbType.findConstructor(Array[Term]())
        if (constructors.isEmpty) {
          report(SemanticError.CONSTRUCTOR_NOT_FOUND, node, sbType, Array[Type]())
          return None
        }
        val noArgConstructor = constructors(0)
        
        // Create StringBuilder
        val sb = new NewObject(noArgConstructor, Array[Term]())
        var result: Term = sb
        
        // Append parts and expressions
        for (i <- parts.indices) {
          if (parts(i).nonEmpty) {
            val part = new StringValue(loc, parts(i), stringType)
            val appendMethods = sbType.findMethod("append", Array(part))
            if (appendMethods.nonEmpty) {
              result = new Call(result, appendMethods(0), Array(part))
            }
          }
          
          if (i < typedExprs.length) {
            val expr = typedExprs(i)
            // Try to find append method for the expression's type
            val appendMethods = sbType.findMethod("append", Array(expr))
            if (appendMethods.nonEmpty) {
              result = new Call(result, appendMethods(0), Array(expr))
            } else {
              // If no direct match, convert to string first
              val toStringMethods = expr.`type`.asInstanceOf[ObjectType].findMethod("toString", Array[Term]())
              if (toStringMethods.nonEmpty) {
                val stringExpr = new Call(expr, toStringMethods(0), Array[Term]())
                val appendStringMethods = sbType.findMethod("append", Array(stringExpr))
                if (appendStringMethods.nonEmpty) {
                  result = new Call(result, appendStringMethods(0), Array(stringExpr))
                }
              }
            }
          }
        }
        
        // Call toString()
        val toStringMethods = sbType.findMethod("toString", Array[Term]())
        if (toStringMethods.isEmpty) {
          report(SemanticError.METHOD_NOT_FOUND, node, sbType, "toString", Array[Type]())
          return None
        }
        Some(new Call(result, toStringMethods(0), Array[Term]()))
      case node@AST.SuperMethodCall(loc, _, _, typeArgs) =>
        val parameters = typedTerms(node.args.toArray, context)
        if (parameters == null) return None
        val contextClass = definition_
        tryFindMethod(node, contextClass.superClass, node.name, parameters) match {
          case Right(method) =>
            val classSubst = classSubstitution(contextClass.superClass)
            val methodSubst =
              if (typeArgs.nonEmpty) {
                GenericMethodTypeArguments.explicit(node, method, typeArgs, classSubst).getOrElse(return None)
              } else {
                GenericMethodTypeArguments.infer(node, method, parameters, classSubst)
              }

            val expectedArgs = method.arguments.map(tp => substituteType(tp, classSubst, methodSubst, defaultToBound = true))
            var i = 0
            while (i < parameters.length) {
              parameters(i) = processAssignable(node.args(i), expectedArgs(i), parameters(i))
              if (parameters(i) == null) return None
              i += 1
            }
            val call = new CallSuper(new This(contextClass), method, parameters)
            val castType = substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
            Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
          case Left(_) =>
            report(METHOD_NOT_FOUND, node, contextClass, node.name, types(parameters))
            None
        }
    }
    def translate(node: AST.CompoundExpression, context: LocalContext): ActionStatement = node match {
      case AST.BlockExpression(loc, elements) =>
        context.openScope {
          new StatementBlock(elements.map{e => translate(e, context)}.toIndexedSeq:_*)
        }
      case node@AST.BreakExpression(loc) =>
        new Break(loc)
      case node@AST.ContinueExpression(loc) =>
        new Continue(loc)
      case node@AST.EmptyExpression(loc) =>
        new NOP(loc)
      case node@AST.ExpressionBox(loc, body) =>
        typed(body, context).map{e =>  new ExpressionActionStatement(loc, e)}.getOrElse(new NOP(loc))
      case node@AST.ForeachExpression(loc, _, _, _) =>
        context.openScope {
          val collection = typed(node.collection, context).getOrElse(null)
          val arg = node.arg
          addArgument(arg, context)
          var block = translate(node.statement, context)
          if (collection.isBasicType) {
            report(INCOMPATIBLE_TYPE, node.collection, load("java.util.Collection"), collection.`type`)
            return new NOP(node.location)
          }
          val elementVar = context.lookupOnlyCurrentScope(arg.name)
          val collectionVar = new ClosureLocalBinding(0, context.add(context.newName, collection.`type`), collection.`type`)
          var init: ActionStatement = null
          if (collection.isArrayType) {
            val counterVariable = new ClosureLocalBinding(0, context.add(context.newName, BasicType.INT), BasicType.INT)
            init = new StatementBlock(new ExpressionActionStatement(new SetLocal(collectionVar, collection)), new ExpressionActionStatement(new SetLocal(counterVariable, new IntValue(0))))
            block = new ConditionalLoop(new BinaryTerm(LESS_THAN, BasicType.BOOLEAN, ref(counterVariable), new ArrayLength(ref(collectionVar))), new StatementBlock(assign(elementVar, indexref(collectionVar, ref(counterVariable))), block, assign(counterVariable, new BinaryTerm(ADD, BasicType.INT, ref(counterVariable), new IntValue(1)))))
            new StatementBlock(init, block)
          }
          else {
            val iteratorType = load("java.util.Iterator")
            val iteratorVar = new ClosureLocalBinding(0, context.add(context.newName, iteratorType), iteratorType)
            val mIterator = findMethod(node.collection, collection.`type`.asInstanceOf[ObjectType], "iterator")
            val mNext = findMethod(node.collection, iteratorType, "next")
            val mHasNext = findMethod(node.collection, iteratorType, "hasNext")
            init = new StatementBlock(new ExpressionActionStatement(new SetLocal(collectionVar, collection)), assign(iteratorVar, new Call(ref(collectionVar), mIterator, new Array[Term](0))))
            var next: Term = new Call(ref(iteratorVar), mNext, new Array[Term](0))
            if (elementVar.tp != rootClass) {
              next = new AsInstanceOf(next, elementVar.tp)
            }
            block = new ConditionalLoop(new Call(ref(iteratorVar), mHasNext, new Array[Term](0)), new StatementBlock(assign(elementVar, next), block))
            new StatementBlock(init, block)
          }
        }
      case node@AST.ForExpression(loc, _, _, _, _) =>
        context.openScope {
          val init = Option(node.init).map{init => translate(init, context)}.getOrElse(new NOP(loc))
          val condition = (for(c <- Option(node.condition)) yield {
            val conditionOpt = typed(c, context)
            val expected = BasicType.BOOLEAN
            for(condition <- conditionOpt; if condition.`type` != expected) {
              report(INCOMPATIBLE_TYPE, node.condition, condition.`type`, expected)
            }
            conditionOpt.getOrElse(null)
          }).getOrElse(new BoolValue(loc, true))
          val update = Option(node.update).flatMap{update => typed(update, context)}.getOrElse(null)
          var loop = translate(node.block, context)
          if(update != null) loop = new StatementBlock(loop, new ExpressionActionStatement(update))
          new StatementBlock(init.location, init, new ConditionalLoop(condition, loop))
        }
      case node@AST.IfExpression(loc, _, _, _) =>
        context.openScope {
          val conditionOpt = typed(node.condition, context)
          val expected = BasicType.BOOLEAN
          for(condition <- conditionOpt if condition.`type` != expected) {
            report(INCOMPATIBLE_TYPE, node.condition, expected, condition.`type`)
          }
          val thenBlock = translate(node.thenBlock, context)
          val elseBlock = if (node.elseBlock == null) null else translate(node.elseBlock, context)
          conditionOpt.map{c => new IfStatement(c, thenBlock, elseBlock)}.getOrElse(new NOP(loc))
        }
      case node@AST.LocalVariableDeclaration(loc, name, typeRef, init) =>
        val binding = context.lookupOnlyCurrentScope(name)
        if (binding != null) {
          report(DUPLICATE_LOCAL_VARIABLE, node, name)
          return new NOP(loc)
        }
        val lhsType = mapFrom(node.typeRef)
        if (lhsType == null) return new NOP(loc)
        val index = context.add(name, lhsType)
        var local: SetLocal = null
        if (init != null) {
          val valueNode = typed(init, context)
          valueNode match {
            case None => return new NOP(loc)
            case Some(v) =>
              val value = processAssignable(init, lhsType, v)
              if(value == null) return new NOP(loc)
              local = new SetLocal(loc, 0, index, lhsType, value)
          }
        }
        else {
          local = new SetLocal(loc, 0, index, lhsType, defaultValue(lhsType))
        }
        new ExpressionActionStatement(local)
      case node@AST.ReturnExpression(loc, _) =>
        val returnType = context.returnType
        if(node.result == null) {
          val expected  = BasicType.VOID
          if (returnType != expected) report(CANNOT_RETURN_VALUE, node)
          new Return(loc, null)
        } else {
          val returnedOpt= typed(node.result, context)
          if (returnedOpt == null) return new Return(loc, null)
          (for(returned <- returnedOpt) yield {
            if (returned.`type` == BasicType.VOID) {
              report(CANNOT_RETURN_VALUE, node)
              new Return(loc, null)
            } else {
              val value = processAssignable(node.result, returnType, returned)
              if (value == null) return new Return(loc, null)
              new Return(loc, value)
            }
          }).getOrElse(new Return(loc, null))
        }
      case node@AST.SelectExpression(loc, _, _, _) =>
        val conditionOpt = typed(node.condition, context)
        if(conditionOpt == None) return new NOP(loc)
        val condition = conditionOpt.get
        val name = context.newName
        val index = context.add(name, condition.`type`)
        val statement = if(node.cases.length == 0) {
          Option(node.elseBlock).map{e => translate(e, context)}.getOrElse(new NOP(loc))
        }else {
          val cases = node.cases
          val nodes = Buffer[Term]()
          val thens = Buffer[ActionStatement]()
          for((expressions, thenClause)<- cases) {
            val bind = context.lookup(name)
            nodes += processNodes(expressions.toArray, condition.`type`, bind, context)
            thens += translate(thenClause, context)
          }
          var branches: ActionStatement = if(node.elseBlock != null) {
            translate(node.elseBlock, context)
          }else {
            null
          }
          for(i <- (cases.length - 1) to (0, -1)) {
            branches = new IfStatement(nodes(i), thens(i), branches)
          }
          branches
        }
        new StatementBlock(condition.location, new ExpressionActionStatement(condition.location, new SetLocal(0, index, condition.`type`, condition)), statement)
      case node@AST.SynchronizedExpression(loc, _, _) =>
        context.openScope {
          val lock = typed(node.condition, context).getOrElse(null)
          val block = translate(node.block, context)
          report(UNIMPLEMENTED_FEATURE, node)
          new Synchronized(node.location, lock, block)
        }
      case node@AST.ThrowExpression(loc, target) =>
        val expressionOpt = typed(target, context)
        for(expression <- expressionOpt) {
          val expected = load("java.lang.Throwable")
          val detected = expression.`type`
          if (!TypeRules.isSuperType(expected, detected)) {
            report(INCOMPATIBLE_TYPE, node, expected, detected)
          }
        }
        new Throw(loc, expressionOpt.getOrElse(null))
      case node@AST.TryExpression(loc, tryBlock, recClauses, finBlock) =>
        val tryStatement = translate(tryBlock, context)
        val binds = new Array[ClosureLocalBinding](recClauses.length)
        val catchBlocks = new Array[ActionStatement](recClauses.length)
        for(i <- 0 until recClauses.length) {
          val (argument, body) = recClauses(i)
          context.openScope {
            val argType = addArgument(argument, context)
            val expected = load("java.lang.Throwable")
            if (!TypeRules.isSuperType(expected, argType)) {
              report(INCOMPATIBLE_TYPE, argument, expected, argType)
            }
            binds(i) = context.lookupOnlyCurrentScope(argument.name)
            catchBlocks(i) = translate(body, context)
          }
        }
        new Try(loc, tryStatement, binds, catchBlocks)
      case node@AST.WhileExpression(loc, _, _) =>
        context.openScope {
          val conditionOpt = typed(node.condition, context)
          val expected = BasicType.BOOLEAN
          for(condition <- conditionOpt) {
            val actual = condition.`type`
            if(actual != expected)  report(INCOMPATIBLE_TYPE, node, expected, actual)
          }
          val thenBlock = translate(node.block, context)
          new ConditionalLoop(loc, conditionOpt.getOrElse(null), thenBlock)
        }
    }
    def defaultValue(typeRef: Type): Term = Term.defaultValue(typeRef)
    def addReturnNode(node: ActionStatement, returnType: Type): StatementBlock = {
      new StatementBlock(node, new Return(defaultValue(returnType)))
    }
    def createMain(top: ClassType, ref: Method, name: String, args: Array[Type], ret: Type): MethodDefinition = {
      val method = new MethodDefinition(null, AST.M_STATIC | AST.M_PUBLIC, top, name, args, ret, null)
      val frame = new LocalFrame(null)
      val params = new Array[Term](args.length)
      for(i <- 0 until args.length) {
        val arg = args(i)
        val index = frame.add("args" + i, arg)
        params(i) = new RefLocal(0, index, arg)
      }
      method.setFrame(frame)
      val constructor = top.findConstructor(new Array[Term](0))(0)
      var block = new StatementBlock(new ExpressionActionStatement(new Call(new NewObject(constructor, new Array[Term](0)), ref, params)))
      block = addReturnNode(block, BasicType.VOID)
      method.setBlock(block)
      method
    }
    unit_ = node
    val toplevels = node.toplevels
    val context = new LocalContext
    val statements = Buffer[ActionStatement]()
    mapper_ = find(topClass)
    val klass = loadTopClass.asInstanceOf[ClassDefinition]
    val argsType = loadArray(load("java.lang.String"), 1)
    val method = new MethodDefinition(node.location, AST.M_PUBLIC, klass, "start", Array[Type](argsType), BasicType.VOID, null)
    context.add("args", argsType)
    for (element <- toplevels) {
      if(!element.isInstanceOf[AST.TypeDeclaration]) definition_ = klass
      element match {
        case node: AST.CompoundExpression =>
          context.setMethod(method)
          statements += translate(node, context)
        case _ =>
          element match {
            case node: AST.ClassDeclaration => processClassDeclaration(node, context)
            case node: AST.InterfaceDeclaration => processInterfaceDeclaration(node, context)
            case node: AST.FunctionDeclaration => processFunctionDeclaration(node, context)
            case node: AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node, context)
            case _ =>
          }
      }
    }
    if (klass != null) {
      statements += new Return(null)
      method.setBlock(new StatementBlock(statements.asJava))
      method.setFrame(context.getContextFrame)
      klass.add(method)
      klass.add(createMain(klass, method, "main", Array[Type](argsType), BasicType.VOID))
    }
  }
  def processDuplication(node: AST.CompilationUnit): Unit = {
    val methods = new JTreeSet[Method](new MethodComparator)
    val fields = new JTreeSet[FieldRef](new FieldComparator)
    val constructors = new JTreeSet[ConstructorRef](new ConstructorComparator)
    val variables = new JTreeSet[FieldRef](new FieldComparator)
    val functions = new JTreeSet[Method](new MethodComparator)
    def processFieldDeclaration(node: AST.FieldDeclaration): Unit = {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processMethodDeclaration(node: AST.MethodDeclaration): Unit = {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit = {
      val constructor = lookupKernelNode(node).asInstanceOf[ConstructorDefinition]
      if (constructor == null) return
      if (constructors.contains(constructor)) {
        report(DUPLICATE_CONSTRUCTOR, node, constructor.affiliation, constructor.getArgs)
      } else {
        constructors.add(constructor)
      }
    }
    def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration): Unit = {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processInterfaceMethodDeclaration(node: AST.MethodDeclaration): Unit = {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def generateMethods(): Unit = {
      val generated = new JTreeSet[Method](new MethodComparator)
      val methodSet = new JTreeSet[Method](new MethodComparator)
      def makeDelegationMethod(delegated: FieldRef, delegator: Method): MethodDefinition = {
        val args = delegator.arguments
        val params = new Array[Term](args.length)
        val frame = new LocalFrame(null)
        for(i <- 0 until params.length) {
          val index = frame.add("arg" + i, args(i))
          params(i) = new RefLocal(new ClosureLocalBinding(0, index, args(i)))
        }
        val target = new Call(new RefField(new This(definition_), delegated), delegator, params)
        val statement = if (delegator.returnType != BasicType.VOID) new StatementBlock(new Return(target)) else new StatementBlock(new ExpressionActionStatement(target), new Return(null))
        val node = new MethodDefinition(null, AST.M_PUBLIC, definition_, delegator.name, delegator.arguments, delegator.returnType, statement)
        node.setFrame(frame)
        node
      }
      def generateDelegationMethods(node: FieldDefinition): Unit = {
        val typeRef = node.`type`.asInstanceOf[ClassType]
        val src = Classes.getInterfaceMethods(typeRef)
        for (method <- src.asScala) {
          if (!methodSet.contains(method)) {
            if (generated.contains(method)) {
              report(DUPLICATE_GENERATED_METHOD, node.location, method.affiliation, method.name, method.arguments)
            }
            else {
              val generatedMethod = makeDelegationMethod(node, method)
              generated.add(generatedMethod)
              definition_.add(generatedMethod)
            }
          }
        }
      }
      for (node <- fields.asScala) {
        if ((AST.M_FORWARDED & node.modifier) != 0) generateDelegationMethods(node.asInstanceOf[FieldDefinition])
      }
    }
    def processAccessSection(node: AST.AccessSection): Unit = {
      for(member <- node.members) member match {
        case node: AST.FieldDeclaration => processFieldDeclaration(node)
        case node: AST.MethodDeclaration => processMethodDeclaration(node)
        case node: AST.ConstructorDeclaration => processConstructorDeclaration(node)
        case node: AST.DelegatedFieldDeclaration => processDelegatedFieldDeclaration(node)
      }
    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration): Unit = {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return
      if (variables.contains(field)) {
        report(DUPLICATE_GLOBAL_VARIABLE, node, field.name)
      }else {
        variables.add(field)
      }
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration): Unit = {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (functions.contains(method)) {
        report(DUPLICATE_FUNCTION, node, method.name, method.arguments)
      } else {
        functions.add(method)
      }
    }
    def processClassDeclaration(node: AST.ClassDeclaration): Unit = {
      val clazz = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      if (clazz == null) return
      methods.clear()
      fields.clear()
      constructors.clear()
      definition_ = clazz
      mapper_ = find(clazz.name)
      for(defaultSection <- node.defaultSection) {
        processAccessSection(defaultSection)
      }
      for (section <- node.sections) processAccessSection(section)
      generateMethods()
      DuplicationChecks.checkOverrideContracts(clazz, node.location)
      DuplicationChecks.checkErasureSignatureCollisions(clazz, node.location)
    }
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration): Unit = {
      val clazz = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      if (clazz == null) return
      methods.clear()
      fields.clear()
      constructors.clear()
      definition_ = clazz
      mapper_ = find(clazz.name)
      for (node <- node.methods) processInterfaceMethodDeclaration(node)
      DuplicationChecks.checkErasureSignatureCollisions(clazz, node.location)
    }

    unit_ = node
    variables.clear()
    functions.clear()
    for (toplevel <- node.toplevels) {
      mapper_ = find(topClass)
      toplevel match {
        case node: AST.ClassDeclaration => processClassDeclaration(node)
        case node: AST.InterfaceDeclaration => processInterfaceDeclaration(node)
        case node: AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node)
        case node: AST.FunctionDeclaration => processFunctionDeclaration(node)
        case _ =>
      }
    }
  }

  private object DuplicationChecks {
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
              val specializedArgs = contract.arguments.map(tp => substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
              val specializedRet = substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)

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
    report(error, node.location, items:_*)
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
  private def put(astNode: AST.Node, kernelNode: Node): Unit = {
    ast2ixt_(astNode) = kernelNode
    ixt2ast_(kernelNode) = astNode
  }
  private def lookupAST(kernelNode: Node): AST.Node =  ixt2ast_.get(kernelNode).getOrElse(null)
  private def lookupKernelNode(astNode: AST.Node): Node = ast2ixt_.get(astNode).getOrElse(null)
  private def add(className: String, mapper: NameMapper): Unit = mappers_(className) = mapper
  private def find(className: String): NameMapper = mappers_.get(className).getOrElse(null)
  private def createName(moduleName: String, simpleName: String): String = (if (moduleName != null) moduleName + "." else "") + simpleName
  private def classpath(paths: Seq[String]): String = paths.foldLeft(new StringBuilder){(builder, path) => builder.append(Systems.pathSeparator).append(path)}.toString()
  private def typesOf(arguments: List[AST.Argument]): Option[List[Type]] = {
    val result = arguments.map{arg => mapFrom(arg.typeRef)}
    if(result.forall(_ != null)) Some(result) else None
  }
  private def mapFrom(typeNode: AST.TypeNode): Type = mapFrom(typeNode, mapper_)
  private def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): Type = {
    val mappedType = mapper.resolveNode(typeNode)
    if (mappedType == null) report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString)
    else validateTypeApplication(typeNode, mappedType)
    mappedType
  }

  private def validateTypeApplication(typeNode: AST.TypeNode, mappedType: Type): Unit = {
    typeNode.desc match {
      case AST.ParameterizedType(_, _) =>
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

  private def openTypeParams[A](scope: TypeParamScope)(block: => A): A = {
    val prev = typeParams_
    typeParams_ = scope
    try block
    finally typeParams_ = prev
  }

  private def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] = {
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

  private def classSubstitution(tp: Type): scala.collection.immutable.Map[String, Type] = tp match {
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

  private def substituteType(tp: Type, classSubst: scala.collection.immutable.Map[String, Type], methodSubst: scala.collection.immutable.Map[String, Type], defaultToBound: Boolean): Type = {
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
      case other =>
        other
    }
  }

  private object GenericMethodTypeArguments {
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
        val upper = substituteType(upper0, classSubst, subst, defaultToBound = true)
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
      classSubst: scala.collection.immutable.Map[String, Type]
    ): scala.collection.immutable.Map[String, Type] = {
      val typeParams = method.typeParameters
      if (typeParams.isEmpty) return scala.collection.immutable.Map.empty

      val bounds = HashMap[String, Type]()
      for (tp <- typeParams) {
        val upper = tp.upperBound.getOrElse(rootClass)
        bounds += tp.name -> substituteType(upper, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
      }

      val inferred = HashMap[String, Type]()
      val paramNames = typeParams.map(_.name).toSet

      def unify(formal: Type, actual: Type, position: AST.Node): Unit = {
        if (actual.isNullType) return
        formal match {
          case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
            inferred.get(tv.name) match {
              case Some(prev) =>
                if (!(prev eq actual)) report(INCOMPATIBLE_TYPE, position, prev, actual)
              case None =>
                inferred += tv.name -> actual
            }
          case apf: TypedAST.AppliedClassType =>
            actual match {
              case apa: TypedAST.AppliedClassType if (apf.raw eq apa.raw) && apf.typeArguments.length == apa.typeArguments.length =>
                var i = 0
                while (i < apf.typeArguments.length) {
                  unify(apf.typeArguments(i), apa.typeArguments(i), position)
                  i += 1
                }
              case _ =>
            }
          case aft: ArrayType =>
            actual match {
              case aat: ArrayType if aft.dimension == aat.dimension =>
                unify(aft.component, aat.component, position)
              case _ =>
            }
          case _ =>
        }
      }

      val formalArgs = method.arguments.map(t => substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
      var i = 0
      while (i < formalArgs.length && i < args.length) {
        unify(formalArgs(i), args(i).`type`, callNode)
        i += 1
      }

      for (tp <- typeParams) {
        val inferredType = inferred.getOrElse(tp.name, bounds(tp.name))
        val bound = bounds(tp.name)
        if (!TypeRules.isAssignable(bound, inferredType)) {
          report(INCOMPATIBLE_TYPE, callNode, bound, inferredType)
        }
        inferred += tp.name -> inferredType
      }

      inferred.toMap
    }
  }
  private def createEquals(kind: Int, lhs: Term, rhs: Term): Term = {
    val params = Array[Term](new AsInstanceOf(rhs, rootClass))
    val target = lhs.`type`.asInstanceOf[ObjectType]
    val methods = target.findMethod("equals", params)
    var node: Term = new Call(lhs, methods(0), params)
    if (kind == BinaryTerm.Constants.NOT_EQUAL) {
      node = new UnaryTerm(NOT, BasicType.BOOLEAN, node)
    }
    node
  }
  private def indexref(bind: ClosureLocalBinding, value: Term): Term = new RefArray(new RefLocal(bind), value)
  private def assign(bind: ClosureLocalBinding, value: Term): ActionStatement = new ExpressionActionStatement(new SetLocal(bind, value))
  private def ref(bind: ClosureLocalBinding): Term = new RefLocal(bind)
  private def findMethod(node: AST.Node, target: ObjectType, name: String): Method =  findMethod(node, target, name, new Array[Term](0))
  private def findMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Method = {
    val methods = MethodResolution.findMethods(target, name, params)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, target, name, params.map{param => param.`type`})
      return null
    }
    methods(0)
  }
  private def hasSamePackage(a: ClassType, b: ClassType): Boolean = {
    var name1 = a.name
    var name2 = b.name
    var index: Int = 0
    index = name1.lastIndexOf(".")
    if (index >= 0)  name1 = name1.substring(0, index)
    else name1 = ""
    index = name2.lastIndexOf(".")
    name2 = if(index >= 0) name2.substring(0, index) else ""
    name1 == name2
  }
  private def isAccessible(target: ClassType, context: ClassType): Boolean = {
    if (hasSamePackage(target, context))  true else (target.modifier & AST.M_INTERNAL) == 0
  }
  private def isAccessible(member: MemberRef, context: ClassType): Boolean = {
    val targetType = member.affiliation
    if (targetType == context) return true
    val modifier = member.modifier
    if (TypeRules.isSuperType(targetType, context)) (modifier & AST.M_PROTECTED) != 0 || (modifier & AST.M_PUBLIC) != 0 else (AST.M_PUBLIC & modifier) != 0
  }
  private def findField(target: ObjectType, name: String): FieldRef = {
    if(target == null) return null
    var field = target.field(name)
    if(field != null) return field
    field = findField(target.superClass, name)
    if (field != null) return field
    for (interface <- target.interfaces) {
      field = findField(interface, name)
      if (field != null) return field
    }
    null
  }
  private def isAccessible(node: AST.Node, target: ObjectType, context: ClassType): Boolean = {
    if (target.isArrayType) {
      val component = target.asInstanceOf[ArrayType].component
      if (!component.isBasicType) {
        if (!isAccessible(component.asInstanceOf[ClassType], definition_)) {
          report(CLASS_NOT_ACCESSIBLE, node, target, context)
          return false
        }
      }
    } else {
      if (!isAccessible(target.asInstanceOf[ClassType], context)) {
        report(CLASS_NOT_ACCESSIBLE, node, target, context)
        return false
      }
    }
    true
  }
  private def hasNumericType(term: Term): Boolean =  numeric(term.`type`)
  private def numeric(symbol: Type): Boolean = {
    symbol.isBasicType && (symbol == BasicType.BYTE || symbol == BasicType.SHORT || symbol == BasicType.CHAR || symbol == BasicType.INT || symbol == BasicType.LONG || symbol == BasicType.FLOAT || symbol == BasicType.DOUBLE)
  }
  private def doCastInsertion(arguments: Array[Type], params: Array[Term]): Array[Term] = {
    for(i <- 0 until params.length) {
      if (arguments(i) != params(i).`type`) params(i) = new AsInstanceOf(params(i), arguments(i))
    }
    params
  }
  private def types(terms: Array[Term]): Array[Type] = terms.map{ term => term.`type`}
  private def typeNames(types: Array[Type]): Array[String] = types.map{ t => t.name}
  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Continuable, Method] = {
    val methods = MethodResolution.findMethods(target, name, params)
    if (methods.length > 0) {
      if (methods.length > 1) {
        report(AMBIGUOUS_METHOD, node, Array[AnyRef](methods(0).affiliation, name, methods(0).arguments), Array[AnyRef](methods(1).affiliation, name, methods(1).arguments))
        Left(false)
      } else if (!isAccessible(methods(0), definition_)) {
        report(METHOD_NOT_ACCESSIBLE, node, methods(0).affiliation, name, methods(0).arguments, definition_)
        Left(false)
      } else {
        Right(methods(0))
      }
    }else {
      Left(true)
    }
  }

  private object MethodResolution {
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
          method.arguments.map(tp => substituteType(tp, ownerViewSubst(method), scala.collection.immutable.Map.empty, defaultToBound = true))
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

  private object AppliedTypeViews {
    def collectAppliedViewsFrom(target: ClassType): scala.collection.immutable.Map[ClassType, TypedAST.AppliedClassType] =
      val views = HashMap[ClassType, TypedAST.AppliedClassType]()
      val visited = MutableSet[String]()

      def keyOf(ap: TypedAST.AppliedClassType): String =
        ap.raw.name + ap.typeArguments.map(_.name).mkString("[", ",", "]")

      def traverse(tp: ClassType, subst: scala.collection.immutable.Map[String, Type]): Unit =
        if tp == null then return
        tp match
          case ap: TypedAST.AppliedClassType =>
            val specializedArgs = ap.typeArguments.map(arg => substituteType(arg, subst, scala.collection.immutable.Map.empty, defaultToBound = false))
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
  private def matches(argTypes: Array[Type], name: String, methods: Seq[Method]): Method = {
    methods.find{m =>  name == m.name && equals(argTypes, m.arguments)}.getOrElse(null)
  }
  private def equals(ltype: Array[Type], rtype: Array[Type]): Boolean = {
    if (ltype.length != rtype.length) return false
    (for(i <- 0 until ltype.length) yield (ltype(i), rtype(i))).forall{ case (l, r) => l eq r }
  }
  private def getter(name: String): String =  "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private def getterBoolean(name: String): String =  "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private def setter(name: String): String =  "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private def promote(left: Type, right: Type): Type = {
    if (!numeric(left) || !numeric(right)) return null
    if ((left eq IRT.BasicType.DOUBLE) || (right eq IRT.BasicType.DOUBLE)) {
      return IRT.BasicType.DOUBLE
    }
    if ((left eq IRT.BasicType.FLOAT) || (right eq IRT.BasicType.FLOAT)) {
      return IRT.BasicType.FLOAT
    }
    if ((left eq IRT.BasicType.LONG) || (right eq IRT.BasicType.LONG)) {
      return IRT.BasicType.LONG
    }
    IRT.BasicType.INT
  }
  private def processNumericExpression(kind: Int, node: AST.BinaryExpression, lt: Term, rt: Term): Term = {
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
  private def promoteInteger(typeRef: Type): Type = {
    if (typeRef == IRT.BasicType.BYTE || typeRef == IRT.BasicType.SHORT || typeRef == IRT.BasicType.CHAR || typeRef == IRT.BasicType.INT) {
      return IRT.BasicType.INT
    }
    if (typeRef == IRT.BasicType.LONG) {
      return IRT.BasicType.LONG
    }
    null
  }
  private def addArgument(arg: AST.Argument, context: LocalContext): Type = {
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
