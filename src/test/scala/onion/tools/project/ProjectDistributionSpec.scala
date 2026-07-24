package onion.tools.project

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.jdk.CollectionConverters.*

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Validates the source launchers directly and, when a real `sbt dist` archive
  * has been unpacked, smokes it end to end. This suite never invokes
  * `sbt dist` itself; doing so recursively from inside ScalaTest would nest
  * one sbt build inside another. The final verification step (see the
  * project's implementation plan, Task 13) builds and unpacks the archive,
  * then re-runs this suite with `-Donion.dist.path=<unpacked-dir>`.
  */
class ProjectDistributionSpec extends AnyFunSuite with Matchers:
  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  private val repoRoot: Path = Paths.get(".").toAbsolutePath.normalize()

  test("the Unix launcher dispatches through the unified OnionCli entry point"):
    val contents = Files.readString(repoRoot.resolve("bin/onion"), UTF_8)

    contents should include("onion.tools.OnionCli")
    contents should not include "onion.tools.ScriptRunner"
    contents should not include "onion.tools.Repl"

  test("the Windows launcher dispatches through the unified OnionCli entry point"):
    val contents = Files.readString(repoRoot.resolve("bin/onion.bat"), UTF_8)

    contents should include("onion.tools.OnionCli")
    contents should not include "onion.tools.ScriptRunner"

  test("the installer writes an onion launcher without a separate REPL branch"):
    val contents = Files.readString(repoRoot.resolve("install.sh"), UTF_8)

    contents should include("write_launcher onion onion.tools.OnionCli \"\"")
    contents should include("write_launcher onionc onion.tools.CompilerFrontend \"\"")
    contents should include("write_launcher onion-repl onion.tools.Repl \"\"")

  test("an unpacked distribution completes the new -> run -> test -> clean journey"):
    sys.props.get("onion.dist.path") match
      case None =>
        cancel("set -Donion.dist.path=<unpacked dist dir> to run this smoke test")
      case Some(distPath) =>
        val dist = Paths.get(distPath).toAbsolutePath.normalize()
        val onionBin = dist.resolve("bin/onion")
        Files.isExecutable(onionBin) shouldBe true

        val workspace = Files.createTempDirectory("onion-dist-smoke")
        val created = run(onionBin, workspace, "new", "greeter")
        created.exitCode shouldBe 0
        val project = workspace.resolve("greeter").toRealPath()
        created.stdout shouldBe s"Created $project\n"

        run(onionBin, project, "run") shouldBe Invocation(0, "Hello, greeter!\n", "")

        val testResult = run(onionBin, project, "test")
        testResult.exitCode shouldBe 0
        testResult.stdout shouldBe "test tests/main_test.on ... ok\n\n1 tests, 1 passed, 0 failed\n"

        run(onionBin, project, "clean") shouldBe Invocation(0, "Cleaned target\n", "")
        Files.notExists(project.resolve("target")) shouldBe true

        val jars = Files.list(dist.resolve("lib")).iterator().asScala
          .map(_.getFileName.toString)
          .toVector
        jars.exists(_.startsWith("tomlj-")) shouldBe true

  private def run(onionBin: Path, cwd: Path, args: String*): Invocation =
    val process = ProcessBuilder((onionBin.toString +: args).asJava)
      .directory(cwd.toFile)
      .start()
    val stdout = String(process.getInputStream.readAllBytes(), UTF_8)
    val stderr = String(process.getErrorStream.readAllBytes(), UTF_8)
    val exitCode = process.waitFor()
    Invocation(exitCode, stdout, stderr)
