# Documentation Example Manifest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract a complete, source-located manifest of classified Onion
examples and declared outputs from Markdown, rejecting every malformed,
ambiguous, or unclassified Onion fence.

**Architecture:** A small immutable domain model represents example kinds,
outputs, locations, and extraction issues. A strict directive parser recognizes
only the approved HTML forms. A line-oriented fenced-block extractor associates
directives without attempting to compile or run examples; compiler-backed
execution is the next independently reviewable PR.

**Tech Stack:** Scala 3.3.7, Java NIO `Path`, ScalaTest 3.2.19.

## Global Constraints

- Use Scala 3 indentation style with two spaces and no tabs.
- Add no third-party dependencies.
- Normalize CRLF and CR line endings to LF.
- Recognize backtick fences of length three or greater.
- A closing fence must contain only backticks and be at least as long as its
  opening fence.
- Every `onion` fence must have exactly one valid `onion-example` directive on
  the immediately preceding non-blank line.
- Support only `compile`, `run`, `reject code=Edddd`, and
  `fragment reason="non-empty text"` example directives.
- A `run` example requires a following `stdout` directive and `text` fence.
- A `run` example may additionally declare one following `stderr` directive
  and `text` fence.
- Preserve one final LF for every non-empty fenced block so later execution can
  compare output exactly.
- Report all issues with the Markdown path and one-origin line.
- Do not classify any of the repository's existing 821 Onion fences in this
  PR.
- Keep `DocExamplesCompileSpec` unchanged until repository-wide classification
  and compiler-backed verification replace it in a later PR.

---

### Task 1: Strict documentation directive model

**Files:**
- Create:
  `src/main/scala/onion/tools/readiness/docs/DocumentationExample.scala`
- Create:
  `src/main/scala/onion/tools/readiness/docs/DocumentationDirective.scala`
- Test:
  `src/test/scala/onion/tools/readiness/docs/DocumentationDirectiveSpec.scala`

**Interfaces:**
- Produces:
  `MarkdownLocation`,
  `ExampleKind`,
  `OutputChannel`,
  `ExpectedOutput`,
  `DocumentationExample`,
  `DocumentationIssue`,
  `ExtractionResult`,
  `DocumentationDirective`,
  and `DocumentationDirective.parse`.
- Consumes: Java NIO `Path` and one Markdown source line.

- [x] **Step 1: Write the failing directive tests**

Create `DocumentationDirectiveSpec.scala`:

```scala
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
        "<!-- onion-output: stdio -->"
      )

      malformed.foreach { line =>
        val issue = DocumentationDirective.parse(line, location)
          .flatMap(_.left.toOption)
          .getOrElse(fail(s"expected malformed directive issue for $line"))
        assert(issue.location == location)
        assert(issue.message.contains("unsupported or malformed"))
      }
```

- [x] **Step 2: Run the directive test and verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.docs.DocumentationDirectiveSpec'
```

Expected: compilation fails because the documentation model and parser do not
exist.

- [x] **Step 3: Add the immutable documentation model**

Create `DocumentationExample.scala`:

```scala
package onion.tools.readiness.docs

import java.nio.file.Path

final case class MarkdownLocation(path: Path, line: Int):
  require(line > 0, "Markdown line must be positive")

enum ExampleKind:
  case Compile
  case Run
  case Reject(code: String)
  case Fragment(reason: String)

enum OutputChannel:
  case Stdout
  case Stderr

final case class ExpectedOutput(
  channel: OutputChannel,
  text: String
)

final case class DocumentationExample(
  location: MarkdownLocation,
  codeLine: Int,
  code: String,
  kind: ExampleKind,
  outputs: Vector[ExpectedOutput]
)

final case class DocumentationIssue(
  location: MarkdownLocation,
  message: String
)

final case class ExtractionResult(
  examples: Vector[DocumentationExample],
  issues: Vector[DocumentationIssue]
):
  def isValid: Boolean = issues.isEmpty
```

- [x] **Step 4: Implement the strict directive parser**

Create `DocumentationDirective.scala`:

```scala
package onion.tools.readiness.docs

enum DocumentationDirective:
  case Example(kind: ExampleKind)
  case Output(channel: OutputChannel)

object DocumentationDirective:
  private val Reject =
    """<!--\s+onion-example:\s+reject\s+code=(E\d{4})\s+-->""".r
  private val Fragment =
    """<!--\s+onion-example:\s+fragment\s+reason="([^"]+)"\s+-->""".r

  def parse(
    line: String,
    location: MarkdownLocation
  ): Option[Either[DocumentationIssue, DocumentationDirective]] =
    line.trim match
      case "<!-- onion-example: compile -->" =>
        Some(Right(Example(ExampleKind.Compile)))
      case "<!-- onion-example: run -->" =>
        Some(Right(Example(ExampleKind.Run)))
      case Reject(code) =>
        Some(Right(Example(ExampleKind.Reject(code))))
      case Fragment(reason) if reason.trim.nonEmpty =>
        Some(Right(Example(ExampleKind.Fragment(reason))))
      case "<!-- onion-output: stdout -->" =>
        Some(Right(Output(OutputChannel.Stdout)))
      case "<!-- onion-output: stderr -->" =>
        Some(Right(Output(OutputChannel.Stderr)))
      case value
          if value.startsWith("<!-- onion-example:") ||
            value.startsWith("<!-- onion-output:") =>
        Some(Left(DocumentationIssue(
          location,
          s"unsupported or malformed Onion documentation directive: $value"
        )))
      case _ => None
```

- [x] **Step 5: Run the directive test and verify GREEN**

Run:

```bash
sbt 'testOnly onion.tools.readiness.docs.DocumentationDirectiveSpec'
```

Expected: four example forms, two output forms, unrelated lines, and malformed
forms all pass.

- [x] **Step 6: Commit the directive boundary**

```bash
git add \
  src/main/scala/onion/tools/readiness/docs/DocumentationExample.scala \
  src/main/scala/onion/tools/readiness/docs/DocumentationDirective.scala \
  src/test/scala/onion/tools/readiness/docs/DocumentationDirectiveSpec.scala
git commit -m "Add documentation example directives"
```

---

### Task 2: Source-located fenced-block manifest extraction

**Files:**
- Create:
  `src/main/scala/onion/tools/readiness/docs/MarkdownExampleExtractor.scala`
- Test:
  `src/test/scala/onion/tools/readiness/docs/MarkdownExampleExtractorSpec.scala`

**Interfaces:**
- Consumes:
  `DocumentationDirective.parse` and Markdown text.
- Produces:
  `MarkdownExampleExtractor.extract(path: Path, markdown: String):
  ExtractionResult`.

- [x] **Step 1: Write the failing happy-path extraction tests**

Create `MarkdownExampleExtractorSpec.scala`:

```scala
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
```

- [x] **Step 2: Write failing validation-path extraction tests**

Append these cases to the same `describe` block:

```scala
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
```

- [x] **Step 3: Run the extractor test and verify RED**

Run:

```bash
sbt 'testOnly onion.tools.readiness.docs.MarkdownExampleExtractorSpec'
```

Expected: compilation fails because `MarkdownExampleExtractor` does not exist.

- [x] **Step 4: Implement fence scanning and directive association**

Create `MarkdownExampleExtractor.scala`:

```scala
package onion.tools.readiness.docs

import java.nio.file.Path
import scala.collection.mutable

object MarkdownExampleExtractor:
  private val FenceOpening = """^\s*(`{3,})([^`]*)$""".r

  private final case class Fence(
    language: String,
    content: String,
    openingLine: Int,
    contentLine: Int,
    closingLine: Int
  )

  def extract(path: Path, markdown: String): ExtractionResult =
    val lines = normalize(markdown).split("\n", -1).toVector
    val (fences, fenceIssues) = scanFences(path, lines)
    val directives = scanDirectives(path, lines, fences)
    val examples = Vector.newBuilder[DocumentationExample]
    val issues = Vector.newBuilder[DocumentationIssue]
    val consumedExamples = mutable.Set.empty[Int]
    val consumedOutputs = mutable.Set.empty[Int]
    val fenceByOpening = fences.map(fence => fence.openingLine -> fence).toMap

    issues ++= fenceIssues
    issues ++= directives.values.flatMap(_.left.toOption)

    fences.filter(_.language == "onion").foreach { fence =>
      classificationBefore(
        path,
        lines,
        fence,
        directives,
        consumedExamples,
        issues
      ).foreach { kind =>
        val outputs =
          if kind == ExampleKind.Run then
            outputsAfter(
              path,
              lines,
              fence,
              directives,
              fenceByOpening,
              consumedOutputs,
              issues
            )
          else Vector.empty
        examples += DocumentationExample(
          MarkdownLocation(path, fence.openingLine),
          fence.contentLine,
          fence.content,
          kind,
          outputs
        )
      }
    }

    directives.foreach {
      case (line, Right(DocumentationDirective.Example(_)))
          if !consumedExamples.contains(line) =>
        issues += DocumentationIssue(
          MarkdownLocation(path, line),
          "orphaned Onion example directive"
        )
      case (line, Right(DocumentationDirective.Output(_)))
          if !consumedOutputs.contains(line) =>
        issues += DocumentationIssue(
          MarkdownLocation(path, line),
          "orphaned Onion output directive"
        )
      case _ => ()
    }

    ExtractionResult(examples.result(), issues.result().distinct)

  private def normalize(value: String): String =
    value.replace("\r\n", "\n").replace('\r', '\n')

  private def scanFences(
    path: Path,
    lines: Vector[String]
  ): (Vector[Fence], Vector[DocumentationIssue]) =
    val fences = Vector.newBuilder[Fence]
    val issues = Vector.newBuilder[DocumentationIssue]
    var index = 0
    while index < lines.length do
      lines(index) match
        case FenceOpening(marker, suffix) =>
          val language = suffix.trim
            .split("\\s+")
            .headOption
            .getOrElse("")
            .toLowerCase
          val closingIndex = (index + 1 until lines.length).find { candidate =>
            val trimmed = lines(candidate).trim
            trimmed.length >= marker.length &&
              trimmed.forall(_ == '`')
          }
          closingIndex match
            case Some(close) =>
              val contentLines = lines.slice(index + 1, close)
              val content =
                if contentLines.isEmpty then ""
                else contentLines.mkString("\n") + "\n"
              fences += Fence(
                language,
                content,
                index + 1,
                index + 2,
                close + 1
              )
              index = close + 1
            case None =>
              issues += DocumentationIssue(
                MarkdownLocation(path, index + 1),
                "unterminated Markdown fence"
              )
              index = lines.length
        case _ =>
          index += 1
    (fences.result(), issues.result())

  private def scanDirectives(
    path: Path,
    lines: Vector[String],
    fences: Vector[Fence]
  ): Map[Int, Either[DocumentationIssue, DocumentationDirective]] =
    val fencedLines = fences.flatMap { fence =>
      fence.openingLine to fence.closingLine
    }.toSet
    lines.zipWithIndex.flatMap { case (line, index) =>
      val oneOriginLine = index + 1
      if fencedLines.contains(oneOriginLine) then None
      else
        DocumentationDirective
          .parse(line, MarkdownLocation(path, oneOriginLine))
          .map(oneOriginLine -> _)
    }.toMap

  private def classificationBefore(
    path: Path,
    lines: Vector[String],
    fence: Fence,
    directives: Map[
      Int,
      Either[DocumentationIssue, DocumentationDirective]
    ],
    consumed: mutable.Set[Int],
    issues: mutable.Builder[DocumentationIssue, Vector[DocumentationIssue]]
  ): Option[ExampleKind] =
    previousNonBlank(lines, fence.openingLine - 1) match
      case None =>
        issues += DocumentationIssue(
          MarkdownLocation(path, fence.openingLine),
          "unclassified Onion fence"
        )
        None
      case Some(line) =>
        directives.get(line) match
          case Some(Left(_))
              if lines(line - 1).trim.startsWith("<!-- onion-example:") =>
            None
          case Some(Right(DocumentationDirective.Example(kind))) =>
            previousNonBlank(lines, line - 1)
              .flatMap(directives.get) match
              case Some(Right(DocumentationDirective.Example(_))) =>
                consumed += line
                previousNonBlank(lines, line - 1).foreach(consumed += _)
                issues += DocumentationIssue(
                  MarkdownLocation(path, line),
                  "multiple Onion example directives for one fence"
                )
                None
              case _ =>
                consumed += line
                Some(kind)
          case _ =>
            issues += DocumentationIssue(
              MarkdownLocation(path, fence.openingLine),
              "unclassified Onion fence"
            )
            None

  private def outputsAfter(
    path: Path,
    lines: Vector[String],
    exampleFence: Fence,
    directives: Map[
      Int,
      Either[DocumentationIssue, DocumentationDirective]
    ],
    fenceByOpening: Map[Int, Fence],
    consumed: mutable.Set[Int],
    issues: mutable.Builder[DocumentationIssue, Vector[DocumentationIssue]]
  ): Vector[ExpectedOutput] =
    nextNonBlank(lines, exampleFence.closingLine) match
      case Some(line) =>
        directives.get(line) match
          case Some(Right(DocumentationDirective.Output(
                OutputChannel.Stdout
              ))) =>
            readOutput(
              path,
              lines,
              line,
              OutputChannel.Stdout,
              fenceByOpening,
              consumed,
              issues
            ) match
              case Some((stdout, stdoutFence)) =>
                val stderr = nextNonBlank(lines, stdoutFence.closingLine)
                  .flatMap { next =>
                    directives.get(next) match
                      case Some(Right(DocumentationDirective.Output(
                            OutputChannel.Stderr
                          ))) =>
                        readOutput(
                          path,
                          lines,
                          next,
                          OutputChannel.Stderr,
                          fenceByOpening,
                          consumed,
                          issues
                        ).map(_._1)
                      case Some(Right(DocumentationDirective.Output(
                            OutputChannel.Stdout
                          ))) =>
                        consumed += next
                        issues += DocumentationIssue(
                          MarkdownLocation(path, next),
                          "duplicate stdout output directive"
                        )
                        None
                      case _ => None
                  }
                Vector(stdout) ++ stderr
              case None => Vector.empty
          case Some(Left(_))
              if lines(line - 1).trim.startsWith("<!-- onion-output:") =>
            Vector.empty
          case Some(Right(DocumentationDirective.Output(
                OutputChannel.Stderr
              ))) =>
            consumed += line
            issues += DocumentationIssue(
              MarkdownLocation(path, line),
              "run example must declare stdout before stderr"
            )
            Vector.empty
          case _ =>
            issues += DocumentationIssue(
              MarkdownLocation(path, exampleFence.openingLine),
              "run example is missing stdout output"
            )
            Vector.empty
      case None =>
        issues += DocumentationIssue(
          MarkdownLocation(path, exampleFence.openingLine),
          "run example is missing stdout output"
        )
        Vector.empty

  private def readOutput(
    path: Path,
    lines: Vector[String],
    directiveLine: Int,
    channel: OutputChannel,
    fenceByOpening: Map[Int, Fence],
    consumed: mutable.Set[Int],
    issues: mutable.Builder[DocumentationIssue, Vector[DocumentationIssue]]
  ): Option[(ExpectedOutput, Fence)] =
    consumed += directiveLine
    nextNonBlank(lines, directiveLine) match
      case Some(fenceLine) =>
        fenceByOpening.get(fenceLine) match
          case Some(fence) if fence.language == "text" =>
            Some(ExpectedOutput(channel, fence.content) -> fence)
          case _ =>
            issues += DocumentationIssue(
              MarkdownLocation(path, directiveLine),
              s"${channel.toString.toLowerCase} output must be followed by a text fence"
            )
            None
      case None =>
        issues += DocumentationIssue(
          MarkdownLocation(path, directiveLine),
          s"${channel.toString.toLowerCase} output must be followed by a text fence"
        )
        None

  private def previousNonBlank(
    lines: Vector[String],
    beforeOneOriginLine: Int
  ): Option[Int] =
    (beforeOneOriginLine - 1 to 0 by -1)
      .find(index => lines(index).trim.nonEmpty)
      .map(_ + 1)

  private def nextNonBlank(
    lines: Vector[String],
    afterOneOriginLine: Int
  ): Option[Int] =
    (afterOneOriginLine until lines.length)
      .find(index => lines(index).trim.nonEmpty)
      .map(_ + 1)
```

- [x] **Step 5: Run the extractor tests and verify GREEN**

Run:

```bash
sbt 'testOnly onion.tools.readiness.docs.MarkdownExampleExtractorSpec'
```

Expected: all happy-path and validation-path tests pass.

- [x] **Step 6: Run all docs readiness tests**

Run:

```bash
sbt 'testOnly onion.tools.readiness.docs.*'
```

Expected: directive and extractor suites pass with no warnings.

- [x] **Step 7: Commit the extractor**

```bash
git add \
  src/main/scala/onion/tools/readiness/docs/MarkdownExampleExtractor.scala \
  src/test/scala/onion/tools/readiness/docs/MarkdownExampleExtractorSpec.scala
git commit -m "Extract documentation example manifests"
```

---

### Task 3: Markdown fixture coverage and PR-boundary audit

**Files:**
- Create:
  `src/test/resources/documentation-examples/extraction-valid.md`
- Create:
  `src/test/resources/documentation-examples/extraction-invalid.md`
- Create:
  `src/test/scala/onion/tools/readiness/docs/DocumentationExampleFixturesSpec.scala`
- Modify:
  `docs/superpowers/plans/2026-07-24-documentation-example-manifest.md`

**Interfaces:**
- Consumes: `MarkdownExampleExtractor.extract`.
- Produces: stable synthetic Markdown fixtures for the next execution-verifier
  PR.

- [x] **Step 1: Add the valid fixture**

Create `extraction-valid.md`:

`````markdown
# Documentation verifier fixture

<!-- onion-example: compile -->
```onion
val answer = 42
```

<!-- onion-example: run -->
```onion
System::err.println("warning")
println("hello")
```
<!-- onion-output: stdout -->
```text
hello
```
<!-- onion-output: stderr -->
```text
warning
```

<!-- onion-example: reject code=E0002 -->
```onion
println(undefinedValue)
```

<!-- onion-example: fragment reason="uses a value introduced by surrounding prose" -->
````onion
value.transform()
````
`````

- [x] **Step 2: Add the invalid fixture**

Create `extraction-invalid.md`:

````markdown
# Invalid documentation verifier fixture

```onion
println("unclassified")
```

<!-- onion-example: run -->
```onion
println("missing output")
```

<!-- onion-example: fragment reason="" -->
```onion
value
```

<!-- onion-output: stdio -->
```text
unsupported
```
````

- [x] **Step 3: Write and run the fixture test**

Create `DocumentationExampleFixturesSpec.scala`:

```scala
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
```

Run:

```bash
sbt 'testOnly onion.tools.readiness.docs.DocumentationExampleFixturesSpec'
```

Expected: both fixture cases pass.

- [x] **Step 4: Run the full regression suite in the release locale**

Run:

```bash
sbt -Duser.language=en test
```

Expected: the full suite passes with zero failed, canceled, ignored, or pending
tests.

- [x] **Step 5: Mark plan progress and commit the fixtures and plan**

Mark every completed checkbox in this plan, then:

```bash
git add \
  docs/superpowers/plans/2026-07-24-documentation-example-manifest.md \
  src/test/resources/documentation-examples/extraction-valid.md \
  src/test/resources/documentation-examples/extraction-invalid.md \
  src/test/scala/onion/tools/readiness/docs/DocumentationExampleFixturesSpec.scala
git commit -m "Cover documentation manifest fixtures"
```

- [x] **Step 6: Audit the PR boundary against the approved design**

Verify from the committed diff and fresh test output that:

- all four approved example directives parse;
- stdout and optional stderr declarations attach only to `run`;
- malformed, duplicate, orphaned, and unclassified directives fail with
  source locations;
- CRLF and longer backtick fences behave deterministically;
- no existing repository documentation was silently classified;
- `DocExamplesCompileSpec` remains intact; and
- the next PR can consume `ExtractionResult` to run `compile`, `run`, and
  `reject` examples in isolated worker JVMs.
