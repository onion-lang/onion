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
