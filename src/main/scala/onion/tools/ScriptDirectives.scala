package onion.tools

import scala.util.boundary
import scala.util.boundary.break

import onion.tools.project.{Dependency, ProjectManifest}

/**
 * `//> using` directives: the Maven dependencies a single-file script declares for itself.
 *
 * {{{
 * //> using dep "org.apache.poi:poi-ooxml:5.5.1"
 * //> using repository "https://nexus.example.com/repository/maven-public"
 * }}}
 *
 * The syntax is scala-cli's, because that is where a JVM script author has already seen it.
 * A project states its dependencies in `onion.toml`; a script had no way to at all (only
 * `-classpath`), which meant a `tool` using a library could not be run as a script, and so
 * could not get `--help` or `--plan` there either.
 *
 * Directives are read only from the **leading comment block**: an optional `#!` line, then
 * blank lines, `//` line comments and `/* */` block comments, up to the first token of code.
 * That is the part of the file a reader scans for "what does this need", and it keeps the
 * scan from depending on the Onion lexer. Nothing in `//>` form is ever ignored:
 *
 *  - in the leading block, a `//>` line must be a well-formed, supported directive;
 *  - after it, a `//> using` line is an error saying it must move up, rather than a comment
 *    that silently does nothing.
 */
object ScriptDirectives {

  /** Everything the directives declared. `repositories` keeps declaration order: it is precedence. */
  final case class Directives(dependencies: Seq[Dependency], repositories: Seq[String]) {
    def isEmpty: Boolean = dependencies.isEmpty
  }

  object Directives {
    val empty: Directives = Directives(Seq.empty, Seq.empty)
  }

  /** A directive that could not be accepted, at a 1-based line and column of the script. */
  final case class DirectiveError(line: Int, column: Int, message: String) {
    def render(file: String): String = s"$file:$line:$column: error: $message"
  }

  private val DependencyKeys = Set("dep", "deps")
  private val RepositoryKeys = Set("repository", "repositories")

  /** A `//> using` line anywhere: what the after-the-leading-block check looks for. */
  private val UsingLine = raw"""^[ \t]*//>[ \t]*using\b.*$$""".r

  def parse(text: String): Either[DirectiveError, Directives] = boundary {
    val lines = splitLines(text)
    val (leading, firstCodeLine) = leadingBlock(lines)
    val dependencies = scala.collection.mutable.ArrayBuffer[(Dependency, Int, Int)]()
    val repositories = scala.collection.mutable.ArrayBuffer[String]()

    for ((lineIndex, column) <- leading) {
      parseDirective(lines(lineIndex), lineIndex + 1, column) match {
        case Left(error) => break(Left(error))
        case Right((key, values)) =>
          if (DependencyKeys.contains(key)) {
            for ((value, valueColumn) <- values) {
              parseDependency(value) match {
                case Left(message) => break(Left(DirectiveError(lineIndex + 1, valueColumn, message)))
                case Right(dependency) => dependencies += ((dependency, lineIndex + 1, valueColumn))
              }
            }
          } else {
            for ((value, valueColumn) <- values) {
              if (!ProjectManifest.validRepository(value))
                break(Left(DirectiveError(lineIndex + 1, valueColumn,
                  s"Invalid repository URL: $value (expected an absolute http, https or file URL)")))
              if (!repositories.contains(value)) repositories += value
            }
          }
      }
    }

    // A second version of the same module is a contradiction, not a preference; say so rather
    // than letting the resolver pick one.
    val byModule = scala.collection.mutable.LinkedHashMap[(String, String), Dependency]()
    for ((dependency, line, column) <- dependencies) {
      val module = (dependency.group, dependency.artifact)
      byModule.get(module) match {
        case Some(previous) if previous.version != dependency.version =>
          break(Left(DirectiveError(line, column,
            s"${dependency.group}:${dependency.artifact} is declared twice, " +
              s"at ${previous.version} and ${dependency.version}")))
        case Some(_) => ()
        case None => byModule(module) = dependency
      }
    }

    firstCodeLine.foreach { start =>
      (start until lines.length).find(i => UsingLine.matches(lines(i))).foreach { i =>
        break(Left(DirectiveError(i + 1, lines(i).indexOf("//>") + 1,
          "`//> using` directives must come before any code, in the comment block at the top of the script")))
      }
    }

    Right(Directives(byModule.values.toSeq, repositories.toSeq))
  }

  private def splitLines(text: String): IndexedSeq[String] =
    text.stripPrefix("﻿").split("\r\n|\r|\n", -1).toIndexedSeq

  /**
   * The `//>` lines of the leading comment block, as (line index, column of `//>`), and the
   * index of the first line holding code (None when the file is all comments).
   */
  private def leadingBlock(lines: IndexedSeq[String]): (Seq[(Int, Int)], Option[Int]) = {
    val directives = scala.collection.mutable.ArrayBuffer[(Int, Int)]()
    var inBlockComment = false
    var i = 0
    while (i < lines.length) {
      val line = lines(i)
      if (i == 0 && line.startsWith("#!")) {
        i += 1
      } else {
        // Walk the line: it may hold the end of a block comment, several comments, or code.
        var pos = 0
        while (pos < line.length) {
          if (inBlockComment) {
            val end = line.indexOf("*/", pos)
            if (end < 0) pos = line.length
            else { inBlockComment = false; pos = end + 2 }
          } else {
            val c = line.charAt(pos)
            if (c == ' ' || c == '\t' || c == '\f') pos += 1
            else if (line.startsWith("//>", pos)) {
              directives += ((i, pos + 1))
              pos = line.length
            } else if (line.startsWith("//", pos)) pos = line.length
            else if (line.startsWith("/*", pos)) { inBlockComment = true; pos += 2 }
            else return (directives.toSeq, Some(i))
          }
        }
        i += 1
      }
    }
    (directives.toSeq, None)
  }

  /** `//> using <key> <value>...` into the key and its values, each with a 1-based column. */
  private def parseDirective(line: String, lineNumber: Int, column: Int): Either[DirectiveError, (String, Seq[(String, Int)])] = {
    val start = column - 1 + 3 // past `//>`
    tokenize(line, start) match {
      case Left((message, at)) => Left(DirectiveError(lineNumber, at + 1, message))
      case Right(tokens) =>
        val malformed = DirectiveError(lineNumber, column,
          "Malformed directive; expected `//> using dep \"group:artifact:version\"` or `//> using repository \"url\"`")
        tokens.toList match {
          case Nil => Left(malformed)
          case (word, at) :: _ if word != "using" =>
            Left(DirectiveError(lineNumber, at + 1, s"Unknown directive `$word`; only `//> using ...` is supported"))
          case _ :: Nil => Left(malformed)
          case _ :: (key, keyAt) :: values =>
            if (!DependencyKeys.contains(key) && !RepositoryKeys.contains(key))
              Left(DirectiveError(lineNumber, keyAt + 1,
                s"Unsupported directive `using $key`; an Onion script supports `using dep` and `using repository`"))
            else if (values.isEmpty)
              Left(DirectiveError(lineNumber, keyAt + 1, s"`using $key` needs at least one value"))
            else Right((key, values.map { case (v, at) => (v, at + 1) }))
        }
    }
  }

  /**
   * Whitespace-separated tokens from `from` on, each with its 0-based start. A token is a
   * `"double-quoted"` string (no escapes) or a bare run of non-whitespace. A bare token that
   * starts with `//` begins a trailing comment, which ends the line; `//` inside a bare token
   * (as in `https://...`) does not.
   */
  private def tokenize(line: String, from: Int): Either[(String, Int), Seq[(String, Int)]] = {
    val tokens = scala.collection.mutable.ArrayBuffer[(String, Int)]()
    var pos = from
    while (pos < line.length) {
      val c = line.charAt(pos)
      if (c == ' ' || c == '\t') pos += 1
      else if (line.startsWith("//", pos)) pos = line.length
      else if (c == '"') {
        val end = line.indexOf('"', pos + 1)
        if (end < 0) return Left(("Unterminated string in directive", pos))
        val value = line.substring(pos + 1, end)
        if (value.isEmpty) return Left(("Empty value in directive", pos))
        tokens += ((value, pos))
        pos = end + 1
        if (pos < line.length && !Character.isWhitespace(line.charAt(pos)))
          return Left(("Expected whitespace after a quoted value", pos))
      } else {
        var end = pos
        while (end < line.length && !Character.isWhitespace(line.charAt(end))) end += 1
        tokens += ((line.substring(pos, end), pos))
        pos = end
      }
    }
    Right(tokens.toSeq)
  }

  /** `group:artifact:version`, with an exact version. */
  private[tools] def parseDependency(value: String): Either[String, Dependency] = {
    if (value.contains("::"))
      Left(s"Invalid dependency coordinate: $value (`::` is scala-cli's Scala cross-version form; " +
        "an Onion script names the full artifact, e.g. \"org.example:lib_3:1.0.0\")")
    else value.split(":", -1) match {
      case Array(group, artifact, version) if group.nonEmpty && artifact.nonEmpty && version.nonEmpty =>
        if (exactVersion(version)) Right(Dependency(group, artifact, version))
        else Left(s"Dependency version must be exact: $value (no ranges, no `latest`, no `+`)")
      case _ =>
        Left(s"Invalid dependency coordinate: $value (expected \"group:artifact:version\")")
    }
  }

  /**
   * A version coursier will not treat as a range or a moving target: no interval brackets or
   * commas (`[1.0,2.0)`), no Ivy-style `1.+`, and none of the `latest.*`/`LATEST`/`RELEASE`
   * aliases. A script has no lock file, so a range would change what it runs against silently.
   */
  private[tools] def exactVersion(version: String): Boolean = {
    val lower = version.toLowerCase
    version.nonEmpty &&
      !version.exists(c => Character.isWhitespace(c) || "[](),".indexOf(c) >= 0) &&
      !version.endsWith("+") &&
      !lower.startsWith("latest") &&
      lower != "release"
  }
}
