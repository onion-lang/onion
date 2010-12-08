package onion.compiler
import _root_.java.util.{TreeSet => JTreeSet}
import _root_.scala.collection.JavaConversions._
import _root_.onion.compiler.util.{Boxing, Classes, Paths, Systems}
import _root_.onion.compiler.SemanticErrorReporter.Constants._
import _root_.onion.compiler.IxCode._
import _root_.onion.compiler.IxCode.BinaryTerm.Constants._
import _root_.onion.compiler.IxCode.UnaryTerm.Constants._
import collection.mutable.{Stack, Buffer, Map, HashMap, Set => MutableSet}

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 10:46:42
 * To change this template use File | Settings | File Templates.
 */
class Typing(config: CompilerConfig) extends AnyRef with ProcessingUnit[Array[AST.CompilationUnit], Array[ClassDefinition]] {
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
    def map(typeNode: AST.TypeNode): TypeRef = map(typeNode.desc)
    def map(descriptor : AST.TypeDescriptor): TypeRef = descriptor match {
      case AST.PrimitiveType(AST.KChar)       => BasicTypeRef.CHAR
      case AST.PrimitiveType(AST.KByte)       => BasicTypeRef.BYTE
      case AST.PrimitiveType(AST.KShort)      => BasicTypeRef.SHORT
      case AST.PrimitiveType(AST.KInt)        => BasicTypeRef.INT
      case AST.PrimitiveType(AST.KLong)       => BasicTypeRef.LONG
      case AST.PrimitiveType(AST.KFloat)      => BasicTypeRef.FLOAT
      case AST.PrimitiveType(AST.KDouble)     => BasicTypeRef.DOUBLE
      case AST.PrimitiveType(AST.KBoolean)    => BasicTypeRef.BOOLEAN
      case AST.PrimitiveType(AST.KVoid)       => BasicTypeRef.VOID
      case AST.ReferenceType(name, qualified) => forName(name, qualified)
      case AST.ParameterizedType(base, _)     => map(base)
      case AST.ArrayType(component)           =>  val (base, dimension) = split(descriptor); table_.loadArray(map(base), dimension)
    }
    private def forName(name: String, qualified: Boolean): ClassTypeRef = {
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
  private val ast2ixt_ = Map[AST.Node, Node]()
  private var ixt2ast_ = Map[Node, AST.Node]()
  private var mappers_  = Map[String, NameMapper]()
  private var access_ : Int = _
  private var mapper_ : NameMapper = _
  private var importedList_ : ImportList = _
  private var staticImportedList_ : StaticImportList = _
  private var definition_ : ClassDefinition = _
  private var unit_ : AST.CompilationUnit = _
  private val reporter_ : SemanticErrorReporter = new SemanticErrorReporter(config.getMaxErrorReports)
  def newEnvironment(source: Array[AST.CompilationUnit]) = new TypingEnvironment
  def doProcess(source: Array[AST.CompilationUnit], environment: TypingEnvironment): Array[ClassDefinition] = {
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
        val node = ClassDefinition.newClass(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null, null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(DUPLICATE_CLASS, declaration, node.name)
        }else {
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(importedList_))
        }
      case declaration: AST.InterfaceDeclaration =>
        val node = ClassDefinition.newInterface(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null)
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
      val node = ClassDefinition.newClass(unit.location, 0, topClass, table_.rootClass, new Array[ClassTypeRef](0))
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
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
      constructTypeHierarchy(definition_, MutableSet[ClassTypeRef]())
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
      definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
      mapper_ = find(definition_.name)
      constructTypeHierarchy(definition_, MutableSet[ClassTypeRef]())
      if (cyclic(definition_)) report(CYCLIC_INHERITANCE, node, definition_.name)
      for(method <- node.methods) processInterfaceMethodDeclaration(method)
    }
    def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration) {
      val typeRef = mapFrom(node.typeRef)
      if (typeRef == null) return null
      val modifier = node.modifiers | AST.M_PUBLIC
      val classType = loadTopClass.asInstanceOf[ClassDefinition]
      val name = node.name
      val field = new FieldDefinition(node.location, modifier, classType, name, typeRef)
      put(node, field)
      classType.add(field)
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if(node.returnType != null) mapFrom(node.returnType) else BasicTypeRef.VOID)
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
      if (typeRef == null) return null
      val modifier = node.modifiers | access_
      val name = node.name
      val field = new FieldDefinition(node.location, modifier, definition_, name, typeRef)
      put(node, field)
      definition_.add(field)
    }
    def processMethodDeclaration(node: AST.MethodDeclaration) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if (node.returnType != null) mapFrom(node.returnType) else BasicTypeRef.VOID)
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
      val returnTypeOption = Option(if(node.returnType != null) mapFrom(node.returnType) else BasicTypeRef.VOID)
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
      if (typeRef == null) return null
      if (!(typeRef.isObjectType && (typeRef.asInstanceOf[ObjectTypeRef]).isInterface)) {
        report(INTERFACE_REQUIRED, node, typeRef)
        return null
      }
      val modifier = node.modifiers | access_ | AST.M_FORWARDED
      var name = node.name
      var field = new FieldDefinition(node.location, modifier, definition_, name, typeRef)
      put(node, field)
      definition_.add(field)
    }
    def cyclic(start: ClassDefinition): Boolean = {
      def loop(node: ClassTypeRef, visit: Set[ClassTypeRef]): Boolean = {
        if(node == null) return false
        if(visit.contains(node)) return true
        val newVisit = visit + node
        if(loop(node.superClass, newVisit)) return true
        for(interface <- node.interfaces) if(loop(interface, newVisit)) return true
        return false
      }
      loop(start, Set[ClassTypeRef]())
    }
    def validateSuperType(node: AST.TypeNode, mustBeInterface: Boolean, mapper: NameMapper): ClassTypeRef = {
      val typeRef = if(node == null) table_.rootClass else mapFrom(node, mapper).asInstanceOf[ClassTypeRef]
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
    def constructTypeHierarchy(node: ClassTypeRef, visit: MutableSet[ClassTypeRef]) {
      if(node == null || visit.contains(node)) return
      visit += node
      node match {
        case node: ClassDefinition =>
          if (node.isResolutionComplete) return
          val interfaces = Buffer[ClassTypeRef]()
          val resolver = find(node.name)
          var superClass: ClassTypeRef = null
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
    def processNodes(nodes: Array[AST.Expression], typeRef: TypeRef, bind: ClosureLocalBinding, context: LocalContext): Term = {
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
          new BinaryTerm(EQUAL, BasicTypeRef.BOOLEAN, new RefLocal(bind), expressions(0))
        }
        for(i <- 1 until expressions.length) {
          node = new BinaryTerm(LOGICAL_OR, BasicTypeRef.BOOLEAN, node, new BinaryTerm(EQUAL, BasicTypeRef.BOOLEAN, new RefLocal(bind), expressions(i)))
        }
        node
      } else {
        null
      }
    }
    def processAssignable(node: AST.Node, a: TypeRef, b: Term): Term = {
      if (b == null) return null
      if (a == b.`type`) return b
      if (!TypeRules.isAssignable(a, b.`type`)) {
        report(INCOMPATIBLE_TYPE, node, a, b.`type`)
        return null
      }
      new AsInstanceOf(node.location, b, a)
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
    def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] = {
      var failed = false
      val result = nodes.map{node => typed(node, context).getOrElse{failed = true; null}}
      if(failed) null else result
    }
    def typed(node: AST.Expression, context: LocalContext): Option[Term] = node match {
      case node@AST.Addition(loc, l, r) =>
        null
      case node@AST.AdditionAssignment(loc, l, r) =>
        null
      case node@AST.Assignment(loc, l, r) =>
        null
      case node@AST.BitAnd(loc, l, r) =>
        null
      case node@AST.BitOr(loc, l, r) =>
        null
      case node@AST.Division(loc, left, right) =>
        null
      case node@AST.DivisionAssignment(loc, left, right) =>
        null
      case node@AST.GreaterOrEqual(loc, left, right) =>
        null
      case node@AST.GreaterThan(loca, left, right) =>
        null
      case node@AST.Elvis(loc, left, right) =>
        null
      case node@AST.Equal(loc, left, right) =>
        null
      case node@AST.LessOrEqual(loc, left, right) =>
        null
      case node@AST.LessThan(loc, left, right) =>
        null
      case node@AST.Indexing(loc, left, right) =>
        null
      case node@AST.LogicalAnd(loc, left, right) =>
        null
      case node@AST.LogicalOr(loc, left, right) =>
        null
      case node@AST.NotEqual(loc, left, right) =>
        null
      case node@AST.LogicalRightShift(loc, left, right) =>
        null
      case node@AST.MathLeftShift(loc, left, right) =>
        null
      case node@AST.MathRightShift(loc, left, right) =>
        null
      case node@AST.Modulo(loc, left, right) =>
        null
      case node@AST.ModuloAssignment(loc, left, right) =>
        null
      case node@AST.Multiplication(loc, left, right) =>
        null
      case node@AST.MultiplicationAssignment(loc, left, right) =>
        null
      case node@AST.ReferenceEqual(loc, left, right) =>
        null
      case node@AST.ReferenceNotEqual(loc, left, right) =>
        null
      case node@AST.Subtraction(loc, left, right) =>
        null
      case node@AST.SubtractionAssignment(loc, left, right) =>
        null
      case node@AST.XOR(loc, left, right) =>
        null
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
      case node@AST.ClosureExpression(loc, typeRe, mname, args, returns, body) =>
        null
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
      case node@AST.IsInstance(loc, target, typeRef) =>
        null
      case node@AST.MemberSelection(loc, target/*nullable*/, name) =>
        null
      case node@AST.MethodCall(loc, target/*nullable*/, name, args) =>
        null
      case node@AST.Negate(loc, target) =>
        var term = typed(node.target, context).getOrElse(null)
        if (term == null) return None
        if (!hasNumericType(term)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "-", Array[TypeRef](term.`type`))
          return None
        }
        Some(new UnaryTerm(MINUS, term.`type`, term))
      case node@AST.NewArray(loc, _, _) =>
        val typeRef = mapFrom(node.typeRef, mapper_)
        var parameters = typedTerms(node.args.toArray, context)
        if(typeRef == null || parameters == null) return None
        val resultType = loadArray(typeRef, parameters.length)
        Some(new NewArray(resultType, parameters))
      case node@AST.NewObject(loc, _, _) =>
        val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassTypeRef]
        val parameters = typedTerms(node.args.toArray, context)
        if (parameters == null || typeRef == null) return None
        val constructors = typeRef.findConstructor(parameters)
        if (constructors.length == 0) {
          report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(parameters))
          return None
        }else if (constructors.length > 1) {
          report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](constructors(0).affiliation, constructors(0).getArgs), Array[AnyRef](constructors(1).affiliation, constructors(1).getArgs))
          return None
        }else {
          Some(new NewObject(constructors(0), parameters))
        }
      case node@AST.Not(loc, target) =>
        val term = typed(node.target, context).getOrElse(null)
        if (term == null) return None
        if (term.`type` != IxCode.BasicTypeRef.BOOLEAN) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "!", Array[TypeRef](term.`type`))
          return None
        }
        Some(new UnaryTerm(NOT, BasicTypeRef.BOOLEAN, term))
      case node@AST.Posit(loc, target) =>
        val term = typed(node.target, context).getOrElse(null)
        if (term == null) return None
        if (!hasNumericType(term)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "+", Array[TypeRef](term.`type`))
          return None
        }
        Some(new UnaryTerm(PLUS, term.`type`, term))
      case node@AST.PostDecrement(loc, target) =>
        val operand = typed(node.target, context).getOrElse(null)
        if (operand == null) return None
        if ((!operand.isBasicType) || !hasNumericType(operand)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "--", Array[TypeRef](operand.`type`))
          return None
        }
        Some(operand match {
          case ref: RefLocal =>
            val varIndex = context.add(context.newName, operand.`type`)
            new Begin(new SetLocal(0, varIndex, operand.`type`, operand), new SetLocal(ref.frame, ref.index, ref.`type`, new BinaryTerm(SUBTRACT, operand.`type`, new RefLocal(0, varIndex, operand.`type`), new IntValue(1))), new RefLocal(0, varIndex, operand.`type`))
          case ref: RefField =>
            var varIndex: Int = context.add(context.newName, ref.target.`type`)
            new Begin(new SetLocal(0, varIndex, ref.target.`type`, ref.target), new SetField(new RefLocal(0, varIndex, ref.target.`type`), ref.field, new BinaryTerm(SUBTRACT, operand.`type`, new RefField(new RefLocal(0, varIndex, ref.target.`type`), ref.field), new IntValue(1))))
          case _ =>
            report(UNIMPLEMENTED_FEATURE, node)
            null
        })
      case node@AST.PostIncrement(loc, target) =>
        val operand = typed(node.target, context).getOrElse(null)
        if (operand == null) return None
        if ((!operand.isBasicType) || !hasNumericType(operand)) {
          report(INCOMPATIBLE_OPERAND_TYPE, node, "++", Array[TypeRef](operand.`type`))
          return None
        }
        Option(operand match {
          case ref: RefLocal =>
            val varIndex = context.add(context.newName, operand.`type`)
            new Begin(new SetLocal(0, varIndex, operand.`type`, operand), new SetLocal(ref.frame, ref.index, ref.`type`, new BinaryTerm(ADD, operand.`type`, new RefLocal(0, varIndex, operand.`type`), new IntValue(1))), new RefLocal(0, varIndex, operand.`type`))
          case ref: RefField =>
            var varIndex: Int = context.add(context.newName, ref.target.`type`)
            new Begin(new SetLocal(0, varIndex, ref.target.`type`, ref.target), new SetField(new RefLocal(0, varIndex, ref.target.`type`), ref.field, new BinaryTerm(ADD, operand.`type`, new RefField(new RefLocal(0, varIndex, ref.target.`type`), ref.field), new IntValue(1))))
          case _ =>
            report(UNIMPLEMENTED_FEATURE, node)
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
            Some(new Call(new This(targetType), methods(0), params))
          }
        }
      case node@AST.StaticMemberSelection(loc, _, _) =>
        val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassTypeRef]
        if (typeRef == null) return null
        val field = findField(typeRef, node.name)
        if (field == null) {
          report(FIELD_NOT_FOUND, node, typeRef, node.name)
          None
        }else {
          Some(new RefStaticField(typeRef,field))
        }
      case node@AST.StaticMethodCall(loc, _, _, _) =>
        val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassTypeRef]
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
        val result = tryFindMethod(node, contextClass.superClass, node.name, parameters)
        result match {
          case Some(method) => Some(new CallSuper(new This(contextClass), method, parameters))
          case None =>
            report(METHOD_NOT_FOUND, node, contextClass, node.name, types(parameters))
            None
        }
    }
    def translate(node: AST.Statement, context: LocalContext): ActionStatement = node match {
      case AST.BlockStatement(loc, elements) =>
        openScope(context){
          new StatementBlock(elements.map{e => translate(e, context)}.toArray:_*)
        }
      case node@AST.BreakStatement(loc) =>
        report(UNIMPLEMENTED_FEATURE, node)
        new Break(loc)
      case node@AST.BranchStatement(loc, _, _) =>
        openScope(context) {
          val size = node.clauses.size
          val expressions = new Stack[Term]
          val statements = new Stack[ActionStatement]
          for((expression, statement) <- node.clauses) {
            val typedExpression = typed(expression, context).getOrElse(null)
            if (typedExpression != null && typedExpression.`type` != BasicTypeRef.BOOLEAN) {
              val expect = BasicTypeRef.BOOLEAN
              val actual = typedExpression.`type`
              report(INCOMPATIBLE_TYPE, expression, expect, actual)
            }
            expressions.push(typedExpression)
            statements.push(translate(statement, context))
          }
          val elseStatement = node.elseBlock
          var result: ActionStatement = null
          if (elseStatement != null) {
            result = translate(elseStatement, context)
          }
          for(i <- 0 until size) {
            result = new IfStatement(expressions.pop, statements.pop, result)
          }
          return result
        }
      case node@AST.ContinueStatement(loc) =>
        report(UNIMPLEMENTED_FEATURE, node)
        new Continue(loc)
      case node@AST.EmptyStatement(loc) =>
        new NOP(loc)
      case node@AST.ExpressionStatement(loc, body) =>
        typed(body, context).map{e =>  new ExpressionActionStatement(loc, e)}.getOrElse(new NOP(loc))
      case node@AST.ForeachStatement(loc, _, _, _) =>
        openScope(context) {
          val collection = typed(node.collection, context).getOrElse(null)
          val arg = node.arg
          mapFrom(arg.typeRef)
          var block = translate(node.statement, context)
          if (collection.isBasicType) {
            report(INCOMPATIBLE_TYPE, node.collection, load("java.util.Collection"), collection.`type`)
            return new NOP(node.location)
          }
          val elementVar = context.lookupOnlyCurrentScope(arg.name)
          val collectionVar = new ClosureLocalBinding(0, context.add(context.newName, collection.`type`), collection.`type`)
          var init: ActionStatement = null
          if (collection.isArrayType) {
            val counterVariable = new ClosureLocalBinding(0, context.add(context.newName, BasicTypeRef.INT), BasicTypeRef.INT)
            init = new StatementBlock(new ExpressionActionStatement(new SetLocal(collectionVar, collection)), new ExpressionActionStatement(new SetLocal(counterVariable, new IntValue(0))))
            block = new ConditionalLoop(new BinaryTerm(LESS_THAN, BasicTypeRef.BOOLEAN, ref(counterVariable), new ArrayLength(ref(collectionVar))), new StatementBlock(assign(elementVar, indexref(collectionVar, ref(counterVariable))), block, assign(counterVariable, new BinaryTerm(ADD, BasicTypeRef.INT, ref(counterVariable), new IntValue(1)))))
            new StatementBlock(init, block)
          }
          else {
            val iteratorType = load("java.util.Iterator")
            var iteratorVar = new ClosureLocalBinding(0, context.add(context.newName, iteratorType), iteratorType)
            var mIterator = findMethod(node.collection, collection.`type`.asInstanceOf[ObjectTypeRef], "iterator")
            var mNext: MethodRef = findMethod(node.collection, iteratorType, "next")
            var mHasNext: MethodRef = findMethod(node.collection, iteratorType, "hasNext")
            init = new StatementBlock(new ExpressionActionStatement(new SetLocal(collectionVar, collection)), assign(iteratorVar, new Call(ref(collectionVar), mIterator, new Array[Term](0))))
            var next: Term = new Call(ref(iteratorVar), mNext, new Array[Term](0))
            if (elementVar.getType != rootClass) {
              next = new AsInstanceOf(next, elementVar.getType)
            }
            block = new ConditionalLoop(new Call(ref(iteratorVar), mHasNext, new Array[Term](0)), new StatementBlock(assign(elementVar, next), block))
            new StatementBlock(init, block)
          }
        }
      case node@AST.ForStatement(loc, _, _, _, _) =>
        openScope(context) {
          val init = Option(node.init).map{init => translate(init, context)}.getOrElse(new NOP(loc))
          val condition = (for(c <- Option(node.condition)) yield {
            val conditionOpt = typed(c, context)
            val expected = BasicTypeRef.BOOLEAN
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
      case node@AST.IfStatement(loc, _, _, _) => 
        openScope(context) {
          val conditionOpt = typed(node.condition, context)
          val expected = BasicTypeRef.BOOLEAN
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
          var valueNode = typed(init, context)
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
      case node@AST.ReturnStatement(loc, _) =>
        val returnType = context.returnType
        if(node.result == null) {
          val expected  = BasicTypeRef.VOID
          if (returnType != expected) report(CANNOT_RETURN_VALUE, node)
          return new Return(loc, null)
        } else {
          val returnedOpt= typed(node.result, context)
          if (returnedOpt == null) return new Return(loc, null)
          (for(returned <- returnedOpt) yield {
            if (returned.`type` == BasicTypeRef.VOID) {
              report(CANNOT_RETURN_VALUE, node)
              new Return(loc, null)
            } else {
              val value = processAssignable(node.result, returnType, returned)
              if (value == null) return new Return(loc, null)
              new Return(loc, value)
            }
          }).getOrElse(new Return(loc, null))
        }
      case node@AST.SelectStatement(loc, _, _, _) =>
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
          for((expressions, then)<- cases) {
            val bind = context.lookup(name)
            nodes += processNodes(expressions.toArray, condition.`type`, bind, context)
            thens += translate(then, context)
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
      case node@AST.SynchronizedStatement(loc, _, _) =>
        openScope(context) {
          val lock = typed(node.condition, context).getOrElse(null)
          val block = translate(node.block, context)
          report(UNIMPLEMENTED_FEATURE, node)
          new Synchronized(node.location, lock, block)
        }
      case node@AST.ThrowStatement(loc, target) => null
        val expressionOpt = typed(target, context)
        for(expression <- expressionOpt) {
          val expected = load("java.lang.Throwable")
          val detected = expression.`type`
          if (!TypeRules.isSuperType(expected, detected)) {
            report(INCOMPATIBLE_TYPE, node, expected, detected)
          }
        }
        new Throw(loc, expressionOpt.getOrElse(null))
      case node@AST.TryStatement(loc, tryBlock, recClauses, finBlock) =>
        val tryStatement = translate(tryBlock, context)
        val binds = new Array[ClosureLocalBinding](recClauses.length)
        val catchBlocks = new Array[ActionStatement](recClauses.length)
        for(i <- 0 until recClauses.length) {
          val (argument, body) = recClauses(i)
          openScope(context) {
            val argType = mapFrom(argument.typeRef)
            val expected = load("java.lang.Throwable")
            if (!TypeRules.isSuperType(expected, argType)) {
              report(INCOMPATIBLE_TYPE, argument, expected, argType)
            }
            binds(i) = context.lookupOnlyCurrentScope(argument.name)
            catchBlocks(i) = translate(body, context)
          }
        }
        new Try(loc, tryStatement, binds, catchBlocks)
      case node@AST.WhileStatement(loc, _, _) =>
        openScope(context) {
          val conditionOpt = typed(node.condition, context)
          val expected = BasicTypeRef.BOOLEAN
          for(condition <- conditionOpt) {
            val actual = condition.`type`
            if(actual != expected)  report(INCOMPATIBLE_TYPE, node, expected, actual)
          }
          val thenBlock = translate(node.block, context)
          new ConditionalLoop(loc, conditionOpt.getOrElse(null), thenBlock)
        }
    }
    def defaultValue(typeRef: TypeRef): Term = Term.defaultValue(typeRef)
    def addReturnNode(node: ActionStatement, returnType: TypeRef): StatementBlock = {
      return new StatementBlock(node, new Return(defaultValue(returnType)))
    }
    def createMain(top: ClassTypeRef, ref: MethodRef, name: String, args: Array[TypeRef], ret: TypeRef): MethodDefinition = {
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
      block = addReturnNode(block, BasicTypeRef.VOID)
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
    val method= new MethodDefinition(node.location, AST.M_PUBLIC, klass, "start", Array[TypeRef](argsType), BasicTypeRef.VOID, null)
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
      statements += new Return(null)
      method.setBlock(new StatementBlock(statements))
      method.setFrame(context.getContextFrame)
      klass.add(method)
      klass.add(createMain(klass, method, "main", Array[TypeRef](argsType), BasicTypeRef.VOID))
    }
  }
  def processDuplication(node: AST.CompilationUnit) {
    val methods = new JTreeSet[MethodRef](new MethodRefComparator)
    val fields = new JTreeSet[FieldRef](new FieldRefComparator)
    val constructors = new JTreeSet[ConstructorRef](new ConstructorRefComparator)
    val variables = new JTreeSet[FieldRef](new FieldRefComparator)
    val functions = new JTreeSet[MethodRef](new MethodRefComparator)
    def processFieldDeclaration(node: AST.FieldDeclaration) {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return null
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processMethodDeclaration(node: AST.MethodDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return null
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def processConstructorDeclaration(node: AST.ConstructorDeclaration) {
      val constructor = lookupKernelNode(node).asInstanceOf[ConstructorDefinition]
      if (constructor == null) return null
      if (constructors.contains(constructor)) {
        report(DUPLICATE_CONSTRUCTOR, node, constructor.affiliation, constructor.getArgs)
      } else {
        constructors.add(constructor)
      }
    }
    def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration) {
      val field = lookupKernelNode(node).asInstanceOf[FieldDefinition]
      if (field == null) return null
      if (fields.contains(field)) {
        report(DUPLICATE_FIELD, node, field.affiliation, field.name)
      } else {
        fields.add(field)
      }
    }
    def processInterfaceMethodDeclaration(node: AST.MethodDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return null
      if (methods.contains(method)) {
        report(DUPLICATE_METHOD, node, method.affiliation, method.name, method.arguments)
      } else {
        methods.add(method)
      }
    }
    def generateMethods() {
      val generated = new JTreeSet[MethodRef](new MethodRefComparator)
      val methodSet = new JTreeSet[MethodRef](new MethodRefComparator)
      def makeDelegationMethod(delegated: FieldRef, delegator: MethodRef): MethodDefinition = {
        val args = delegator.arguments
        val params = new Array[Term](args.length)
        val frame = new LocalFrame(null)
        for(i <- 0 until params.length) {
          val index = frame.add("arg" + i, args(i))
          params(i) = new RefLocal(new ClosureLocalBinding(0, index, args(i)))
        }
        val target = new Call(new RefField(new This(definition_), delegated), delegator, params)
        val statement = if (delegator.returnType != BasicTypeRef.VOID) new StatementBlock(new Return(target)) else new StatementBlock(new ExpressionActionStatement(target), new Return(null))
        val node = new MethodDefinition(null, AST.M_PUBLIC, definition_, delegator.name, delegator.arguments, delegator.returnType, statement)
        node.setFrame(frame)
        node
      }
      def generateDelegationMethods(node: FieldDefinition) {
        val typeRef = node.`type`.asInstanceOf[ClassTypeRef]
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
      if (field == null) return null
      if (variables.contains(field)) {
        report(DUPLICATE_GLOBAL_VARIABLE, node, field.name)
      }else {
        variables.add(field)
      }
    }
    def processFunctionDeclaration(node: AST.FunctionDeclaration) {
      val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
      if (method == null) return null
      if (functions.contains(method)) {
        report(DUPLICATE_FUNCTION, node, method.name, method.arguments)
      } else {
        functions.add(method)
      }
    }
    def processClassDeclaration(node: AST.ClassDeclaration) {
      val clazz = lookupKernelNode(node).asInstanceOf[ClassDefinition]
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
      var clazz = lookupKernelNode(node).asInstanceOf[ClassDefinition]
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
  def load(name: String): ClassTypeRef = table_.load(name)
  def loadTopClass: ClassTypeRef = table_.load(topClass)
  def loadArray(base: TypeRef, dimension: Int): ArrayTypeRef = table_.loadArray(base, dimension)
  def rootClass: ClassTypeRef = table_.rootClass
  def problems: Array[CompileError] = reporter_.getProblems
  def sourceClasses: Array[ClassDefinition] = table_.classes.values.toArray(new Array[ClassDefinition](0))
  def topClass: String = {
    val module = unit_.module
    val moduleName = if (module != null) module.name else null
    return createName(moduleName, Paths.cutExtension(unit_.sourceFile) + "Main")
  }
  private def put(astNode: AST.Node, kernelNode: Node) {
    ast2ixt_(astNode) = kernelNode
    ixt2ast_(kernelNode) = astNode
  }
  private def lookupAST(kernelNode: Node): Option[AST.Node] =  ixt2ast_.get(kernelNode)
  private def lookupKernelNode(astNode: AST.Node): Option[Node] = ast2ixt_.get(astNode)
  private def add(className: String, mapper: NameMapper): Unit = mappers_(className) = mapper
  private def find(className: String): NameMapper = mappers_(className)
  private def createName(moduleName: String, simpleName: String): String = (if (moduleName != null) moduleName + "." else "") + simpleName
  private def classpath(paths: Array[String]): String = paths.foldLeft(new StringBuilder){(builder, path) => builder.append(Systems.getPathSeparator).append(path)}.toString
  private def typesOf(arguments: List[AST.Argument]): Option[List[TypeRef]] = {
    val result = arguments.map{arg => mapFrom(arg.typeRef)}
    if(result.exists(_ == null)) Some(result) else None
  }
  private def mapFrom(typeNode: AST.TypeNode): TypeRef = mapFrom(typeNode, mapper_)
  private def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): TypeRef = {
    val mappedType = mapper.map(typeNode)
    if (mappedType == null) report(CLASS_NOT_FOUND, typeNode, AST.toString(typeNode.desc))
    return mappedType
  }
  private def createEquals(kind: Int, lhs: Term, rhs: Term): Term = {
    val params = Array[Term](new AsInstanceOf(rhs, rootClass))
    val target = lhs.`type`.asInstanceOf[ObjectTypeRef]
    val methods = target.findMethod("equals", params)
    var node: Term = new Call(lhs, methods(0), params)
    if (kind == BinaryTerm.Constants.NOT_EQUAL) {
      node = new UnaryTerm(NOT, BasicTypeRef.BOOLEAN, node)
    }
    node
  }
  private def indexref(bind: ClosureLocalBinding, value: Term): Term = new RefArray(new RefLocal(bind), value)
  private def assign(bind: ClosureLocalBinding, value: Term): ActionStatement = new ExpressionActionStatement(new SetLocal(bind, value))
  private def ref(bind: ClosureLocalBinding): Term = new RefLocal(bind)
  private def findMethod(node: AST.Node, target: ObjectTypeRef, name: String): MethodRef = {
    return findMethod(node, target, name, new Array[Term](0))
  }
  private def findMethod(node: AST.Node, target: ObjectTypeRef, name: String, params: Array[Term]): MethodRef = {
    val methods = target.findMethod(name, params)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, target, name, params.map{param => param.`type`})
      return null
    }
    return methods(0)
  }
  private def hasSamePackage(a: ClassTypeRef, b: ClassTypeRef): Boolean = {
    var name1 = a.name
    var name2 = b.name
    var index: Int = 0
    index = name1.lastIndexOf(".")
    if (index >= 0)  name1 = name1.substring(0, index)
    else name1 = ""
    index = name2.lastIndexOf(".")
    name2 = if(index >= 0) name2.substring(0, index) else ""
    return name1 == name2
  }
  private def isAccessible(target: ClassTypeRef, context: ClassTypeRef): Boolean = {
    if (hasSamePackage(target, context))  true else (target.modifier & AST.M_INTERNAL) == 0
  }
  private def isAccessible(member: MemberRef, context: ClassTypeRef): Boolean = {
    val targetType = member.affiliation
    if (targetType == context) return true
    val modifier = member.modifier
    if (TypeRules.isSuperType(targetType, context)) (modifier & AST.M_PROTECTED) != 0 || (modifier & AST.M_PUBLIC) != 0 else (AST.M_PUBLIC & modifier) != 0
  }
  private def findField(target: ObjectTypeRef, name: String): FieldRef = {
    if(target == null) return null
    var field = target.field(name)
    if(target != null) return field
    field = findField(target.superClass, name)
    if (field != null) return field
    for (interface <- target.interfaces) {
      field = findField(interface, name)
      if (field != null) return field
    }
    return null
  }
  private def isAccessible(node: AST.Node, target: ObjectTypeRef, context: ClassTypeRef): Boolean = {
    if (target.isArrayType) {
      val component = (target.asInstanceOf[ArrayTypeRef]).component
      if (!component.isBasicType) {
        if (!isAccessible(component.asInstanceOf[ClassTypeRef], definition_)) {
          report(CLASS_NOT_ACCESSIBLE, node, target, context)
          return false
        }
      }
    } else {
      if (!isAccessible(target.asInstanceOf[ClassTypeRef], context)) {
        report(CLASS_NOT_ACCESSIBLE, node, target, context)
        return false
      }
    }
    return true
  }
  private def hasNumericType(term: Term): Boolean =  return numeric(term.`type`)
  private def numeric(symbol: TypeRef): Boolean = {
    (symbol.isBasicType) && (symbol == BasicTypeRef.BYTE || symbol == BasicTypeRef.SHORT || symbol == BasicTypeRef.CHAR || symbol == BasicTypeRef.INT || symbol == BasicTypeRef.LONG || symbol == BasicTypeRef.FLOAT || symbol == BasicTypeRef.DOUBLE)
  }
  private def doCastInsertion(arguments: Array[TypeRef], params: Array[Term]): Array[Term] = {
    for(i <- 0 until params.length) {
      if (arguments(i) != params(i).`type`) params(i) = new AsInstanceOf(params(i), arguments(i))
    }
    return params
  }
  private def types(terms: Array[Term]): Array[TypeRef] = terms.map{term => term.`type`}
  private def typeNames(types: Array[TypeRef]): Array[String] = types.map{t => t.name}
  private def tryFindMethod(node: AST.Node, target: ObjectTypeRef, name: String, params: Array[Term]): Option[MethodRef] = {
    val methods = target.findMethod(name, params)
    if (methods.length > 0) {
      if (methods.length > 1) {
        report(AMBIGUOUS_METHOD, node, Array[AnyRef](methods(0).affiliation, name, methods(0).arguments), Array[AnyRef](methods(1).affiliation, name, methods(1).arguments))
        None
      } else if (!isAccessible(methods(0), definition_)) {
        report(METHOD_NOT_ACCESSIBLE, node, methods(0).affiliation, name, methods(0).arguments, definition_)
        None
      } else {
        Some(methods(0))
      }
    }else {
      None
    }
  }
}
