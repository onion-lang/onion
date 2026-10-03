package onion.compiler.source

import onion.compiler.{CompilerConfig, OnionCompiler}
import onion.tools.CompilerOptions
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.Charset
import java.nio.file.Files

/**
 * Source files are decoded in the configured `-encoding`, which defaults to UTF-8 rather than
 * the platform charset. The check is a top-level `example`, which the compiler evaluates at
 * build time: a mis-decoded literal has the wrong length, so the example fails (E0065).
 */
class SourceEncodingSpec extends AnyFunSuite with Matchers:
  private val program = "example { \"日本語\".length() == 3 }\nprintln(\"ok\")\n"

  private def errorsCompiling(bytesIn: Charset, configured: String): Seq[String] =
    val file = Files.createTempFile("onion-source-encoding", ".on")
    try
      Files.write(file, program.getBytes(bytesIn))
      new OnionCompiler(CompilerConfig(Seq("."), null, configured, "", 10))
        .compileDetailed(Array(file.toString))
        .allErrors
        .map(e => s"${e.errorCode.getOrElse("")} ${e.message}")
        .toSeq
    finally Files.deleteIfExists(file)

  test("onion and onionc read sources as UTF-8 unless -encoding says otherwise"):
    CompilerOptions.DEFAULT_ENCODING shouldBe "UTF-8"

  test("a UTF-8 source is decoded as UTF-8, whatever the platform charset is"):
    errorsCompiling(Charset.forName("UTF-8"), "UTF-8") shouldBe empty

  test("-encoding is honored for a source in another charset"):
    errorsCompiling(Charset.forName("Shift_JIS"), "Shift_JIS") shouldBe empty

  test("decoding in the wrong charset is what the example would catch"):
    errorsCompiling(Charset.forName("Shift_JIS"), "UTF-8") should not be empty
