package onion.compiler
import _root_.java.util.{TreeSet => JTreeSet}
import _root_.scala.collection.JavaConversions._
import _root_.onion.compiler.util.{Boxing, Classes, Paths, Systems}
import _root_.onion.compiler.SemanticErrorReporter.Constants._
import _root_.onion.compiler.IxCode.BinaryExpression.Constants._
import _root_.onion.compiler.IxCode.UnaryExpression.Constants._
import collection.mutable.{Stack, Buffer, Map, HashMap, Set => MutableSet}

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 10:46:42
 * To change this template use File | Settings | File Templates.
 */
class Typing(config: CompilerConfig) extends AnyRef with ProcessingUnit[Array[AST.CompilationUnit], Array[IxCode.ClassDefinition]] {
  class TypingEnvironment
  type Environment = TypingEnvironment
  type Dimension = Int
  private def split(descriptor: AST.TypeDescriptor): (AST.TypeDescriptor, Dimension) = {
    def loop(target: AST.TypeDescriptor, dimension: Int): (AST.TypeDescriptor, Int) = target match {
      case AST.ArrayType(component) => loop(component, dimension + 1)
      case otherwise => (otherwise, dimension)
    }
    loop(descriptor, 0)
  }
  private class NameMapper(imports: ImportList) {
    def map(typeNode: AST.TypeNode): IxCode.TypeRef = map(typeNode.desc)
    def map(descriptor : AST.TypeDescriptor): IxCode.TypeRef = descriptor match {
      case AST.PrimitiveType(AST.KChar)       => IxCode.BasicTypeRef.CHAR
      case AST.PrimitiveType(AST.KByte)       => IxCode.BasicTypeRef.BYTE
      case AST.PrimitiveType(AST.KShort)      => IxCode.BasicTypeRef.SHORT
      case AST.PrimitiveType(AST.KInt)        => IxCode.BasicTypeRef.INT
      case AST.PrimitiveType(AST.KLong)       => IxCode.BasicTypeRef.LONG
      case AST.PrimitiveType(AST.KFloat)      => IxCode.BasicTypeRef.FLOAT
      case AST.PrimitiveType(AST.KDouble)     => IxCode.BasicTypeRef.DOUBLE
      case AST.PrimitiveType(AST.KBoolean)    => IxCode.BasicTypeRef.BOOLEAN
      case AST.PrimitiveType(AST.KVoid)       => IxCode.BasicTypeRef.VOID
      case AST.ReferenceType(name, qualified) => forName(name, qualified)
      case AST.ParameterizedType(base, _)     => map(base)
      case AST.ArrayType(component)           =>  val (base, dimension) = split(descriptor); table_.loadArray(map(base), dimension)
    }
    private def forName(name: String, qualified: Boolean): IxCode.ClassTypeRef = {
      if(qualified) {
        return table_.load(name);
      }else {
        for(item <- imports) {
          val qname = item `match` name
          if(qname != null) {
            val mappedType = forName(qname, true)
            if(mappedType != null) return mappedType
          }
        }
        return null
      }
    }
  }
  private val table_  = new ClassTable(classpath(config.getClassPath))
  private val ast2ixt_ = Map[AST.Node, IxCode.Node]()
  private var ixt2ast_ = Map[IxCode.Node, AST.Node]()
  private var mappers_  = Map[String, NameMapper]()
  private var access_ : Int = _
  private var mapper_ : NameMapper = _
  private var importedList_ : ImportList = _
  private var staticImportedList_ : StaticImportList = _
  private var definition_ : IxCode.ClassDefinition = _
  private var unit_ : AST.CompilationUnit = _
  private val reporter_ : SemanticErrorReporter = new SemanticErrorReporter(config.getMaxErrorReports)
  def newEnvironment(source: Array[AST.CompilationUnit]) = new TypingEnvironment
  def doProcess(source: Array[AST.CompilationUnit], environment: TypingEnvironment): Array[IxCode.ClassDefinition] = {
    for(unit <- source) processHeader(unit)
    for(unit <- source) processOutline(unit)
    for(unit <- source) processTyping(unit)
    for(unit <- source) processDuplication(unit)
    table_.classes.values.toList.toArray
  }

  def processHeader(unit: AST.CompilationUnit) {
    unit_ = unit
    val module = unit.module
    val imports = unit.imports
    val moduleName = if (module != null) module.name else null
    val list = new ImportList
    list.add(new ImportItem("*", "java.lang.*"))
    list.add(new ImportItem("*", "java.io.*"))
    list.add(new ImportItem("*", "java.util.*"))
    list.add(new ImportItem("*", "javax.swing.*"))
    list.add(new ImportItem("*", "java.awt.event.*"))
    list.add(new ImportItem("*", "onion.*"))
    list.add(new ImportItem("*", if (moduleName != null) moduleName + ".*" else "*"))
    for((key, value) <- imports.mapping) {
      list.add(new ImportItem(key, value))
    }
    val staticList = new StaticImportList
    staticList.add(new StaticImportItem("java.lang.System", true))
    staticList.add(new StaticImportItem("java.lang.Runtime", true))
    staticList.add(new StaticImportItem("java.lang.Math", true))
    importedList_ = list
    staticImportedList_ = staticList
    var count = 0
    for(top <- unit.toplevels) top match {
      case declaration: AST.ClassDeclaration =>
        val node = IxCode.ClassDefinition.newClass(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null, null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(DUPLICATE_CLASS, declaration, node.name)
        }else {
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(importedList_))
        }
      case declaration: AST.InterfaceDeclaration =>
        val node = IxCode.ClassDefinition.newInterface(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(DUPLICATE_CLASS, declaration, node.name)
        }else{
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(importedList_))
        }
      case otherwise =>
        count += 1
    }
    if (count > 0) {
      val node = IxCode.ClassDefinition.newClass(unit.location, 0, topClass, table_.rootClass, new Array[IxCode.ClassTypeRef](0))
      node.setSourceFile(Paths.nameOf(unit_.sourceFile))
      node.setResolutionComplete(true)
      table_.classes.add(node)
      node.addDefaultConstructor()
      put(unit, node)
      add(node.name, new NameMapper(list))
    }
  }
  def processOutline(unit: AST.CompilationUnit) {
    var nconstructors = 0
    unit_ = unit
    def processClassDeclaration(node: AST.ClassDeclaration) {
      nconstructors = 0
      definition_ = lookupKernelNode(node).asInstanceOf[IxCode.ClassDefinition]
      mapper_ = find(definition_.name)
      constructTypeHierarchy(definition_, MutableSet[IxCode.ClassTypeRef]())
      if (cyclic(definition_)) report(CYCLIC_INHERITANCE, node, definition_.name)
      if(node.defaultSection != null) {
        access_ = node.defaultSection.modifiers
        for(member <- node.defaultSection.members) member match {
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
      definition_ = lookupKernelNode(node).asInstanceOf[IxCode.ClassDefinition]
      mapper_ = find(definition_.name)
      constructTypeHierarchy(definition_, MutableSet[IxCode.ClassTypeRef]())
      if (cyclic(definition_)) report(CYCLIC_INHERITANCE, node, definition_.name)
      for(method <- node.methods) processInterfaceMethodDeclaration(method)
    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration) {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return null
      val modifier = node.modifiers | AST.M_PUBLIC
      val classType = loadTopClass.asInstanceOf[IxCode.ClassDefinition]
      val name = node.name
      val field = new IxCode.FieldDefinition(node.location, modifier, classType, name, typeRef)
      put(node, field)
      classType.add(field)
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if(node.returnType != null) mapFrom(node.returnType) else IxCode.BasicTypeRef.VOID)
      for(args <- argsOption; returnType <- returnTypeOption) {
        val classType= loadTopClass.asInstanceOf[IxCode.ClassDefinition]
        val modifier = node.modifiers | AST.M_PUBLIC
        val name = node.name
        val method = new IxCode.MethodDefinition(node.location, modifier, classType, name, args.toArray, returnType, null)
        put(node, method)
        classType.add(method)
      }
    }
    def processFieldDeclaration(node: AST.FieldDeclaration) {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return null
      val modifier = node.modifiers | access_
      val name = node.name
      val field = new IxCode.FieldDefinition(node.location, modifier, definition_, name, typeRef)
      put(node, field)
      definition_.add(field)
    }
    def processMethodDeclaration(node: AST.MethodDeclaration) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if (node.returnType != null) mapFrom(node.returnType) else IxCode.BasicTypeRef.VOID)
      for(args <- argsOption; returnType <- returnTypeOption) {
        var modifier = node.modifiers | access_
        if (node.block == null) modifier |= AST.M_ABSTRACT
        val name = node.name
        val method = new IxCode.MethodDefinition(node.location, modifier, definition_, name, args.toArray, returnType, null)
        put(node, method)
        definition_.add(method)
      }
    }
    def processInterfaceMethodDeclaration(node: AST.MethodDeclaration) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if(node.returnType != null) mapFrom(node.returnType) else IxCode.BasicTypeRef.VOID)
      for(args <- argsOption; returnType <- returnTypeOption) {
        val modifier = AST.M_PUBLIC | AST.M_ABSTRACT
        val name = node.name
        var method = new IxCode.MethodDefinition(node.location, modifier, definition_, name, args.toArray, returnType, null)
        put(node, method)
        definition_.add(method)
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration) {
      nconstructors += 1
      val argsOption = typesOf(node.args)
      for(args <- argsOption) {
        val modifier = node.modifiers | access_
        val constructor = new IxCode.ConstructorDefinition(node.location, modifier, definition_, args.toArray, null, null)
        put(node, constructor)
        definition_.add(constructor)
      }
    }
    def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration) {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return null
      if (!(typeRef.isObjectType && (typeRef.asInstanceOf[IxCode.ObjectTypeRef]).isInterface)) {
        report(INTERFACE_REQUIRED, node, typeRef)
        return null
      }
      val modifier = node.modifiers | access_ | AST.M_FORWARDED
      var name = node.name
      var field = new IxCode.FieldDefinition(node.location, modifier, definition_, name, typeRef)
      put(node, field)
      definition_.add(field)
    }
    def cyclic(start: IxCode.ClassDefinition): Boolean = {
      def loop(node: IxCode.ClassTypeRef, visit: Set[IxCode.ClassTypeRef]): Boolean = {
        if(node == null) return false
        if(visit.contains(node)) return true
        val newVisit = visit + node
        if(loop(node.superClass, newVisit)) return true
        for(interface <- node.interfaces) if(loop(interface, newVisit)) return true
        return false
      }
      loop(start, Set[IxCode.ClassTypeRef]())
    }
    def validateSuperType(node: AST.TypeNode, mustBeInterface: Boolean, mapper: NameMapper): IxCode.ClassTypeRef = {
      val typeRef = if(node == null) table_.rootClass else mapFrom(node, mapper).asInstanceOf[IxCode.ClassTypeRef]
      if (typeRef == null) return null
      val isInterface = typeRef.isInterface
      if (((!isInterface) && mustBeInterface) || (isInterface && (!mustBeInterface))) {
        var location: Location = null
        if (typeRef.isInstanceOf[IxCode.ClassDefinition]) {
          location = typeRef.asInstanceOf[IxCode.ClassDefinition].location
        }
        report(ILLEGAL_INHERITANCE, location, typeRef.name)
      }
      typeRef
    }
    def constructTypeHierarchy(node: IxCode.ClassTypeRef, visit: MutableSet[IxCode.ClassTypeRef]) {
      if(node == null || visit.contains(node)) return
      visit += node
      node match {
        case node: IxCode.ClassDefinition =>
          if (node.isResolutionComplete) return
          val interfaces = Buffer[IxCode.ClassTypeRef]()
          val resolver = find(node.name)
          var superClass: IxCode.ClassTypeRef = null
          if (node.isInterface) {
            val ast = lookupAST(node).asInstanceOf[AST.InterfaceDeclaration]
            val superClass = rootClass
            for (typeSpec <- ast.superInterfaces) {
              val superType = validateSuperType(typeSpec, true, resolver)
              if (superType != null) interfaces += superType
            }
          }else {
            val ast = lookupAST(node).asInstanceOf[AST.ClassDeclaration]
            val superClass = validateSuperType(ast.superClass, false, resolver)
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
    def processNodes(nodes: Array[AST.Expression], typeRef: IxCode.TypeRef, bind: ClosureLocalBinding, context: LocalContext): IxCode.Expression = {
      val expressions = new Array[IxCode.Expression](nodes.length)
      var error: Boolean = false
      for(i <- 0 until nodes.length){
        val expressionOpt = typed(nodes(i), context)
        expressions(i) = expressionOpt.getOrElse(null)
        if(expressions(i) == null) {
          error = true
        } else if (!IxCode.TypeRules.isAssignable(typeRef, expressions(i).`type`)) {
          report(INCOMPATIBLE_TYPE, nodes(i), typeRef, expressions(i).`type`)
          error = true
        } else {
          if (expressions(i).isBasicType && expressions(i).`type` != typeRef) expressions(i) = new IxCode.AsInstanceOf(expressions(i), typeRef)
          if (expressions(i).isReferenceType && expressions(i).`type` != rootClass) expressions(i) = new IxCode.AsInstanceOf(expressions(i), rootClass)
        }
      }
      if (!error) {
        var node: IxCode.Expression = if(expressions(0).isReferenceType) {
          createEquals(IxCode.BinaryExpression.Constants.EQUAL, new IxCode.RefLocal(bind), expressions(0))
        } else {
          new IxCode.BinaryExpression(EQUAL, IxCode.BasicTypeRef.BOOLEAN, new IxCode.RefLocal(bind), expressions(0))
        }
        for(i <- 1 until expressions.length) {
          node = new IxCode.BinaryExpression(LOGICAL_OR, IxCode.BasicTypeRef.BOOLEAN, node, new IxCode.BinaryExpression(EQUAL, IxCode.BasicTypeRef.BOOLEAN, new IxCode.RefLocal(bind), expressions(i)))
        }
        node
      } else {
        null
      }
    }
    def processAssignable(node: AST.Node, a: IxCode.TypeRef, b: IxCode.Expression): IxCode.Expression = {
      if (b == null) return null
      if (a == b.`type`) return b
      if (!IxCode.TypeRules.isAssignable(a, b.`type`)) {
        report(INCOMPATIBLE_TYPE, node, a, b.`type`)
        return null
      }
      new IxCode.AsInstanceOf(node.location, b, a)
    }
    def openScope[A](context: LocalContext)(block: => A): A = try {
      context.openScope()
      block
    } finally {
      context.closeScope()
    }
    def openFrame[A](context: LocalContext)(block: => A): A = try {
      context.openFrame()
      block
    } finally {
      context.closeFrame
    }
    def processClassDeclaration(node: AST.ClassDeclaration, context: LocalContext) {

    }
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration, context: LocalContext) {

    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration, context: LocalContext) {

    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration, context: LocalContext){

    }
    def typed(node: AST.Expression, context: LocalContext): Option[IxCode.Expression] = {
      null //TODO Implement this method
    }
    def translate(node: AST.Statement, context: LocalContext): IxCode.ActionStatement = node match {
      case AST.BlockStatement(loc, elements) =>
        openScope(context){
          new IxCode.StatementBlock(elements.map{e => translate(e, context)}.toArray:_*)
        }
      case node@AST.BreakStatement(loc) =>
        report(UNIMPLEMENTED_FEATURE, node)
        new IxCode.Break(loc)
      case node@AST.BranchStatement(loc, _, _) =>
        openScope(context) {
          val size = node.clauses.size
          val expressions = new Stack[IxCode.Expression]
          val statements = new Stack[IxCode.ActionStatement]
          for((expression, statement) <- node.clauses) {
            val typedExpression = typed(expression, context).getOrElse(null)
            if (typedExpression != null && typedExpression.`type` != IxCode.BasicTypeRef.BOOLEAN) {
              val expect = IxCode.BasicTypeRef.BOOLEAN
              val actual = typedExpression.`type`
              report(INCOMPATIBLE_TYPE, expression, expect, actual)
            }
            expressions.push(typedExpression)
            statements.push(translate(statement, context))
          }
          val elseStatement = node.elseBlock
          var result: IxCode.ActionStatement = null
          if (elseStatement != null) {
            result = translate(elseStatement, context)
          }
          for(i <- 0 until size) {
            result = new IxCode.IfStatement(expressions.pop, statements.pop, result)
          }
          return result
        }
      case node@AST.ContinueStatement(loc) =>
        report(UNIMPLEMENTED_FEATURE, node)
        new IxCode.Continue(loc)
      case node@AST.EmptyStatement(loc) =>
        new IxCode.NOP(loc)
      case node@AST.ExpressionStatement(loc, body) =>
        typed(body, context).map{e =>  new IxCode.ExpressionStatement(loc, e)}.getOrElse(new IxCode.NOP(loc))
      case node@AST.ForeachStatement(loc, _, _, _) =>
        openScope(context) {
          val collection = typed(node.collection, context).getOrElse(null)
          val arg = node.arg
          mapFrom(arg.typeRef)
          var block = translate(node.statement, context)
          if (collection.isBasicType) {
            report(INCOMPATIBLE_TYPE, node.collection, load("java.util.Collection"), collection.`type`)
            return new IxCode.NOP(node.location)
          }
          val elementVar = context.lookupOnlyCurrentScope(arg.name)
          val collectionVar = new ClosureLocalBinding(0, context.add(context.newName, collection.`type`), collection.`type`)
          var init: IxCode.ActionStatement = null
          if (collection.isArrayType) {
            val counterVariable = new ClosureLocalBinding(0, context.add(context.newName, IxCode.BasicTypeRef.INT), IxCode.BasicTypeRef.INT)
            init = new IxCode.StatementBlock(new IxCode.ExpressionStatement(new IxCode.SetLocal(collectionVar, collection)), new IxCode.ExpressionStatement(new IxCode.SetLocal(counterVariable, new IxCode.IntLiteral(0))))
            block = new IxCode.ConditionalLoop(new IxCode.BinaryExpression(LESS_THAN, IxCode.BasicTypeRef.BOOLEAN, ref(counterVariable), new IxCode.ArrayLength(ref(collectionVar))), new IxCode.StatementBlock(assign(elementVar, indexref(collectionVar, ref(counterVariable))), block, assign(counterVariable, new IxCode.BinaryExpression(ADD, IxCode.BasicTypeRef.INT, ref(counterVariable), new IxCode.IntLiteral(1)))))
            new IxCode.StatementBlock(init, block)
          }
          else {
            val iteratorType = load("java.util.Iterator")
            var iteratorVar = new ClosureLocalBinding(0, context.add(context.newName, iteratorType), iteratorType)
            var mIterator = findMethod(node.collection, collection.`type`.asInstanceOf[IxCode.ObjectTypeRef], "iterator")
            var mNext: IxCode.MethodRef = findMethod(node.collection, iteratorType, "next")
            var mHasNext: IxCode.MethodRef = findMethod(node.collection, iteratorType, "hasNext")
            init = new IxCode.StatementBlock(new IxCode.ExpressionStatement(new IxCode.SetLocal(collectionVar, collection)), assign(iteratorVar, new IxCode.Call(ref(collectionVar), mIterator, new Array[IxCode.Expression](0))))
            var next: IxCode.Expression = new IxCode.Call(ref(iteratorVar), mNext, new Array[IxCode.Expression](0))
            if (elementVar.getType != rootClass) {
              next = new IxCode.AsInstanceOf(next, elementVar.getType)
            }
            block = new IxCode.ConditionalLoop(new IxCode.Call(ref(iteratorVar), mHasNext, new Array[IxCode.Expression](0)), new IxCode.StatementBlock(assign(elementVar, next), block))
            new IxCode.StatementBlock(init, block)
          }
        }
      case node@AST.ForStatement(loc, _, _, _, _) =>
        openScope(context) {
          val init = Option(node.init).map{init => translate(init, context)}.getOrElse(new IxCode.NOP(loc))
          val condition = (for(c <- Option(node.condition)) yield {
            val conditionOpt = typed(c, context)
            val expected = IxCode.BasicTypeRef.BOOLEAN
            for(condition <- conditionOpt; if condition.`type` != expected) {
              report(INCOMPATIBLE_TYPE, node.condition, condition.`type`, expected)
            }
            conditionOpt.getOrElse(null)
          }).getOrElse(new IxCode.BoolLiteral(loc, true))
          val update = Option(node.update).flatMap{update => typed(update, context)}.getOrElse(null)
          var loop = translate(node.block, context)
          if(update != null) loop = new IxCode.StatementBlock(loop, new IxCode.ExpressionStatement(update))
          new IxCode.StatementBlock(init.location, init, new IxCode.ConditionalLoop(condition, loop))
        }
      case node@AST.IfStatement(loc, _, _, _) => 
        openScope(context) {
          val conditionOpt = typed(node.condition, context)
          val expected = IxCode.BasicTypeRef.BOOLEAN
          for(condition <- conditionOpt if condition.`type` != expected) {
            report(INCOMPATIBLE_TYPE, node.condition, expected, condition.`type`)
          }
          val thenBlock = translate(node.thenBlock, context)
          val elseBlock = if (node.elseBlock == null) null else translate(node.elseBlock, context)
          conditionOpt.map{c => new IxCode.IfStatement(c, thenBlock, elseBlock)}.getOrElse(new IxCode.NOP(loc))
        }
      case node@AST.LocalVariableDeclaration(loc, name, typeRef, init) =>
        val binding = context.lookupOnlyCurrentScope(name)
        if (binding != null) {
          report(DUPLICATE_LOCAL_VARIABLE, node, name)
          return new IxCode.NOP(loc)
        }
        val lhsType = mapFrom(node.typeRef)
        if (lhsType == null) return new IxCode.NOP(loc)
        val index = context.add(name, lhsType)
        var local: IxCode.SetLocal = null
        if (init != null) {
          var valueNode = typed(init, context)
          valueNode match {
            case None => return new IxCode.NOP(loc)
            case Some(v) =>
              val value = processAssignable(init, lhsType, v)
              if(value == null) return new IxCode.NOP(loc)
              local = new IxCode.SetLocal(loc, 0, index, lhsType, value)
          }
        }
        else {
          local = new IxCode.SetLocal(loc, 0, index, lhsType, defaultValue(lhsType))
        }
        new IxCode.ExpressionStatement(local)
      case node@AST.ReturnStatement(loc, _) =>
        val returnType = context.returnType
        if(node.result == null) {
          val expected  = IxCode.BasicTypeRef.VOID
          if (returnType != expected) report(CANNOT_RETURN_VALUE, node)
          return new IxCode.Return(loc, null)
        } else {
          val returnedOpt= typed(node.result, context)
          if (returnedOpt == null) return new IxCode.Return(loc, null)
          (for(returned <- returnedOpt) yield {
            if (returned.`type` == IxCode.BasicTypeRef.VOID) {
              report(CANNOT_RETURN_VALUE, node)
              new IxCode.Return(loc, null)
            } else {
              val value = processAssignable(node.result, returnType, returned)
              if (value == null) return new IxCode.Return(loc, null)
              new IxCode.Return(loc, value)
            }
          }).getOrElse(new IxCode.Return(loc, null))
        }
      case node@AST.SelectStatement(loc, _, _, _) =>
        val conditionOpt = typed(node.condition, context)
        if(conditionOpt == None) return new IxCode.NOP(loc)
        val condition = conditionOpt.get
        val name = context.newName
        val index = context.add(name, condition.`type`)
        val statement = if(node.cases.length == 0) {
          Option(node.elseBlock).map{e => translate(e, context)}.getOrElse(new IxCode.NOP(loc))
        }else {
          val cases = node.cases
          val nodes = Buffer[IxCode.Expression]()
          val thens = Buffer[IxCode.ActionStatement]()
          for((expressions, then)<- cases) {
            val bind = context.lookup(name)
            nodes += processNodes(expressions.toArray, condition.`type`, bind, context)
            thens += translate(then, context)
          }
          var branches: IxCode.ActionStatement = if(node.elseBlock != null) {
            translate(node.elseBlock, context)
          }else {
            null
          }
          for(i <- (cases.length - 1) to (0, -1)) {
            branches = new IxCode.IfStatement(nodes(i), thens(i), branches)
          }
          branches
        }
        new IxCode.StatementBlock(condition.location, new IxCode.ExpressionStatement(condition.location, new IxCode.SetLocal(0, index, condition.`type`, condition)), statement)
      case node@AST.SynchronizedStatement(loc, _, _) =>
        openScope(context) {
          val lock = typed(node.condition, context).getOrElse(null)
          val block = translate(node.block, context)
          report(UNIMPLEMENTED_FEATURE, node)
          new IxCode.Synchronized(node.location, lock, block)
        }
      case node@AST.ThrowStatement(loc, target) => null
        val expressionOpt = typed(target, context)
        for(expression <- expressionOpt) {
          val expected = load("java.lang.Throwable")
          val detected = expression.`type`
          if (!IxCode.TypeRules.isSuperType(expected, detected)) {
            report(INCOMPATIBLE_TYPE, node, expected, detected)
          }
        }
        new IxCode.Throw(loc, expressionOpt.getOrElse(null))
      case node@AST.TryStatement(loc, tryBlock, recClauses, finBlock) =>
        val tryStatement = translate(tryBlock, context)
        val binds = new Array[ClosureLocalBinding](recClauses.length)
        val catchBlocks = new Array[IxCode.ActionStatement](recClauses.length)
        for(i <- 0 until recClauses.length) {
          val (argument, body) = recClauses(i)
          openScope(context) {
            val argType = mapFrom(argument.typeRef)
            val expected = load("java.lang.Throwable")
            if (!IxCode.TypeRules.isSuperType(expected, argType)) {
              report(INCOMPATIBLE_TYPE, argument, expected, argType)
            }
            binds(i) = context.lookupOnlyCurrentScope(argument.name)
            catchBlocks(i) = translate(body, context)
          }
        }
        new IxCode.Try(loc, tryStatement, binds, catchBlocks)
      case node@AST.WhileStatement(loc, _, _) =>
        openScope(context) {
          val conditionOpt = typed(node.condition, context)
          val expected = IxCode.BasicTypeRef.BOOLEAN
          for(condition <- conditionOpt) {
            val actual = condition.`type`
            if(actual != expected)  report(INCOMPATIBLE_TYPE, node, expected, actual)
          }
          val thenBlock = translate(node.block, context)
          new IxCode.ConditionalLoop(loc, conditionOpt.getOrElse(null), thenBlock)
        }
    }
    def defaultValue(typeRef: IxCode.TypeRef): IxCode.Expression = IxCode.Expression.defaultValue(typeRef)
    def addReturnNode(node: IxCode.ActionStatement, returnType: IxCode.TypeRef): IxCode.StatementBlock = {
      return new IxCode.StatementBlock(node, new IxCode.Return(defaultValue(returnType)))
    }
    def createMain(top: IxCode.ClassTypeRef, ref: IxCode.MethodRef, name: String, args: Array[IxCode.TypeRef], ret: IxCode.TypeRef): IxCode.MethodDefinition = {
      val method = new IxCode.MethodDefinition(null, AST.M_STATIC | AST.M_PUBLIC, top, name, args, ret, null)
      val frame = new LocalFrame(null)
      val params = new Array[IxCode.Expression](args.length)
      for(i <- 0 until args.length) {
        val arg = args(i)
        val index = frame.add("args" + i, arg)
        params(i) = new IxCode.RefLocal(0, index, arg)
      }
      method.setFrame(frame)
      val constructor = top.findConstructor(new Array[IxCode.Expression](0))(0)
      var block = new IxCode.StatementBlock(new IxCode.ExpressionStatement(new IxCode.Call(new IxCode.NewObject(constructor, new Array[IxCode.Expression](0)), ref, params)))
      block = addReturnNode(block, IxCode.BasicTypeRef.VOID)
      method.setBlock(block)
      method
    }
    unit_ = node
    val toplevels = node.toplevels
    val context = new LocalContext
    val statements = Buffer[IxCode.ActionStatement]()
    mapper_ = find(topClass)
    val klass = loadTopClass.asInstanceOf[IxCode.ClassDefinition]
    val argsType = loadArray(load("java.lang.String"), 1)
    val method= new IxCode.MethodDefinition(node.location, AST.M_PUBLIC, klass, "start", Array[IxCode.TypeRef](argsType), IxCode.BasicTypeRef.VOID, null)
    context.add("args", argsType)
    for (element <- toplevels) {
      if(!element.isInstanceOf[AST.TypeDeclaration]) definition_ = klass;
      if(element.isInstanceOf[AST.Statement]){
        context.setMethod(method)
        statements += translate(element.asInstanceOf[AST.Statement], context)
      }else {
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
      statements += new IxCode.Return(null)
      method.setBlock(new IxCode.StatementBlock(statements))
      method.setFrame(context.getContextFrame)
      klass.add(method)
      klass.add(createMain(klass, method, "main", Array[IxCode.TypeRef](argsType), IxCode.BasicTypeRef.VOID))
    }
  }
  def processDuplication(node: AST.CompilationUnit) {
    val methods = new JTreeSet[IxCode.MethodRef](new IxCode.MethodRefComparator)
    val fields = new JTreeSet[IxCode.FieldRef](new IxCode.FieldRefComparator)
    val constructors = new JTreeSet[IxCode.ConstructorRef](new IxCode.ConstructorRefComparator)
    val variables = new JTreeSet[IxCode.FieldRef](new IxCode.FieldRefComparator)
    val functions = new JTreeSet[IxCode.MethodRef](new IxCode.MethodRefComparator)
    def processFieldDeclaration(node: AST.FieldDeclaration) {
      val field = lookupKernelNode(node).asInstanceOf[IxCode.FieldDefinition]
      if (field == null) return null
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processMethodDeclaration(node: AST.MethodDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[IxCode.MethodDefinition]
      if (method == null) return null
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration) {
      val constructor = lookupKernelNode(node).asInstanceOf[IxCode.ConstructorDefinition]
      if (constructor == null) return null
      if (constructors.contains(constructor)) {
        report(DUPLICATE_CONSTRUCTOR, node, constructor.affiliation, constructor.getArgs)
      } else {
        constructors.add(constructor)
      }
    }
    def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration) {
      val field = lookupKernelNode(node).asInstanceOf[IxCode.FieldDefinition]
      if (field == null) return null
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processInterfaceMethodDeclaration(node: AST.MethodDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[IxCode.MethodDefinition]
      if (method == null) return null
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def generateMethods() {
      val generated = new JTreeSet[IxCode.MethodRef](new IxCode.MethodRefComparator)
      val methodSet = new JTreeSet[IxCode.MethodRef](new IxCode.MethodRefComparator)
      def makeDelegationMethod(delegated: IxCode.FieldRef, delegator: IxCode.MethodRef): IxCode.MethodDefinition = {
        val args = delegator.arguments
        val params = new Array[IxCode.Expression](args.length)
        val frame = new LocalFrame(null)
        for(i <- 0 until params.length) {
          val index = frame.add("arg" + i, args(i))
          params(i) = new IxCode.RefLocal(new ClosureLocalBinding(0, index, args(i)))
        }
        val target = new IxCode.Call(new IxCode.RefField(new IxCode.This(definition_), delegated), delegator, params)
        val statement = if (delegator.returnType != IxCode.BasicTypeRef.VOID) new IxCode.StatementBlock(new IxCode.Return(target)) else new IxCode.StatementBlock(new IxCode.ExpressionStatement(target), new IxCode.Return(null))
        val node = new IxCode.MethodDefinition(null, AST.M_PUBLIC, definition_, delegator.name, delegator.arguments, delegator.returnType, statement)
        node.setFrame(frame)
        node
      }
      def generateDelegationMethods(node: IxCode.FieldDefinition) {
        val typeRef = node.`type`.asInstanceOf[IxCode.ClassTypeRef]
        val src = Classes.getInterfaceMethods(typeRef)
        for (method <- src) {
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
      for (node <- fields) {
        if ((AST.M_FORWARDED & node.modifier) != 0) generateDelegationMethods(node.asInstanceOf[IxCode.FieldDefinition])
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
      val field = lookupKernelNode(node).asInstanceOf[IxCode.FieldDefinition]
      if (field == null) return null
      if (variables.contains(field)) {
        report(DUPLICATE_GLOBAL_VARIABLE, node, field.name)
      }else {
        variables.add(field)
      }
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[IxCode.MethodDefinition]
      if (method == null) return null
      if (functions.contains(method)) {
        report(DUPLICATE_FUNCTION, node, method.name, method.arguments)
      } else {
        functions.add(method)
      }
    }
    def processClassDeclaration(node: AST.ClassDeclaration) {
      val clazz = lookupKernelNode(node).asInstanceOf[IxCode.ClassDefinition]
      if (clazz == null) return null
      methods.clear()
      fields.clear()
      constructors.clear()
      definition_ = clazz
      mapper_ = find(clazz.name)
      if (node.defaultSection != null) processAccessSection(node.defaultSection)
      for (section <- node.sections) processAccessSection(section)
      generateMethods()
    }
    def processInterfaceDeclaration(node: AST.InterfaceDeclaration) {
      var clazz = lookupKernelNode(node).asInstanceOf[IxCode.ClassDefinition]
      if (clazz == null) return null
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
    report(error, node.location, items)
  }
  def report(error: Int, location: Location, items: AnyRef*) {
    reporter_.setSourceFile(unit_.sourceFile)
    reporter_.report(error, location, items.toArray)
  }
  def createFQCN(moduleName: String, simpleName: String): String =  (if (moduleName != null) moduleName + "." else "") + simpleName
  def load(name: String): IxCode.ClassTypeRef = table_.load(name)
  def loadTopClass: IxCode.ClassTypeRef = table_.load(topClass)
  def loadArray(base: IxCode.TypeRef, dimension: Int): IxCode.ArrayTypeRef = table_.loadArray(base, dimension)
  def rootClass: IxCode.ClassTypeRef = table_.rootClass
  def problems: Array[CompileError] = reporter_.getProblems
  def sourceClasses: Array[IxCode.ClassDefinition] = table_.classes.values.toArray(new Array[IxCode.ClassDefinition](0))
  def topClass: String = {
    val module = unit_.module
    val moduleName = if (module != null) module.name else null
    return createName(moduleName, Paths.cutExtension(unit_.sourceFile) + "Main")
  }
  private def put(astNode: AST.Node, kernelNode: IxCode.Node) {
    ast2ixt_(astNode) = kernelNode
    ixt2ast_(kernelNode) = astNode
  }
  private def lookupAST(kernelNode: IxCode.Node): Option[AST.Node] =  ixt2ast_.get(kernelNode)
  private def lookupKernelNode(astNode: AST.Node): Option[IxCode.Node] = ast2ixt_.get(astNode)
  private def add(className: String, mapper: NameMapper): Unit = mappers_(className) = mapper
  private def find(className: String): NameMapper = mappers_(className)
  private def createName(moduleName: String, simpleName: String): String = (if (moduleName != null) moduleName + "." else "") + simpleName
  private def classpath(paths: Array[String]): String = paths.foldLeft(new StringBuilder){(builder, path) => builder.append(Systems.getPathSeparator).append(path)}.toString
  private def typesOf(arguments: List[AST.Argument]): Option[List[IxCode.TypeRef]] = {
    val result = arguments.map{arg => mapFrom(arg.typeRef)}
    if(result.exists(_ == null)) Some(result) else None
  }
  private def mapFrom(typeNode: AST.TypeNode): IxCode.TypeRef = mapFrom(typeNode, mapper_)
  private def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): IxCode.TypeRef = {
    val mappedType = mapper.map(typeNode)
    if (mappedType == null) report(CLASS_NOT_FOUND, typeNode, AST.toString(typeNode.desc))
    return mappedType
  }
  private def createEquals(kind: Int, lhs: IxCode.Expression, rhs: IxCode.Expression): IxCode.Expression = {
    val params = Array[IxCode.Expression](new IxCode.AsInstanceOf(rhs, rootClass))
    val target = lhs.`type`.asInstanceOf[IxCode.ObjectTypeRef]
    val methods = target.findMethod("equals", params)
    var node: IxCode.Expression = new IxCode.Call(lhs, methods(0), params)
    if (kind == IxCode.BinaryExpression.Constants.NOT_EQUAL) {
      node = new IxCode.UnaryExpression(NOT, IxCode.BasicTypeRef.BOOLEAN, node)
    }
    node
  }
  private def indexref(bind: ClosureLocalBinding, value: IxCode.Expression): IxCode.Expression = new IxCode.ArrayRef(new IxCode.RefLocal(bind), value)
  private def assign(bind: ClosureLocalBinding, value: IxCode.Expression): IxCode.ActionStatement = new IxCode.ExpressionStatement(new IxCode.SetLocal(bind, value))
  private def ref(bind: ClosureLocalBinding): IxCode.Expression = new IxCode.RefLocal(bind)
  private def findMethod(node: AST.Node, target: IxCode.ObjectTypeRef, name: String): IxCode.MethodRef = {
    return findMethod(node, target, name, new Array[IxCode.Expression](0))
  }
  private def findMethod(node: AST.Node, target: IxCode.ObjectTypeRef, name: String, params: Array[IxCode.Expression]): IxCode.MethodRef = {
    val methods: Array[IxCode.MethodRef] = target.findMethod(name, params)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, target, name, params.map{param => param.`type`})
      return null
    }
    return methods(0)
  }
}
