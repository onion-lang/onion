package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Classes
import onion.compiler.typing.session.TypingUnitContext

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import java.util.{TreeSet => JTreeSet}

final class TypingDuplicationPass(private val typing: Typing, private val unitContext: TypingUnitContext) {
  private val unit = unitContext.unit

  private val seenMethods = new JTreeSet[Method](new MethodComparator)
  private val seenFields = new JTreeSet[FieldRef](new FieldComparator)
  private val seenConstructors = new JTreeSet[ConstructorRef](new ConstructorComparator)
  private val seenGlobalVariables = new JTreeSet[FieldRef](new FieldComparator)
  private val seenFunctions = new JTreeSet[Method](new MethodComparator)

  private def withKernel[T <: Node : ClassTag](ast: AST.Node)(f: T => Unit): Unit =
    typing.kernelNodeOf[T](ast).foreach(f)

  def run(): Unit = {
    typing.find(typing.topClass).foreach(unitContext.currentMapper = _)
    seenGlobalVariables.clear()
    seenFunctions.clear()
    unit.toplevels.foreach {
      case node: AST.ClassDeclaration => processClassDeclaration(node)
      case node: AST.InterfaceDeclaration => processInterfaceDeclaration(node)
      case node: AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node)
      case node: AST.FunctionDeclaration => processFunctionDeclaration(node)
      case _ =>
    }
  }

  private def resetForTypeDeclaration(clazz: ClassDefinition): Unit = {
    seenMethods.clear()
    seenFields.clear()
    seenConstructors.clear()
    unitContext.currentDefinition = clazz
    typing.find(clazz.name).foreach(unitContext.currentMapper = _)
  }

  private def registerField(ast: AST.Node, field: FieldDefinition): Unit =
    if (seenFields.contains(field)) typing.report(SemanticError.DUPLICATE_FIELD, ast, field.affiliation, field.name)
    else seenFields.add(field)

  private def registerMethod(ast: AST.Node, method: MethodDefinition): Unit =
    if (seenMethods.contains(method)) typing.report(SemanticError.DUPLICATE_METHOD, ast, method.affiliation, method.name, method.arguments)
    else seenMethods.add(method)

  private def registerConstructor(ast: AST.Node, constructor: ConstructorDefinition): Unit =
    if (seenConstructors.contains(constructor)) typing.report(SemanticError.DUPLICATE_CONSTRUCTOR, ast, constructor.affiliation, constructor.getArgs)
    else seenConstructors.add(constructor)

  private def processFieldLikeDeclaration(ast: AST.Node): Unit =
    withKernel[FieldDefinition](ast)(field => registerField(ast, field))

  private def processMethodDeclaration(node: AST.MethodDeclaration): Unit =
    withKernel[MethodDefinition](node)(method => registerMethod(node, method))

  private def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit =
    withKernel[ConstructorDefinition](node)(ctor => registerConstructor(node, ctor))

  private def processAccessSection(node: AST.AccessSection): Unit = {
    for (member <- node.members)
      member match {
        case node: AST.FieldDeclaration => processFieldLikeDeclaration(node)
        case node: AST.DelegatedFieldDeclaration => processFieldLikeDeclaration(node)
        case node: AST.MethodDeclaration => processMethodDeclaration(node)
        case node: AST.ConstructorDeclaration => processConstructorDeclaration(node)
      }
  }

  private def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration): Unit =
    withKernel[FieldDefinition](node) { field =>
      if (seenGlobalVariables.contains(field)) typing.report(SemanticError.DUPLICATE_GLOBAL_VARIABLE, node, field.name)
      else seenGlobalVariables.add(field)
    }

  private def processFunctionDeclaration(node: AST.FunctionDeclaration): Unit =
    withKernel[MethodDefinition](node) { method =>
      if (seenFunctions.contains(method)) typing.report(SemanticError.DUPLICATE_FUNCTION, node, method.name, method.arguments)
      else seenFunctions.add(method)
    }

  private def makeDelegationMethod(delegated: FieldRef, delegator: Method): MethodDefinition = {
    // Substitute the forwarded interface's type parameters with the field's type
    // arguments, so `forward val x: Container[Int]` generates get(): Int (the
    // specialized signature the class must implement), not the raw get(): T. Use
    // the hierarchy substitution keyed on the method's declaring interface so an
    // inherited method's own type parameters are mapped through the extends chain;
    // a method-level type parameter (unbound by the class) erases to its bound.
    val classSubst = TypeSubstitution.hierarchySubstitution(delegated.`type`, delegator.affiliation)
    def sub(t: Type): Type =
      TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
    val args = delegator.arguments.map(sub)
    val returnType = sub(delegator.returnType)
    val params = new Array[Term](args.length)
    val frame = new LocalFrame(null)
    for (i <- 0 until params.length) {
      val index = frame.add("arg" + i, args(i))
      params(i) = new RefLocal(new ClosureLocalBinding(0, index, args(i), isMutable = true))
    }
    val call = new Call(new RefField(new This(unitContext.currentDefinition), delegated), delegator, params)
    // The delegate call keeps the raw (erased) return type; cast it to the
    // substituted return type so the generated method's `areturn` is well-typed.
    val target: Term =
      if (returnType != BasicType.VOID && !(call.`type` eq returnType) && !returnType.isBasicType)
        new AsInstanceOf(call, returnType)
      else call
    val statement =
      if (returnType != BasicType.VOID) new StatementBlock(new Return(target))
      else new StatementBlock(new ExpressionActionStatement(target), new Return(null))
    val node = new MethodDefinition(null, AST.M_PUBLIC, unitContext.currentDefinition, delegator.name, args, returnType, statement)
    node.setFrame(frame)
    node
  }

  /** The JVM (erased) signature of a method — name plus each argument's bytecode
    * descriptor. Two methods with the same key define the same bytecode method. */
  private def bytecodeSignature(method: Method): String =
    method.name + method.arguments.map(a => onion.compiler.generics.Erasure.asmType(a).getDescriptor).mkString("(", "", ")")

  private def generateForwardedMethods(): Unit = {
    // Track emitted JVM signatures, seeded with the class's own methods. A method
    // declared at several levels of the forwarded interface's hierarchy (e.g.
    // addLast on both List and SequencedCollection) substitutes to the same
    // specialized signature, so key on that to emit each once; a second copy
    // would be a ClassFormatError.
    val generatedKeys = scala.collection.mutable.HashSet[String]()
    unitContext.currentDefinition.methods.foreach(m => generatedKeys.add(bytecodeSignature(m)))

    def generateDelegationMethods(field: FieldDefinition): Unit = {
      field.`type` match {
        case typeRef: ClassType =>
          val src = Classes.getInterfaceMethods(typeRef)
          for (method <- src.asScala) {
            if (!seenMethods.contains(method)) {
              val generatedMethod = makeDelegationMethod(field, method)
              val key = bytecodeSignature(generatedMethod)
              if (!generatedKeys.contains(key)) {
                generatedKeys.add(key)
                unitContext.currentDefinition.add(generatedMethod)
              }
            }
          }
        case _ =>
          // Forwarded fields must be interface/class types; non-class types
          // cannot generate delegation methods. Skip rather than crash.
          ()
      }
    }

    for (field <- seenFields.asScala) {
      if ((AST.M_FORWARDED & field.modifier) != 0) generateDelegationMethods(field.asInstanceOf[FieldDefinition])
    }
  }

  private def processClassDeclaration(node: AST.ClassDeclaration): Unit =
    withKernel[ClassDefinition](node) { clazz =>
      resetForTypeDeclaration(clazz)
      for (defaultSection <- node.defaultSection) processAccessSection(defaultSection)
      for (section <- node.sections) processAccessSection(section)
      generateForwardedMethods()
      DuplicationChecks.checkOverrideContracts(typing, clazz, node.location)
      DuplicationChecks.checkAbstractMethodImplementation(typing, clazz, node.location)
      DuplicationChecks.checkErasureSignatureCollisions(typing, clazz, node.location)
    }

  private def processInterfaceDeclaration(node: AST.InterfaceDeclaration): Unit =
    withKernel[ClassDefinition](node) { clazz =>
      resetForTypeDeclaration(clazz)
      for (methodDecl <- node.methods) processMethodDeclaration(methodDecl)
      DuplicationChecks.checkErasureSignatureCollisions(typing, clazz, node.location)
    }
}
