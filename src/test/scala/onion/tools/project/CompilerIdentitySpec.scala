package onion.tools.project

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * F18: the build fingerprint's compiler identity must change when the compiler's bytes do,
 * even when its version string does not, and must not cost a full jar hash on every build.
 * Jars here are stand-in files: the identity reads bytes, not zip structure.
 */
class CompilerIdentitySpec extends AnyFunSuite with Matchers:

  private def tempJar(contents: String): Path =
    val jar = Files.createTempDirectory("onion-compiler-identity").resolve("onion.jar")
    Files.writeString(jar, contents, UTF_8)
    jar

  private def cacheDir(): Path = Files.createTempDirectory("onion-compiler-identity-cache")

  test("the same jar gives the same identity, and it starts with the version"):
    val jar = tempJar("compiler v1")
    val cache = cacheDir()
    val first = CompilerIdentity.compute("0.2.0", Seq(jar), Some(cache))
    first should startWith("0.2.0+")
    CompilerIdentity.compute("0.2.0", Seq(jar), Some(cache)) shouldBe first
    CompilerIdentity.compute("0.2.0", Seq(jar), None) shouldBe first

  test("a rebuilt jar with the same version string is a different compiler"):
    val jar = tempJar("compiler v1")
    val cache = cacheDir()
    val before = CompilerIdentity.compute("0.2.0", Seq(jar), Some(cache))
    Files.writeString(jar, "compiler v1, new effect table", UTF_8)
    CompilerIdentity.compute("0.2.0", Seq(jar), Some(cache)) should not be before

  test("a different version string is a different compiler, even from the same jar"):
    val jar = tempJar("compiler")
    CompilerIdentity.compute("0.2.0", Seq(jar), None) should not be
      CompilerIdentity.compute("0.3.0", Seq(jar), None)

  test("a jar's digest is cached by path, size and mtime, and not recomputed while they hold"):
    val jar = tempJar("compiler v1")
    val cache = cacheDir()
    val real = CompilerIdentity.jarDigest(jar, Some(cache)).get
    val cacheFiles = Using.resource(Files.walk(cache))(_.iterator.asScala.filter(Files.isRegularFile(_)).toVector)
    cacheFiles should have size 1

    // Plant a different digest under the same key: a cache hit returns it without reading the jar.
    val planted = "f" * 64
    val lines = Files.readAllLines(cacheFiles.head, UTF_8).asScala.toVector
    Files.write(cacheFiles.head, (lines.init :+ planted).asJava, UTF_8)
    CompilerIdentity.jarDigest(jar, Some(cache)) shouldBe Some(planted)

    // Touching the jar changes the key: the digest is recomputed and the entry rewritten.
    Files.setLastModifiedTime(jar, FileTime.fromMillis(Files.getLastModifiedTime(jar).toMillis + 60000))
    CompilerIdentity.jarDigest(jar, Some(cache)) shouldBe Some(real)
    Files.readAllLines(cacheFiles.head, UTF_8).asScala.last shouldBe real

  test("an unreadable or malformed cache entry only costs a recomputation"):
    val jar = tempJar("compiler v1")
    val cache = cacheDir()
    val real = CompilerIdentity.jarDigest(jar, Some(cache)).get
    val entry = Using.resource(Files.walk(cache))(_.iterator.asScala.filter(Files.isRegularFile(_)).toVector).head
    Files.writeString(entry, "garbage", UTF_8)
    CompilerIdentity.jarDigest(jar, Some(cache)) shouldBe Some(real)

  test("a class directory's identity follows its files"):
    val dir = Files.createTempDirectory("onion-compiler-classes")
    Files.createDirectories(dir.resolve("onion"))
    val table = dir.resolve("onion/effect-table.txt")
    Files.writeString(table, "java.io.File#delete write\n", UTF_8)
    val before = CompilerIdentity.compute("0.2.0", Seq(dir), None)
    CompilerIdentity.compute("0.2.0", Seq(dir), None) shouldBe before
    Files.writeString(table, "java.io.File#delete write\njava.io.File#mkdir write\n", UTF_8)
    CompilerIdentity.compute("0.2.0", Seq(dir), None) should not be before

  test("with nothing to read, the identity is the version, as it was before"):
    CompilerIdentity.compute("0.2.0", Seq.empty, None) shouldBe "0.2.0"
    CompilerIdentity.compute("0.2.0", Seq(Path.of("does-not-exist.jar").toAbsolutePath), None) shouldBe "0.2.0"

  test("this JVM's compiler is located and identified"):
    val locations = CompilerIdentity.locations()
    locations should not be empty
    CompilerIdentity.compute(onion.tools.OnionVersion.value, locations, None) should
      startWith(onion.tools.OnionVersion.value + "+")
