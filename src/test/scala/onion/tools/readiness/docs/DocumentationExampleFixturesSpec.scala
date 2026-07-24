package onion.tools.readiness.docs

import org.scalatest.funspec.AnyFunSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

class DocumentationExampleFixturesSpec extends AnyFunSpec:
  private def fixture(name: String) =
    val resource = getClass.getResource(
      s"/documentation-examples/$name"
    )
    assert(resource != null, s"missing fixture: $name")
    val path = Paths.get(resource.toURI)
    path -> Files.readString(path, StandardCharsets.UTF_8)

  describe("documentation example fixtures"):
    it("extracts a complete valid manifest"):
      val (path, markdown) = fixture("extraction-valid.md")
      val result = MarkdownExampleExtractor.extract(path, markdown)

      assert(result.issues.isEmpty)
      assert(result.examples.size == 4)
      assert(result.examples.map(_.kind) == Vector(
        ExampleKind.Compile,
        ExampleKind.Run,
        ExampleKind.Reject("E0002"),
        ExampleKind.Fragment(
          "uses a value introduced by surrounding prose"
        )
      ))
      assert(result.examples(1).outputs.map(_.channel) == Vector(
        OutputChannel.Stdout,
        OutputChannel.Stderr
      ))

    it("returns every invalid-fixture classification issue"):
      val (path, markdown) = fixture("extraction-invalid.md")
      val result = MarkdownExampleExtractor.extract(path, markdown)

      assert(result.examples.map(_.kind) == Vector(ExampleKind.Run))
      assert(result.issues.exists(_.message.contains("unclassified")))
      assert(result.issues.exists(_.message.contains("missing stdout")))
      assert(result.issues.exists(_.message.contains("malformed")))
