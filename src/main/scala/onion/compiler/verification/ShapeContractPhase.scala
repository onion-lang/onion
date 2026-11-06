package onion.compiler.verification

import onion.compiler.{CompileError, CompileWarning, CompiledClass, CompilerConfig, ContractMode, OnionClassLoader, SemanticError, WarningCategory}
import onion.compiler.contracts.ContractTable
import onion.compiler.exceptions.CompilationException
import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.toolbox.Message

import java.lang.reflect.{Modifier, ParameterizedType}
import scala.collection.mutable.ArrayBuffer

/**
 * The contract of `onion.Shape` (RFC #2557, design 5.1), checked on every concrete implementation.
 *
 * L1, the round-trip, is the contract: `parse(print(v)) == Ok(v)` for each value `v` the shape can
 * print, which is `canPrint()` and `printable(v)`. L2 (`print(parse(t)) == t`) is false in general
 * and is never asserted. The values are sampled with the sampler a predicate with parameters uses.
 *
 *  - a value that does not round-trip is `E0114`, with the value and the seed that reproduces it;
 *  - a type that cannot be sampled is the warning `W0022`, never a silent pass;
 *  - a shape that cannot print (`canPrint() == false`) owes nothing.
 *
 * Only with `--contracts check`, and not in the editor (`runContracts = false`).
 */
final class ShapeContractPhase(config: CompilerConfig) extends CompilerPhase[Seq[CompiledClass], Seq[CompiledClass]] {
  def name: String = "ShapeContract"

  /** The interface the table holds a round-trip entry for; its method names come from the table. */
  private val entry: ContractTable.RoundTrip =
    ContractTable.forInterface("onion.Shape").collect { case r: ContractTable.RoundTrip => r }.get
  private val ShapeName = entry.interface

  def run(classes: Seq[CompiledClass], ctx: PhaseContext): Seq[CompiledClass] = {
    if (config.contracts != ContractMode.Check || !config.runContracts) return classes
    val loader = new OnionClassLoader(classOf[OnionClassLoader].getClassLoader, config.classPath, classes)
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    val errors = ArrayBuffer.empty[CompileError]
    val warnings = ArrayBuffer.empty[CompileWarning]
    try {
      for (cc <- classes) {
        val clazz = try Some(Class.forName(cc.className, false, loader)) catch { case _: Throwable => None }
        clazz.filter(isConcreteShape).foreach(check(_, cc, loader, errors, warnings))
      }
    } finally thread.setContextClassLoader(previous)
    ctx.addWarnings(warnings.toSeq)
    if (errors.nonEmpty) throw new CompilationException(errors.toSeq)
    classes
  }

  private def isConcreteShape(c: Class[?]): Boolean =
    !c.isInterface && !Modifier.isAbstract(c.getModifiers) && c.getInterfaces.exists(_.getName == ShapeName)

  /** The `T` of `Shape<T>` as the class declares it, or none when it is not a plain class. */
  private def valueType(c: Class[?]): Option[Class[?]] =
    c.getGenericInterfaces.collectFirst {
      case p: ParameterizedType if p.getRawType.getTypeName == ShapeName => p.getActualTypeArguments.head
    }.collect { case k: Class[?] => k }

  private def check(c: Class[?], cc: CompiledClass, loader: ClassLoader,
                    errors: ArrayBuffer[CompileError], warnings: ArrayBuffer[CompileWarning]): Unit = {
    val simple = c.getSimpleName
    val file = cc.className
    def warn(why: String): Unit =
      warnings += CompileWarning(file, null, WarningCategory.ShapeNotSampled,
        s"the contract of Shape '$simple' was not checked: $why")
    val tpe = valueType(c).getOrElse { warn("its value type is not a plain class"); return }
    val shape = try {
      val ctor = c.getDeclaredConstructor()
      ctor.setAccessible(true)
      ctor.newInstance().asInstanceOf[AnyRef]
    } catch { case _: Throwable => warn("it has no no-argument constructor"); return }
    val values = ArgGenerator.generateValues(tpe, loader, config.contractSamples, config.contractSeed)
      .getOrElse { warn(s"values of type ${tpe.getSimpleName} cannot be sampled"); return }
    val canPrint = c.getMethod(entry.canPrint)
    val print = c.getMethods.find(m => m.getName == entry.print && m.getParameterCount == 1 && !m.isBridge)
    val printable = c.getMethods.find(m => m.getName == entry.printable && m.getParameterCount == 1 && !m.isBridge)
    val parse = c.getMethods.find(m => m.getName == entry.parse && m.getParameterCount == 1 && !m.isBridge)
    (print, parse) match {
      case (Some(p), Some(r)) =>
        val canPrintNow: Boolean = try canPrint.invoke(shape).asInstanceOf[java.lang.Boolean].booleanValue catch { case _: Throwable => false }
        if (!canPrintNow) return
        val outcome = values.iterator.map(v => roundTrip(shape, p, r, printable, v)).collectFirst { case Some(why) => why }
        outcome.foreach { why =>
          errors += CompileError(file, null,
            Message("error.semantic.shapeContractBroken", Array[Any](simple, why,
              s"--contract-seed ${config.contractSeed} --contract-samples ${config.contractSamples}")),
            Some(SemanticError.SHAPE_CONTRACT_BROKEN.errorCode))
        }
      case _ => warn("it does not declare parse and print")
    }
  }

  /** `None` when `v` is not printable or round-trips; otherwise how it failed. */
  private def roundTrip(shape: AnyRef, print: java.lang.reflect.Method, parse: java.lang.reflect.Method,
                        printable: Option[java.lang.reflect.Method], v: AnyRef): Option[String] =
    try {
      val admitted = printable.forall(_.invoke(shape, v).asInstanceOf[java.lang.Boolean].booleanValue)
      if (!admitted) None
      else {
        val text = print.invoke(shape, v).asInstanceOf[String]
        val back = parse.invoke(shape, text)
        val isOk = back.getClass.getMethod("isOk").invoke(back).asInstanceOf[java.lang.Boolean].booleanValue
        val got = if (isOk) back.getClass.getMethod("get").invoke(back) else null
        if (isOk && java.util.Objects.equals(got, v)) None
        else Some(s"for ${v} the text \"$text\" reads back as ${if (isOk) String.valueOf(got) else "a defect"}")
      }
    } catch {
      case e: java.lang.reflect.InvocationTargetException =>
        Some(s"for ${v} it threw ${Option(e.getCause).getOrElse(e).getClass.getSimpleName}")
      case e: Throwable => Some(s"for ${v} it threw ${e.getClass.getSimpleName}")
    }
}
