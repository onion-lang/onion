package onion.compiler
import _root_.java.util.{TreeSet => JTreeSet}
import _root_.scala.collection.JavaConversions._
import _root_.onion.compiler.util.{Boxing, Classes, Paths, Systems}
import _root_.onion.compiler.SemanticErrorReporter.Constants._
import _root_.onion.compiler.IxCode.BinaryExpression.Constants._
import _root_.onion.compiler.IxCode.UnaryExpression.Constants._
import _root_.scala.collection.mutable.{Buffer, Map, HashMap, Set => MutableSet}

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
    //TODO: Implement this method.
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
}
