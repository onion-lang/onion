package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import scala.collection.mutable.Buffer
import scala.jdk.CollectionConverters.*

final class ClosureTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def typeClosure(node: AST.ClosureExpression, context: LocalContext, expected: Type): Option[Term] = {
    val args = node.args
    val name = node.mname
    body.openFrame(context) {
      body.openClosure(context) {
        val argTypes = args.map { arg => addArgument(arg, context) }.toArray
        if (argTypes.exists(_ == null)) {
          None
        } else {
          val inferredTarget: ClassType =
            expected match {
              case ct: ClassType if node.typeRef.isRelaxed && ct.isInterface => ct
              case _ => null
            }

          val typeRef = Option(inferredTarget).getOrElse(mapFrom(node.typeRef).asInstanceOf[ClassType])
          if (typeRef == null) {
            None
          } else if (!typeRef.isInterface) {
            report(INTERFACE_REQUIRED, node.typeRef, typeRef)
            None
          } else {
            val classSubst = TypeSubstitution.classSubstitution(typeRef)

            def substitutedArgs(method: Method): Array[Type] =
              method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true))

            def substitutedReturn(method: Method): Type =
              TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)

            val candidates = typeRef.methods.filter(m => m.name == name && m.arguments.length == argTypes.length)
            val method = candidates.find(m => sameTypes(substitutedArgs(m), argTypes)).getOrElse(null)
            if (method == null) {
              report(METHOD_NOT_FOUND, node, typeRef, name, argTypes)
              None
            } else {
              val expectedArgs = substitutedArgs(method)
              val expectedRet = substitutedReturn(method)

              val typedMethod = new Method {
                def modifier: Int = method.modifier
                def affiliation: ClassType = method.affiliation
                def name: String = method.name
                override def arguments: Array[Type] = expectedArgs.clone()
                override def returnType: Type = expectedRet
                override def typeParameters: Array[TypedAST.TypeParameter] = Array()
              }

              context.setMethod(typedMethod)
              context.getContextFrame.parent.setAllClosed(true)

              val prologue = Buffer[ActionStatement]()
              var i = 0
              while (i < args.length) {
                val bind = context.lookup(args(i).name)
                if (bind != null) {
                  val erased =
                    TypeSubstitution.substituteType(
                      method.arguments(i),
                      scala.collection.immutable.Map.empty,
                      scala.collection.immutable.Map.empty,
                      defaultToBound = true
                    )
                  val desired = expectedArgs(i)
                  if (desired ne erased) {
                    val rawBind = new ClosureLocalBinding(bind.frameIndex, bind.index, erased, bind.isMutable)
                    val casted = new AsInstanceOf(new RefLocal(rawBind), desired)
                    prologue += new ExpressionActionStatement(new SetLocal(bind, casted))
                  }
                }
                i += 1
              }

              var block: ActionStatement = body.translate(node.body, context)
              if (prologue.nonEmpty) {
                block = new StatementBlock((prologue.toIndexedSeq :+ block).asJava)
              }

              block = body.addReturnNode(block, expectedRet)
              val result = new NewClosure(typeRef, method, block)
              result.frame_=(context.getContextFrame)
              Some(result)
            }
          }
        }
      }
    }
  }

  private def addArgument(arg: AST.Argument, context: LocalContext): Type =
    body.addArgument(arg, context)

  private def sameTypes(left: Array[Type], right: Array[Type]): Boolean = {
    if (left.length != right.length) return false
    (for (i <- 0 until left.length) yield (left(i), right(i))).forall { case (l, r) => l eq r }
  }
}
