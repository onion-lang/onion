package onion.tools.project

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

object ProjectClassRunnerLoaderProbe:
  @volatile private var observed: ClassLoader = null

  def record(loader: ClassLoader): Unit =
    observed = loader

  def reset(): Unit =
    observed = null

  def loader: Option[ClassLoader] =
    Option(observed)

class ProjectClassRunnerSpec extends AnyFunSuite with Matchers:
  private lazy val classes: Path =
    compileJava(
      Map(
        "fixture.VoidMain" ->
          """package fixture;
            |public final class VoidMain {
            |  public static void main(String[] args) {}
            |}
            |""".stripMargin,
        "fixture.ArgsMain" ->
          """package fixture;
            |public final class ArgsMain {
            |  public static String main(String[] args) {
            |    return String.join("|", args);
            |  }
            |}
            |""".stripMargin,
        "fixture.ZeroIntMain" ->
          """package fixture;
            |public final class ZeroIntMain {
            |  public static int main(String[] args) { return 0; }
            |}
            |""".stripMargin,
        "fixture.ZeroDoubleMain" ->
          """package fixture;
            |public final class ZeroDoubleMain {
            |  public static double main(String[] args) { return -0.0d; }
            |}
            |""".stripMargin,
        "fixture.NonzeroMain" ->
          """package fixture;
            |public final class NonzeroMain {
            |  public static long main(String[] args) { return 7L; }
            |}
            |""".stripMargin,
        "fixture.TinyBigDecimalMain" ->
          """package fixture;
            |public final class TinyBigDecimalMain {
            |  public static java.math.BigDecimal main(String[] args) {
            |    return new java.math.BigDecimal("1e-10000");
            |  }
            |}
            |""".stripMargin,
        "fixture.UnderflowNumber" ->
          """package fixture;
            |public final class UnderflowNumber extends Number {
            |  public int intValue() { return 1; }
            |  public long longValue() { return 1L; }
            |  public float floatValue() { return 0.0f; }
            |  public double doubleValue() { return 0.0d; }
            |}
            |""".stripMargin,
        "fixture.UnderflowNumberMain" ->
          """package fixture;
            |public final class UnderflowNumberMain {
            |  public static Number main(String[] args) {
            |    return new UnderflowNumber();
            |  }
            |}
            |""".stripMargin,
        "fixture.OtherMain" ->
          """package fixture;
            |public final class OtherMain {
            |  public static String main(String[] args) { return "done"; }
            |}
            |""".stripMargin,
        "fixture.NoMain" ->
          """package fixture;
            |public final class NoMain {}
            |""".stripMargin,
        "fixture.InitializerNoMain" ->
          """package fixture;
            |public final class InitializerNoMain {
            |  static {
            |    if (Boolean.parseBoolean("true")) {
            |      throw new IllegalStateException("initializer ran");
            |    }
            |  }
            |}
            |""".stripMargin,
        "fixture.PrivateMain" ->
          """package fixture;
            |public final class PrivateMain {
            |  private static void main(String[] args) {}
            |}
            |""".stripMargin,
        "fixture.InstanceMain" ->
          """package fixture;
            |public final class InstanceMain {
            |  public void main(String[] args) {}
            |}
            |""".stripMargin,
        "fixture.ThrowingMain" ->
          """package fixture;
            |public final class ThrowingMain {
            |  public static void main(String[] args) {
            |    throw new IllegalStateException("boom");
            |  }
            |}
            |""".stripMargin,
        "fixture.ContextMain" ->
          """package fixture;
            |public final class ContextMain {
            |  public static String main(String[] args) {
            |    return Thread.currentThread().getContextClassLoader()
            |      == ContextMain.class.getClassLoader() ? "context" : "wrong";
            |  }
            |}
            |""".stripMargin,
        "fixture.FreshMain" ->
          """package fixture;
            |public final class FreshMain {
            |  private static int count = 0;
            |  public static int main(String[] args) { return ++count; }
            |}
            |""".stripMargin,
        "fixture.CloseProbeMain" ->
          """package fixture;
            |public final class CloseProbeMain {
            |  public static String main(String[] args) {
            |    ClassLoader loader = Thread.currentThread().getContextClassLoader();
            |    if (loader.getResource(args[0]) == null) {
            |      throw new IllegalStateException("probe resource was unavailable");
            |    }
            |    onion.tools.project.ProjectClassRunnerLoaderProbe$.MODULE$.record(loader);
            |    return "recorded";
            |  }
            |}
            |""".stripMargin
      ),
      resources = Map("loader-close-probe.txt" -> "probe")
    )

  test("invokes a public static main with the argument array as one reflection argument"):
    ProjectClassRunner.run(
      Vector(classes),
      "fixture.ArgsMain",
      Array("alpha", "--beta", "")
    ) shouldBe ProgramResult.Success("alpha|--beta|")

  test("returns success values for void numeric zero and non-numeric results"):
    ProjectClassRunner.run(Vector(classes), "fixture.VoidMain", Array.empty) shouldBe
      ProgramResult.Success(null)
    ProjectClassRunner.run(Vector(classes), "fixture.ZeroIntMain", Array.empty) shouldBe
      ProgramResult.Success(0)
    ProjectClassRunner.run(Vector(classes), "fixture.ZeroDoubleMain", Array.empty) shouldBe
      ProgramResult.Success(-0.0d)
    ProjectClassRunner.run(Vector(classes), "fixture.OtherMain", Array.empty) shouldBe
      ProgramResult.Success("done")

  test("retains a nonzero numeric result for command-level normalization"):
    ProjectClassRunner.run(Vector(classes), "fixture.NonzeroMain", Array.empty) shouldBe
      ProgramResult.Success(7L)

  test("classifies exact standard Scala and Java numeric values conservatively"):
    def exitCode(className: String): Int =
      ProjectClassRunner.run(Vector(classes), className, Array.empty) match
        case ProgramResult.Success(value) => ProjectCommands.programExitCode(value)
        case failure => fail(s"expected success, got $failure")

    exitCode("fixture.ZeroIntMain") shouldBe 0
    exitCode("fixture.ZeroDoubleMain") shouldBe 0
    exitCode("fixture.NonzeroMain") shouldBe 1
    exitCode("fixture.TinyBigDecimalMain") shouldBe 1
    exitCode("fixture.UnderflowNumberMain") shouldBe 1

    ProjectCommands.programExitCode(java.lang.Double.valueOf(Double.NaN)) shouldBe 1
    ProjectCommands.programExitCode(
      java.lang.Double.valueOf(Double.PositiveInfinity)
    ) shouldBe 1
    ProjectCommands.programExitCode(
      java.lang.Float.valueOf(Float.NegativeInfinity)
    ) shouldBe 1
    ProjectCommands.programExitCode(java.lang.Double.valueOf(-0.0d)) shouldBe 0
    ProjectCommands.programExitCode(java.lang.Float.valueOf(-0.0f)) shouldBe 0
    ProjectCommands.programExitCode(scala.math.BigInt(0)) shouldBe 0
    ProjectCommands.programExitCode(scala.math.BigInt(1)) shouldBe 1
    ProjectCommands.programExitCode(scala.math.BigDecimal(0)) shouldBe 0
    ProjectCommands.programExitCode(scala.math.BigDecimal("1e-10000")) shouldBe 1

  test("reports a missing class and a missing main deterministically"):
    ProjectClassRunner.run(Vector(classes), "fixture.Missing", Array.empty) match
      case ProgramResult.Failure(message, cause) =>
        message shouldBe "Entrypoint class fixture.Missing was not found"
        cause.value shouldBe a[ClassNotFoundException]
      case result => fail(s"expected failure, got $result")

    ProjectClassRunner.run(Vector(classes), "fixture.NoMain", Array.empty) match
      case ProgramResult.Failure(message, cause) =>
        message shouldBe "Entrypoint fixture.NoMain.main(String[]) was not found"
        cause.value shouldBe a[NoSuchMethodException]
      case result => fail(s"expected failure, got $result")

  test("rejects non-public and non-static main methods precisely"):
    ProjectClassRunner.run(Vector(classes), "fixture.PrivateMain", Array.empty) shouldBe
      ProgramResult.Failure(
        "Entrypoint fixture.PrivateMain.main(String[]) is not public",
        None
      )
    ProjectClassRunner.run(Vector(classes), "fixture.InstanceMain", Array.empty) shouldBe
      ProgramResult.Failure(
        "Entrypoint fixture.InstanceMain.main(String[]) is not static",
        None
      )

  test("validates main before a class initializer can mask the diagnostic"):
    ProjectClassRunner.run(
      Vector(classes),
      "fixture.InitializerNoMain",
      Array.empty
    ) match
      case ProgramResult.Failure(message, cause) =>
        message shouldBe "Entrypoint fixture.InitializerNoMain.main(String[]) was not found"
        cause.value shouldBe a[NoSuchMethodException]
      case result => fail(s"expected failure, got $result")

  test("unwraps an exception thrown by user code"):
    ProjectClassRunner.run(Vector(classes), "fixture.ThrowingMain", Array.empty) match
      case ProgramResult.Failure(message, cause) =>
        message shouldBe "IllegalStateException: boom"
        cause.value shouldBe a[IllegalStateException]
        cause.value.getMessage shouldBe "boom"
      case result => fail(s"expected failure, got $result")

  test("sets the fresh loader as context and restores the previous loader"):
    val thread = Thread.currentThread()
    val previous = thread.getContextClassLoader

    ProjectClassRunner.run(Vector(classes), "fixture.ContextMain", Array.empty) shouldBe
      ProgramResult.Success("context")
    thread.getContextClassLoader should be theSameInstanceAs previous

  test("uses a fresh class loader for every invocation"):
    ProjectClassRunner.run(Vector(classes), "fixture.FreshMain", Array.empty) shouldBe
      ProgramResult.Success(1)
    ProjectClassRunner.run(Vector(classes), "fixture.FreshMain", Array.empty) shouldBe
      ProgramResult.Success(1)

  test("prefers test then project classes before a same-named parent class"):
    def precedence(label: String): Path =
      compileJava(
        Map(
          "precedence.Main" ->
            s"""package precedence;
              |public final class Main {
              |  public static String main(String[] args) { return "$label"; }
              |}
              |""".stripMargin
        ),
        Map.empty
      )

    val testClasses = precedence("test")
    val projectClasses = precedence("project")
    val parentClasses = precedence("parent")
    val parent = URLClassLoader(Array(parentClasses.toUri.toURL), getClass.getClassLoader)

    try
      ProjectClassRunner.run(
        Vector(testClasses, projectClasses),
        "precedence.Main",
        Array.empty,
        parent
      ) shouldBe ProgramResult.Success("test")
      ProjectClassRunner.run(
        Vector(projectClasses),
        "precedence.Main",
        Array.empty,
        parent
      ) shouldBe ProgramResult.Success("project")
      ProjectClassRunner.run(
        Vector.empty,
        "precedence.Main",
        Array.empty,
        parent
      ) shouldBe ProgramResult.Success("parent")
    finally parent.close()

  test("closes the URL class loader after execution"):
    ProjectClassRunnerLoaderProbe.reset()

    ProjectClassRunner.run(
      Vector(classes),
      "fixture.CloseProbeMain",
      Array("loader-close-probe.txt")
    ) shouldBe ProgramResult.Success("recorded")

    ProjectClassRunnerLoaderProbe.loader.value
      .getResource("loader-close-probe.txt") shouldBe null

  test("retains lifecycle failures as a cause or a suppressed cause"):
    val closeFailure = IOException("close failed")
    val withoutCause = ProjectClassRunner.withLifecycleFailure(
      Some(ProgramResult.Failure("invalid main", None)),
      "close the project class loader",
      closeFailure
    )
    withoutCause.value shouldBe ProgramResult.Failure(
      "invalid main",
      Some(closeFailure)
    )

    val primary = IllegalStateException("user failure")
    val restoreFailure = SecurityException("restore failed")
    val withCause = ProjectClassRunner.withLifecycleFailure(
      Some(ProgramResult.Failure("IllegalStateException: user failure", Some(primary))),
      "restore the context class loader",
      restoreFailure
    )
    withCause.value shouldBe ProgramResult.Failure(
      "IllegalStateException: user failure",
      Some(primary)
    )
    primary.getSuppressed.toVector shouldBe Vector(restoreFailure)

  private def compileJava(
    sources: Map[String, String],
    resources: Map[String, String]
  ): Path =
    val root = Files.createTempDirectory("project-class-runner-fixtures")
    val sourceRoot = Files.createDirectory(root.resolve("src"))
    val classesRoot = Files.createDirectory(root.resolve("classes"))
    val sourceFiles = sources.toVector.sortBy(_._1).map { case (className, source) =>
      val path = sourceRoot.resolve(className.replace('.', '/') + ".java")
      Files.createDirectories(path.getParent)
      Files.writeString(path, source, UTF_8)
      path
    }
    val testClasses =
      Path.of(
        ProjectClassRunnerLoaderProbe.getClass
          .getProtectionDomain
          .getCodeSource
          .getLocation
          .toURI
      )
    val errors = ByteArrayOutputStream()
    val compiler = Option(ToolProvider.getSystemJavaCompiler)
      .getOrElse(fail("ProjectClassRunnerSpec requires a JDK Java compiler"))
    val compilerArgs =
      Array("-classpath", testClasses.toString, "-d", classesRoot.toString) ++
        sourceFiles.map(_.toString)
    val exitCode = compiler.run(null, null, errors, compilerArgs*)
    withClue(errors.toString(UTF_8)) {
      exitCode shouldBe 0
    }
    resources.foreach { case (relative, contents) =>
      val path = classesRoot.resolve(relative)
      Files.createDirectories(path.getParent)
      Files.writeString(path, contents, UTF_8)
    }
    classesRoot
