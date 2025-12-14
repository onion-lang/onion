package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{Buffer, Set => MutableSet}

final class TypingOutlinePass(private val typing: Typing, private val unit: AST.CompilationUnit) {
  import typing.*

  private var constructorCount = 0

  def run(): Unit = {
    unit_ = unit
    mapper_ = find(topClass)
    unit.toplevels.foreach {
      case node: AST.ClassDeclaration => processClassDeclaration(node)
      case node: AST.InterfaceDeclaration => processInterfaceDeclaration(node)
      case node: AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node)
      case node: AST.FunctionDeclaration => processFunctionDeclaration(node)
      case _ =>
    }
  }

  private def processClassDeclaration(node: AST.ClassDeclaration): Unit = {
    constructorCount = 0
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)

    val classTypeParams = createTypeParams(node.typeParameters)
    declaredTypeParams_(node) = classTypeParams
    definition_.setTypeParameters(classTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray)
    constructTypeHierarchy(definition_, MutableSet[ClassType]())
    if (cyclic(definition_)) report(SemanticError.CYCLIC_INHERITANCE, node, definition_.name)

    openTypeParams(emptyTypeParams ++ classTypeParams) {
      node.defaultSection.foreach(processAccessSection)
      node.sections.foreach(processAccessSection)
    }

    if (constructorCount == 0) definition_.addDefaultConstructor
  }

  private def processInterfaceDeclaration(node: AST.InterfaceDeclaration): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)

    val interfaceTypeParams = createTypeParams(node.typeParameters)
    declaredTypeParams_(node) = interfaceTypeParams
    definition_.setTypeParameters(interfaceTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray)
    constructTypeHierarchy(definition_, MutableSet[ClassType]())
    if (cyclic(definition_)) report(SemanticError.CYCLIC_INHERITANCE, node, definition_.name)

    openTypeParams(emptyTypeParams ++ interfaceTypeParams) {
      node.methods.foreach(processInterfaceMethodDeclaration)
    }
  }

  private def processAccessSection(section: AST.AccessSection): Unit = {
    access_ = section.modifiers
    section.members.foreach {
      case node: AST.FieldDeclaration => processFieldDeclaration(node)
      case node: AST.MethodDeclaration => processMethodDeclaration(node)
      case node: AST.ConstructorDeclaration => processConstructorDeclaration(node)
      case node: AST.DelegatedFieldDeclaration => processDelegatedFieldDeclaration(node)
    }
  }

  private def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration): Unit = {
    val typeRef = mapFrom(node.typeRef)
    if (typeRef == null) return
    val modifier = node.modifiers | AST.M_PUBLIC
    val classType = loadTopClass.asInstanceOf[ClassDefinition]
    val field = new FieldDefinition(node.location, modifier, classType, node.name, typeRef)
    put(node, field)
    classType.add(field)
  }

  private def processFunctionDeclaration(node: AST.FunctionDeclaration): Unit = {
    val argsOption = typesOf(node.args)
    val returnTypeOption = Option(if (node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
    for (args <- argsOption; returnType <- returnTypeOption) {
      val classType = loadTopClass.asInstanceOf[ClassDefinition]
      val modifier = node.modifiers | AST.M_PUBLIC
      val method = new MethodDefinition(node.location, modifier, classType, node.name, args.toArray, returnType, null)
      put(node, method)
      classType.add(method)
    }
  }

  private def processFieldDeclaration(node: AST.FieldDeclaration): Unit = {
    val typeRef = mapFrom(node.typeRef)
    if (typeRef == null) return
    val modifier = node.modifiers | access_
    val field = new FieldDefinition(node.location, modifier, definition_, node.name, typeRef)
    put(node, field)
    definition_.add(field)
  }

  private def processMethodDeclaration(node: AST.MethodDeclaration): Unit = {
    val methodTypeParams = createTypeParams(node.typeParameters)
    declaredTypeParams_(node) = methodTypeParams
    openTypeParams(typeParams_ ++ methodTypeParams) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if (node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
      for (args <- argsOption; returnType <- returnTypeOption) {
        var modifier = node.modifiers | access_
        if (node.block == null) modifier |= AST.M_ABSTRACT
        val method = new MethodDefinition(
          node.location,
          modifier,
          definition_,
          node.name,
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

  private def processInterfaceMethodDeclaration(node: AST.MethodDeclaration): Unit = {
    val methodTypeParams = createTypeParams(node.typeParameters)
    declaredTypeParams_(node) = methodTypeParams
    openTypeParams(typeParams_ ++ methodTypeParams) {
      val argsOption = typesOf(node.args)
      val returnTypeOption = Option(if (node.returnType != null) mapFrom(node.returnType) else BasicType.VOID)
      for (args <- argsOption; returnType <- returnTypeOption) {
        val modifier = AST.M_PUBLIC | AST.M_ABSTRACT
        val method = new MethodDefinition(
          node.location,
          modifier,
          definition_,
          node.name,
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

  private def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit = {
    constructorCount += 1
    val argsOption = typesOf(node.args)
    for (args <- argsOption) {
      val modifier = node.modifiers | access_
      val constructor = new ConstructorDefinition(node.location, modifier, definition_, args.toArray, null, null)
      put(node, constructor)
      definition_.add(constructor)
    }
  }

  private def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration): Unit = {
    val typeRef = mapFrom(node.typeRef)
    if (typeRef == null) return
    if (!(typeRef.isObjectType && (typeRef.asInstanceOf[ObjectType]).isInterface)) {
      report(SemanticError.INTERFACE_REQUIRED, node, typeRef)
      return
    }
    val modifier = node.modifiers | access_ | AST.M_FORWARDED
    val field = new FieldDefinition(node.location, modifier, definition_, node.name, typeRef)
    put(node, field)
    definition_.add(field)
  }

  private def cyclic(start: ClassDefinition): Boolean = {
    def loop(node: ClassType, visited: Set[ClassType]): Boolean =
      node != null && {
        if (visited.contains(node)) true
        else {
          val next = visited + node
          loop(node.superClass, next) || node.interfaces.exists(loop(_, next))
        }
      }

    loop(start, Set.empty)
  }

  private def validateSuperType(node: AST.TypeNode, mustBeInterface: Boolean, mapper: NameMapper): ClassType = {
    if (node == null) {
      return if (mustBeInterface) null else table_.rootClass
    }
    val mapped = mapFrom(node, mapper)
    if (mapped == null) return if (mustBeInterface) null else table_.rootClass
    val typeRef = mapped match {
      case ct: ClassType => ct
      case _ =>
        report(SemanticError.INCOMPATIBLE_TYPE, node, table_.rootClass, mapped)
        return if (mustBeInterface) null else table_.rootClass
    }
    val isInterface = typeRef.isInterface
    if (((!isInterface) && mustBeInterface) || (isInterface && (!mustBeInterface))) {
      val location =
        typeRef match
          case cd: ClassDefinition => cd.location
          case _ => null
      report(SemanticError.ILLEGAL_INHERITANCE, location, typeRef.name)
    }
    typeRef
  }

  private def constructTypeHierarchy(node: ClassType, visit: MutableSet[ClassType]): Unit = {
    if (node == null || visit.contains(node)) return
    visit += node
    node match {
      case node: ClassDefinition =>
        if (node.isResolutionComplete) return
        val interfaces = Buffer[ClassType]()
        val resolver = find(node.name)
        val superClass =
          if (node.isInterface) {
            val ast = lookupAST(node).asInstanceOf[AST.InterfaceDeclaration]
            for (typeSpec <- ast.superInterfaces) {
              val superType = validateSuperType(typeSpec, mustBeInterface = true, resolver)
              if (superType != null) interfaces += superType
            }
            rootClass
          } else {
            val ast = lookupAST(node).asInstanceOf[AST.ClassDeclaration]
            val superClass0 = validateSuperType(ast.superClass, mustBeInterface = false, resolver)
            for (typeSpec <- ast.superInterfaces) {
              val superType = validateSuperType(typeSpec, mustBeInterface = true, resolver)
              if (superType != null) interfaces += superType
            }
            superClass0
          }

        constructTypeHierarchy(superClass, visit)
        interfaces.foreach(constructTypeHierarchy(_, visit))
        node.setSuperClass(superClass)
        node.setInterfaces(interfaces.toArray)
        node.setResolutionComplete(true)
      case _ =>
        constructTypeHierarchy(node.superClass, visit)
        node.interfaces.foreach(constructTypeHierarchy(_, visit))
    }
  }
}

