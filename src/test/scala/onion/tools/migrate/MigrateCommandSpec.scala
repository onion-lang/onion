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

  test("a file that names ret and still has an old ret is reported, not guessed at"):
    val source =
      """val ret = 1
        |val r = do[Option] { a <- Option::some(ret); ret a + 1 }
        |""".stripMargin
    val (root, file) = script(source)

    val result = invoke(root, file.toString)

    result.exitCode shouldBe 1
    read(file) shouldBe source
    result.stderr should include("old.on:2:46")
    result.stderr should include("uses `ret` as a name")

  test("a ret bound by a lambda parameter or a destructuring val is reported, not rewritten"):
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
        result.exitCode shouldBe 1
        read(file) shouldBe source
        result.stderr should include("uses `ret` as a name")
      }
    }

  test("a ret with another clause after it is reported, even when its declaration is in another file"):
    val sources = Seq(
      """class Main extends Base {
        |public:
        |  def this {}
        |  def go(): Option[Int] = do[Option] { a <- Option::some(1); ret(a); yield a }
        |}
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
        result.exitCode shouldBe 1
        read(file) shouldBe source
        result.stderr should include("not the last clause of its do block")
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
        |val s = do[Option] { xs <- Option::some([1, 2])
        |  ret xs
        |    .map { x -> x * 2 }
        |    |> size;
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
    invoke(root, root.resolve("missing.on").toString).exitCode shouldBe 2
