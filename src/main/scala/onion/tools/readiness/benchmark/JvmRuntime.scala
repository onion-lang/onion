package onion.tools.readiness.benchmark

import onion.compiler.OnionCompiler
import org.jline.terminal.Terminal
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.util.CheckClassAdapter

import java.io.File
import java.nio.file.{Files, Path, Paths}

final case class JvmRuntime(javaExecutable: Path, classPath: String)

object JvmRuntime:
  def current(): JvmRuntime =
    val executableName =
      if System.getProperty("os.name").toLowerCase.contains("win") then "java.exe"
      else "java"
    val executable =
      Paths.get(System.getProperty("java.home"), "bin", executableName)
        .toAbsolutePath
        .normalize()
    require(Files.isRegularFile(executable), s"Java executable not found: $executable")

    val classes = Vector(
      classOf[OnionCompiler],
      classOf[scala.Option[?]],
      classOf[scala.deriving.Mirror],
      classOf[ClassReader],
      classOf[GeneratorAdapter],
      classOf[ClassNode],
      classOf[Analyzer[?]],
      classOf[CheckClassAdapter],
      classOf[Terminal]
    )
    val entries = classes.flatMap(codeLocation).distinct
    require(entries.nonEmpty, "no child-JVM classpath entries were resolved")
    JvmRuntime(executable, entries.mkString(File.pathSeparator))

  private def codeLocation(klass: Class[?]): Option[String] =
    Option(klass.getProtectionDomain)
      .flatMap(domain => Option(domain.getCodeSource))
      .flatMap(source => Option(source.getLocation))
      .map(location => Paths.get(location.toURI).toAbsolutePath.normalize().toString)
