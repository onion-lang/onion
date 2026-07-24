package onion.tools.readiness.docs

import org.scalatest.funspec.AnyFunSpec

import java.nio.file.Paths

class MarkdownExampleExtractorSpec extends AnyFunSpec:
  private val path = Paths.get("docs/example.md")

  describe("MarkdownExampleExtractor.extract"):
    it("extracts all four example kinds and declared output"):
      val markdown =
        """# Examples
          |<!-- onion-example: compile -->
          |```onion
          |val x = 1
          |```
          |
          |<!-- onion-example: run -->
          |```onion
          |println("ok")
          |```
          |<!-- onion-output: stdout -->
          |```text
          |ok
          |```
          |
          |<!-- onion-example: reject code=E0059 -->
          |```onion
          |bad
          |```
          |
          |<!-- onion-example: fragment reason="existing scope" -->
          |```onion
          |value.method()
          |```
          |""".stripMargin

      val result = MarkdownExampleExtractor.extract(path, markdown)

      assert(result.issues.isEmpty)
      assert(result.examples.map(_.kind) == Vector(
        ExampleKind.Compile,
        ExampleKind.Run,
        ExampleKind.Reject("E0059"),
        ExampleKind.Fragment("existing scope")
      ))
      assert(result.examples.map(_.location.line) == Vector(3, 8, 17, 22))
      assert(result.examples.map(_.codeLine) == Vector(4, 9, 18, 23))
      assert(result.examples(1).outputs == Vector(
        ExpectedOutput(OutputChannel.Stdout, "ok\n")
      ))

    it("accepts blank lines between a directive and its fence"):
      val result = MarkdownExampleExtractor.extract(
        path,
        "<!-- onion-example: compile -->\n\n```onion\n1\n```\n"
      )

      assert(result.issues.isEmpty)
      assert(result.examples.map(_.code) == Vector("1\n"))

    it("normalizes CRLF and preserves a nested shorter backtick marker"):
      val result = MarkdownExampleExtractor.extract(
        path,
        "<!-- onion-example: compile -->\r\n" +
          "````onion\r\n```text\r\n````\r\n"
      )

      assert(result.issues.isEmpty)
      assert(result.examples.head.code == "```text\n")

    it("ignores directive-looking lines inside fenced content"):
      val result = MarkdownExampleExtractor.extract(
        path,
        """<!-- onion-example: compile -->
          |````onion
          |<!-- onion-example: run -->
          |```
          |````
          |""".stripMargin
      )

      assert(result.issues.isEmpty)
      assert(result.examples.map(_.kind) == Vector(ExampleKind.Compile))

    it("extracts stdout followed by optional stderr"):
      val markdown =
        """<!-- onion-example: run -->
          |```onion
          |System::err.println("warn")
          |println("ok")
          |```
          |<!-- onion-output: stdout -->
          |```text
          |ok
          |```
          |<!-- onion-output: stderr -->
          |```text
          |warn
          |```
          |""".stripMargin

      val result = MarkdownExampleExtractor.extract(path, markdown)

      assert(result.issues.isEmpty)
      assert(result.examples.head.outputs == Vector(
        ExpectedOutput(OutputChannel.Stdout, "ok\n"),
        ExpectedOutput(OutputChannel.Stderr, "warn\n")
      ))

    it("rejects an unclassified Onion fence"):
      val result = MarkdownExampleExtractor.extract(
        path,
        "```onion\n1\n```\n"
      )
      assert(result.examples.isEmpty)
      assert(result.issues.exists(_.message.contains("unclassified")))

    it("rejects multiple example directives for one fence"):
      val markdown =
        """<!-- onion-example: compile -->
          |<!-- onion-example: run -->
          |```onion
          |println("x")
          |```
          |<!-- onion-output: stdout -->
          |```text
          |x
          |```
          |""".stripMargin
      val result = MarkdownExampleExtractor.extract(path, markdown)
      assert(result.examples.isEmpty)
      assert(result.issues.exists(_.message.contains("multiple")))

    it("reports a malformed directive without a duplicate unclassified issue"):
      val markdown =
        """<!-- onion-example: execute -->
          |```onion
          |1
          |```
          |""".stripMargin
      val result = MarkdownExampleExtractor.extract(path, markdown)
      assert(result.examples.isEmpty)
      assert(result.issues.size == 1)
      assert(result.issues.head.message.contains("malformed"))

    it("rejects an orphaned example directive"):
      val result = MarkdownExampleExtractor.extract(
        path,
        "<!-- onion-example: compile -->\nordinary text\n"
      )
      assert(result.issues.exists(_.message.contains("orphaned")))

    it("does not attach output declarations to non-run examples"):
      val markdown =
        """<!-- onion-example: compile -->
          |```onion
          |val x = 1
          |```
          |<!-- onion-output: stdout -->
          |```text
          |ignored
          |```
          |""".stripMargin
      val result = MarkdownExampleExtractor.extract(path, markdown)
      assert(result.examples.map(_.outputs) == Vector(Vector.empty))
      assert(result.issues.exists(_.message.contains("orphaned")))

    it("requires stdout as the first run output"):
      val markdown =
        """<!-- onion-example: run -->
          |```onion
          |println("x")
          |```
          |<!-- onion-output: stderr -->
          |```text
          |x
          |```
          |""".stripMargin
      val result = MarkdownExampleExtractor.extract(path, markdown)
      assert(result.issues.exists(_.message.contains("stdout")))

    it("requires each output directive to be followed by a text fence"):
      val markdown =
        """<!-- onion-example: run -->
          |```onion
          |println("x")
          |```
          |<!-- onion-output: stdout -->
          |```json
          |"x"
          |```
          |""".stripMargin
      val result = MarkdownExampleExtractor.extract(path, markdown)
      assert(result.issues.exists(_.message.contains("text fence")))

    it("rejects duplicate and orphaned output directives"):
      val markdown =
        """<!-- onion-example: run -->
          |```onion
          |println("x")
          |```
          |<!-- onion-output: stdout -->
          |```text
          |x
          |```
          |<!-- onion-output: stdout -->
          |```text
          |again
          |```
          |""".stripMargin
      val result = MarkdownExampleExtractor.extract(path, markdown)
      assert(result.issues.exists(issue =>
        issue.message.contains("duplicate") ||
          issue.message.contains("orphaned")
      ))

    it("reports unsupported output streams at their source line"):
      val markdown =
        """<!-- onion-example: run -->
          |```onion
          |println("x")
          |```
          |<!-- onion-output: stdio -->
          |```text
          |x
          |```
          |""".stripMargin
      val result = MarkdownExampleExtractor.extract(path, markdown)
      assert(result.issues.exists(issue =>
        issue.location.line == 5 && issue.message.contains("malformed")
      ))

    it("rejects an unterminated Onion fence"):
      val result = MarkdownExampleExtractor.extract(
        path,
        "<!-- onion-example: compile -->\n```onion\n1\n"
      )
      assert(result.issues.exists(_.message.contains("unterminated")))
