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

    it("rewrites a call nested in another call's arguments, and the script runs (#3116 block 1)") {
      val source =
        """val a = OnionMath::randomInt(1, OnionMath::randomInt(2, 6))
          |val b = OnionMath::randomInt(OnionMath::randomInt(0, 2), 9)
          |println(a >= 1 && a <= 6 && b >= 0 && b <= 9)
          |""".stripMargin
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after ==
        """val a = Rand::nextInt(1, Rand::nextInt(2, 7) + 1)
          |val b = Rand::nextInt(Rand::nextInt(0, 3), 10)
          |println(a >= 1 && a <= 6 && b >= 0 && b <= 9)
          |""".stripMargin)
      assert(compile(after).allErrors.isEmpty, compile(after).allErrors.map(_.message))
    }

    it("writes onion.Rand where the file's own Rand would capture the call (#3116 block 2)") {
      val source =
        """class Rand {
          |public:
          |  static def nextInt(a: Int, b: Int): Int = 42
          |}
          |println(OnionMath::randomInt(1, 6) < 7)
          |""".stripMargin
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after.contains("println(onion.Rand::nextInt(1, 7) < 7)"), after)
      assert(compile(after).allErrors.isEmpty, compile(after).allErrors.map(_.message))
    }

    it("writes onion.Rand where another file named alongside declares Rand") {
      val root = Files.createTempDirectory("onion-migrate-3069-dir").toRealPath()
      Files.writeString(root.resolve("Rand.on"),
        "class Rand {\npublic:\n  static def nextInt(a: Int, b: Int): Int = 42\n}\n", UTF_8)
      val script = root.resolve("Dice.on")
      Files.writeString(script, "class Dice {\npublic:\n  static def roll(): Int = OnionMath::randomInt(1, 6)\n}\n", UTF_8)
      val out = new ByteArrayOutputStream()
      val code = MigrateCommand.run(root, Array(root.toString), new PrintStream(out, true, UTF_8), new PrintStream(out, true, UTF_8))
      assert(code == 0, out.toString(UTF_8))
      assert(Files.readString(script, UTF_8).contains("= onion.Rand::nextInt(1, 7)"), Files.readString(script, UTF_8))
    }

    Seq("onion.OnionMath::*", "onion.OnionMath::randomInt").foreach { imported =>
      it(s"rewrites a bare call through `import { $imported }`, which also gets the hint (#3116 block 3)") {
        val source = s"import { $imported }\nprintln(randomInt(1, 6) < 7)\n"
        val errors = compile(source).allErrors
        assert(errors.exists(_.errorCode.contains("E0005")), errors.map(_.message))
        assert(errors.exists(_.message.contains(hint)), errors.map(_.message))
        val (code, after, output) = migrate(source)
        assert(code == 0, output)
        assert(after == s"import { $imported }\nprintln(Rand::nextInt(1, 7) < 7)\n")
        assert(compile(after).allErrors.isEmpty, compile(after).allErrors.map(_.message))
      }
    }

    it("rewrites a retired `self` inside a call's arguments along with the call") {
      val source =
        """class Die {
          |  var sides: Int
          |public:
          |  def this { sides = 6 }
          |  def roll(): Int = OnionMath::randomInt(1, self.sides)
          |  def size(): Int = self.sides
          |}
          |println(new Die().roll() <= new Die().size())
          |""".stripMargin
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after.contains("def roll(): Int = Rand::nextInt(1, this.sides + 1)"), after)
      assert(after.contains("def size(): Int = this.sides"), after)
      assert(compile(after).allErrors.isEmpty, compile(after).allErrors.map(_.message))
    }

    it("rewrites a call through an import alias of OnionMath") {
      val source = "import { onion.OnionMath as OM; }\nprintln(OM::randomInt(1, 6) < 7)\n"
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after == "import { onion.OnionMath as OM; }\nprintln(Rand::nextInt(1, 7) < 7)\n")
    }

    it("leaves a bare randomInt the file declares itself, and a method of that name, alone") {
      val source =
        """def randomInt(a: Int, b: Int): Int = a
          |class Die {
          |public:
          |  def randomInt(a: Int, b: Int): Int = b
          |}
          |println(randomInt(1, 6) + new Die().randomInt(1, 6))
          |""".stripMargin
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after == source)
    }

    // #3116 block 4: a bare `randomInt` that is the file's own value must keep meaning that
    // value; only the qualified call beside it is the removed method.
    Seq(
      "val randomInt: (Int, Int) -> Int = (a, b) -> 100\nprintln(randomInt(1, 6))\nprintln(OnionMath::randomInt(1, 6) < 7)\n",
      "import { onion.OnionMath::* }\nval randomInt: (Int, Int) -> Int = (a, b) -> 100\nprintln(randomInt(1, 6))\nprintln(OnionMath::randomInt(1, 6) < 7)\n",
      "def pick(randomInt: (Int, Int) -> Int): Int = randomInt(1, 6)\nprintln(pick((a, b) -> 100))\nprintln(OnionMath::randomInt(1, 6) < 7)\n",
      "import { onion.OnionMath::* }\ndef pick(randomInt: (Int, Int) -> Int): Int = randomInt(1, 6)\nprintln(pick((a, b) -> 100))\nprintln(OnionMath::randomInt(1, 6) < 7)\n"
    ).foreach { source =>
      it(s"leaves a bare call of the file's own function value alone: `${source.linesIterator.take(2).mkString("; ")}`") {
        val (_, after, output) = migrate(source)
        assert(after.contains("randomInt(1, 6))") || after.contains("Int = randomInt(1, 6)"), s"$after\n$output")
        assert(!after.contains("OnionMath::randomInt"), s"$after\n$output")
        assert(after.contains("println(Rand::nextInt(1, 7) < 7)"), s"$after\n$output")
      }
    }

    it("does not promise a migrate edit for a bare randomInt with no static import (#3116 block 7)") {
      val source = "val a = randomInt(1, 6)\nprintln(a)\n"
      val messages = compile(source).allErrors.map(_.message)
      assert(messages.exists(_.contains(Message("suggestion.onionMathRandomIntByHand"))), messages)
      assert(!messages.exists(_.contains("onion migrate")), messages)
      val (_, after, _) = migrate(source)
      assert(after == source)
    }

    it("leaves a bare randomInt alone in a file with no static import of OnionMath") {
      val source = "println(randomInt(1, 6))\nprintln(OnionMath::randomInt(1, 6) < 7)\n"
      val (_, after, output) = migrate(source)
      assert(after.startsWith("println(randomInt(1, 6))\n"), s"$after\n$output")
    }

    // #3116 block 5: the rule is "rewrite only where the compiler reported the removed
    // method". A call the compiler resolved keeps its meaning, however the rest of the file fails.
    def migrateDir(files: (String, String)*): (Int, Map[String, String], String) = {
      val root = Files.createTempDirectory("onion-migrate-3069-own").toRealPath()
      files.foreach((name, text) => Files.writeString(root.resolve(name), text, UTF_8))
      val out = new ByteArrayOutputStream()
      val code = MigrateCommand.run(root, Array(root.toString), new PrintStream(out, true, UTF_8), new PrintStream(out, true, UTF_8))
      (code, files.map((name, _) => name -> Files.readString(root.resolve(name), UTF_8)).toMap, out.toString(UTF_8))
    }

    val ownOnionMath = "class OnionMath {\npublic:\n  static def randomInt(a: Int, b: Int): Int = 100\n}\n"
    val mixedCalls = "    IO::println(OnionMath::randomInt(1, 6))\n    IO::println(onion.OnionMath::randomInt(5, 5))\n"

    it("leaves a call to a sibling file's own OnionMath alone beside a removed call (#3116 block 5)") {
      val main = "class Main {\npublic:\n  static def main(args: String[]): void {\n" + mixedCalls + "  }\n}\n"
      val (code, after, output) = migrateDir("OnionMath.on" -> ownOnionMath, "Main.on" -> main)
      assert(code == 0, output)
      assert(after("Main.on").contains("IO::println(OnionMath::randomInt(1, 6))"), s"${after("Main.on")}\n$output")
      assert(after("Main.on").contains("IO::println(Rand::nextInt(5, 6))"), s"${after("Main.on")}\n$output")
      assert(after("OnionMath.on") == ownOnionMath)
    }

    it("leaves a call to the file's own OnionMath alone beside a removed call") {
      val source = ownOnionMath + mixedCalls.replace("    ", "")
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after.contains("IO::println(OnionMath::randomInt(1, 6))"), s"$after\n$output")
      assert(after.contains("IO::println(Rand::nextInt(5, 6))"), s"$after\n$output")
    }

    it("leaves a bare randomInt inherited from another file alone beside a removed call") {
      val base = "class Base {\npublic:\n  def this {}\n  def randomInt(a: Int, b: Int): Int = 100\n}\n"
      val kid =
        """import { onion.OnionMath::* }
          |class Kid extends Base {
          |public:
          |  def this {}
          |  def roll(): Int = randomInt(1, 6)
          |  def real(): Int = OnionMath::randomInt(5, 5)
          |}
          |""".stripMargin
      val (code, after, output) = migrateDir("Base.on" -> base, "Kid.on" -> kid)
      assert(code == 0, output)
      assert(after("Kid.on").contains("def roll(): Int = randomInt(1, 6)"), s"${after("Kid.on")}\n$output")
      assert(after("Kid.on").contains("def real(): Int = Rand::nextInt(5, 6)"), s"${after("Kid.on")}\n$output")
    }

    it("leaves the file's own OnionMath call around a removed call alone, and rewrites the inner one") {
      val source = ownOnionMath + "println(OnionMath::randomInt(1, onion.OnionMath::randomInt(2, 2)))\n"
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after.contains("println(OnionMath::randomInt(1, Rand::nextInt(2, 3)))"), s"$after\n$output")
    }

    // #3116 block 5: `max + 1` of Int.MAX_VALUE overflows, whatever spells the constant.
    Seq(
      "2147483647",
      "2_147_483_647",
      "0x7FFFFFFF",
      "0x7fff_ffff",
      "0b1111111111111111111111111111111",
      "Integer::MAX_VALUE",
      "JInteger::MAX_VALUE",
      "java.lang.Integer::MAX_VALUE",
      "(Integer::MAX_VALUE)",
      "Integer::MIN_VALUE - 1",
      "2147483646 + 1",
      // #3116 block 6: the rule is by form, so every operator the compiler folds counts.
      "(1 << 31) - 1",
      "~0 >>> 1",
      "-1 >>> 1",
      "Integer::MAX_VALUE as Int",
      "Integer::MAX_VALUE | 0",
      "Integer::MAX_VALUE & -1",
      "Integer::MIN_VALUE ^ -1",
      "~Integer::MIN_VALUE",
      "~(Integer::MIN_VALUE * 1)",
      "0x3FFFFFFF << 1 | 1"
    ).foreach { max =>
      it(s"notes a max of Int.MAX_VALUE spelled `$max`, and leaves it as written") {
        val source = s"val n = OnionMath::randomInt(0, $max)\n"
        val (_, after, output) = migrate(source)
        assert(after == source, output)
        assert(output.contains("old.on:1:20: note:"), output)
      }
    }

    it("notes a max of Int.MAX_VALUE through an import alias of Integer") {
      val source = "import { java.lang.Integer as I; }\nval n = OnionMath::randomInt(0, I::MAX_VALUE)\n"
      val (_, after, output) = migrate(source)
      assert(after == source, output)
      assert(output.contains("old.on:2:20: note:"), output)
    }

    // #3116 block 7: the rule lists what is rewritten (a value migrate works out below
    // Int.MAX_VALUE, or a max that reads a variable or a call); every other constant is noted.
    Seq(
      "'a' as Int",
      "2147483647.0 as Int",
      "1e10 as Int",
      "Long::MAX_VALUE as Int >>> 1",
      "JLong::MAX_VALUE as Int >>> 1",
      "Short::MAX_VALUE",
      "6L as Int"
    ).foreach { max =>
      it(s"notes a constant max it cannot show is below Int.MAX_VALUE: `$max`") {
        val source = s"val n = OnionMath::randomInt(0, $max)\n"
        val (_, after, output) = migrate(source)
        assert(after == source, output)
        assert(output.contains("old.on:1:20: note:"), output)
        assert(output.contains("cannot show"), output)
      }
    }

    it("still rewrites a max that reads a variable, a field or a call, and the script runs") {
      val source =
        """val n = 6
          |val xs: List[Int] = [1, 2, 3]
          |val a = OnionMath::randomInt(0, n)
          |val b = OnionMath::randomInt(0, xs.size)
          |val c = OnionMath::randomInt(0, Math::abs(n))
          |val d = OnionMath::randomInt(0, n as Int)
          |println(a <= 6 && b <= 3 && c <= 6 && d <= 6)
          |""".stripMargin
      val (code, after, output) = migrate(source)
      assert(code == 0, output)
      assert(after.contains("val a = Rand::nextInt(0, n + 1)"), s"$after\n$output")
      assert(after.contains("val b = Rand::nextInt(0, xs.size + 1)"), s"$after\n$output")
      assert(after.contains("val c = Rand::nextInt(0, Math::abs(n) + 1)"), s"$after\n$output")
      assert(after.contains("val d = Rand::nextInt(0, (n as Int) + 1)"), s"$after\n$output")
      assert(compile(after).allErrors.isEmpty, compile(after).allErrors.map(_.message))
    }

    it("still rewrites a max that is a constant below Int.MAX_VALUE, in any spelling") {
      Seq(
        "(1 << 4)" -> "Rand::nextInt(0, (1 << 4) + 1)",
        "1 << 4" -> "Rand::nextInt(0, (1 << 4) + 1)",
        "~0 >>> 2" -> "Rand::nextInt(0, (~0 >>> 2) + 1)",
        "~Integer::MIN_VALUE * 1 - 1" -> "Rand::nextInt(0, (~Integer::MIN_VALUE * 1 - 1) + 1)",
        "0x7FFFFFFE" -> "Rand::nextInt(0, 0x7FFFFFFE + 1)",
        "Integer::MAX_VALUE - 1" -> "Rand::nextInt(0, Integer::MAX_VALUE)"
      ).foreach { (max, expected) =>
        val (code, after, output) = migrate(s"val n = OnionMath::randomInt(0, $max)\nprintln(n >= 0)\n")
        assert(code == 0, output)
        assert(after.contains(expected), s"$after\n$output")
      }
    }
  }
}
