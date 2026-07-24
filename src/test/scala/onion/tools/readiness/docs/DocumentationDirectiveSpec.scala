package onion.tools.readiness.docs

import org.scalatest.funspec.AnyFunSpec

import java.nio.file.Paths

class DocumentationDirectiveSpec extends AnyFunSpec:
  private val location = MarkdownLocation(Paths.get("docs/example.md"), 7)

  describe("DocumentationDirective.parse"):
    it("parses every supported directive"):
      val cases = Vector(
        "<!-- onion-example: compile -->" ->
          DocumentationDirective.Example(ExampleKind.Compile),
        "<!-- onion-example: run -->" ->
          DocumentationDirective.Example(ExampleKind.Run),
        "<!-- onion-example: reject code=E0059 -->" ->
          DocumentationDirective.Example(ExampleKind.Reject("E0059")),
        """<!-- onion-example: fragment reason="existing scope" -->""" ->
          DocumentationDirective.Example(
            ExampleKind.Fragment("existing scope")
          ),
        "<!-- onion-output: stdout -->" ->
          DocumentationDirective.Output(OutputChannel.Stdout),
        "<!-- onion-output: stderr -->" ->
          DocumentationDirective.Output(OutputChannel.Stderr)
      )

      cases.foreach { case (line, expected) =>
        assert(
          DocumentationDirective.parse(line, location).contains(Right(expected))
        )
      }

    it("allows surrounding indentation but not invented syntax"):
      assert(
        DocumentationDirective.parse(
          "  <!-- onion-example: compile -->  ",
          location
        ).contains(Right(
          DocumentationDirective.Example(ExampleKind.Compile)
        ))
      )

    it("ignores unrelated Markdown and HTML comments"):
      assert(DocumentationDirective.parse("# Heading", location).isEmpty)
      assert(
        DocumentationDirective.parse("<!-- ordinary comment -->", location)
          .isEmpty
      )

    it("rejects malformed and unknown Onion directives"):
      val malformed = Vector(
        "<!-- onion-example: reject -->",
        "<!-- onion-example: reject code=59 -->",
        "<!-- onion-example: reject code=E59 -->",
        """<!-- onion-example: fragment reason="" -->""",
        "<!-- onion-example: execute -->",
        "<!--  onion-example: execute -->",
        "<!-- onion-output: stdio -->",
        "<!-- onion-example: compile",
        "<!-- onion-output: stdout --> trailing text"
      )

      malformed.foreach { line =>
        val issue = DocumentationDirective.parse(line, location)
          .flatMap(_.left.toOption)
          .getOrElse(fail(s"expected malformed directive issue for $line"))
        assert(issue.location == location)
        assert(issue.message.contains("unsupported or malformed"))
      }
