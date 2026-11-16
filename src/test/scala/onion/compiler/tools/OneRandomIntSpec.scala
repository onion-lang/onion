package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import onion.tools.Shell
import onion.tools.migrate.MigrateCommand

/**
 * One random Int in a range (#3069): `Rand::nextInt(min, max)`, whose max is exclusive.
 * `OnionMath::randomInt(min, max)` included max, so the same arguments gave different
 * ranges. It is removed: a call is E0005 with a hint naming `Rand::nextInt(min, max + 1)`,
 * and `onion migrate` makes that edit. The hint text comes from `Message(...)`, so this
 * holds in both locales.
 */
class OneRandomIntSpec extends AbstractShellSpec {

  private def compile(code: String) =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private val hint = Message("suggestion.onionMathRandomInt")

  private def migrate(source: String): (Int, String, String) = {
    val root = Files.createTempDirectory("onion-migrate-3069").toRealPath()
    val file = root.resolve("old.on")
    Files.writeString(file, source, UTF_8)
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val code = MigrateCommand.run(root, Array(file.toString), new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8))
    (code, Files.readString(file, UTF_8), out.toString(UTF_8) + err.toString(UTF_8))
  }

  describe("OnionMath::randomInt") {
    it("is gone from OnionMath") {
      assert(!classOf[onion.OnionMath].getMethods.exists(_.getName == "randomInt"))
    }

    Seq(
      "val n = OnionMath::randomInt(1, 6)",
      "val n = onion.OnionMath::randomInt(1, 6)",
      "import { onion.OnionMath; }\nval n = OnionMath::randomInt(0, 9)",
      "val s = \"#{OnionMath::randomInt(1, 6)}\""
    ).foreach { code =>
      it(s"is E0005 with a hint naming Rand::nextInt: `${code.replace("\n", "; ")}`") {
        val errors = compile(code + "\n").allErrors
        assert(errors.exists(_.errorCode.contains("E0005")), s"expected E0005, got: ${errors.map(_.message)}")
        assert(errors.exists(_.message.contains(hint)), s"expected the randomInt hint, got: ${errors.map(_.message)}")
      }
    }

    it("hints nothing for another missing OnionMath method, or a user's own randomInt") {
      Seq(
        "val n = OnionMath::randomLong(1, 6)",
        "class Dice {\npublic:\n  static def roll(): Int = 1\n}\nval n = Dice::randomInt(1, 6)"
      ).foreach { code =>
        val errors = compile(code + "\n").allErrors.map(_.message)
        assert(errors.nonEmpty)
        assert(!errors.exists(_.contains(hint)), s"unexpected randomInt hint in: $errors")
      }
    }
  }

  describe("Rand::nextInt(min, max)") {
    it("returns min up to max - 1, never max") {
      val result = shell.run(
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val seen = new Boolean[7]
          |    for var i: Int = 0; i < 5000; i++ {
          |      val n = Rand::nextInt(1, 6)
          |      if n < 1 || n > 5 { return -1 }
          |      seen[n] = true
          |    }
          |    return if seen[1] && seen[5] { 1 } else { 0 }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(result == Shell.Success(1))
    }
  }

  describe("onion migrate") {
    it("rewrites each call to Rand::nextInt with max + 1, and the script runs") {
      val source =
        """import { onion.OnionMath; }
          |val xs: List[Int] = [1, 2, 3]
          |val a = OnionMath::randomInt(1, 6)   // a die
          |val b = onion.OnionMath::randomInt(0, xs.size - 1)
          |val c = OnionMath::randomInt(a, if a > 3 { 10 } else { 20 })
          |val d = OnionMath::randomInt(a, b * 2)
          |val s = "rolled #{OnionMath::randomInt(1, 6)}"
          |println(a >= 1 && a <= 6 && b <= 2 && c >= a && d >= a && s.length() > 7)
          |""".stripMargin
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after ==
        """import { onion.OnionMath; }
          |val xs: List[Int] = [1, 2, 3]
          |val a = Rand::nextInt(1, 7)   // a die
          |val b = Rand::nextInt(0, xs.size)
          |val c = Rand::nextInt(a, (if a > 3 { 10 } else { 20 }) + 1)
          |val d = Rand::nextInt(a, b * 2 + 1)
          |val s = "rolled #{Rand::nextInt(1, 7)}"
          |println(a >= 1 && a <= 6 && b <= 2 && c >= a && d >= a && s.length() > 7)
          |""".stripMargin)
      assert(output.contains("old.on:3:20: OnionMath::randomInt(min, max) -> Rand::nextInt(min, max + 1)"), output)
      assert(output.contains("old.on:7:30:"), output)
      assert(compile(after).allErrors.isEmpty, compile(after).allErrors.map(_.message))
    }

    it("leaves a user's own OnionMath.randomInt alone") {
      val source =
        """class OnionMath {
          |public:
          |  static def randomInt(a: Int, b: Int): Int = a + b
          |}
          |println(OnionMath::randomInt(1, 2))
          |""".stripMargin
      val (code, after, _) = migrate(source)
      assert(code == 0)
      assert(after == source)
    }

    it("notes a max of 2147483647, which has no max + 1, and leaves it as written") {
      val source = "val n = OnionMath::randomInt(0, 2147483647)\n"
      val (_, after, output) = migrate(source)
      assert(after == source)
      assert(output.contains("old.on:1:20: note:"), output)
    }
  }
}
