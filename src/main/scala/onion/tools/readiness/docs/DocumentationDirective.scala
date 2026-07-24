package onion.tools.readiness.docs

enum DocumentationDirective:
  case Example(kind: ExampleKind)
  case Output(channel: OutputChannel)

object DocumentationDirective:
  private val ExampleDirective =
    """<!--\s*onion-example:.*""".r
  private val OutputDirective =
    """<!--\s*onion-output:.*""".r
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
      case value if looksLikeExample(line) || looksLikeOutput(line) =>
        Some(Left(DocumentationIssue(
          location,
          s"unsupported or malformed Onion documentation directive: $value"
        )))
      case _ => None

  private[docs] def looksLikeExample(line: String): Boolean =
    ExampleDirective.matches(line.trim)

  private[docs] def looksLikeOutput(line: String): Boolean =
    OutputDirective.matches(line.trim)
