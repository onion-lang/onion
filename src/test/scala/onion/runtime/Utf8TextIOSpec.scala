package onion.runtime

import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files => JFiles, Path}
import scala.jdk.CollectionConverters.*

/**
 * Onion's stdlib text I/O is UTF-8 on every JDK. JDK 18+ made UTF-8 the default charset
 * (JEP 400); on JDK 17 the default is the platform charset -- MS932 under a Japanese Windows
 * locale -- and every call that relied on it (`Files::readLines`, `writeLines`, `appendText`,
 * `file"…".lines()`/`append()`, and the stdin readers in `IO`) read or wrote MS932 there.
 *
 * The byte-level assertions below fail on such a JDK 17 without the fix and pass everywhere
 * with it; on a UTF-8 platform they pin the behaviour down.
 */
class Utf8TextIOSpec extends AnyFunSpec with Matchers with BeforeAndAfterEach {

  private val japanese = "日本語のテキスト、ｶﾀｶﾅ、①"
  private val utf8Bytes = (japanese + "\n").getBytes(StandardCharsets.UTF_8)
  private val Property = onion.tools.ConsoleEncoding.StdinEncodingProperty

  private var savedIn: java.io.InputStream = scala.compiletime.uninitialized
  private var savedProperty: Option[String] = None

  override def beforeEach(): Unit = {
    savedIn = System.in
    savedProperty = Option(System.getProperty(Property))
    System.clearProperty(Property)
  }

  override def afterEach(): Unit = {
    System.setIn(savedIn)
    savedProperty match {
      case Some(v) => System.setProperty(Property, v)
      case None => System.clearProperty(Property)
    }
  }

  private def tempFile(): Path = {
    val f = JFiles.createTempFile("onion-utf8-", ".txt")
    f.toFile.deleteOnExit()
    f
  }

  describe("onion.Files text I/O") {
    it("readLines decodes UTF-8") {
      val f = tempFile()
      JFiles.write(f, (japanese + "\nsecond\n").getBytes(StandardCharsets.UTF_8))
      onion.Files.readLines(f.toString).asScala.toList shouldBe List(japanese, "second")
    }

    it("writeLines and appendText encode UTF-8") {
      val f = tempFile()
      onion.Files.writeLines(f.toString, java.util.List.of(japanese))
      onion.Files.appendText(f.toString, "追記")
      val expected = (japanese + System.lineSeparator() + "追記").getBytes(StandardCharsets.UTF_8)
      JFiles.readAllBytes(f).toList shouldBe expected.toList
    }

    it("readText and writeText default to UTF-8 and keep their explicit-charset overloads") {
      val f = tempFile()
      onion.Files.writeText(f.toString, japanese)
      JFiles.readAllBytes(f).toList shouldBe japanese.getBytes(StandardCharsets.UTF_8).toList
      onion.Files.readText(f.toString) shouldBe japanese

      val sjis = Charset.forName("Shift_JIS")
      onion.Files.writeText(f.toString, "日本語", sjis)
      JFiles.readAllBytes(f).toList shouldBe "日本語".getBytes(sjis).toList
      onion.Files.readText(f.toString, sjis) shouldBe "日本語"
    }

    it("file\"…\" lines() and append() go through the same UTF-8 path") {
      val f = tempFile()
      val resource = new onion.FileResource(f.toString)
      resource.write(japanese + "\n")
      resource.append("末尾\n")
      JFiles.readAllBytes(f).toList shouldBe (japanese + "\n末尾\n").getBytes(StandardCharsets.UTF_8).toList
      resource.lines().asScala.toList shouldBe List(japanese, "末尾")
    }
  }

  describe("onion.IO standard input") {
    it("decodes UTF-8 when no launcher chose a charset") {
      System.setIn(new ByteArrayInputStream(utf8Bytes))
      onion.IO.readLine() shouldBe japanese
    }

    it("decodes with the charset the launcher recorded in onion.stdin.encoding") {
      val ms932 = Charset.forName("MS932")
      System.setProperty(Property, "MS932")
      System.setIn(new ByteArrayInputStream("日本語\n".getBytes(ms932)))
      onion.IO.readLine() shouldBe "日本語"
    }

    it("falls back to UTF-8 for an unsupported onion.stdin.encoding") {
      System.setProperty(Property, "no-such-charset")
      System.setIn(new ByteArrayInputStream(utf8Bytes))
      onion.IO.readLine() shouldBe japanese
    }

    it("readAll decodes UTF-8 and keeps what readLine already buffered") {
      System.setIn(new ByteArrayInputStream(("first\n" + japanese + "\nlast").getBytes(StandardCharsets.UTF_8)))
      onion.IO.readLine() shouldBe "first"
      onion.IO.readAll() shouldBe japanese + "\nlast"
    }

    it("readLines decodes UTF-8") {
      System.setIn(new ByteArrayInputStream((japanese + "\n二行目\n").getBytes(StandardCharsets.UTF_8)))
      onion.IO.readLines().asScala.toList shouldBe List(japanese, "二行目")
    }
  }
}
