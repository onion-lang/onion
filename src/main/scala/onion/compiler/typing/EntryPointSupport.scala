package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.Buffer

private[compiler] final class EntryPointSupport(
  typing: Typing,
  addReturnNode: (ActionStatement, Type) => StatementBlock
) {
  def stringArgsType: ArrayType =
    typing.loadArray(typing.loadRequired("java.lang.String"), 1)

  def createStartMethod(
    unit: AST.CompilationUnit,
    klass: ClassDefinition,
    argsType: Type
  ): MethodDefinition =
    new MethodDefinition(unit.location, AST.M_PUBLIC, klass, "start", Array[Type](argsType), BasicType.VOID, null)

  def createMain(top: ClassType, ref: Method, name: String, args: Array[Type], ret: Type): MethodDefinition = {
    val method = new MethodDefinition(null, AST.M_STATIC | AST.M_PUBLIC, top, name, args, ret, null)
    val frame = new LocalFrame(null)
    val params = new Array[Term](args.length)
    for (i <- 0 until args.length) {
      val arg = args(i)
      val index = frame.add("args" + i, arg)
      params(i) = new RefLocal(0, index, arg)
    }
    method.setFrame(frame)
    val constructor = top.findConstructor(new Array[Term](0))(0)
    var block = new StatementBlock(
      new ExpressionActionStatement(new Call(new NewObject(constructor, new Array[Term](0)), ref, params))
    )
    block = addReturnNode(block, BasicType.VOID)
    method.setBlock(block)
    method
  }

  def attachStartAndMain(
    klass: ClassDefinition,
    startMethod: MethodDefinition,
    statements: Buffer[ActionStatement],
    context: LocalContext,
    argsType: Type
  ): Unit = {
    statements += new Return(null)
    startMethod.setBlock(new StatementBlock(statements.asJava))
    startMethod.setFrame(context.getContextFrame)
    klass.add(startMethod)
    // If the user already defined a top-level `def main(args: String[])`, the
    // typing pass added it as a public static method on this class, so it already
    // serves as the JVM entry point. Synthesizing a competing `main` would
    // collide, so use the user's main instead (the entry-point rule prefers a
    // main on the top-level class; any bare top-level statements in `start` are
    // then not run).
    val existingMain = klass.methods("main").exists { m =>
      m.arguments.length == 1 && m.arguments(0) == argsType
    }
    if (!existingMain) {
      klass.add(createMain(klass, startMethod, "main", Array[Type](argsType), BasicType.VOID))
    }
  }
}
