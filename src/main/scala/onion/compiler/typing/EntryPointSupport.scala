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
    fieldInitStatements: Buffer[ActionStatement],
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
    val userMain = klass.methods("main").collectFirst {
      case m: MethodDefinition if m.arguments.length == 1 && m.arguments(0) == argsType => m
    }
    userMain match {
      case Some(main) =>
        // The user's `main` is the entry point, so `start` is never invoked and
        // top-level `val`/`var` field initializers would never run, leaving the
        // static fields at their default (null/0/false) — a silent miscompile
        // (#270). Run just the field initializers (not bare executable top-level
        // statements) before the user's `main` body. They reuse `start`'s frame,
        // so we synthesize a field-init-only instance method mirroring `start`
        // and prepend a call to it at the top of `main`.
        if (fieldInitStatements.nonEmpty) {
          prependFieldInitializers(klass, startMethod, main, fieldInitStatements, context, argsType)
        }
        // Bare executable top-level statements (not `val`/`var` field
        // initializers, not the synthetic trailing return) are silently dropped
        // because the user's `main` is the entry point. Warn so the code is not
        // mistaken for something that runs (#278).
        val bareExecs = statements.filterNot(s =>
          s.isInstanceOf[Return] || fieldInitStatements.contains(s))
        bareExecs.headOption.foreach { first =>
          val loc = if (first.location != null) first.location else main.location
          typing.warningReporter_.discardedTopLevelStatements(loc, bareExecs.size)
        }
      case None =>
        klass.add(createMain(klass, startMethod, "main", Array[Type](argsType), BasicType.VOID))
    }
  }

  /**
   * Synthesize a field-initializer-only instance method (reusing `start`'s frame)
   * and prepend a call to it at the top of the user-defined `main`, so top-level
   * `val`/`var` initializers run before `main` executes (#270).
   */
  private def prependFieldInitializers(
    klass: ClassDefinition,
    startMethod: MethodDefinition,
    main: MethodDefinition,
    fieldInitStatements: Buffer[ActionStatement],
    context: LocalContext,
    argsType: Type
  ): Unit = {
    val initMethod = new MethodDefinition(
      null, AST.M_PUBLIC, klass, "onion$fieldinit", Array[Type](argsType), BasicType.VOID, null
    )
    val initStatements = Buffer[ActionStatement]()
    initStatements ++= fieldInitStatements
    initStatements += new Return(null)
    initMethod.setBlock(new StatementBlock(initStatements.asJava))
    // The field initializers reference locals in `start`'s frame; share it.
    initMethod.setFrame(context.getContextFrame)
    klass.add(initMethod)

    // `new TopClass().onion$fieldinit(args)` — `args` is the first parameter of
    // the static `main`, so it lives at local slot 0.
    val constructor = klass.findConstructor(new Array[Term](0))(0)
    val argsRef = new RefLocal(0, 0, argsType)
    val call = new Call(new NewObject(constructor, new Array[Term](0)), initMethod, Array[Term](argsRef))
    val initCall = new ExpressionActionStatement(call)

    val body = main.getBlock
    val newBody =
      if (body == null) new StatementBlock(initCall)
      else new StatementBlock((initCall +: body.statements.toIndexedSeq)*)
    main.setBlock(newBody)
  }
}
