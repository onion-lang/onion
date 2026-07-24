package onion.tools.project

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.file.Path

import scala.util.control.NonFatal

import onion.compiler.ExplicitClasspathClassLoader

sealed trait ProgramResult

object ProgramResult:
  final case class Success(value: Any) extends ProgramResult
  final case class Failure(
    message: String,
    cause: Option[Throwable]
  ) extends ProgramResult

object ProjectClassRunner:
  def run(
    classPath: Vector[Path],
    className: String,
    args: Array[String]
  ): ProgramResult =
    run(
      classPath,
      className,
      args,
      ProjectClassRunner.getClass.getClassLoader
    )

  private[project] def run(
    classPath: Vector[Path],
    className: String,
    args: Array[String],
    parent: ClassLoader
  ): ProgramResult =
    val thread = Thread.currentThread()
    val previousLoader = thread.getContextClassLoader
    var loader: ExplicitClasspathClassLoader = null
    var contextChanged = false
    var outcome: Option[ProgramResult] = None

    try
      val urls = classPath.map(_.toUri.toURL).toArray
      loader = ExplicitClasspathClassLoader(urls, parent)
      thread.setContextClassLoader(loader)
      contextChanged = true
      outcome = Some(invoke(loader, className, args))
    catch
      case error: LinkageError =>
        outcome = Some(runtimeFailure(className, error))
      case NonFatal(error) =>
        outcome = Some(runtimeFailure(className, error))
    finally
      if contextChanged then
        try thread.setContextClassLoader(previousLoader)
        catch
          case NonFatal(error) =>
            outcome = withLifecycleFailure(outcome, "restore the context class loader", error)
      if loader != null then
        try loader.close()
        catch
          case NonFatal(error) =>
            outcome = withLifecycleFailure(outcome, "close the project class loader", error)

    outcome.getOrElse(
      ProgramResult.Failure(
        s"Could not initialize entrypoint $className",
        None
      )
    )

  private def invoke(
    loader: ClassLoader,
    className: String,
    args: Array[String]
  ): ProgramResult =
    try
      val entryClass = Class.forName(className, false, loader)
      val main =
        try entryClass.getDeclaredMethod("main", classOf[Array[String]])
        catch
          case error: NoSuchMethodException =>
            return ProgramResult.Failure(
              s"Entrypoint $className.main(String[]) was not found",
              Some(error)
            )

      if !Modifier.isPublic(main.getModifiers) then
        ProgramResult.Failure(
          s"Entrypoint $className.main(String[]) is not public",
          None
        )
      else if !Modifier.isStatic(main.getModifiers) then
        ProgramResult.Failure(
          s"Entrypoint $className.main(String[]) is not static",
          None
        )
      else
        ProgramResult.Success(
          main.invoke(null, args.asInstanceOf[Object])
        )
    catch
      case error: ClassNotFoundException =>
        ProgramResult.Failure(
          s"Entrypoint class $className was not found",
          Some(error)
        )
      case error: InvocationTargetException =>
        val cause = Option(error.getCause).getOrElse(error)
        ProgramResult.Failure(describe(cause), Some(cause))
      case error: LinkageError =>
        runtimeFailure(className, error)
      case NonFatal(error) =>
        runtimeFailure(className, error)

  private def runtimeFailure(
    className: String,
    error: Throwable
  ): ProgramResult.Failure =
    ProgramResult.Failure(
      s"Could not invoke entrypoint $className: ${describe(error)}",
      Some(error)
    )

  private[project] def withLifecycleFailure(
    current: Option[ProgramResult],
    action: String,
    error: Throwable
  ): Option[ProgramResult] =
    current match
      case Some(ProgramResult.Failure(_, Some(primary))) =>
        primary.addSuppressed(error)
        current
      case Some(ProgramResult.Failure(message, None)) =>
        Some(ProgramResult.Failure(message, Some(error)))
      case _ =>
        Some(ProgramResult.Failure(
          s"Could not $action: ${describe(error)}",
          Some(error)
        ))

  private def describe(error: Throwable): String =
    val simpleName =
      Option(error.getClass.getSimpleName)
        .filter(_.nonEmpty)
        .getOrElse(error.getClass.getName)
    s"$simpleName: ${Option(error.getMessage).getOrElse("")}"
