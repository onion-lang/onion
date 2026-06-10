package onion.compiler.tools

import java.io.{FileOutputStream, StringReader}
import java.nio.file.Files
import java.util.jar.{JarEntry, JarOutputStream}

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import org.scalatest.funspec.AnyFunSpec

/**
 * Verifies Onion-to-Onion library reuse (issue #126): classes compiled in
 * one compilation can be packaged into a jar and consumed by a later
 * compilation through the classpath, at compile time and at runtime.
 */
class JarReuseSpec extends AnyFunSpec {

  private def compileToJar(source: String, fileName: String): java.io.File = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), fileName)))
    assert(!result.hasErrors, s"library failed to compile: ${result.allErrors.map(_.message).mkString("; ")}")

    val jarFile = Files.createTempFile("onion-lib", ".jar").toFile
    jarFile.deleteOnExit()
    val out = new JarOutputStream(new FileOutputStream(jarFile))
    try {
      result.classes.foreach { compiled =>
        out.putNextEntry(new JarEntry(compiled.className.replace('.', '/') + ".class"))
        out.write(compiled.content)
        out.closeEntry()
      }
    } finally out.close()
    jarFile
  }

  describe("Onion-to-Onion jar reuse") {
    it("consumes classes, records and static methods from a jar") {
      val jar = compileToJar(
        """
          |class Greeter {
          |public:
          |  def this {}
          |  def greet(name: String): String { return "Hello, " + name }
          |  static def version(): Int { return 7 }
          |}
          |
          |record Pair(a: Int, b: Int)
          |""".stripMargin,
        "MyLib.on"
      )

      val shell = Shell(Seq(jar.getPath))
      val result = shell.run(
        """
          |class App {
          |public:
          |  static def main(args: String[]): String {
          |    val g = new Greeter()
          |    val p = new Pair(40, 2)
          |    return g.greet("jar") + ":" + Greeter::version() + ":" + (p.a() + p.b())
          |  }
          |}
          |""".stripMargin,
        "App.on",
        Array()
      )
      assert(Shell.Success("Hello, jar:7:42") == result)
    }
  }
}
