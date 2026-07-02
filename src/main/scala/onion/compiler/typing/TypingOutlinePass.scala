package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.NameResolutionContext
import onion.compiler.typing.session.TypingUnitContext
import onion.compiler.toolbox.Boxing

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{Buffer, Set => MutableSet}

final class TypingOutlinePass(private val typing: Typing, private val unitContext: TypingUnitContext) {
  private val unit = unitContext.unit
  private def table_ = typing.table_
  private def definition_ = unitContext.currentDefinition
  private def mapper_ = unitContext.currentMapper
  private def access_ = unitContext.currentAccess
  private def topClass = typing.topClass
  private def rootClass = typing.rootClass
  private def typeAliases_ = typing.typeAliases_
  private def declaredTypeParams_ = typing.declaredTypeParams_
  private def typeParams_ = unitContext.currentTypeParams
  private def emptyTypeParams = typing.emptyTypeParams
  private def report(error: SemanticError, node: AST.Node, items: AnyRef*): Unit =
    typing.report(error, node, items*)
  private def report(error: SemanticError, location: Location, items: AnyRef*): Unit =
    typing.report(error, location, items*)
  private def put(ast: AST.Node, kernel: TypedAST.Node): Unit =
    typing.put(ast, kernel)
  private def find(name: String): Option[NameResolver] =
    typing.find(name)
  private def lookupAST(kernel: TypedAST.Node): Option[AST.Node] =
    typing.lookupAST(kernel)
  private def loadRequired(name: String): ClassType =
    typing.loadRequired(name)
  private def loadTopClass: ClassType =
    typing.loadRequired(typing.topClass)
  // Outline-pass type references are all declared positions (return types,
  // fields, globals, throws, inheritance, extension receivers), so raw generic
  // types are forbidden here.
  private def mapFrom(typeNode: AST.TypeNode): Option[Type] =
    typing.mapFromDeclared(typeNode)
  private def mapFrom(typeNode: AST.TypeNode, mapper: NameResolver): Option[Type] =
    typing.mapFromDeclared(typeNode, mapper)
  private def typesOf(arguments: List[AST.Argument]): Option[List[Type]] =
    typing.typesOf(arguments)
  private def createTypeParams(nodes: List[AST.TypeParameter]): Seq[TypeParam] =
    typing.createTypeParams(nodes)
  private def openTypeParams[A](scope: TypeParamScope)(block: => A): A =
    typing.openTypeParams(scope)(block)
  private def registerExtensionMethod(receiverFqcn: String, method: ExtensionMethodDefinition): Unit =
    typing.registerExtensionMethod(receiverFqcn, method)
  private def createFQCN(moduleName: String, className: String): String =
    typing.createFQCN(moduleName, className)

  private var constructorCount = 0

  def run(): Unit = {
    find(topClass).foreach(unitContext.currentMapper = _)
    // Pre-pass: register every declared type's type parameters before any
    // hierarchy resolution, so 'class A : B[String]' works even when B is
    // declared later in the file.
    unit.toplevels.foreach {
      case node: AST.ClassDeclaration =>
        typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
          find(definition.name).foreach(unitContext.currentMapper = _)
          val tps = createTypeParams(node.typeParameters)
          declaredTypeParams_(node) = tps
          definition.setTypeParameters(tps.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray)
        }
      case node: AST.InterfaceDeclaration =>
        typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
          find(definition.name).foreach(unitContext.currentMapper = _)
          val tps = createTypeParams(node.typeParameters)
          declaredTypeParams_(node) = tps
          definition.setTypeParameters(tps.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray)
        }
      case node: AST.RecordDeclaration =>
        typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
          find(definition.name).foreach(unitContext.currentMapper = _)
          val tps = createTypeParams(node.typeParameters)
          declaredTypeParams_(node) = tps
          definition.setTypeParameters(tps.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray)
        }
      case _ =>
    }
    find(topClass).foreach(unitContext.currentMapper = _)
    unit.toplevels.foreach {
      case node: AST.ClassDeclaration => processClassDeclaration(node)
      case node: AST.InterfaceDeclaration => processInterfaceDeclaration(node)
      case node: AST.RecordDeclaration => processRecordDeclaration(node)
      case node: AST.EnumDeclaration => processEnumDeclaration(node)
      case node: AST.ExtensionDeclaration => processExtensionDeclaration(node)
      case node: AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node)
      case node: AST.FunctionDeclaration => processFunctionDeclaration(node)
      case node: AST.TypeAliasDeclaration => processTypeAliasDeclaration(node)
      case _ =>
    }
  }

  private def processClassDeclaration(node: AST.ClassDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    constructorCount = 0
    unitContext.currentDefinition = definition
    find(definition.name).foreach(unitContext.currentMapper = _)

    val classTypeParams = declaredTypeParams_.getOrElse(node, createTypeParams(node.typeParameters))
    declaredTypeParams_(node) = classTypeParams
    definition_.setTypeParameters(classTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray)
    constructTypeHierarchy(definition_, MutableSet[ClassType]())
    if (cyclic(definition_)) report(SemanticError.CYCLIC_INHERITANCE, node, definition_.name)

    openTypeParams(emptyTypeParams ++ classTypeParams) {
      node.defaultSection.foreach(processAccessSection)
      node.sections.foreach(processAccessSection)
    }

    if (constructorCount == 0) definition_.addDefaultConstructor
  }

  private def processInterfaceDeclaration(node: AST.InterfaceDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    unitContext.currentDefinition = definition
    find(definition.name).foreach(unitContext.currentMapper = _)

    val interfaceTypeParams = declaredTypeParams_.getOrElse(node, createTypeParams(node.typeParameters))
    declaredTypeParams_(node) = interfaceTypeParams
    definition_.setTypeParameters(interfaceTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray)
    constructTypeHierarchy(definition_, MutableSet[ClassType]())
    if (cyclic(definition_)) report(SemanticError.CYCLIC_INHERITANCE, node, definition_.name)

    openTypeParams(emptyTypeParams ++ interfaceTypeParams) {
      node.methods.foreach(processInterfaceMethodDeclaration)
    }
  }

  private def processRecordDeclaration(node: AST.RecordDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    unitContext.currentDefinition = definition
    find(definition.name).foreach(unitContext.currentMapper = _)

    val recordTypeParams = declaredTypeParams_.getOrElse(node, createTypeParams(node.typeParameters))
    declaredTypeParams_(node) = recordTypeParams
    definition_.setTypeParameters(recordTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray)

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

    // Process record components (args) to create fields and getters,
    // with the record's type parameters in scope
    openTypeParams(emptyTypeParams ++ recordTypeParams) {
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
      // Set argument names for named argument support (records don't have default values)
      val methodArgs = node.args.zip(argTypes).map { case (arg, argType) =>
        MethodArgument(arg.name, argType, None)
      }.toArray
      ctor.setArgumentsWithDefaults(methodArgs)
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
      val stringType = loadRequired("java.lang.String")
      val toStringMethod = new MethodDefinition(
        node.location, toStringModifier, definition_, "toString",
        Array.empty, stringType, null
      )
      definition_.add(toStringMethod)

      // Generate copy(components...): ThisType method
      val copyModifier = Modifier.PUBLIC | Modifier.SYNTHETIC_RECORD
      val copyMethod = new MethodDefinition(
        node.location, copyModifier, definition_, "copy",
        argTypes, definition_, null
      )
      definition_.add(copyMethod)

      // Pattern-attached records (`record ... from re"..."`): validate that every
      // component type can be derived from a captured String, then register the
      // synthesized parse/parseAll static methods so their bodies type normally.
      // When a component type is unsupported we report E0061 and skip synthesis so
      // the otherwise-invalid bodies don't produce noisy follow-on errors.
      // A componentless record has nothing to parse into, so a `from` clause on it
      // is meaningless; skip synthesis (no parse/parseAll) rather than emit a select
      // whose zero bindings would also dodge the E0060 group-count check.
      val hasFrom = node.fromPattern.isDefined
      val hasData = node.derives.exists(m => m == "Json" || m == "Yaml")
      // law/example methods (B3) register unconditionally — independent of from/derive and of
      // args.nonEmpty (a componentless record can still carry laws/examples; the law's own
      // params drive the check). from/data methods stay gated on the component-type check.
      val (checkMethods, derivedMethods) = node.synthesizedMethods.partition(m =>
        m.name.startsWith("onion$$law$$") || m.name.startsWith("onion$$example$$"))
      checkMethods.foreach(processMethodDeclaration)
      if ((hasFrom || hasData) && node.args.nonEmpty) {
        val unsupportedFrom =
          if (hasFrom) node.args.zip(argTypes).filterNot { case (_, argType) => isFromDerivableType(argType) }
          else Nil
        unsupportedFrom.foreach { case (arg, argType) =>
          report(SemanticError.RECORD_FROM_COMPONENT_UNSUPPORTED, arg, arg.name, argType.displayName)
        }
        val unsupportedData =
          if (hasData) node.args.zip(argTypes).filterNot { case (_, argType) => isDataDerivableType(argType) }
          else Nil
        unsupportedData.foreach { case (arg, argType) =>
          report(SemanticError.RECORD_DERIVE_COMPONENT_UNSUPPORTED, arg, arg.name, argType.displayName)
        }
        if (unsupportedFrom.isEmpty && unsupportedData.isEmpty)
          derivedMethods.foreach(processMethodDeclaration)
      }
      // Unknown derive! markers — Json and Yaml are supported at present.
      node.derives.filterNot(m => m == "Json" || m == "Yaml").foreach { mk =>
        report(SemanticError.RECORD_DERIVE_UNKNOWN_MARKER, node, mk)
      }
    }
    }
  }

  /** Component types a `from re"..."` clause can produce from a captured String. */
  private def isFromDerivableType(tp: Type): Boolean = tp match {
    case BasicType.INT | BasicType.LONG | BasicType.DOUBLE | BasicType.FLOAT |
         BasicType.BOOLEAN | BasicType.SHORT | BasicType.BYTE => true
    case ct: ClassType => ct.name == "java.lang.String"
    case _ => false
  }

  /** Component types `derive!` (Json/Yaml) can serialize — the same scalar set as `from`. */
  private def isDataDerivableType(tp: Type): Boolean = isFromDerivableType(tp)

  private def processEnumDeclaration(node: AST.EnumDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    unitContext.currentDefinition = definition
    find(definition.name).foreach(unitContext.currentMapper = _)

    // Enum extends java.lang.Enum<E>
    definition_.setSuperClass(loadRequired("java.lang.Enum"))
    definition_.setInterfaces(Array.empty)
    definition_.setResolutionComplete(true)

    val paramTypes: Array[Type] = typesOf(node.params).map(_.toArray).getOrElse(Array.empty)

    // Create static final fields for each enum constant
    node.constants.zipWithIndex.foreach { case (constant, ordinal) =>
      val fieldModifier = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL
      val field = new FieldDefinition(constant.location, fieldModifier, definition_, constant.name, definition_)
      definition_.add(field)
    }

    // Record-style parameters become private final fields with public
    // accessors (the synthetic-getter codegen path emits the bodies)
    node.params.zip(paramTypes).foreach { case (param, paramType) =>
      val field = new FieldDefinition(node.location, Modifier.PRIVATE | Modifier.FINAL, definition_, param.name, paramType)
      definition_.add(field)
      val getter = new MethodDefinition(node.location, Modifier.PUBLIC, definition_, param.name, Array.empty, paramType, null)
      definition_.add(getter)
    }

    // Create values() static method that returns array of all constants
    val valuesModifier = Modifier.PUBLIC | Modifier.STATIC
    val valuesMethod = new MethodDefinition(
      node.location, valuesModifier, definition_, "values",
      Array.empty, table_.loadArray(definition_, 1), null
    )
    definition_.add(valuesMethod)

    // Create valueOf(String) static method
    val stringType = loadRequired("java.lang.String")
    val valueOfModifier = Modifier.PUBLIC | Modifier.STATIC
    val valueOfMethod = new MethodDefinition(
      node.location, valueOfModifier, definition_, "valueOf",
      Array(stringType), definition_, null
    )
    definition_.add(valueOfMethod)

    // Create private constructor(String name, int ordinal, params...)
    // The constructor calls super(name, ordinal) on java.lang.Enum and
    // stores each parameter into its field
    val ctorModifier = Modifier.PRIVATE
    locally {
      val ctorArgs = Array[Type](stringType, BasicType.INT) ++ paramTypes
      // Create super initializer that passes both parameters to Enum constructor
      val superArgs = Array[Type](stringType, BasicType.INT)
      // Reference to constructor parameters (index 0 = name, index 1 = ordinal)
      val superTerms = Array[Term](
        new RefLocal(new ClosureLocalBinding(0, 0, stringType, false)),
        new RefLocal(new ClosureLocalBinding(0, 1, BasicType.INT, false))
      )
      val superInit = new TypedAST.Super(definition_.superClass, superArgs, superTerms)
      val fieldStores: Array[ActionStatement] = node.params.zip(paramTypes).zipWithIndex.map { case ((param, paramType), i) =>
        val field = definition_.field(param.name)
        val paramRef = new RefLocal(new ClosureLocalBinding(0, i + 2, paramType, false))
        new ExpressionActionStatement(new SetField(new This(definition_), field, paramRef)): ActionStatement
      }.toArray
      val block = if (fieldStores.isEmpty) null else new StatementBlock(fieldStores.toIndexedSeq*)
      val ctor = new ConstructorDefinition(node.location, ctorModifier, definition_, ctorArgs, block, superInit)
      definition_.add(ctor)
    }

    // User-defined members (access sections after the constant list)
    node.sections.foreach(processAccessSection)
  }

  private def processExtensionDeclaration(node: AST.ExtensionDeclaration): Unit = typing.kernelNodeOf[ClassDefinition](node).foreach { definition =>
    // The container class was registered in HeaderPass
    unitContext.currentDefinition = definition
    find(definition.name).foreach(unitContext.currentMapper = _)

    // Resolve the receiver type (the type being extended). An extension on a
    // generic type applies across all instantiations under erasure, so the
    // receiver is a raw-exempt position (like `is`/`as`); use the lenient
    // resolver rather than the declared (raw-banning) one.
    typing.mapFrom(node.receiverType).foreach { receiverType =>
      // Get the FQCN for the receiver type (for extension method lookup).
      // Primitive receivers are registered under their boxed class name because
      // method-call targets are boxed before extension resolution.
      val receiverFqcn = receiverType match {
        case ct: ClassType => ct.name
        case at: ArrayType => s"[${at.component}"  // array representation
        case bt: BasicType if bt != BasicType.VOID => Boxing.boxedType(typing.table_, bt).name
        case _ => receiverType.toString
      }

      // Process each method in the extension
      for (method <- node.methods) {
        processExtensionMethodDeclaration(method, receiverType, receiverFqcn)
      }

      // Add default constructor to container class
      definition.addDefaultConstructor
    }
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
        } else mapFrom(node.returnType)

      for (args <- argsOption; returnType <- returnTypeOption) {
        // Extension methods are always public and static in the container class
        val modifier = Modifier.PUBLIC | Modifier.STATIC
        val throwsTypes = node.throwsTypes.flatMap(t => mapFrom(t).collect { case ct: ClassType => ct }).toArray
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
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray
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
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray,
          throwsTypes,
          hasVararg
        )
        definition_.add(staticMethod)
      }
    }
  }

  private def processAccessSection(section: AST.AccessSection): Unit = {
    unitContext.currentAccess = section.modifiers
    section.members.foreach {
      case node: AST.FieldDeclaration => processFieldDeclaration(node)
      case node: AST.MethodDeclaration => processMethodDeclaration(node)
      case node: AST.ConstructorDeclaration => processConstructorDeclaration(node)
      case node: AST.DelegatedFieldDeclaration => processDelegatedFieldDeclaration(node)
    }
  }

  private def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration): Unit = mapFrom(node.typeRef).foreach { typeRef =>
    val modifier = node.modifiers | AST.M_PUBLIC
    loadTopClass match {
      case classType: ClassDefinition =>
        val field = new FieldDefinition(node.location, modifier, classType, node.name, typeRef)
        put(node, field)
        classType.add(field)
      case _ =>
        // The top-level class was resolved to something other than a source
        // class definition (e.g., a classpath collision); skip rather than crash.
        ()
    }
  }

  private def processFunctionDeclaration(node: AST.FunctionDeclaration): Unit = {
    val functionTypeParams = createTypeParams(node.typeParameters)
    declaredTypeParams_(node) = functionTypeParams
    openTypeParams(emptyTypeParams ++ functionTypeParams) {
      val argsOption = typesOf(node.args)
      val returnTypeOption =
        if (node.returnType == null) {
          report(SemanticError.RETURN_TYPE_REQUIRED, node, node.name)
          None
        } else mapFrom(node.returnType)
      for (args <- argsOption; returnType <- returnTypeOption) {
        loadTopClass match {
          case classType: ClassDefinition =>
            // final: top-level functions are never overridden, and marking them so
            // lets tail-call optimization rewrite direct self-recursion into a loop.
            val modifier = node.modifiers | AST.M_PUBLIC | AST.M_FINAL | AST.M_STATIC
            val throwsTypes = node.throwsTypes.flatMap(t => mapFrom(t).collect { case ct: ClassType => ct }).toArray
            val hasVararg = node.args.lastOption.exists(_.isVararg)
            val annotations = node.annotations.map(_.name).toSet
            val typeParams = functionTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray
            val method = new MethodDefinition(node.location, modifier, classType, node.name, args.toArray, returnType, null, typeParams, throwsTypes, hasVararg, annotations)
            put(node, method)
            classType.add(method)
          case _ =>
            // Top-level class not available as a source definition; skip.
            ()
        }
      }
    }
  }

  private def processFieldDeclaration(node: AST.FieldDeclaration): Unit = mapFrom(node.typeRef).foreach { typeRef =>
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
        } else mapFrom(node.returnType)
      for (args <- argsOption; returnType <- returnTypeOption) {
        var modifier = node.modifiers | access_
        if (node.block == null) modifier |= AST.M_ABSTRACT
        val throwsTypes = node.throwsTypes.flatMap(t => mapFrom(t).collect { case ct: ClassType => ct }).toArray
        val hasVararg = node.args.lastOption.exists(_.isVararg)
        val annotations = node.annotations.map(_.name).toSet
        val method = new MethodDefinition(
          node.location,
          modifier,
          definition_,
          node.name,
          args.toArray,
          returnType,
          null,
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray,
          throwsTypes,
          hasVararg,
          annotations
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
        } else mapFrom(node.returnType)
      for (args <- argsOption; returnType <- returnTypeOption) {
        // A body makes this a default method (non-abstract)
        val modifier = if (node.block == null) AST.M_PUBLIC | AST.M_ABSTRACT else AST.M_PUBLIC
        val throwsTypes = node.throwsTypes.flatMap(t => mapFrom(t).collect { case ct: ClassType => ct }).toArray
        val hasVararg = node.args.lastOption.exists(_.isVararg)
        val method = new MethodDefinition(
          node.location,
          modifier,
          definition_,
          node.name,
          args.toArray,
          returnType,
          null,
          methodTypeParams.map(p => TypedAST.TypeParameter(p.name, Some(p.upperBound), p.variableType.nullability)).toArray,
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

  private def processDelegatedFieldDeclaration(node: AST.DelegatedFieldDeclaration): Unit = mapFrom(node.typeRef).foreach { typeRef =>
    typeRef match {
      case objectType: ObjectType if objectType.isInterface =>
        val modifier = node.modifiers | access_ | AST.M_FORWARDED
        val field = new FieldDefinition(node.location, modifier, definition_, node.name, typeRef)
        put(node, field)
        definition_.add(field)
      case _ =>
        report(SemanticError.INTERFACE_REQUIRED, node, typeRef)
    }
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

  private def validateSuperType(node: AST.TypeNode, mustBeInterface: Boolean, mapper: NameResolver): ClassType = {
    if (node == null) {
      return if (mustBeInterface) null else table_.rootClass
    }
    val typeRef = mapFrom(node, mapper) match {
      case Some(ct: ClassType) => ct
      case Some(mapped) =>
        report(SemanticError.INCOMPATIBLE_TYPE, node, table_.rootClass, mapped)
        return if (mustBeInterface) null else table_.rootClass
      case None =>
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
        // The node's own type parameters must be in scope while resolving its
        // supertypes so that 'class Sub[T] : Super[T]' can mention T.
        def ownTypeParams(astNode: AST.Node, params: List[AST.TypeParameter]): Seq[TypeParam] =
          declaredTypeParams_.getOrElse(astNode, createTypeParams(params))
        val superClass = (find(node.name), lookupAST(node)) match {
          case (Some(resolver), Some(ast: AST.InterfaceDeclaration)) =>
            openTypeParams(typing.emptyTypeParams ++ ownTypeParams(ast, ast.typeParameters)) {
              for (typeSpec <- ast.superInterfaces) {
                val superType = validateSuperType(typeSpec, mustBeInterface = true, resolver)
                if (superType != null) interfaces += superType
              }
            }
            rootClass
          case (Some(resolver), Some(ast: AST.ClassDeclaration)) =>
            openTypeParams(typing.emptyTypeParams ++ ownTypeParams(ast, ast.typeParameters)) {
              val superClass0 = validateSuperType(ast.superClass, mustBeInterface = false, resolver)
              for (typeSpec <- ast.superInterfaces) {
                val superType = validateSuperType(typeSpec, mustBeInterface = true, resolver)
                if (superType != null) interfaces += superType
              }
              superClass0
            }
          case _ => rootClass
        }

        constructTypeHierarchy(superClass, visit)
        interfaces.foreach(constructTypeHierarchy(_, visit))
        node.setSuperClass(superClass)
        node.setInterfaces(interfaces.toArray)
        // Register this class/interface as a subtype of any sealed parent so the
        // `select` exhaustiveness check sees it. (Record subtypes are registered
        // separately in processRecordDeclaration.)
        def registerSealedParent(parent: ClassType): Unit = parent match {
          case parentDef: ClassDefinition if parentDef.isSealed => parentDef.addSealedSubtype(node)
          case _ =>
        }
        registerSealedParent(superClass)
        interfaces.foreach(registerSealedParent)
        node.setResolutionComplete(true)
      case _ =>
        constructTypeHierarchy(node.superClass, visit)
        node.interfaces.foreach(constructTypeHierarchy(_, visit))
    }
  }

  private def processTypeAliasDeclaration(node: AST.TypeAliasDeclaration): Unit = {
    val moduleName = if (unit.module != null) unit.module.name else null
    val fqcn = createFQCN(moduleName, node.name)

    typeAliases_.get(fqcn).foreach { entry =>
      // Create type parameters for generic aliases
      val aliasTypeParams = createTypeParams(node.typeParameters)

      // Update entry with resolved type parameters
      typeAliases_(fqcn) = entry.copy(typeParameters = aliasTypeParams)

      // Validate target type can be resolved
      openTypeParams(emptyTypeParams ++ aliasTypeParams) {
        val mapper = new NameResolver(NameResolutionContext.fromTyping(typing, entry.imports))
        val targetType = mapper.map(node.targetType.desc)
        if (targetType == null) {
          report(SemanticError.CLASS_NOT_FOUND, node.targetType, node.targetType.desc.toString, mapper.getCandidateClassNames)
        }
      }
    }
  }
}
