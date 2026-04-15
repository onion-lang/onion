package onion.compiler.source

import onion.compiler.{CompilerConfig, FileInputSource, Parsing, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.StringReader
import java.nio.file.Files

class InputSourceSpec extends AnyFunSpec with Matchers {
  private val config = CompilerConfig(Seq("."), "", "UTF-8", "", 10)

  describe("InputSource contract") {
    it("FileInputSource returns a fresh reader every time") {
      val path = Files.createTempFile("onion-input-source", ".on")
      Files.writeString(path, "hello")
      val source = new FileInputSource(path.toString)

      val first = source.openReader()
      try {
        first.read() shouldBe 'h'.toInt
      } finally {
        first.close()
      }

      val second = source.openReader()
      try {
        second.read() shouldBe 'h'.toInt
      } finally {
        second.close()
      }
    }

    it("ReaderSource uses the reader factory for each open") {
      var opened = 0
      val source = new ReaderSource(
        () => {
          opened += 1
          new StringReader("ok")
        },
        "reader.on"
      )

      source.openReader().close()
      source.openReader().close()

      opened shouldBe 2
    }

    it("parsing can reread a stream-backed source after the parser closes it") {
      val source = new StreamInputSource(() => new StringReader("""IO::println("ok")"""), "Twice.on")
      val parsing = new Parsing(config)

      parsing.process(Seq(source)).size shouldBe 1
      parsing.process(Seq(source)).size shouldBe 1
    }
  }
}
