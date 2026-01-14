/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.      *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import onion.compiler._
import onion.compiler.CompilationOutcome.{Failure, Success}
import org.jline.reader._
import org.jline.reader.impl.DefaultParser
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.{Terminal, TerminalBuilder}
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle

import java.io.StringReader
import java.nio.file.Paths
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

/**
 * Interactive REPL for Onion language with JLine3 support.
 * Features: tab completion, history, syntax highlighting, REPL commands.
 */
object Repl {
  val VERSION = "1.0.0"
  val PROMPT = "onion> "
  val CONTINUATION_PROMPT = "     | "

  // ANSI color codes
  object Colors {
    val RESET = "\u001b[0m"
    val BOLD = "\u001b[1m"
    val RED = "\u001b[31m"
    val GREEN = "\u001b[32m"
    val YELLOW = "\u001b[33m"
    val BLUE = "\u001b[34m"
    val MAGENTA = "\u001b[35m"
    val CYAN = "\u001b[36m"
  }

  // Onion language keywords for completion
  val KEYWORDS: Array[String] = Array(
    "class", "interface", "abstract", "final", "static", "public", "private", "protected",
    "def", "var", "val", "if", "else", "while", "for", "return", "break", "continue",
    "new", "this", "super", "null", "true", "false", "import", "package",
    "try", "catch", "finally", "throw", "throws", "enum", "record", "sealed",
    "select", "case", "otherwise", "extends", "implements"
  )

  // Built-in types
  val TYPES: Array[String] = Array(
    "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String", "Unit", "Void",
    "Object", "Array", "List", "Map", "Set", "Option", "Result"
  )

  // REPL commands
  val COMMANDS: Array[String] = Array(
    ":help", ":quit", ":exit", ":clear", ":history", ":type", ":ast", ":typed", ":reset", ":paste"
  )

  def main(args: Array[String]): Unit = {
    new Repl(Seq(".")).run()
  }
}

class Repl(classpath: Seq[String]) {
  import Repl._

  private val encoding = Option(System.getenv("ONION_ENCODING"))
    .getOrElse(java.nio.charset.Charset.defaultCharset().name())
  private val config = new CompilerConfig(classpath, null, encoding, "", 10)
  private val shell = Shell(classpath)
  private val history = ArrayBuffer[String]()
  private var sessionCounter = 0

  def run(): Unit = {
    val terminal = TerminalBuilder.builder()
      .name("Onion REPL")
      .system(true)
      .build()

    val completer = new OnionCompleter()
    val highlighter = new OnionHighlighter()

    val reader = LineReaderBuilder.builder()
      .terminal(terminal)
      .completer(completer)
      .highlighter(highlighter)
      .parser(new MultilineParser())
      .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".onion_history"))
      .variable(LineReader.HISTORY_SIZE, 1000)
      .option(LineReader.Option.HISTORY_BEEP, false)
      .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
      .build()

    printBanner()

    var running = true
    while (running) {
      try {
        val line = reader.readLine(PROMPT)
        if (line != null) {
          val trimmed = line.trim
          if (trimmed.nonEmpty) {
            history += trimmed
            running = processInput(trimmed, terminal)
          }
        }
      } catch {
        case _: UserInterruptException =>
          println("\n" + Colors.YELLOW + "Use :quit or Ctrl-D to exit" + Colors.RESET)
        case _: EndOfFileException =>
          println(Colors.GREEN + "\nGoodbye!" + Colors.RESET)
          running = false
      }
    }

    terminal.close()
  }

  private def printBanner(): Unit = {
    println(s"""${Colors.CYAN}${Colors.BOLD}
    |   ____        _
    |  / __ \\____  (_)___  ____
    | / / / / __ \\/ / __ \\/ __ \\
    |/ /_/ / / / / / /_/ / / / /
    |\\____/_/ /_/_/\\____/_/ /_/
    |${Colors.RESET}
    |${Colors.GREEN}Onion REPL v$VERSION${Colors.RESET}
    |Type ${Colors.YELLOW}:help${Colors.RESET} for available commands.
    |""".stripMargin)
  }

  private def processInput(input: String, terminal: Terminal): Boolean = {
    if (input.startsWith(":")) {
      processCommand(input, terminal)
    } else {
      executeCode(input)
      true
    }
  }

  private def processCommand(input: String, terminal: Terminal): Boolean = {
    val parts = input.split("\\s+", 2)
    val cmd = parts(0).toLowerCase
    val arg = if (parts.length > 1) Some(parts(1)) else None

    cmd match {
      case ":quit" | ":exit" | ":q" =>
        println(Colors.GREEN + "Goodbye!" + Colors.RESET)
        false

      case ":help" | ":h" | ":?" =>
        printHelp()
        true

      case ":clear" | ":cls" =>
        terminal.writer().print("\u001b[2J\u001b[H")
        terminal.writer().flush()
        true

      case ":history" =>
        history.zipWithIndex.foreach { case (line, idx) =>
          println(f"${Colors.CYAN}${idx + 1}%4d${Colors.RESET} $line")
        }
        true

      case ":type" | ":t" =>
        arg match {
          case Some(expr) => showType(expr)
          case None => println(Colors.RED + "Usage: :type <expression>" + Colors.RESET)
        }
        true

      case ":ast" =>
        arg match {
          case Some(expr) => showAst(expr)
          case None => println(Colors.RED + "Usage: :ast <expression>" + Colors.RESET)
        }
        true

      case ":typed" =>
        arg match {
          case Some(expr) => showTyped(expr)
          case None => println(Colors.RED + "Usage: :typed <expression>" + Colors.RESET)
        }
        true

      case ":reset" =>
        sessionCounter = 0
        println(Colors.GREEN + "Session reset." + Colors.RESET)
        true

      case ":paste" | ":p" =>
        println(Colors.YELLOW + "Entering paste mode (Ctrl-D to finish):" + Colors.RESET)
        val lines = ArrayBuffer[String]()
        var reading = true
        val reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))
        while (reading) {
          val line = reader.readLine()
          if (line == null) {
            reading = false
          } else {
            lines += line
          }
        }
        if (lines.nonEmpty) {
          println(Colors.YELLOW + "Executing pasted code..." + Colors.RESET)
          executeCode(lines.mkString("\n"))
        }
        true

      case _ =>
        println(Colors.RED + s"Unknown command: $cmd" + Colors.RESET)
        println("Type :help for available commands.")
        true
    }
  }

  private def printHelp(): Unit = {
    println(s"""${Colors.BOLD}Available Commands:${Colors.RESET}
    |  ${Colors.YELLOW}:help${Colors.RESET}, :h, :?     Show this help message
    |  ${Colors.YELLOW}:quit${Colors.RESET}, :exit, :q  Exit the REPL
    |  ${Colors.YELLOW}:clear${Colors.RESET}, :cls      Clear the screen
    |  ${Colors.YELLOW}:history${Colors.RESET}          Show command history
    |  ${Colors.YELLOW}:type${Colors.RESET} <expr>      Show the type of an expression
    |  ${Colors.YELLOW}:ast${Colors.RESET} <expr>       Show the parsed AST
    |  ${Colors.YELLOW}:typed${Colors.RESET} <expr>     Show the typed AST summary
    |  ${Colors.YELLOW}:reset${Colors.RESET}            Reset the session
    |  ${Colors.YELLOW}:paste${Colors.RESET}, :p        Enter paste mode
    |
    |${Colors.BOLD}Keyboard Shortcuts:${Colors.RESET}
    |  ${Colors.CYAN}Tab${Colors.RESET}                 Auto-complete
    |  ${Colors.CYAN}Ctrl-R${Colors.RESET}              Search history
    |  ${Colors.CYAN}Ctrl-C${Colors.RESET}              Cancel current input
    |  ${Colors.CYAN}Ctrl-D${Colors.RESET}              Exit REPL
    |  ${Colors.CYAN}Up/Down${Colors.RESET}             Navigate history
    |""".stripMargin)
  }

  private def executeCode(code: String): Unit = {
    sessionCounter += 1
    val wrappedCode = wrapCode(code, sessionCounter)
    val fileName = s"repl_$sessionCounter.on"

    val compiler = new OnionCompiler(config)
    val outcome = compiler.compile(Seq(new StreamInputSource(new StringReader(wrappedCode), fileName)))

    outcome match {
      case Success(classes) =>
        shell.run(classes, Array()) match {
          case Shell.Success(result) =>
            if (result != null && result != ()) {
              println(s"${Colors.GREEN}res$sessionCounter${Colors.RESET} = $result")
            }
          case Shell.Failure(_) =>
            // Runtime error handled by exception
        }
      case Failure(errors) =>
        // Try without wrapping (might be a complete program)
        val outcome2 = compiler.compile(Seq(new StreamInputSource(new StringReader(code), fileName)))
        outcome2 match {
          case Success(classes) =>
            shell.run(classes, Array())
          case Failure(errors2) =>
            CompilationReporter.printErrors(errors)
        }
    }
  }

  private def wrapCode(code: String, id: Int): String = {
    // Check if it looks like a complete program
    if (code.contains("class ") || code.contains("def main") || code.contains("interface ")) {
      code
    } else {
      // Wrap as a script expression
      s"""class Repl$id {
         |public:
         |  static def main(args: String[]): Unit {
         |    $code
         |  }
         |}""".stripMargin
    }
  }

  private def showType(expr: String): Unit = {
    // For now, just compile and extract type from error message or success
    // A more sophisticated implementation would use the type checker directly
    val code = s"""class TypeCheck {
      |public:
      |  static def main(args: String[]): Unit {
      |    val __result__ = $expr
      |    IO::println(__result__)
      |  }
      |}""".stripMargin

    val compiler = new OnionCompiler(config)
    val outcome = compiler.compile(Seq(new StreamInputSource(new StringReader(code), "typecheck.on")))

    outcome match {
      case Success(_) =>
        println(s"${Colors.CYAN}Expression compiles successfully${Colors.RESET}")
        println("(Full type inference requires direct type checker access)")
      case Failure(errors) =>
        CompilationReporter.printErrors(errors)
    }
  }

  private def showAst(expr: String): Unit = {
    val code = wrapCode(expr, sessionCounter + 1)
    val fileName = s"repl_ast_${sessionCounter + 1}.on"
    try {
      val parsing = new Parsing(config)
      val parsed = parsing.process(Seq(new StreamInputSource(new StringReader(code), fileName)))
      DiagnosticsPrinter.dumpAst(parsed)
    } catch {
      case e: onion.compiler.exceptions.CompilationException =>
        CompilationReporter.printErrors(e.problems.toIndexedSeq)
    }
  }

  private def showTyped(expr: String): Unit = {
    val code = wrapCode(expr, sessionCounter + 1)
    val fileName = s"repl_typed_${sessionCounter + 1}.on"
    try {
      val parsing = new Parsing(config)
      val rewriting = new Rewriting(config)
      val typing = new Typing(config)
      val parsed = parsing.process(Seq(new StreamInputSource(new StringReader(code), fileName)))
      val rewritten = rewriting.process(parsed)
      val typed = typing.process(rewritten)
      DiagnosticsPrinter.dumpTyped(typed)
    } catch {
      case e: onion.compiler.exceptions.CompilationException =>
        CompilationReporter.printErrors(e.problems.toIndexedSeq)
    }
  }

  /**
   * Custom completer for Onion language
   */
  private class OnionCompleter extends Completer {
    override def complete(reader: LineReader, line: ParsedLine, candidates: java.util.List[Candidate]): Unit = {
      val word = line.word()
      val wordLower = word.toLowerCase

      // Commands
      if (word.startsWith(":")) {
        COMMANDS.filter(_.startsWith(word)).foreach { cmd =>
          candidates.add(new Candidate(cmd, cmd, null, null, null, null, true))
        }
      } else {
        // Keywords
        KEYWORDS.filter(_.toLowerCase.startsWith(wordLower)).foreach { kw =>
          candidates.add(new Candidate(kw, kw, "keyword", null, null, null, true))
        }

        // Types
        TYPES.filter(_.toLowerCase.startsWith(wordLower)).foreach { t =>
          candidates.add(new Candidate(t, t, "type", null, null, null, true))
        }

        // Common IO methods
        if (word.startsWith("IO::") || wordLower.startsWith("io::")) {
          val ioMethods = Array("println", "print", "readLine")
          ioMethods.foreach { m =>
            candidates.add(new Candidate(s"IO::$m", s"IO::$m", "IO method", null, null, null, true))
          }
        }
      }
    }
  }

  /**
   * Syntax highlighter for Onion code
   */
  private class OnionHighlighter extends Highlighter {
    private val keywordSet = KEYWORDS.toSet
    private val typeSet = TYPES.toSet

    override def highlight(reader: LineReader, buffer: String): org.jline.utils.AttributedString = {
      val builder = new AttributedStringBuilder()
      val tokens = tokenize(buffer)

      tokens.foreach { token =>
        if (token.startsWith(":")) {
          // REPL command - yellow
          builder.styled(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW), token)
        } else if (keywordSet.contains(token)) {
          // Keyword - blue bold
          builder.styled(AttributedStyle.BOLD.foreground(AttributedStyle.BLUE), token)
        } else if (typeSet.contains(token)) {
          // Type - cyan
          builder.styled(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN), token)
        } else if (token.matches("\".*\"")) {
          // String - green
          builder.styled(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN), token)
        } else if (token.matches("\\d+")) {
          // Number - magenta
          builder.styled(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA), token)
        } else if (token == "//" || token.startsWith("//")) {
          // Comment - gray
          builder.styled(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT), token)
        } else {
          builder.append(token)
        }
      }

      builder.toAttributedString
    }

    override def setErrorPattern(errorPattern: java.util.regex.Pattern): Unit = {}
    override def setErrorIndex(errorIndex: Int): Unit = {}

    private def tokenize(input: String): Seq[String] = {
      val tokens = ArrayBuffer[String]()
      val current = new StringBuilder()
      var inString = false
      var i = 0

      while (i < input.length) {
        val c = input(i)
        if (inString) {
          current.append(c)
          if (c == '"' && (i == 0 || input(i - 1) != '\\')) {
            tokens += current.toString
            current.clear()
            inString = false
          }
        } else if (c == '"') {
          if (current.nonEmpty) {
            tokens += current.toString
            current.clear()
          }
          current.append(c)
          inString = true
        } else if (c.isWhitespace || "(){}[];,".contains(c)) {
          if (current.nonEmpty) {
            tokens += current.toString
            current.clear()
          }
          tokens += c.toString
        } else {
          current.append(c)
        }
        i += 1
      }

      if (current.nonEmpty) {
        tokens += current.toString
      }

      tokens.toSeq
    }
  }

  /**
   * Parser that handles multi-line input (e.g., unmatched braces)
   */
  private class MultilineParser extends org.jline.reader.Parser {
    private val defaultParser = new DefaultParser()

    override def parse(line: String, cursor: Int, context: Parser.ParseContext): ParsedLine = {
      // Check for unbalanced braces/parens
      if (context == Parser.ParseContext.ACCEPT_LINE) {
        var braceCount = 0
        var parenCount = 0
        var bracketCount = 0
        var inString = false

        for (c <- line) {
          if (!inString) {
            c match {
              case '"' => inString = true
              case '{' => braceCount += 1
              case '}' => braceCount -= 1
              case '(' => parenCount += 1
              case ')' => parenCount -= 1
              case '[' => bracketCount += 1
              case ']' => bracketCount -= 1
              case _ =>
            }
          } else if (c == '"') {
            inString = false
          }
        }

        if (braceCount > 0 || parenCount > 0 || bracketCount > 0) {
          throw new EOFError(-1, cursor, "Unclosed bracket")
        }
      }

      defaultParser.parse(line, cursor, context)
    }
  }
}
