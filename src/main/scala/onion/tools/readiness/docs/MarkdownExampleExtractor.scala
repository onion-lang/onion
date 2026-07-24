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
          case Some(Left(_)) =>
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
          case Some(Left(_)) =>
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
