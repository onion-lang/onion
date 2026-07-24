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
