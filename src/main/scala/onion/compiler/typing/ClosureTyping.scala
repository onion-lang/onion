package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

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
            candidates.find(m => sameTypes(substitutedArgs(m), argTypes)) match {
              case None =>
                report(METHOD_NOT_FOUND, node, typeRef, name, argTypes)
                None
              case Some(method) =>
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

                val prologue = args.zipWithIndex.flatMap { case (arg, i) =>
                  Option(context.lookup(arg.name)).flatMap { bind =>
                    val erased = TypeSubstitution.substituteType(
                      typedMethod.arguments(i), Map.empty, Map.empty, defaultToBound = true
                    )
                    val desired = expectedArgs(i)
                    Option.when(desired ne erased) {
                      val rawBind = new ClosureLocalBinding(bind.frameIndex, bind.index, erased, bind.isMutable)
                      val casted = new AsInstanceOf(new RefLocal(rawBind), desired)
                      new ExpressionActionStatement(new SetLocal(bind, casted))
                    }
                  }
                }

                val baseBlock: ActionStatement = body.translate(node.body, context)
                val block = if (prologue.nonEmpty)
                  new StatementBlock((prologue :+ baseBlock).asJava)
                else baseBlock

                val finalBlock = body.addReturnNode(block, expectedRet)
                val result = new NewClosure(typeRef, typedMethod, finalBlock)
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
