package onion.compiler

import java.io.ByteArrayOutputStream
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ExplicitClasspathClassLoaderSpec extends AnyFunSuite with Matchers:
  test("loads ordinary classes from explicit URLs in declared order before the parent"):
    val testClasses = compileJava("test")
    val testJar = jar(testClasses)
    val projectClasses = compileJava("project")
    val parentClasses = compileJava("parent")
    val parent = URLClassLoader(Array(parentClasses.toUri.toURL), getClass.getClassLoader)
    val loader = ExplicitClasspathClassLoader(
      Array(testJar.toUri.toURL, projectClasses.toUri.toURL),
      parent
    )

    try
      origin(loader.loadClass("precedence.Sample")) shouldBe "test"
      resources(loader, "precedence/marker.txt") shouldBe
        Vector("test", "project", "parent")
      read(loader.getResource("precedence/marker.txt").openStream()) shouldBe "test"
      read(loader.getResourceAsStream("precedence/marker.txt")) shouldBe "test"
    finally
      loader.close()
      parent.close()

  test("falls back to the parent only when no explicit URL provides the class"):
    val projectClasses = compileJava("project")
    val parentClasses = compileJava("parent")
    val parent = URLClassLoader(Array(parentClasses.toUri.toURL), getClass.getClassLoader)
    val projectLoader = ExplicitClasspathClassLoader(
      Array(projectClasses.toUri.toURL),
      parent
    )
    val parentOnlyLoader = ExplicitClasspathClassLoader(Array.empty, parent)

    try
      origin(projectLoader.loadClass("precedence.Sample")) shouldBe "project"
      origin(parentOnlyLoader.loadClass("precedence.Sample")) shouldBe "parent"
      projectLoader.loadClass("java.lang.String") should be theSameInstanceAs classOf[String]
      projectLoader.loadClass("onion.Assert") should be theSameInstanceAs classOf[onion.Assert]
    finally
      projectLoader.close()
      parentOnlyLoader.close()
      parent.close()

  private def compileJava(label: String): Path =
    val root = Files.createTempDirectory(s"explicit-classpath-$label")
    val sourceRoot = Files.createDirectory(root.resolve("src"))
    val classesRoot = Files.createDirectory(root.resolve("classes"))
    val source = sourceRoot.resolve("precedence/Sample.java")
    Files.createDirectories(source.getParent)
    Files.writeString(
      source,
      s"""package precedence;
         |public final class Sample {
         |  public static String origin() { return "$label"; }
         |}
         |""".stripMargin,
      UTF_8
    )
    val errors = ByteArrayOutputStream()
    val compiler = Option(ToolProvider.getSystemJavaCompiler)
      .getOrElse(fail("ExplicitClasspathClassLoaderSpec requires a JDK compiler"))
    val exitCode = compiler.run(
      null,
      null,
      errors,
      "-d",
      classesRoot.toString,
      source.toString
    )
    withClue(errors.toString(UTF_8)) {
      exitCode shouldBe 0
    }
    val resource = classesRoot.resolve("precedence/marker.txt")
    Files.writeString(resource, label, UTF_8)
    classesRoot

  private def jar(classes: Path): Path =
    val target = Files.createTempFile("explicit-classpath", ".jar")
    Using.resource(JarOutputStream(Files.newOutputStream(target))) { output =>
      Using.resource(Files.walk(classes)) { paths =>
        paths.iterator.asScala
          .filter(Files.isRegularFile(_))
          .toVector
          .sortBy(_.toString)
          .foreach { path =>
            val name = classes.relativize(path).iterator.asScala.mkString("/")
            output.putNextEntry(JarEntry(name))
            output.write(Files.readAllBytes(path))
            output.closeEntry()
          }
      }
    }
    target

  private def origin(clazz: Class[?]): String =
    clazz.getMethod("origin").invoke(null).asInstanceOf[String]

  private def resources(loader: ClassLoader, name: String): Vector[String] =
    loader.getResources(name).asScala.map(url =>
      read(url.openStream())
    ).toVector

  private def read(input: java.io.InputStream): String =
    Using.resource(input)(stream => String(stream.readAllBytes(), UTF_8))
