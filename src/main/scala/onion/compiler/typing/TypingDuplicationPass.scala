package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Classes

import scala.jdk.CollectionConverters.*
import java.util.{TreeSet => JTreeSet}

final class TypingDuplicationPass(private val typing: Typing, private val unit: AST.CompilationUnit) {
  import typing.*

  private val seenMethods = new JTreeSet[Method](new MethodComparator)
  private val seenFields = new JTreeSet[FieldRef](new FieldComparator)
  private val seenConstructors = new JTreeSet[ConstructorRef](new ConstructorComparator)
  private val seenGlobalVariables = new JTreeSet[FieldRef](new FieldComparator)
  private val seenFunctions = new JTreeSet[Method](new MethodComparator)

  private def withKernel[T <: Node](ast: AST.Node)(f: T => Unit): Unit = {
    val kernel = lookupKernelNode(ast)
    if (kernel != null) f(kernel.asInstanceOf[T])
  }

  def run(): Unit = {
    unit_ = unit
    mapper_ = find(topClass)
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
    definition_ = clazz
    mapper_ = find(clazz.name)
  }

  private def registerField(ast: AST.Node, field: FieldDefinition): Unit =
    if (seenFields.contains(field)) report(SemanticError.DUPLICATE_FIELD, ast, field.affiliation, field.name)
    else seenFields.add(field)

  private def registerMethod(ast: AST.Node, method: MethodDefinition): Unit =
    if (seenMethods.contains(method)) report(SemanticError.DUPLICATE_METHOD, ast, method.affiliation, method.name, method.arguments)
    else seenMethods.add(method)

  private def registerConstructor(ast: AST.Node, constructor: ConstructorDefinition): Unit =
    if (seenConstructors.contains(constructor)) report(SemanticError.DUPLICATE_CONSTRUCTOR, ast, constructor.affiliation, constructor.getArgs)
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
      if (seenGlobalVariables.contains(field)) report(SemanticError.DUPLICATE_GLOBAL_VARIABLE, node, field.name)
      else seenGlobalVariables.add(field)
    }

  private def processFunctionDeclaration(node: AST.FunctionDeclaration): Unit =
    withKernel[MethodDefinition](node) { method =>
      if (seenFunctions.contains(method)) report(SemanticError.DUPLICATE_FUNCTION, node, method.name, method.arguments)
      else seenFunctions.add(method)
    }

  private def makeDelegationMethod(delegated: FieldRef, delegator: Method): MethodDefinition = {
    val args = delegator.arguments
    val params = new Array[Term](args.length)
    val frame = new LocalFrame(null)
    for (i <- 0 until params.length) {
      val index = frame.add("arg" + i, args(i))
      params(i) = new RefLocal(new ClosureLocalBinding(0, index, args(i), isMutable = true))
    }
    val target = new Call(new RefField(new This(definition_), delegated), delegator, params)
    val statement =
      if (delegator.returnType != BasicType.VOID) new StatementBlock(new Return(target))
      else new StatementBlock(new ExpressionActionStatement(target), new Return(null))
    val node = new MethodDefinition(null, AST.M_PUBLIC, definition_, delegator.name, delegator.arguments, delegator.returnType, statement)
    node.setFrame(frame)
    node
  }

  private def generateForwardedMethods(): Unit = {
    val generated = new JTreeSet[Method](new MethodComparator)

    def generateDelegationMethods(field: FieldDefinition): Unit = {
      val typeRef = field.`type`.asInstanceOf[ClassType]
      val src = Classes.getInterfaceMethods(typeRef)
      for (method <- src.asScala) {
        if (!seenMethods.contains(method)) {
          if (generated.contains(method)) {
            report(SemanticError.DUPLICATE_GENERATED_METHOD, field.location, method.affiliation, method.name, method.arguments)
          } else {
            val generatedMethod = makeDelegationMethod(field, method)
            generated.add(generatedMethod)
            definition_.add(generatedMethod)
          }
        }
      }
    }

    for (field <- seenFields.asScala) {
      if ((AST.M_FORWARDED & field.modifier) != 0) generateDelegationMethods(field.asInstanceOf[FieldDefinition])
    }
  }

  private def processClassDeclaration(node: AST.ClassDeclaration): Unit = {
    val clazz = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    if (clazz == null) return
    resetForTypeDeclaration(clazz)
    for (defaultSection <- node.defaultSection) processAccessSection(defaultSection)
    for (section <- node.sections) processAccessSection(section)
    generateForwardedMethods()
    DuplicationChecks.checkOverrideContracts(typing, clazz, node.location)
    DuplicationChecks.checkErasureSignatureCollisions(typing, clazz, node.location)
  }

  private def processInterfaceDeclaration(node: AST.InterfaceDeclaration): Unit = {
    val clazz = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    if (clazz == null) return
    resetForTypeDeclaration(clazz)
    for (methodDecl <- node.methods) processMethodDeclaration(methodDecl)
    DuplicationChecks.checkErasureSignatureCollisions(typing, clazz, node.location)
  }
}
