package onion.compiler

import onion.compiler.AST.AccessSection

import _root_.scala.collection.JavaConverters._
import _root_.onion.compiler.toolbox.{Boxing, Classes, Paths, Systems}
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.IRT._
import _root_.onion.compiler.IRT.BinaryTerm.Constants._
import _root_.onion.compiler.IRT.UnaryTerm.Constants._
import _root_.onion.compiler.SemanticErrorConstants._
import collection.mutable.{Stack, Buffer, Map, HashMap, Set => MutableSet}
import java.util.{Arrays, TreeSet => JTreeSet}

class Typing(config: CompilerConfig) extends AnyRef with ProcessingUnit[Array[AST.CompilationUnit], Array[ClassDefinition]] {
  class TypingEnvironment
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
    def map(typeNode: AST.TypeNode): Type = map(typeNode.desc)
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
      case AST.ParameterizedType(base, _)     => map(base)
      case AST.ArrayType(component)           =>  val (base, dimension) = split(descriptor); table_.loadArray(map(base), dimension)
    }
    private def forName(name: String, qualified: Boolean): ClassType = {
      if(qualified) {
        table_.load(name)
      }else {
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
  private val table_  = new ClassTable(classpath(config.classPath))
  private val ast2ixt_ = Map[AST.Node, IRT.Node]()
  private val ixt2ast_ = Map[IRT.Node, AST.Node]()
  private val mappers_  = Map[String, NameMapper]()
  private var access_ : Int = _
  private var mapper_ : NameMapper = _
  private var staticImportedList_ : StaticImportList = _
  private var definition_ : ClassDefinition = _
  private var unit_ : AST.CompilationUnit = _
  private val reporter_ : SemanticErrorReporter = new SemanticErrorReporter(config.maxErrorReports)
  def newEnvironment(source: Array[AST.CompilationUnit]) = new TypingEnvironment
  def doProcess(source: Array[AST.CompilationUnit], environment: TypingEnvironment): Array[ClassDefinition] = {
    for(unit <- source) processHeader(unit)
    for(unit <- source) processOutline(unit)
    for(unit <- source) processTyping(unit)
    for(unit <- source) processDuplication(unit)
    val problems = reporter_.getProblems
    if (problems.length > 0) throw new CompilationException(Arrays.asList(problems:_*))
    table_.classes.values.toList.toArray
  }

  def processHeader(unit: AST.CompilationUnit) {
    unit_ = unit
    val module = unit.module
    val imports = unit.imports
    val moduleName = if (module != null) module.name else null
    val builtinImports = Buffer[ImportItem](
      new ImportItem("*", "java.lang.*"),
      new ImportItem("*", "java.io.*"),
      new ImportItem("*", "java.util.*"),
      new ImportItem("*", "javax.swing.*"),
      new ImportItem("*", "java.awt.event.*"),
      new ImportItem("JByte", "java.lang.Byte"),
      new ImportItem("JShort", "java.lang.Short"),
      new ImportItem("JCharacter", "java.lang.Character"),
      new ImportItem("JInteger", "java.lang.Integer"),
      new ImportItem("JLong", "java.lang.Long"),
      new ImportItem("JFloat", "java.lang.Float"),
      new ImportItem("JDouble", "java.lang.Double"),
      new ImportItem("JBoolean", "java.lang.Boolean"),
      new ImportItem("*", "onion.*"),
      new ImportItem("*", if (moduleName != null) moduleName + ".*" else "*")
    )
    if(imports != null) {
      for((key, value) <- imports.mapping) {
        builtinImports.append(new ImportItem(key, value))
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
          report(DUPLICATE_CLASS, declaration, node.name)
        }else {
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(builtinImports))
        }
      case declaration: AST.InterfaceDeclaration =>
        val node = ClassDefinition.newInterface(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(DUPLICATE_CLASS, declaration, node.name)
        }else{
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(builtinImports))
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
      add(node.name, new NameMapper(builtinImports))
    }
  }
  def processOutline(unit: AST.CompilationUnit) {
    var nconstructors = 0
    unit_ = unit
    def processClassDeclaration(node: AST.ClassDeclaration) {
      nconstructors = 0
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
      constructTypeHierarchy(definition_, MutableSet[ClassType]())
      if (cyclic(definition_)) report(CYCLIC_INHERITANCE, node, definition_.name)
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
      if (nconstructors == 0) definition_.addDefaultConstructor
    }
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration) {
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
      constructTypeHierarchy(definition_, MutableSet[ClassType]())
      if (cyclic(definition_)) report(CYCLIC_INHERITANCE, node, definition_.name)
      for(method <- node.methods) processInterfaceMethodDeclaration(method)
    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration) {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return
      val modifier = node.modifiers | AST.M_PUBLIC
      val classType = loadTopClass.asInstanceOf[ClassDefinition]
      val name = node.name
      val field = new FieldDefinition(node.location, modifier, classType, name, typeRef)
      put(node, field)
      classType.add(field)
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration) {
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
    def processFieldDeclaration(node: AST.FieldDeclaration) {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return; val modifier = node.modifiers | access_
      val name = node.name
      val field = new FieldDefinition(node.location, modifier, definition_, name, typeRef)
      put(node, field)
      definition_.add(field)
    }
    def processMethodDeclaration(node: AST.MethodDeclaration) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if (node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
      for(args <- argsOption; returnType <- returnTypeOption) {
        var modifier = node.modifiers | access_
        if (node.block == null) modifier |= AST.M_ABSTRACT
        val name = node.name
        val method = new MethodDefinition(node.location, modifier, definition_, name, args.toArray, returnType, null)
        put(node, method)
        definition_.add(method)
      }
    }
    def processInterfaceMethodDeclaration(node: AST.MethodDeclaration) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if(node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
      for(args <- argsOption; returnType <- returnTypeOption) {
        val modifier = AST.M_PUBLIC | AST.M_ABSTRACT
        val name = node.name
        var method = new MethodDefinition(node.location, modifier, definition_, name, args.toArray, returnType, null)
        put(node, method)
        definition_.add(method)
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration) {
      nconstructors += 1
      val argsOption = typesOf(node.args)
      for(args <- argsOption) {
        val modifier = node.modifiers | access_
        val constructor = new ConstructorDefinition(node.location, modifier, definition_, args.toArray, null, null)
        put(node, constructor)
        definition_.add(constructor)
      }
    }
    def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration) {
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
      val typeRef = if(node == null) table_.rootClass else mapFrom(node, mapper).asInstanceOf[ClassType]
      if (typeRef == null) return null
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
    def constructTypeHierarchy(node: ClassType, visit: MutableSet[ClassType]) {
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
  def processTyping(node: AST.CompilationUnit) {
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
    def processMethodDeclaration(node: AST.MethodDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (node.block == null) return
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
    def processConstructorDeclaration(node: AST.ConstructorDeclaration) {
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
    def processClassDeclaration(node: AST.ClassDeclaration, context: LocalContext) {
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
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
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration, context: LocalContext) { () }
    def processFunctionDeclaration(node: AST.FunctionDeclaration, context: LocalContext) {
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
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration, context: LocalContext){()}
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
    def processThisFieldAssign(node: AST.Assignment, context: LocalContext): Term = {
      var value = typed(node.rhs, context).getOrElse(null)
      if (value == null) return null
      val ref = node.lhs.asInstanceOf[AST.UnqualifiedFieldReference]
      var selfClass: ClassType = null
      if (context.isGlobal) {
        selfClass = loadTopClass
      } else {
        if (context.method != null) {
          selfClass = context.method.affiliation
        }
        else {
          selfClass = context.constructor.affiliation
        }
      }
      val field: FieldRef = findField(selfClass, ref.name)
      if (field == null) {
        report(FIELD_NOT_FOUND, ref, selfClass, ref.name)
        return null
      }
      if (!isAccessible(field, selfClass)) {
        report(FIELD_NOT_ACCESSIBLE, node, field.affiliation, field.name, selfClass)
        return null
      }
      value = processAssignable(node.rhs, field.`type`, value)
      if (value != null) new SetField(new This(selfClass), field, value) else null
    }
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
      typed(node.rhs, context)
      report(UNIMPLEMENTED_FEATURE, node)
      null
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
          case _ : AST.UnqualifiedFieldReference =>
            Option(processThisFieldAssign(node, context))
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
          return Some(new RefField(target, field))
        }
        tryFindMethod(node, targetType, name, new Array[Term](0)) match {
          case Right(method) =>
            return Some(new Call(target, method, new Array[Term](0)))
          case Left(continuable) =>
            if(!continuable) return None
        }
        tryFindMethod(node, targetType, getter(name), new Array[Term](0)) match {
          case Right(method) =>
            return Some(new Call(target, method, new Array[Term](0)))
          case Left(continuable) =>
            if(!continuable) return None
        }
        tryFindMethod(node, targetType, getterBoolean(name), new Array[Term](0)) match {
          case Right(method) =>
            Some(new Call(target, method, new Array[Term](0)))
          case Left(_) =>
            if (field == null) {
              report(FIELD_NOT_FOUND, node, targetType, node.name)
            } else {
              report(FIELD_NOT_ACCESSIBLE, node, targetType, node.name, definition_)
            }
            None
        }
      case node@AST.MethodCall(loc, target, name, args) =>
        val target = typed(node.target, context).getOrElse(null)
        if (target == null) return None
        val params = typedTerms(node.args.toArray, context)
        if (params == null) return None
        val targetType = target.`type`.asInstanceOf[ObjectType]
        val name = node.name
        val methods = targetType.findMethod(name, params)
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
          Some(new Call(target, methods(0), doCastInsertion(methods(0).arguments, params)))
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
          Some(new NewObject(constructors(0), parameters))
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
      case node@AST.UnqualifiedFieldReference(loc, name) =>
        if (context.isStatic) return None
        val selfClass = definition_
        val field = findField(selfClass, node.name)
        if (field == null) {
          report(FIELD_NOT_FOUND, node, selfClass, node.name)
          None
        }else if (!isAccessible(field, selfClass)) {
          report(FIELD_NOT_ACCESSIBLE, node, field.affiliation, node.name, selfClass)
          None
        }else {
          Some(new RefField(new This(selfClass), field))
        }
      case node@AST.UnqualifiedMethodCall(loc, name, args) =>
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
          params = doCastInsertion(methods(0).arguments, params)
          if ((methods(0).modifier & AST.M_STATIC) != 0) {
            Some(new CallStatic(targetType, methods(0), params))
          } else {
            if(context.isClosure) {
              Some(new Call(new OuterThis(targetType), methods(0), params))
            }else {
              Some(new Call(new This(targetType), methods(0), params))
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
      case node@AST.StaticMethodCall(loc, _, _, _) =>
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
            Some(new CallStatic(typeRef, methods(0), doCastInsertion(methods(0).arguments, parameters)))
          }
        }
      case node@AST.StringLiteral(loc, value) =>
        Some(new StringValue(loc, value, load("java.lang.String")))
      case node@AST.SuperMethodCall(loc, _, _) =>
        val parameters = typedTerms(node.args.toArray, context)
        if (parameters == null) return None
        val contextClass = definition_
        tryFindMethod(node, contextClass.superClass, node.name, parameters) match {
          case Right(method) => Some(new CallSuper(new This(contextClass), method, parameters))
          case Left(_) =>
            report(METHOD_NOT_FOUND, node, contextClass, node.name, types(parameters))
            None
        }
    }
    def translate(node: AST.CompoundExpression, context: LocalContext): ActionStatement = node match {
      case AST.BlockExpression(loc, elements) =>
        context.openScope {
          new StatementBlock(elements.map{e => translate(e, context)}.toArray:_*)
        }
      case node@AST.BreakExpression(loc) =>
        report(UNIMPLEMENTED_FEATURE, node)
        new Break(loc)
      case node@AST.ContinueExpression(loc) =>
        report(UNIMPLEMENTED_FEATURE, node)
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
  def processDuplication(node: AST.CompilationUnit) {
    val methods = new JTreeSet[Method](new MethodComparator)
    val fields = new JTreeSet[FieldRef](new FieldComparator)
    val constructors = new JTreeSet[ConstructorRef](new ConstructorComparator)
    val variables = new JTreeSet[FieldRef](new FieldComparator)
    val functions = new JTreeSet[Method](new MethodComparator)
    def processFieldDeclaration(node: AST.FieldDeclaration) {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processMethodDeclaration(node: AST.MethodDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration) {
      val constructor = lookupKernelNode(node).asInstanceOf[ConstructorDefinition]
      if (constructor == null) return
      if (constructors.contains(constructor)) {
        report(DUPLICATE_CONSTRUCTOR, node, constructor.affiliation, constructor.getArgs)
      } else {
        constructors.add(constructor)
      }
    }
    def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration) {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processInterfaceMethodDeclaration(node: AST.MethodDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def generateMethods() {
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
      def generateDelegationMethods(node: FieldDefinition) {
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
    def processAccessSection(node: AST.AccessSection) {
      for(member <- node.members) member match {
        case node: AST.FieldDeclaration => processFieldDeclaration(node)
        case node: AST.MethodDeclaration => processMethodDeclaration(node)
        case node: AST.ConstructorDeclaration => processConstructorDeclaration(node)
        case node: AST.DelegatedFieldDeclaration => processDelegatedFieldDeclaration(node)
      }
    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration) {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return
      if (variables.contains(field)) {
        report(DUPLICATE_GLOBAL_VARIABLE, node, field.name)
      }else {
        variables.add(field)
      }
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return
      if (functions.contains(method)) {
        report(DUPLICATE_FUNCTION, node, method.name, method.arguments)
      } else {
        functions.add(method)
      }
    }
    def processClassDeclaration(node: AST.ClassDeclaration) {
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
    }
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration) {
      val clazz = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      if (clazz == null) return
      methods.clear()
      fields.clear()
      constructors.clear()
      definition_ = clazz
      mapper_ = find(clazz.name)
      for (node <- node.methods) processInterfaceMethodDeclaration(node)
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
  def report(error: Int, node: AST.Node, items: AnyRef*) {
    report(error, node.location, items:_*)
  }
  def report(error: Int, location: Location, items: AnyRef*) {
    def report_(items: Array[AnyRef]) {
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
  private def put(astNode: AST.Node, kernelNode: Node) {
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
    val mappedType = mapper.map(typeNode)
    if (mappedType == null) report(CLASS_NOT_FOUND, typeNode, typeNode.desc.toString)
    mappedType
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
    val methods = target.findMethod(name, params)
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
    val methods = target.findMethod(name, params)
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
