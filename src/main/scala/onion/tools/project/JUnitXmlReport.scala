package onion.tools.project

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

/**
 * Writes a test run as JUnit XML.
 *
 * `onion test` printed `N tests, N passed, N failed` and returned an exit code, which
 * tells CI *that* something broke and never *what*. Every CI system on the JVM reads this
 * format, so producing it is what turns a red build into an annotated diff.
 *
 * The escaping is the part that has to be right rather than approximately right. A test's
 * captured output is arbitrary — it can contain `&`, `<`, a stray `]]>`, or a raw control
 * character from a program writing bytes — and an XML file the CI parser rejects is worse
 * than no file at all, because the failure then looks like a broken pipeline instead of a
 * failing test.
 */
object JUnitXmlReport:

  /** Writes the report, creating parent directories as needed. */
  def write(result: TestRunResult, path: Path, suiteName: String): Either[ProjectError, Unit] =
    try
      Option(path.getParent).foreach(Files.createDirectories(_))
      Files.writeString(path, render(result, suiteName), UTF_8)
      Right(())
    catch
      case error: IOException =>
        Left(ProjectError(s"Could not write the test report to $path: ${error.getMessage}", Some(error)))

  private[project] def render(result: TestRunResult, suiteName: String): String =
    val body = new StringBuilder
    body ++= """<?xml version="1.0" encoding="UTF-8"?>""" + "\n"
    body ++= s"""<testsuite name="${attribute(suiteName)}" tests="${result.cases.size}"""" +
      s""" failures="${result.failed}" errors="0" skipped="0">""" + "\n"

    result.cases.foreach { testCase =>
      // The class name is the source path and the name is the file, so a CI report groups
      // by directory the way it would for any other suite.
      val name = fileName(testCase.source)
      body ++= s"""  <testcase classname="${attribute(testCase.source)}" name="${attribute(name)}">""" + "\n"
      testCase.failure.foreach { message =>
        val summary = message.linesIterator.nextOption().getOrElse(message)
        body ++= s"""    <failure message="${attribute(summary)}">${text(message)}</failure>""" + "\n"
      }
      if testCase.stdout.nonEmpty then
        body ++= s"""    <system-out>${text(testCase.stdout)}</system-out>""" + "\n"
      if testCase.stderr.nonEmpty then
        body ++= s"""    <system-err>${text(testCase.stderr)}</system-err>""" + "\n"
      body ++= "  </testcase>\n"
    }

    body ++= "</testsuite>\n"
    body.toString

  private def fileName(source: String): String =
    val slash = source.lastIndexOf('/')
    if slash < 0 then source else source.substring(slash + 1)

  /** Escapes for an attribute value, where quotes matter as well as markup. */
  private def attribute(value: String): String =
    escape(value).replace("\"", "&quot;").replace("'", "&apos;")
      // A newline inside an attribute is legal but is normalised to a space by every
      // parser, so collapse it here rather than let the file say one thing and the
      // reader see another.
      .replace("\n", " ").replace("\r", " ")

  /** Escapes for element content. */
  private def text(value: String): String = escape(value)

  private def escape(value: String): String =
    val out = new StringBuilder(value.length)
    value.foreach { c =>
      c match
        case '&' => out ++= "&amp;"
        case '<' => out ++= "&lt;"
        case '>' => out ++= "&gt;"
        case _ if isXmlValid(c) => out += c
        case _ =>
          // XML 1.0 has no way to represent most control characters, not even as a
          // numeric reference. A test that writes one would otherwise produce a file the
          // CI parser rejects outright.
          out += '�'
    }
    out.toString

  /** The characters XML 1.0 allows; tab, newline and carriage return are the exceptions. */
  private def isXmlValid(c: Char): Boolean =
    c == '\t' || c == '\n' || c == '\r' ||
      (c >= ' ' && c <= '퟿') ||
      (c >= '' && c <= '�')
