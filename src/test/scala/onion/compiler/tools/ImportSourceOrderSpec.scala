package onion.compiler.tools

import java.io.{File, StringReader}
import java.nio.file.Files
import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * Issue #2776: the order of source files on the command line does not change what an import
 * finds. `onionc app.on lib.on` used to fail with E0003 on `import { shapes.Plain }`, and
 * Which class a bare name binds is not part of that fix and stays as on main.
 */
class ImportSourceOrderSpec extends AnyFunSpec {
  private val lib =
    """module shapes;
      |class Plain {
      |public:
      |  static def make(): Int = 7
      |}
      |class Box[T] {
      |}
      |class Timer {
      |}""".stripMargin

  private def errorCodes(sources: (String, String)*): Either[Seq[String], Unit] =
    errorCodesWith(Seq("."), sources*)

  private def errorCodesWith(classPath: Seq[String], sources: (String, String)*): Either[Seq[String], Unit] = {
    val config = new CompilerConfig(classPath, null, "UTF-8", "", 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(_)      => Right(())
      case CompilationOutcome.Failure(errors) => Left(errors.map(_.toString))
    }
  }

  private def app(importLine: String, body: String): String =
    s"""module app;
       |import { $importLine }
       |class Use {
       |public:
       |  static def f(s: $body): void { }
       |}""".stripMargin

  describe("a single-class import of a class declared in another file") {
    val a = app("shapes.Plain", "Plain")
    it("resolves when the declaring file comes first") {
      assert(errorCodes("lib.on" -> lib, "app.on" -> a) == Right(()))
    }
    it("resolves when the declaring file comes later") {
      assert(errorCodes("app.on" -> a, "lib.on" -> lib) == Right(()))
    }
    it("is still E0003 when no file declares the class") {
      val Left(errors) = errorCodes("app.on" -> app("shapes.Nope", "Plain"), "lib.on" -> lib)
      assert(errors.exists(_.contains("E0003")))
    }
  }

  describe("a wildcard import of a module that declares a name the JDK also has") {
    // Binding is unchanged from main (#2855's rule): the default imports still supply
    // `java.util.Timer`, in either file order. A shadowing change needs its own RFC.
    def timerApp = app("shapes.*", "Timer").replace("static def f(s: Timer): void { }", "static def f(s: Timer): void { s.cancel() }")
    it("still binds the JDK class when the module's file comes first") {
      assert(errorCodes("lib.on" -> lib, "app.on" -> timerApp) == Right(()))
    }
    it("still binds the JDK class when the module's file comes later") {
      assert(errorCodes("app.on" -> timerApp, "lib.on" -> lib) == Right(()))
    }
  }

  /** Compiles `sources` and writes their classes to a fresh directory, for use as a classpath. */
  private def classDir(sources: (String, String)*): String = {
    val dir = Files.createTempDirectory("onion-import-order").toFile
    val config = new CompilerConfig(Seq("."), null, "UTF-8", dir.getAbsolutePath, 10)
    val inputs = sources.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Success(classes) =>
        for (c <- classes) {
          val file = new File(dir, c.className.replace('.', '/') + ".class")
          file.getParentFile.mkdirs()
          Files.write(file.toPath, c.content)
        }
      case CompilationOutcome.Failure(errors) => fail("library failed: " + errors.mkString("; "))
    }
    dir.getAbsolutePath
  }

  describe("two wildcard imports, one module from the classpath and one from source") {
    // The panel's repro on #2855: between the user's own wildcards the written order
    // decides, whether the class comes from source or a jar.
    val qSource = "module q;\nclass Foo {\npublic:\n  def this {}\n  def onlyQ(): Int = 2\n}"
    lazy val pOnly = classDir("p.on" -> "module p;\nclass Foo {\npublic:\n  def this {}\n  def onlyP(): Int = 1\n}")
    def useFoo(imports: String, call: String): String =
      s"""module app;
         |import {
         |  $imports
         |}
         |class Use {
         |public:
         |  static def f(): Int {
         |    val f: Foo = new Foo()
         |    return f.$call()
         |  }
         |}""".stripMargin

    it("binds the first written import's class when the second one's comes from source") {
      val src = useFoo("p.*\n  q.*", "onlyP")
      assert(errorCodesWith(Seq(".", pOnly), "use.on" -> src, "q.on" -> qSource) == Right(()))
      assert(errorCodesWith(Seq(".", pOnly), "q.on" -> qSource, "use.on" -> src) == Right(()))
    }
    it("binds the source class when its import is written first") {
      val src = useFoo("q.*\n  p.*", "onlyQ")
      assert(errorCodesWith(Seq(".", pOnly), "use.on" -> src, "q.on" -> qSource) == Right(()))
    }
  }

  describe("a wildcard import of classes that come only from the classpath") {
    // Two modules on the classpath both declare `Foo`; `p.*` is written first, so a bare
    // `Foo` is `p.Foo`. Whether an earlier line already loaded `q.Foo` must not matter.
    lazy val lib = classDir(
      "p.on" -> "module p;\nclass Foo {\npublic:\n  def this {}\n  def onlyP(): Int = 1\n}",
      "q.on" -> "module q;\nclass Foo {\npublic:\n  def this {}\n  def onlyQ(): Int = 2\n}",
      "t.on" -> "module mypkg;\nclass Timer {\npublic:\n  def this {}\n}"
    )
    def use(pre: String, body: String, imports: String = "p.*\n  q.*"): String =
      s"""module app;
         |import {
         |  $imports
         |}
         |class Use {
         |public:
         |  static def f(): Int {
         |    $pre
         |    $body
         |  }
         |}""".stripMargin

    it("binds the first import's class when nothing loaded the other one") {
      assert(errorCodesWith(Seq(".", lib), "use.on" -> use("", "val f: Foo = new Foo()\n    return f.onlyP()")) == Right(()))
    }
    it("binds the first import's class when an earlier line loaded the other one") {
      val src = use("val pre: q.Foo = new q.Foo()", "val f: Foo = new Foo()\n    return f.onlyP() + pre.onlyQ()")
      assert(errorCodesWith(Seq(".", lib), "use.on" -> src) == Right(()))
    }
    it("keeps a default-imported JDK class over a classpath class, loaded or not") {
      // `Timer` is java.util.Timer (a default import) on main; `mypkg.Timer` has no `cancel`
      val before = use("", "val t: Timer = new Timer()\n    t.cancel()\n    return 0", "mypkg.*")
      val after = use("val m: mypkg.Timer = new mypkg.Timer()", "val t: Timer = new Timer()\n    t.cancel()\n    return 0", "mypkg.*")
      assert(errorCodesWith(Seq(".", lib), "use.on" -> before) == Right(()))
      assert(errorCodesWith(Seq(".", lib), "use.on" -> after) == Right(()))
    }
  }
}
