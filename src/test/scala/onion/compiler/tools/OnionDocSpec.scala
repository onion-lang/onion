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
  }
}
