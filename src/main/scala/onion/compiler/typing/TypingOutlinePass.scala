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
      case node: AST.RecordDeclaration => processRecordDeclaration(node)
      case node: AST.EnumDeclaration => processEnumDeclaration(node)
      case node: AST.ExtensionDeclaration => processExtensionDeclaration(node)
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

  private def processRecordDeclaration(node: AST.RecordDeclaration): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)

    // Record extends Object, may implement interfaces
    definition_.setSuperClass(rootClass)

    // Process super interfaces if any
    val interfaces = Buffer[ClassType]()
    for (typeSpec <- node.superInterfaces) {
      val superType = validateSuperType(typeSpec, mustBeInterface = true, mapper_)
      if (superType != null) {
        interfaces += superType
        // Register this record as a subtype of sealed interfaces
        superType match {
          case classDef: ClassDefinition if classDef.isSealed =>
            classDef.addSealedSubtype(definition_)
          case _ =>
        }
      }
    }
    definition_.setInterfaces(interfaces.toArray)
    definition_.setResolutionComplete(true)

    // Process record components (args) to create fields and getters
    val argsOption = typesOf(node.args)
    for (args <- argsOption) {
      val argTypes = args.toArray

      // Store record components for codegen
      val components = node.args.zip(argTypes).map { case (arg, argType) =>
        (arg.name, argType)
      }.toArray
      definition_.setRecordComponents(components)

      // Create private final fields for each component
      node.args.zip(argTypes).foreach { case (arg, argType) =>
        val fieldModifier = Modifier.PRIVATE | Modifier.FINAL
        val field = new FieldDefinition(node.location, fieldModifier, definition_, arg.name, argType)
        definition_.add(field)
      }

      // Create public getter methods for each component
      node.args.zip(argTypes).foreach { case (arg, argType) =>
        val getterModifier = Modifier.PUBLIC
        val getter = new MethodDefinition(node.location, getterModifier, definition_, arg.name, Array.empty, argType, null)
        definition_.add(getter)
      }

      // Create constructor
      val ctorModifier = Modifier.PUBLIC
      val ctor = new ConstructorDefinition(node.location, ctorModifier, definition_, argTypes, null, null)
      definition_.add(ctor)

      // Generate equals(Object): Boolean method
      val equalsModifier = Modifier.PUBLIC | Modifier.SYNTHETIC_RECORD
      val equalsMethod = new MethodDefinition(
        node.location, equalsModifier, definition_, "equals",
        Array(rootClass), BasicType.BOOLEAN, null
      )
      definition_.add(equalsMethod)

      // Generate hashCode(): Int method
      val hashCodeModifier = Modifier.PUBLIC | Modifier.SYNTHETIC_RECORD
      val hashCodeMethod = new MethodDefinition(
        node.location, hashCodeModifier, definition_, "hashCode",
        Array.empty, BasicType.INT, null
      )
      definition_.add(hashCodeMethod)

      // Generate toString(): String method
      val toStringModifier = Modifier.PUBLIC | Modifier.SYNTHETIC_RECORD
      val stringType = load("java.lang.String")
      if (stringType != null) {
        val toStringMethod = new MethodDefinition(
          node.location, toStringModifier, definition_, "toString",
          Array.empty, stringType, null
        )
        definition_.add(toStringMethod)
      }

      // Generate copy(components...): ThisType method
      val copyModifier = Modifier.PUBLIC | Modifier.SYNTHETIC_RECORD
      val copyMethod = new MethodDefinition(
        node.location, copyModifier, definition_, "copy",
        argTypes, definition_, null
      )
      definition_.add(copyMethod)
    }
  }

  private def processEnumDeclaration(node: AST.EnumDeclaration): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)

    // Enum extends java.lang.Enum<E>
    val enumClass = load("java.lang.Enum")
    if (enumClass != null) {
      definition_.setSuperClass(enumClass)
    } else {
      // Fallback to Object if Enum not found
      definition_.setSuperClass(rootClass)
    }
    definition_.setInterfaces(Array.empty)
    definition_.setResolutionComplete(true)

    // Create static final fields for each enum constant
    node.constants.zipWithIndex.foreach { case (constant, ordinal) =>
      val fieldModifier = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL
      val field = new FieldDefinition(constant.location, fieldModifier, definition_, constant.name, definition_)
      definition_.add(field)
    }

    // Create values() static method that returns array of all constants
    val valuesModifier = Modifier.PUBLIC | Modifier.STATIC
    val valuesMethod = new MethodDefinition(
      node.location, valuesModifier, definition_, "values",
      Array.empty, table_.loadArray(definition_, 1), null
    )
    definition_.add(valuesMethod)

    // Create valueOf(String) static method
    val stringType = load("java.lang.String")
    if (stringType != null) {
      val valueOfModifier = Modifier.PUBLIC | Modifier.STATIC
      val valueOfMethod = new MethodDefinition(
        node.location, valueOfModifier, definition_, "valueOf",
        Array(stringType), definition_, null
      )
      definition_.add(valueOfMethod)
    }

    // Create private constructor(String name, int ordinal)
    // The constructor calls super(name, ordinal) on java.lang.Enum
    val ctorModifier = Modifier.PRIVATE
    if (stringType != null) {
      val ctorArgs = Array[Type](stringType, BasicType.INT)
      // Create super initializer that passes both parameters to Enum constructor
      val superArgs = Array[Type](stringType, BasicType.INT)
      // Reference to constructor parameters (index 0 = name, index 1 = ordinal)
      val superTerms = Array[Term](
        new RefLocal(new ClosureLocalBinding(0, 0, stringType, false)),
        new RefLocal(new ClosureLocalBinding(0, 1, BasicType.INT, false))
      )
      val superInit = new TypedAST.Super(definition_.superClass, superArgs, superTerms)
      val ctor = new ConstructorDefinition(node.location, ctorModifier, definition_, ctorArgs, null, superInit)
      definition_.add(ctor)
    }
  }

  private def processExtensionDeclaration(node: AST.ExtensionDeclaration): Unit = {
    // Get the container class registered in HeaderPass
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)

    // Resolve the receiver type (the type being extended)
    val receiverType = mapFrom(node.receiverType)
    if (receiverType == null) {
      report(SemanticError.CLASS_NOT_FOUND, node.receiverType, node.receiverType.desc.toString)
      return
    }

    // Get the FQCN for the receiver type (for extension method lookup)
    val receiverFqcn = receiverType match {
      case ct: ClassType => ct.name
      case at: ArrayType => s"[${at.component}"  // array representation
      case _ => receiverType.toString
    }

    // Process each method in the extension
    for (method <- node.methods) {
      processExtensionMethodDeclaration(method, receiverType, receiverFqcn)
    }

    // Add default constructor to container class
    definition_.addDefaultConstructor
  }

  private def processExtensionMethodDeclaration(
    node: AST.MethodDeclaration,
    receiverType: Type,
    receiverFqcn: String
  ): Unit = {
    val methodTypeParams = createTypeParams(node.typeParameters)
    declaredTypeParams_(node) = methodTypeParams

    openTypeParams(typeParams_ ++ methodTypeParams) {
      val argsOption = typesOf(node.args)
      val returnTypeOption =
        if (node.returnType == null) {
          report(SemanticError.RETURN_TYPE_REQUIRED, node, s"extension ${receiverFqcn}.${node.name}")
          None
        } else Option(mapFrom(node.returnType))

      for (args <- argsOption; returnType <- returnTypeOption) {
        // Extension methods are always public and static in the container class
        val modifier = Modifier.PUBLIC | Modifier.STATIC
        val throwsTypes = node.throwsTypes.flatMap(t => Option(mapFrom(t)).map(_.asInstanceOf[ClassType])).toArray
        val hasVararg = node.args.lastOption.exists(_.isVararg)

        // Create ExtensionMethodDefinition
        val extensionMethod = new ExtensionMethodDefinition(
          node.location,
          modifier,
          receiverType,
          definition_,  // container class
          node.name,
          args.toArray,
          returnType,
          null,  // block will be set in BodyPass
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray
        )
        put(node, extensionMethod)

        // Register the extension method for lookup during method resolution
        registerExtensionMethod(receiverFqcn, extensionMethod)

        // Also create a static method in the container class
        // The static method has an extra first parameter for the receiver
        val staticArgs = Array(receiverType) ++ args.toArray
        val staticMethod = new MethodDefinition(
          node.location,
          modifier,
          definition_,
          node.name,
          staticArgs,
          returnType,
          null,
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray,
          throwsTypes,
          hasVararg
        )
        definition_.add(staticMethod)
      }
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
    val returnTypeOption =
      if (node.returnType == null) {
        report(SemanticError.RETURN_TYPE_REQUIRED, node, node.name)
        None
      } else Option(mapFrom(node.returnType))
    for (args <- argsOption; returnType <- returnTypeOption) {
      val classType = loadTopClass.asInstanceOf[ClassDefinition]
      val modifier = node.modifiers | AST.M_PUBLIC
      val throwsTypes = node.throwsTypes.flatMap(t => Option(mapFrom(t)).map(_.asInstanceOf[ClassType])).toArray
      val hasVararg = node.args.lastOption.exists(_.isVararg)
      val method = new MethodDefinition(node.location, modifier, classType, node.name, args.toArray, returnType, null, Array(), throwsTypes, hasVararg)
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
      val returnTypeOption =
        if (node.returnType == null) {
          report(SemanticError.RETURN_TYPE_REQUIRED, node, s"${definition_.name}.${node.name}")
          None
        } else Option(mapFrom(node.returnType))
      for (args <- argsOption; returnType <- returnTypeOption) {
        var modifier = node.modifiers | access_
        if (node.block == null) modifier |= AST.M_ABSTRACT
        val throwsTypes = node.throwsTypes.flatMap(t => Option(mapFrom(t)).map(_.asInstanceOf[ClassType])).toArray
        val hasVararg = node.args.lastOption.exists(_.isVararg)
        val method = new MethodDefinition(
          node.location,
          modifier,
          definition_,
          node.name,
          args.toArray,
          returnType,
          null,
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray,
          throwsTypes,
          hasVararg
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
      val returnTypeOption =
        if (node.returnType == null) {
          report(SemanticError.RETURN_TYPE_REQUIRED, node, s"${definition_.name}.${node.name}")
          None
        } else Option(mapFrom(node.returnType))
      for (args <- argsOption; returnType <- returnTypeOption) {
        val modifier = AST.M_PUBLIC | AST.M_ABSTRACT
        val throwsTypes = node.throwsTypes.flatMap(t => Option(mapFrom(t)).map(_.asInstanceOf[ClassType])).toArray
        val hasVararg = node.args.lastOption.exists(_.isVararg)
        val method = new MethodDefinition(
          node.location,
          modifier,
          definition_,
          node.name,
          args.toArray,
          returnType,
          null,
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound))).toArray,
          throwsTypes,
          hasVararg
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
