package onion.compiler.tools

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import onion.tools.doc.{DocModel, HtmlWriter, OnionDoc}
import org.scalatest.funspec.AnyFunSpec

class OnionDocSpec extends AnyFunSpec {

  private val source =
    """/**
      | * # Greeter
      | *
      | * A friendly greeter that says hello.
      | */
      |class Greeter {
      |public:
      |  /**
      |   * Greets the given person by name.
      |   *
      |   * @param name the person to greet
      |   * @return a greeting string
      |   */
      |  def greet(name: String): String {
      |    return "Hello, " + name
      |  }
      |}
      |""".stripMargin

  describe("DocModel + HtmlWriter") {
    it("extracts the type, member signature, and rendered doc") {
      val model = DocModel.fromSource(source, "Greeter.on")
      assert(model.types.map(_.name) == List("Greeter"))
      val greeter = model.types.head
      assert(greeter.doc.isDefined)
      assert(greeter.doc.get.body.contains("# Greeter"))
      val method = greeter.methods.find(_.name == "greet").get
      assert(method.signature.contains("def greet(name: String): String"))
      assert(method.doc.get.params.head.arg == "name")

      val outDir = Files.createTempDirectory("oniondoc-test").toFile
      HtmlWriter.write(List(model), outDir)

      val index = new File(outDir, "index.html")
      val typePage = new File(outDir, "Greeter.html")
      assert(index.exists())
      assert(typePage.exists())

      val indexHtml = new String(Files.readAllBytes(index.toPath), StandardCharsets.UTF_8)
      assert(indexHtml.contains("Greeter"))
      assert(indexHtml.contains("Greeter.html"))

      val typeHtml = new String(Files.readAllBytes(typePage.toPath), StandardCharsets.UTF_8)
      // type name present
      assert(typeHtml.contains("Greeter"))
      // rendered heading from the type doc
      assert(typeHtml.contains("<h1>Greeter</h1>"))
      // method signature (HTML-escaped)
      assert(typeHtml.contains("def greet(name: String): String"))
      // rendered @param text
      assert(typeHtml.contains("the person to greet"))
      assert(typeHtml.contains("a greeting string"))
    }

    it("still extracts the parseable types from a file with a syntax error") {
      // DocModel wants a best-effort AST even from broken source: the recovery parse must
      // return the declarations that did parse, structurally intact, not throw and not
      // carry corrupted state (stranded scopes/lexer modes) across the recovery boundary.
      val broken =
        """/**
          | * Fine.
          | */
          |class Good {
          |public:
          |  def ok(): Int { return 1 }
          |}
          |class Broken {
          |public:
          |  def bad(: Int): Int { return 2 }
          |}
          |/**
          | * Also fine.
          | */
          |class AlsoGood {
          |public:
          |  def fine(name: String): String { return name }
          |}
          |""".stripMargin
      val model = DocModel.fromSource(broken, "Broken.on")
      assert(model.types.map(_.name).contains("Good"))
      assert(model.types.map(_.name).contains("AlsoGood"))
      val alsoGood = model.types.find(_.name == "AlsoGood").get
      assert(alsoGood.methods.exists(_.signature.contains("def fine(name: String): String")))
      assert(alsoGood.doc.exists(_.body.contains("Also fine")))
    }

    it("runs end-to-end via OnionDoc.run") {
      val srcDir = Files.createTempDirectory("oniondoc-src").toFile
      val srcFile = new File(srcDir, "Greeter.on")
      Files.write(srcFile.toPath, source.getBytes(StandardCharsets.UTF_8))
      val outDir = Files.createTempDirectory("oniondoc-out").toFile

      val code = OnionDoc.run(Array("-d", outDir.getAbsolutePath, srcFile.getAbsolutePath))
      assert(code == 0)
      assert(new File(outDir, "index.html").exists())
      assert(new File(outDir, "Greeter.html").exists())
    }

    it("returns exit code 1 (not an exception) when a source file does not exist") {
      val outDir = Files.createTempDirectory("oniondoc-out").toFile
      val missing = new File(outDir, "DoesNotExist.on").getAbsolutePath
      val code = OnionDoc.run(Array("-d", outDir.getAbsolutePath, missing))
      assert(code == 1)
    }

    it("returns exit code 1 (not an exception) when a source file fails to parse") {
      // A truncated \\uXXXX escape makes the lexer throw java.lang.Error before any
      // recovery machinery runs (see Parsing.scala's own catch for the same case);
      // DocModel.fromSource has no such guard, so OnionDoc.run must catch it itself.
      val srcDir = Files.createTempDirectory("oniondoc-src").toFile
      val srcFile = new File(srcDir, "Bad.on")
      Files.write(srcFile.toPath, "\\u12".getBytes(StandardCharsets.UTF_8))
      val outDir = Files.createTempDirectory("oniondoc-out").toFile

      val code = OnionDoc.run(Array("-d", outDir.getAbsolutePath, srcFile.getAbsolutePath))
      assert(code == 1)
    }

    it("returns exit code 1 for an unknown option") {
      assert(OnionDoc.run(Array("--bogus")) == 1)
    }

    it("returns exit code 1 when no source files are given") {
      assert(OnionDoc.run(Array()) == 1)
    }

    it("returns exit code 1 when -d is missing its argument") {
      assert(OnionDoc.run(Array("-d")) == 1)
    }

    it("returns exit code 0 for --help without requiring a source file") {
      assert(OnionDoc.run(Array("--help")) == 0)
    }
  }
}
