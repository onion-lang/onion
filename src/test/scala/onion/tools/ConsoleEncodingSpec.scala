package onion.tools

import onion.tools.ConsoleEncoding.{Decision, Mode}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * The console-encoding heuristic, against the system properties each JDK actually sets
 * (observed on Corretto 17 and Liberica 21 under a Japanese Windows locale).
 */
class ConsoleEncodingSpec extends AnyFunSuite with Matchers:
  private val Windows = "Windows 11"

  private def decide(
    props: Map[String, String],
    mode: Mode = Mode.Auto,
    os: String = Windows,
    terminal: Boolean = false
  ): Decision =
    ConsoleEncoding.decide(mode, os, props.get, () => terminal)

  // JDK 17: sun.std*.encoding exists only for a console handle.
  private val jdk17 = Map("native.encoding" -> "MS932", "file.encoding" -> "MS932")
  private val jdk17Console =
    jdk17 ++ Map("sun.stdout.encoding" -> "ms932", "sun.stderr.encoding" -> "ms932")

  // JDK 19+: std*.encoding is always set, to the console code page or to native.encoding.
  private val jdk21Piped = Map(
    "native.encoding" -> "MS932", "file.encoding" -> "UTF-8",
    "stdout.encoding" -> "MS932", "stderr.encoding" -> "MS932"
  )
  private val jdk21Console =
    jdk21Piped ++ Map("stdout.encoding" -> "ms932", "stderr.encoding" -> "ms932")

  test("JDK 17 on Windows: a pipe or mintty gets UTF-8, a console keeps its code page"):
    decide(jdk17) shouldBe Decision(stdout = true, stderr = true)
    decide(jdk17Console) shouldBe Decision(stdout = false, stderr = false)

  test("each stream is judged on its own: stdout redirected to a file, stderr still the console"):
    decide(jdk17 + ("sun.stderr.encoding" -> "ms932")) shouldBe Decision(stdout = true, stderr = false)

  test("JDK 19+ on Windows: the console code page is spelled differently from native.encoding"):
    decide(jdk21Piped) shouldBe Decision(stdout = true, stderr = true)
    decide(jdk21Console) shouldBe Decision(stdout = false, stderr = false)

  test("JDK 19+ with matching spellings falls back to System.console()"):
    decide(jdk21Piped, terminal = true) shouldBe Decision(stdout = false, stderr = false)

  test("a console already on UTF-8 (chcp 65001) is left alone, and so is a UTF-8 system locale"):
    decide(jdk17 ++ Map("sun.stdout.encoding" -> "UTF-8", "sun.stderr.encoding" -> "UTF-8")) shouldBe
      Decision(stdout = false, stderr = false)
    decide(Map("native.encoding" -> "UTF-8", "file.encoding" -> "UTF-8")) shouldBe
      Decision(stdout = false, stderr = false)

  test("other platforms are left alone unless UTF-8 is asked for"):
    decide(Map("native.encoding" -> "ANSI_X3.4-1968"), os = "Linux") shouldBe Decision(false, false)
    decide(Map.empty, os = "Mac OS X") shouldBe Decision(false, false)
    decide(Map.empty, mode = Mode.Utf8, os = "Linux") shouldBe Decision(true, true)

  test("native never changes anything; utf-8 always re-encodes, even a console"):
    decide(jdk17, mode = Mode.Native) shouldBe Decision(false, false)
    decide(jdk17Console, mode = Mode.Utf8) shouldBe Decision(true, true)

  test("parses the environment variable, warning about an unknown value"):
    ConsoleEncoding.parseMode(null) shouldBe Right(Mode.Auto)
    ConsoleEncoding.parseMode(" AUTO ") shouldBe Right(Mode.Auto)
    ConsoleEncoding.parseMode("native") shouldBe Right(Mode.Native)
    ConsoleEncoding.parseMode("UTF-8") shouldBe Right(Mode.Utf8)
    ConsoleEncoding.parseMode("utf8") shouldBe Right(Mode.Utf8)
    ConsoleEncoding.parseMode("sjis").left.toOption.get should include("ONION_CONSOLE_ENCODING=sjis")

  // Standard input: the charset onion.IO decodes it with.
  private def stdin(
    props: Map[String, String],
    mode: Mode = Mode.Auto,
    os: String = Windows,
    terminal: Boolean = false
  ): Option[String] =
    ConsoleEncoding.decideStdin(mode, os, props.get, () => terminal)

  test("stdin on Windows: a pipe or a file is read as UTF-8 on every JDK"):
    stdin(jdk17) shouldBe Some("UTF-8")
    stdin(jdk17Console) shouldBe Some("UTF-8") // stdout is a console, stdin is not (no System.console())
    stdin(jdk21Piped) shouldBe Some("UTF-8")
    // JDK 25+ sets stdin.encoding to native.encoding when stdin is not a console.
    stdin(jdk21Piped + ("stdin.encoding" -> "MS932")) shouldBe Some("UTF-8")

  test("stdin on Windows: a console is read in its code page, not as UTF-8"):
    stdin(jdk17Console, terminal = true) shouldBe Some("ms932")
    stdin(jdk21Console, terminal = true) shouldBe Some("ms932")
    stdin(jdk21Piped, terminal = true) shouldBe Some("MS932")
    // JDK 25+ says so for stdin itself, even with stdout redirected.
    stdin(jdk21Piped + ("stdin.encoding" -> "ms932")) shouldBe Some("ms932")
    // chcp 65001: the console itself is UTF-8.
    stdin(jdk17 ++ Map("sun.stdout.encoding" -> "UTF-8"), terminal = true) shouldBe Some("UTF-8")

  test("stdin elsewhere is UTF-8 (JEP 400); native leaves it to the JVM, utf-8 forces it"):
    stdin(Map("native.encoding" -> "ANSI_X3.4-1968"), os = "Linux", terminal = true) shouldBe Some("UTF-8")
    stdin(jdk17Console, mode = Mode.Native, terminal = true) shouldBe None
    stdin(jdk17Console, mode = Mode.Utf8, terminal = true) shouldBe Some("UTF-8")
