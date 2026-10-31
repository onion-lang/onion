package onion.tools.migrate

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import onion.tools.Shell
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `onion migrate` (#2442): rewrites a pre-1.0 script for the breaking changes since, in place.
 *
 * The rule it must never break is the one the old `perl -pi` line did: a variable that
 * happens to be called `ret` is the user's, not a leftover keyword.
 */
class MigrateCommandSpec extends AnyFunSuite with Matchers:

  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  private def invoke(cwd: Path, args: String*): Invocation =
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val outStream = new PrintStream(out, true, UTF_8)
    val errStream = new PrintStream(err, true, UTF_8)
    val code =
      try MigrateCommand.run(cwd, args.toArray, outStream, errStream)
      finally
        outStream.close()
        errStream.close()
    Invocation(code, out.toString(UTF_8), err.toString(UTF_8))

  private def script(content: String, name: String = "old.on"): (Path, Path) =
    val root = Files.createTempDirectory("onion-migrate").toRealPath()
    val file = root.resolve(name)
    Files.writeString(file, content, UTF_8)
    (root, file)

  private def read(file: Path): String = Files.readString(file, UTF_8)

  private val OldScript =
    """// ret a + b: the old way
      |def sum(x: Option[Int], y: Option[Int]): Option[Int] = do[Option] {
      |  a <- x
      |  b <- y   // the second one
      |  ret a + b
      |}
      |IO::println(sum(Option::some(1), Option::some(2)).getOrElse(0))
      |IO::println("ret stays in a string")
      |""".stripMargin

  test("a do block's ret becomes yield, and comments, strings and spacing stay as written"):
    val (root, file) = script(OldScript)

    val result = invoke(root, file.toString)

    result.exitCode shouldBe 0
    read(file) shouldBe OldScript.replace("  ret a + b", "  yield a + b")
    result.stdout should include("old.on:5:3: ret -> yield")
    result.stdout should include("Migrated 1 of 1 file(s)")

  test("the migrated script compiles and runs with the same answer"):
    val (root, file) = script(
      """class Old {
        |public:
        |  static def sum(x: Option[Int], y: Option[Int]): Option[Int] = do[Option] { a <- x; b <- y; ret a + b }
        |  static def main(args: String[]): Int = sum(Option::some(40), Option::some(2)).getOrElse(0)
        |}
        |""".stripMargin)

    invoke(root, file.toString).exitCode shouldBe 0

    Shell(Seq()).run(read(file), "old.on", Array()) shouldBe Shell.Success(42)

  test("ret on its own line, with no separator, inside nested do blocks"):
    val source =
      """val r = do[Option] {
        |  a <- Option::some(1)
        |  b <- do[Option] { c <- Option::some(2)
        |    ret c }
        |  ret
        |    a + b
        |}
        |""".stripMargin
    val (root, file) = script(source)

    invoke(root, file.toString).exitCode shouldBe 0

    read(file) shouldBe source.replace("    ret c }", "    yield c }").replace("  ret\n", "  yield\n")

  test("a variable named ret is left alone"):
    val source =
      """val ret = 41
        |val r = do[Option] { a <- Option::some(ret); yield a + 1 }
        |IO::println(ret)
        |""".stripMargin
    val (root, file) = script(source)

    val result = invoke(root, file.toString)

    result.exitCode shouldBe 0
    read(file) shouldBe source
    result.stdout should include("1 file(s) already up to date")

  test("a file that does not compile with a final ret, and does with yield, is rewritten"):
    val source =
      """val ret = 1
        |val r = do[Option] { a <- Option::some(ret); ret a + 1 }
        |IO::println(r)
        |""".stripMargin
    val (root, file) = script(source)

    val result = invoke(root, file.toString)

    result.exitCode shouldBe 0
    read(file) shouldBe source.replace("ret a + 1", "yield a + 1")
    result.stdout should include("old.on:2:46: ret -> yield")

  test("a ret bound by a lambda parameter or a destructuring val is left alone"):
    val sources = Seq(
      """val log: (Int) -> Option[Int] = (x) -> Option::some(x)
        |val (ret, q) = [log, log]
        |val o = do[Option] { a <- Option::some(1); ret(a); yield a }
        |""".stripMargin,
      """val f: ((Int) -> Option[Int]) -> Option[Int] =
        |  (ret) -> do[Option] { a <- Option::some(1); ret(a); yield a + 1 }
        |""".stripMargin,
      """record Box(v: (Int) -> Option[Int])
        |def g(b: Box): Option[Int] = select b {
        |  case Box(ret): do[Option] { a <- Option::some(1); ret(a); yield a }
        |}
        |""".stripMargin)
    sources.foreach { source =>
      val (root, file) = script(source)

      val result = invoke(root, file.toString)

      withClue(source) {
        result.exitCode shouldBe 0
        read(file) shouldBe source
      }
    }

  test("a ret with another clause after it is a call, noted and left alone, with exit 0"):
    val sources = Seq(
      """class Main extends Base {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret(a); yield a }
        |}
        |""".stripMargin,
      """class Main extends Base {
        |public:
        |  def this {}
        |  def go(): Object = do[Option] { a <- Option::some(1); ret(a) Option::some(a + 1) }
        |}
        |""".stripMargin,
      """val o = do[Option] { a <- Option::some(1); ret a Option::some(a) }
        |""".stripMargin,
      """val o = do[Option] {
        |  a <- Option::some(1)
        |  ret(a)
        |  b <- Option::some(2)
        |  yield a + b
        |}
        |""".stripMargin,
      """val o = do[Option] {
        |  a <- Option::some(1)
        |  ret(a)
        |  log(a)
        |}
        |""".stripMargin)
    sources.foreach { source =>
      val (root, file) = script(source)

      val result = invoke(root, file.toString)

      withClue(source) {
        result.exitCode shouldBe 0
        read(file) shouldBe source
        result.stdout should include("note:")
        result.stdout should include("not the last clause of its do block")
        result.stdout should include("1 file(s) already up to date")
        result.stderr shouldBe ""
      }
    }

  /** `Base.on` declares `ret` as `member`; its class file goes into a fresh directory. */
  private def baseClasses(member: String): Path =
    val dir = Files.createTempDirectory("onion-migrate-base").toRealPath()
    val base =
      s"""class Base {
         |public:
         |  def this {}
         |  $member
         |}
         |""".stripMargin
    val config = onion.compiler.CompilerConfig(Seq(), null, "UTF-8", "", 10, checkLaws = false)
    val classes = new onion.compiler.OnionCompiler(config)
      .compileOrThrow(Seq(onion.compiler.StringInputSource(base, "Base.on")))
    classes.foreach(c => Files.write(dir.resolve(c.className + ".class"), c.content))
    dir

  test("a file that compiles as written is never changed: a final ret through an inherited name"):
    val cases = Seq(
      "def ret(): Option[Int] = Option::none()" -> "ret a",
      "val ret: Option[Int] = Option::none()" -> "ret a",
      "def ret(x: Int): Option[Int] = Option::none()" -> "ret(a)",
      "def ret(x: Int): Option[Int] = Option::none()" -> "ret (a)",
      "def ret(x: Int): Option[Int] = Option::none()" -> "ret(-a)",
      "val ret: Option[Int] = Option::none()" -> "ret a + 1")
    for (member, clause) <- cases do
      val source =
        s"""class Main extends Base {
           |public:
           |  def this {}
           |  def go(): Object = do[Option] { a <- Option::some(1); $clause }
           |}
           |""".stripMargin
      val (root, file) = script(source, "Main.on")

      val result = invoke(root, "-classpath", baseClasses(member).toString, file.toString)

      withClue(s"$member / $clause: ${result.stdout}${result.stderr}") {
        result.exitCode shouldBe 0
        read(file) shouldBe source
        result.stdout should include("note: the file compiles as written")
        result.stdout should include("1 file(s) already up to date")
      }

  test("the panel's repro: a final ret a through an inherited ret() is left as written"):
    val source =
      """class Main extends Base {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret a }
        |  static def main(args: String[]): void { IO::println(new Main().go()) }
        |}
        |""".stripMargin
    val (root, file) = script(source, "Main.on")
    val classes = baseClasses("def ret(): Option[Int] = Option::none()")

    val result = invoke(root, "-classpath", classes.toString, file.toString)

    result.exitCode shouldBe 0
    read(file) shouldBe source
    result.stdout should include("Main.on:4:62: note: the file compiles as written")
    result.stdout should include("1 file(s) already up to date")

  test("without the class that declares ret, the file is reported, not guessed at"):
    val source =
      """class Main extends Base {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret a }
        |}
        |""".stripMargin
    val (root, file) = script(source, "Main.on")

    val result = invoke(root, file.toString)

    result.exitCode shouldBe 1
    read(file) shouldBe source
    result.stderr should include("Main.on:4:62: this `ret` may be the old do-block keyword")
    result.stderr should include("-classpath")
    result.stdout should include("1 file(s) need a manual change")
    result.stdout should not include "ret -> yield"

  test("an old final ret followed by ( [ - + ! is rewritten when nothing else declares ret"):
    val sources = Seq(
      "val o = do[Option] { a <- Option::some(1); ret(a) }\n",
      "val o = do[Option] { a <- Option::some(1); ret -a }\n",
      "val o = do[Option] { a <- Option::some(1); ret [a, 2] }\n",
      "val o = do[Option] { a <- Option::some(1); ret +a }\n",
      "val o = do[Option] { a <- Option::some(true); ret !a }\n")
    sources.foreach { source =>
      val (root, file) = script(source)

      val result = invoke(root, file.toString)

      withClue(source + result.stderr) {
        result.exitCode shouldBe 0
        read(file) shouldBe source.replace("ret", "yield")
        result.stdout should include("ret -> yield")
      }
    }

  test("an old ret whose expression spans several lines is still rewritten"):
    val source =
      """val r = do[Option] {
        |  a <- Option::some(1)
        |  ret if a > 0 {
        |    a +
        |      1
        |  }
        |  else { 0 }
        |}
        |def count(xs: List[Int]): Int = xs.size
        |val s = do[Option] { xs <- Option::some([1, 2])
        |  ret xs
        |    .map { x -> x * 2 }
        |    |> count;
        |}
        |""".stripMargin
    val (root, file) = script(source)

    invoke(root, file.toString).exitCode shouldBe 0

    read(file) shouldBe source.replace("  ret if", "  yield if").replace("  ret xs", "  yield xs")

  test("ret outside a do block, or after a dot, is not the keyword"):
    val source =
      """def ret(x: Int): Int = x
        |val a = ret(1)
        |val b = do[Option] { x <- Option::some(this.ret(2)); yield x }
        |""".stripMargin
    val (root, file) = script(source)

    invoke(root, file.toString).exitCode shouldBe 0
    read(file) shouldBe source

  test("--check writes nothing and exits 1 when a file would change"):
    val (root, file) = script(OldScript)

    val result = invoke(root, "--check", file.toString)

    result.exitCode shouldBe 1
    read(file) shouldBe OldScript
    result.stdout should include("old.on:5:3: ret -> yield")
    result.stdout should include("1 file(s) would be migrated")

  test("the panel's repro: a ret inside a string interpolation is rewritten too"):
    val source =
      """val x = Option::some(20)
        |println("answer: #{do[Option] { a <- x; ret a + 1 }}")
        |println("nested: #{do[Option] { a <- x; ret "#{a}!" }} and \#{do[Option] { a <- x; ret a }}")
        |println(QQQ
        |  multi: #{do[Option] { a <- x; ret a }}
        |QQQ)
        |""".stripMargin.replace("QQQ", "\"\"\"")
    val (root, file) = script(source)

    val check = invoke(root, "--check", file.toString)

    check.exitCode shouldBe 1
    check.stdout should include("1 file(s) would be migrated")

    val result = invoke(root, file.toString)

    result.exitCode shouldBe 0
    result.stdout should include("old.on:2:41: ret -> yield")
    read(file) shouldBe source
      .replace("ret a + 1", "yield a + 1")
      .replace("""ret "#{a}!"""", """yield "#{a}!"""")
      .replace("  multi: #{do[Option] { a <- x; ret a }}", "  multi: #{do[Option] { a <- x; yield a }}")
    Shell(Seq()).run(read(file), "old.on", Array()) shouldBe a[Shell.Success]

  test("the panel's repro: files that use each other are compiled together"):
    val root = Files.createTempDirectory("onion-migrate-project").toRealPath()
    val src = Files.createDirectories(root.resolve("src"))
    val model =
      """class Calc {
        |public:
        |  def this {}
        |  def add(x: Int, y: Int): Option[Int] = do[Option] { a <- Option::some(x); b <- Option::some(y); ret a + b * Helper::ten() }
        |}
        |""".stripMargin
    val main =
      """class Helper {
        |public:
        |  static def ten(): Int = 10
        |}
        |val c = new Calc()
        |val r = do[Option] { s <- c.add(1, 2); ret s * 10 }
        |IO::println(r)
        |""".stripMargin
    Files.writeString(src.resolve("model.on"), model, UTF_8)
    Files.writeString(src.resolve("main.on"), main, UTF_8)

    val result = invoke(root, src.toString)

    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 0
      result.stderr shouldBe ""
      result.stdout should include("Migrated 2 of 2 file(s)")
    }
    read(src.resolve("model.on")) shouldBe model.replace("ret a + b", "yield a + b")
    read(src.resolve("main.on")) shouldBe main.replace("ret s * 10", "yield s * 10")

  test("a file that compiles with the other named files is left as written"):
    val root = Files.createTempDirectory("onion-migrate-project").toRealPath()
    val base =
      """class Base {
        |public:
        |  def this {}
        |  def ret(): Option[Int] = Option::none()
        |}
        |""".stripMargin
    val main =
      """class Main extends Base {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret a }
        |}
        |""".stripMargin
    Files.writeString(root.resolve("Base.on"), base, UTF_8)
    Files.writeString(root.resolve("Main.on"), main, UTF_8)

    val result = invoke(root, root.toString)

    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 0
      result.stdout should include("Main.on:4:62: note: the file compiles as written")
      result.stdout should include("2 file(s) already up to date")
    }
    read(root.resolve("Main.on")) shouldBe main

  test("the panel's repro: a file that compiles alone only with yield, but together as written, is left alone"):
    val root = Files.createTempDirectory("onion-migrate-project").toRealPath()
    val ext =
      """extension Main {
        |  def ret(): Option[Int] = Option::none()
        |}
        |""".stripMargin
    val main =
      """class Main {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret a }
        |  static def main(args: String[]): void { IO::println(new Main().go()) }
        |}
        |""".stripMargin
    Files.writeString(root.resolve("Ext.on"), ext, UTF_8)
    Files.writeString(root.resolve("Main.on"), main, UTF_8)

    val result = invoke(root, root.resolve("Ext.on").toString, root.resolve("Main.on").toString)

    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 0
      result.stdout should include("Main.on:4:62: note: the file compiles as written")
      result.stdout should not include "ret -> yield"
      result.stdout should include("2 file(s) already up to date")
    }
    read(root.resolve("Main.on")) shouldBe main

  test("a first-pass rewrite waits when a syntax error in another named file stops the joint compile"):
    val root = Files.createTempDirectory("onion-migrate-project").toRealPath()
    val main = "val r = do[Option] { a <- Option::some(1); ret a }\nIO::println(r)\n"
    Files.writeString(root.resolve("Main.on"), main, UTF_8)
    Files.writeString(root.resolve("Broken.on"), "val x: Int = (\n", UTF_8)

    val result = invoke(root, root.toString)

    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 1
      result.stdout should not include "ret -> yield"
      result.stderr should include("Main.on:1:44:")
      result.stderr should include("Broken.on")
      result.stdout should include("1 file(s) need a manual change")
    }
    read(root.resolve("Main.on")) shouldBe main

  test("the panel's repro: a file that cannot be read leaves every pending ret undecided"):
    val root = Files.createTempDirectory("onion-migrate-project").toRealPath()
    val src = Files.createDirectories(root.resolve("src"))
    val model =
      """class Calc {
        |public:
        |  def this {}
        |  def add(x: Int, b: Int): Option[Int] = do[Option] { a <- Option::some(x); ret a + b }
        |}
        |""".stripMargin
    val main = "val c = new Calc()\nval r = do[Option] { s <- c.add(1, 2); ret s * 10 }\nIO::println(r)\n"
    Files.writeString(src.resolve("model.on"), model, UTF_8)
    Files.writeString(src.resolve("main.on"), main, UTF_8)
    Files.write(src.resolve("sjis.on"), "IO::println(\"こんにちは\")\n".getBytes("Shift_JIS"))

    for args <- Seq(Seq("--check", src.toString), Seq(src.toString)) do
      val result = invoke(root, args*)
      withClue(result.stdout + result.stderr) {
        result.exitCode shouldBe 1
        result.stderr should include("Could not migrate src/sjis.on: it is not valid UTF-8")
        result.stderr should not include "Exception"
        result.stderr should include("src/model.on:4:77:")
        result.stderr should include("src/main.on:2:40:")
        result.stderr should include("left out: src/sjis.on")
        result.stdout should include("2 file(s) need a manual change")
        result.stdout should not include "Migrated"
        result.stdout should not include "would be migrated"
      }
      read(src.resolve("model.on")) shouldBe model
      read(src.resolve("main.on")) shouldBe main

    Files.writeString(src.resolve("sjis.on"), "IO::println(\"こんにちは\")\n", UTF_8)
    val converted = invoke(root, src.toString)
    withClue(converted.stdout + converted.stderr) {
      converted.exitCode shouldBe 0
      converted.stdout should include("Migrated 2 of 3 file(s)")
    }
    read(src.resolve("model.on")) shouldBe model.replace("ret a + b", "yield a + b")
    read(src.resolve("main.on")) shouldBe main.replace("ret s * 10", "yield s * 10")

  test("the panel's repro: an unreadable file that declares ret keeps a ret that calls it"):
    val dir = Files.createTempDirectory("onion-migrate-sj").toRealPath()
    val sj = Files.createDirectories(dir.resolve("sj"))
    val ext = "// あい comment in Shift_JIS\nextension Main {\n  def ret(): Option[Int] = Option::none()\n}\n"
    val main =
      """class Main {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret a }
        |  static def main(args: String[]): void { IO::println(new Main().go()) }
        |}
        |""".stripMargin
    Files.write(sj.resolve("Ext.on"), ext.getBytes("Shift_JIS"))
    Files.writeString(sj.resolve("Main.on"), main, UTF_8)

    val check = invoke(dir, "--check", sj.toString)
    withClue(check.stdout + check.stderr) {
      check.exitCode shouldBe 1
      check.stdout should not include "would be migrated"
      check.stderr should include("sj/Main.on:4:62: this `ret` may be the old do-block keyword")
    }

    val result = invoke(dir, sj.toString)
    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 1
      result.stderr should include("Could not migrate sj/Ext.on")
      result.stderr should include("sj/Main.on:4:62:")
      result.stdout should not include "ret -> yield"
      result.stdout should include("1 file(s) need a manual change")
    }
    read(sj.resolve("Main.on")) shouldBe main

  test("the panel's repro: a syntax error in another file makes a pending ret undecided, not a name"):
    val root = Files.createTempDirectory("onion-migrate-project").toRealPath()
    val src = Files.createDirectories(root.resolve("src"))
    val tests = Files.createDirectories(root.resolve("tests"))
    val util =
      """class Util {
        |public:
        |  static def add(a: Int, b: Int): Option[Int] = do[Option] { x <- Option::some(a); ret x + b }
        |}
        |""".stripMargin
    val t = "val r = do[Option] { a <- Util::add(1, 2); ret a * 10 }\n"
    val broken = "val x: Int = (\n"
    Files.writeString(src.resolve("Util.on"), util, UTF_8)
    Files.writeString(tests.resolve("T.on"), t, UTF_8)
    Files.writeString(src.resolve("Main.on"), broken, UTF_8)

    val result = invoke(root, src.toString, tests.toString)

    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 1
      result.stdout should not include "compiles as written"
      result.stderr should include("tests/T.on:1:44:")
      result.stderr should include("src/Main.on")
      // Util.on compiles alone only with yield, but the joint compile cannot confirm it.
      result.stderr should include("src/Util.on:3:84:")
      result.stdout should include("2 file(s) need a manual change")
    }
    read(tests.resolve("T.on")) shouldBe t
    read(src.resolve("Util.on")) shouldBe util

  /** The panel's ninth-round repro: `Main.on` uses an extension's `ret`, `A.on` is unrelated. */
  private def extensionAndUnrelated(dir: Path, unrelated: String): (String, String) =
    val ext = "extension Main {\n  def ret(): Option[Int] = Option::none()\n}\n"
    val main =
      """class Helper {
        |public:
        |  static def ten(): Int = 10
        |}
        |class Main {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(Helper::ten()); ret a }
        |  static def main(args: String[]): void { IO::println(new Main().go()) }
        |}
        |""".stripMargin
    Files.writeString(dir.resolve("Ext.on"), ext, UTF_8)
    Files.writeString(dir.resolve("Main.on"), main, UTF_8)
    Files.writeString(dir.resolve(unrelated),
      "class Helper {\npublic:\n  static def ten(): Int = 20\n}\nIO::println(Helper::ten())\n", UTF_8)
    (ext, main)

  test("the panel's repro: an error that is not at a ret decides nothing, whatever the file names"):
    for unrelated <- Seq("A.on", "Zed.on") do
      val root = Files.createTempDirectory("onion-migrate-names").toRealPath()
      val (_, main) = extensionAndUnrelated(root, unrelated)

      val result = invoke(root, root.toString)

      withClue(s"$unrelated: " + result.stdout + result.stderr) {
        result.stdout should not include "ret -> yield"
        read(root.resolve("Main.on")) shouldBe main
      }

  test("an error in another file decides nothing: two unrelated Base classes, only one declares ret"):
    val root = Files.createTempDirectory("onion-migrate-base").toRealPath()
    val main =
      """class Main extends Base {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret a }
        |  static def main(args: String[]): void { IO::println(new Main().go()) }
        |}
        |""".stripMargin
    // The real program is B.on + Main.on; A.on is an unrelated script with its own Base.
    Files.writeString(root.resolve("A.on"), "class Base {\npublic:\n  def this {}\n}\nIO::println(1)\n", UTF_8)
    Files.writeString(root.resolve("B.on"),
      "class Base {\npublic:\n  def this {}\n  def ret(): Option[Int] = Option::none()\n}\n", UTF_8)
    Files.writeString(root.resolve("Main.on"), main, UTF_8)

    val result = invoke(root, root.toString)

    withClue(result.stdout + result.stderr) {
      result.stdout should not include "ret -> yield"
    }
    read(root.resolve("Main.on")) shouldBe main

  test("the panel's repro: one file named twice, under two spellings, is migrated once"):
    val root = Files.createTempDirectory("onion-migrate-twice").toRealPath()
    val dir = Files.createDirectories(root.resolve("t6"))
    val main =
      """class Main {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret a }
        |  static def main(args: String[]): void { IO::println(new Main().go()) }
        |}
        |""".stripMargin
    Files.writeString(dir.resolve("Ext.on"), "extension Main {\n  def ret(): Option[Int] = Option::none()\n}\n", UTF_8)
    Files.writeString(dir.resolve("Main.on"), main, UTF_8)

    val result = invoke(root, dir.toString, root.toString + "/./t6/Main.on")

    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 0
      result.stdout should include("t6/Main.on:4:62: note: the file compiles as written")
      result.stdout should not include "ret -> yield"
      result.stdout should include("2 file(s) already up to date")
    }
    read(dir.resolve("Main.on")) shouldBe main

  test("a file whose old ret [..] does not parse is read with yield, and does not hold back the others"):
    val root = Files.createTempDirectory("onion-migrate-unparsed").toRealPath()
    val list = "val xs = do[Option] { a <- Option::some(1); ret [a, 2] }\nIO::println(xs)\n"
    val other = "val o = do[Option] { b <- Option::some(3); ret b }\nIO::println(o)\n"
    Files.writeString(root.resolve("list.on"), list, UTF_8)
    Files.writeString(root.resolve("other.on"), other, UTF_8)

    val result = invoke(root, root.toString)

    withClue(result.stdout + result.stderr) {
      result.exitCode shouldBe 0
      result.stdout should include("Migrated 2 of 2 file(s)")
    }
    read(root.resolve("list.on")) shouldBe list.replace("ret [a, 2]", "yield [a, 2]")
    read(root.resolve("other.on")) shouldBe other.replace("ret b", "yield b")

  test("a file that compiles neither way is reported with the compiler's first error"):
    val source = "val o = do[Option] { a <- Option::some(1); ret a }\nval n: Int = \"no\"\n"
    val (root, file) = script(source)

    val result = invoke(root, file.toString)

    result.exitCode shouldBe 1
    read(file) shouldBe source
    result.stderr should include("old.on:1:44:")
    result.stderr should include("old.on:2:")
    result.stdout should include("1 file(s) need a manual change")

  test("--help prints the usage and exits 0"):
    val (root, _) = script(OldScript)

    val result = invoke(root, "--help")

    result.exitCode shouldBe 0
    result.stdout should include("Usage: onion migrate")

  test("--check exits 0 when nothing would change"):
    val (root, file) = script("IO::println(1)\n")

    invoke(root, "--check", file.toString).exitCode shouldBe 0

  test("a directory is walked for .on files"):
    val (root, file) = script(OldScript)
    Files.writeString(root.resolve("notes.txt"), "ret a + b\n", UTF_8)

    val result = invoke(root, root.toString)

    result.exitCode shouldBe 0
    read(file) should include("yield a + b")
    read(root.resolve("notes.txt")) shouldBe "ret a + b\n"

  test("a tab before ret does not shift the rewrite"):
    val source = "val r = do[Option] {\n\ta <- Option::some(1)\n\tret a\n}\n"
    val (root, file) = script(source)

    invoke(root, file.toString).exitCode shouldBe 0
    read(file) shouldBe "val r = do[Option] {\n\ta <- Option::some(1)\n\tyield a\n}\n"

  test("no path is a usage error, and so is an unknown option"):
    val (root, _) = script(OldScript)

    invoke(root).exitCode shouldBe 2
    invoke(root, "--frobnicate", "x.on").exitCode shouldBe 2
    invoke(root, "x.on", "-classpath").exitCode shouldBe 2
    invoke(root, root.resolve("missing.on").toString).exitCode shouldBe 2
