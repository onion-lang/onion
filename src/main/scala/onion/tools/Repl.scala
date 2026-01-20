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

import java.io.{BufferedReader, ByteArrayOutputStream, File, FileInputStream, FileOutputStream, InputStreamReader, PrintStream, StringReader}
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
    ":help", ":quit", ":exit", ":clear", ":history", ":imports", ":type", ":ast", ":typed", ":reset", ":paste"
  )

  def main(args: Array[String]): Unit = {
    new Repl(Seq(".")).run()
  }
}

class Repl(classpath: Seq[String]) {
  import Repl._

  private sealed trait Snippet {
    def code: String
  }
  private case class RawSnippet(code: String) extends Snippet
  private case class ExprSnippet(code: String, name: String) extends Snippet

  private case class TerminalContext(terminal: Terminal, useJLine: Boolean)

  private val encoding = Option(System.getenv("ONION_ENCODING"))
    .getOrElse(java.nio.charset.Charset.defaultCharset().name())
  private val config = new CompilerConfig(classpath, null, encoding, "", 10)
  private val shell = Shell(classpath)
  private val history = ArrayBuffer[String]()
  private val sessionSnippets = ArrayBuffer[Snippet]()
  private val sessionImports = ArrayBuffer[(String, String)]()
  private var sessionModule: Option[String] = None
  private var resultCounter = 0
  private var inputCounter = 0
  private var dummyCounter = 0

  def run(): Unit = {
    val context = buildTerminal()
    val terminal = context.terminal
    try {
      printBanner()
      if (context.useJLine) {
        val reader = buildLineReader(terminal)
        runWithReader(reader, terminal)
      } else {
        printDumbWarning()
        runPlain(terminal)
      }
    } finally {
      terminal.close()
    }
  }

  private def buildTerminal(): TerminalContext = {
    val preferred = firstNonDumb(Seq(
      () => openTtyTerminal(Some("exec")),
      () => openSystemTerminal(Some("exec")),
      () => openTtyTerminal(None),
      () => openSystemTerminal(None)
    ))
    preferred match {
      case Some(terminal) => TerminalContext(terminal, useJLine = true)
      case None =>
        openSystemTerminal(None) match {
          case Some(fallback) => TerminalContext(fallback, useJLine = false)
          case None =>
            val dumb = TerminalBuilder.builder()
              .name("Onion REPL")
              .system(true)
              .dumb(true)
              .build()
            TerminalContext(dumb, useJLine = false)
        }
    }
  }

  private def firstNonDumb(builders: Seq[() => Option[Terminal]]): Option[Terminal] = {
    val iter = builders.iterator
    while (iter.hasNext) {
      val terminal = iter.next()()
      terminal match {
        case Some(value) if value.getType != Terminal.TYPE_DUMB =>
          return Some(value)
        case Some(value) =>
          value.close()
        case None =>
      }
    }
    None
  }

  private def openSystemTerminal(provider: Option[String]): Option[Terminal] = {
    try {
      val builder = TerminalBuilder.builder()
        .name("Onion REPL")
        .system(true)
      provider.foreach(builder.provider)
      Some(builder.build())
    } catch {
      case _: Exception => None
    }
  }

  private def openTtyTerminal(provider: Option[String]): Option[Terminal] = {
    val ttyFile = new File("/dev/tty")
    if (!ttyFile.exists() || !ttyFile.canRead || !ttyFile.canWrite) return None
    var in: FileInputStream = null
    var out: FileOutputStream = null
    try {
      in = new FileInputStream(ttyFile)
      out = new FileOutputStream(ttyFile)
      val builder = TerminalBuilder.builder()
        .name("Onion REPL")
        .streams(in, out)
        .system(false)
      provider.foreach(builder.provider)
      Some(builder.build())
    } catch {
      case _: Exception =>
        if (in != null) in.close()
        if (out != null) out.close()
        None
    }
  }

  private def buildLineReader(terminal: Terminal): LineReader = {
    val completer = new OnionCompleter()
    val highlighter = new OnionHighlighter()
    LineReaderBuilder.builder()
      .terminal(terminal)
      .completer(completer)
      .highlighter(highlighter)
      .parser(new DefaultParser())
      .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".onion_history"))
      .variable(LineReader.HISTORY_SIZE, 1000)
      .option(LineReader.Option.HISTORY_BEEP, false)
      .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
      .build()
  }

  private def runWithReader(reader: LineReader, terminal: Terminal): Unit = {
    var running = true
    while (running) {
      try {
        val line = readInput(reader)
        if (line != null) {
          val trimmed = line.trim
          if (trimmed.nonEmpty) {
            history += trimmed
            running = processInput(trimmed, terminal, Some(reader))
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
  }

  private def runPlain(terminal: Terminal): Unit = {
    val reader = new BufferedReader(new InputStreamReader(terminal.input(), terminal.encoding()))
    var running = true
    while (running) {
      terminal.writer().print(PROMPT)
      terminal.writer().flush()
      val line = readPlainInput(reader, terminal)
      if (line == null) {
        println(Colors.GREEN + "\nGoodbye!" + Colors.RESET)
        running = false
      } else {
        val trimmed = line.trim
        if (trimmed.nonEmpty) {
          history += trimmed
          running = processInput(trimmed, terminal, None)
        }
      }
    }
  }

  private def readPlainInput(reader: BufferedReader, terminal: Terminal): String = {
    val first = reader.readLine()
    if (first == null) return null
    val buffer = new StringBuilder(first)
    while (requiresContinuation(buffer.toString())) {
      terminal.writer().print(CONTINUATION_PROMPT)
      terminal.writer().flush()
      val next = reader.readLine()
      if (next == null) return buffer.toString()
      buffer.append("\n").append(next)
    }
    buffer.toString()
  }

  private def printDumbWarning(): Unit = {
    println("Warning: running without a full-featured terminal. Line editing is disabled.")
  }

  private def readInput(reader: LineReader): String = {
    val first = reader.readLine(PROMPT)
    if (first == null) return null
    val buffer = new StringBuilder(first)
    while (requiresContinuation(buffer.toString())) {
      val next = reader.readLine(CONTINUATION_PROMPT)
      if (next == null) return buffer.toString()
      buffer.append("\n").append(next)
    }
    buffer.toString()
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

  private def processInput(input: String, terminal: Terminal, readerOpt: Option[LineReader]): Boolean = {
    if (input.startsWith(":")) {
      processCommand(input, terminal)
    } else {
      executeCode(input, terminal, readerOpt)
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

      case ":imports" =>
        printImports()
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
        sessionSnippets.clear()
        sessionImports.clear()
        sessionModule = None
        resultCounter = 0
        inputCounter = 0
        println(Colors.GREEN + "Session reset." + Colors.RESET)
        true

      case ":paste" | ":p" =>
        println(Colors.YELLOW + "Entering paste mode (Ctrl-D to finish):" + Colors.RESET)
        val lines = ArrayBuffer[String]()
        var reading = true
        val reader = new BufferedReader(new InputStreamReader(terminal.input(), terminal.encoding()))
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
          executeCode(lines.mkString("\n"), terminal, None)
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
    |  ${Colors.YELLOW}:imports${Colors.RESET}          Show current imports
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

  private sealed trait InputKind
  private case class ModuleInput(name: String) extends InputKind
  private case class ImportInput(entries: Seq[(String, String)]) extends InputKind
  private case class DeclarationInput(code: String) extends InputKind
  private case class ExpressionInput(code: String) extends InputKind

  private def executeCode(code: String, terminal: Terminal, readerOpt: Option[LineReader]): Unit = {
    val inputId = inputCounter + 1
    val fileName = s"repl_input_$inputId.on"
    val unit = parseSnippet(code, fileName)
    if (unit.isEmpty) return

    classifySnippet(unit.get, code) match {
      case Left(message) =>
        println(Colors.RED + message + Colors.RESET)
      case Right(action) =>
        action match {
          case ModuleInput(name) =>
            if (sessionModule.exists(_ != name)) {
              println(Colors.RED + s"Module already set to ${sessionModule.get}." + Colors.RESET)
              return
            }
            val nextModule = Some(name)
            val source = buildSessionSource(nextModule, sessionImports.toSeq, sessionSnippets.toSeq, printLastResult = false)
            compileSession(source, fileName) match {
              case Right(_) =>
                sessionModule = nextModule
                inputCounter += 1
              case Left(errors) =>
                CompilationReporter.printErrors(errors)
            }
          case ImportInput(entries) =>
            val nextImports = mergeImports(sessionImports.toSeq, entries)
            val source = buildSessionSource(sessionModule, nextImports, sessionSnippets.toSeq, printLastResult = false)
            compileSession(source, fileName) match {
              case Right(_) =>
                sessionImports.clear()
                sessionImports ++= nextImports
                inputCounter += 1
              case Left(errors) =>
                CompilationReporter.printErrors(errors)
            }
          case DeclarationInput(snippet) =>
            val nextSnippets = sessionSnippets.toSeq :+ RawSnippet(snippet)
            val source = buildSessionSource(sessionModule, sessionImports.toSeq, nextSnippets, printLastResult = false)
            compileSession(source, fileName) match {
              case Right(_) =>
                sessionSnippets += RawSnippet(snippet)
                inputCounter += 1
              case Left(errors) =>
                CompilationReporter.printErrors(errors)
            }
          case ExpressionInput(snippet) =>
            val resName = s"res$resultCounter"
            val exprSnippet = ExprSnippet(snippet, resName)
            val nextSnippets = sessionSnippets.toSeq :+ exprSnippet
            val source = buildSessionSource(sessionModule, sessionImports.toSeq, nextSnippets, printLastResult = true)
            compileSession(source, fileName) match {
              case Right(classes) =>
                runClasses(classes, terminal, readerOpt)
                sessionSnippets += exprSnippet
                resultCounter += 1
                inputCounter += 1
              case Left(errors) if isVoidAssignmentErrors(errors) =>
                val rawSnippet = RawSnippet(snippet)
                val fallbackSnippets = sessionSnippets.toSeq :+ rawSnippet
                val fallbackSource = buildSessionSource(sessionModule, sessionImports.toSeq, fallbackSnippets, printLastResult = false)
                compileSession(fallbackSource, fileName) match {
                  case Right(classes) =>
                    runClasses(classes, terminal, readerOpt)
                    sessionSnippets += rawSnippet
                    inputCounter += 1
                  case Left(fallbackErrors) =>
                    CompilationReporter.printErrors(fallbackErrors)
                }
              case Left(errors) =>
                CompilationReporter.printErrors(errors)
            }
        }
    }
  }

  private def showType(expr: String): Unit = {
    val resName = "__repl_type__"
    val source = buildSessionSource(
      sessionModule,
      sessionImports.toSeq,
      sessionSnippets.toSeq :+ ExprSnippet(expr, resName),
      printLastResult = false
    )
    compileSession(source, "typecheck.on") match {
      case Right(_) =>
        println(s"${Colors.CYAN}Expression compiles successfully${Colors.RESET}")
        println("(Full type inference requires direct type checker access)")
      case Left(errors) =>
        CompilationReporter.printErrors(errors)
    }
  }

  private def showAst(expr: String): Unit = {
    val resName = "__repl_ast__"
    val source = buildSessionSource(
      sessionModule,
      sessionImports.toSeq,
      sessionSnippets.toSeq :+ ExprSnippet(expr, resName),
      printLastResult = false
    )
    val fileName = s"repl_ast_${inputCounter + 1}.on"
    try {
      val parsing = new Parsing(config)
      val parsed = parsing.process(Seq(new StreamInputSource(new StringReader(source), fileName)))
      DiagnosticsPrinter.dumpAst(parsed)
    } catch {
      case e: onion.compiler.exceptions.CompilationException =>
        CompilationReporter.printErrors(e.problems.toIndexedSeq)
    }
  }

  private def showTyped(expr: String): Unit = {
    val resName = "__repl_typed__"
    val source = buildSessionSource(
      sessionModule,
      sessionImports.toSeq,
      sessionSnippets.toSeq :+ ExprSnippet(expr, resName),
      printLastResult = false
    )
    val fileName = s"repl_typed_${inputCounter + 1}.on"
    try {
      val parsing = new Parsing(config)
      val rewriting = new Rewriting(config)
      val typing = new Typing(config)
      val parsed = parsing.process(Seq(new StreamInputSource(new StringReader(source), fileName)))
      val rewritten = rewriting.process(parsed)
      val typed = typing.process(rewritten)
      DiagnosticsPrinter.dumpTyped(typed)
    } catch {
      case e: onion.compiler.exceptions.CompilationException =>
        CompilationReporter.printErrors(e.problems.toIndexedSeq)
    }
  }

  private def printImports(): Unit = {
    sessionModule.foreach { name =>
      println(s"${Colors.CYAN}module${Colors.RESET} $name")
    }
    if (sessionImports.isEmpty) {
      println("No imports.")
    } else {
      sessionImports.foreach { case (alias, path) =>
        println(s"${Colors.CYAN}-${Colors.RESET} ${renderImportEntry(alias, path)}")
      }
    }
  }

  private def parseSnippet(code: String, fileName: String): Option[AST.CompilationUnit] = {
    tryParse(code, fileName) match {
      case Right(unit) => Some(unit)
      case Left(errors) if looksLikeHeaderOnly(code) =>
        dummyCounter += 1
        val dummyName = s"__ReplDummy${dummyCounter}__"
        val wrapped = s"$code\nclass $dummyName {}"
        tryParse(wrapped, fileName) match {
          case Right(unit) =>
            val filtered = unit.toplevels.filterNot {
              case decl: AST.ClassDeclaration if decl.name == dummyName => true
              case _ => false
            }
            Some(unit.copy(toplevels = filtered))
          case Left(nextErrors) =>
            CompilationReporter.printErrors(nextErrors)
            None
        }
      case Left(errors) =>
        CompilationReporter.printErrors(errors)
        None
    }
  }

  private def tryParse(code: String, fileName: String): Either[IndexedSeq[CompileError], AST.CompilationUnit] = {
    try {
      val parsing = new Parsing(config)
      val parsed = parsing.process(Seq(new StreamInputSource(new StringReader(code), fileName)))
      Right(parsed.head)
    } catch {
      case e: onion.compiler.exceptions.CompilationException =>
        Left(e.problems.toIndexedSeq)
    }
  }

  private def looksLikeHeaderOnly(code: String): Boolean = {
    val trimmed = code.trim
    trimmed.startsWith("import") || trimmed.startsWith("module")
  }

  private def classifySnippet(unit: AST.CompilationUnit, code: String): Either[String, InputKind] = {
    if (unit.module != null) {
      if (unit.imports != null || unit.toplevels.nonEmpty) {
        Left("Module declaration must be a standalone input.")
      } else {
        Right(ModuleInput(unit.module.name))
      }
    } else if (unit.imports != null) {
      if (unit.toplevels.nonEmpty) {
        Left("Import clause must be a standalone input.")
      } else {
        Right(ImportInput(unit.imports.mapping))
      }
    } else if (unit.toplevels.isEmpty) {
      Left("No input to evaluate.")
    } else if (unit.toplevels.length == 1 && isValueExpression(unit.toplevels.head)) {
      Right(ExpressionInput(code))
    } else {
      Right(DeclarationInput(code))
    }
  }

  private def isValueExpression(node: AST.Toplevel): Boolean = node match {
    case _: AST.LocalVariableDeclaration => false
    case _: AST.ReturnExpression => false
    case _: AST.BreakExpression => false
    case _: AST.ContinueExpression => false
    case _: AST.CompoundExpression => true
    case _ => false
  }

  private def isVoidAssignmentErrors(errors: Seq[CompileError]): Boolean = {
    errors.exists { error =>
      val msg = error.message.toLowerCase
      val objectMentioned = msg.contains("java.lang.object") || msg.contains("object")
      val voidMismatch = msg.contains("void") && objectMentioned
      val codeMatches = error.errorCode.contains("E0000") && msg.contains("void")
      voidMismatch || codeMatches
    }
  }

  private def mergeImports(existing: Seq[(String, String)], incoming: Seq[(String, String)]): Seq[(String, String)] = {
    val merged = ArrayBuffer[(String, String)]()
    existing.foreach(merged += _)
    incoming.foreach { entry =>
      if (!merged.contains(entry)) merged += entry
    }
    merged.toSeq
  }

  private def buildSessionSource(
    moduleName: Option[String],
    imports: Seq[(String, String)],
    snippets: Seq[Snippet],
    printLastResult: Boolean
  ): String = {
    val builder = new StringBuilder()
    moduleName.foreach { name =>
      builder.append("module ").append(name).append("\n")
    }
    if (imports.nonEmpty) {
      builder.append("import {\n")
      imports.foreach { case (alias, path) =>
        builder.append("  ").append(renderImportEntry(alias, path)).append(";\n")
      }
      builder.append("}\n")
    }
    snippets.zipWithIndex.foreach { case (snippet, idx) =>
      snippet match {
        case RawSnippet(code) =>
          builder.append(code).append("\n")
        case ExprSnippet(code, name) =>
          builder.append("val ").append(name).append(" = {\n")
          builder.append(code).append("\n")
          builder.append("}\n")
          if (printLastResult && idx == snippets.length - 1) {
            builder.append("IO::println(\"").append(name).append(" = \" + ").append(name).append(")\n")
          }
      }
    }
    if (snippets.isEmpty) {
      builder.append("0\n")
    }
    builder.toString()
  }

  private def renderImportEntry(alias: String, path: String): String = {
    val last = lastSegment(path)
    if (alias == "*" || alias == last) path else s"$alias = $path"
  }

  private def lastSegment(path: String): String = {
    val index = path.lastIndexOf(".")
    if (index < 0) path else path.substring(index + 1)
  }

  private def compileSession(source: String, fileName: String): Either[Seq[CompileError], Seq[CompiledClass]] = {
    val compiler = new OnionCompiler(config)
    compiler.compile(Seq(new StreamInputSource(new StringReader(source), fileName))) match {
      case Success(classes) => Right(classes)
      case Failure(errors) => Left(errors)
    }
  }

  private def runClasses(classes: Seq[CompiledClass], terminal: Terminal, readerOpt: Option[LineReader]): Unit = {
    val encoding = config.encoding
    val (result, output) = captureStdOut(encoding) {
      shell.run(classes, Array())
    }
    result match {
      case Shell.Success(_) => ()
      case Shell.Failure(_) => ()
    }
    val normalizedOutput = if (output.endsWith(System.lineSeparator())) output else output + System.lineSeparator()
    if (normalizedOutput.trim.nonEmpty) {
      readerOpt match {
        case Some(reader) => reader.printAbove(normalizedOutput.stripSuffix(System.lineSeparator()))
        case None =>
          terminal.writer().print(normalizedOutput)
          terminal.writer().flush()
      }
    }
  }

  private def captureStdOut[T](encoding: String)(block: => T): (T, String) = {
    val original = System.out
    val buffer = new java.io.ByteArrayOutputStream()
    val stream = new java.io.PrintStream(buffer, true, encoding)
    System.setOut(stream)
    try {
      val result = block
      stream.flush()
      (result, buffer.toString(encoding))
    } finally {
      stream.flush()
      System.setOut(original)
    }
  }

  private def terminalWriter(): java.io.PrintStream = Console.out

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
    private val keywordStyle = AttributedStyle.BOLD.foreground(AttributedStyle.BLUE)
    private val typeStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)
    private val stringStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
    private val numberStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA)
    private val commentStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT)

    override def highlight(reader: LineReader, buffer: String): org.jline.utils.AttributedString = {
      val builder = new AttributedStringBuilder()
      var i = 0
      while (i < buffer.length) {
        val ch = buffer.charAt(i)
        if (ch == '/' && i + 1 < buffer.length && buffer.charAt(i + 1) == '/') {
          val comment = buffer.substring(i)
          builder.styled(commentStyle, comment)
          i = buffer.length
        } else if (ch == '"') {
          val (text, next) = readStringLiteral(buffer, i)
          builder.styled(stringStyle, text)
          i = next
        } else if (ch.isDigit) {
          val (text, next) = readNumber(buffer, i)
          builder.styled(numberStyle, text)
          i = next
        } else if (isIdentifierStart(ch)) {
          val (text, next) = readWord(buffer, i)
          if (keywordSet.contains(text)) {
            builder.styled(keywordStyle, text)
          } else if (typeSet.contains(text)) {
            builder.styled(typeStyle, text)
          } else {
            builder.append(text)
          }
          i = next
        } else {
          builder.append(ch.toString)
          i += 1
        }
      }
      builder.toAttributedString
    }

    override def setErrorPattern(errorPattern: java.util.regex.Pattern): Unit = {}
    override def setErrorIndex(errorIndex: Int): Unit = {}

    private def isIdentifierStart(ch: Char): Boolean =
      ch.isLetter || ch == '_'

    private def readWord(buffer: String, start: Int): (String, Int) = {
      var i = start
      while (i < buffer.length && (buffer.charAt(i).isLetterOrDigit || buffer.charAt(i) == '_')) {
        i += 1
      }
      (buffer.substring(start, i), i)
    }

    private def readNumber(buffer: String, start: Int): (String, Int) = {
      var i = start
      while (i < buffer.length && buffer.charAt(i).isDigit) {
        i += 1
      }
      (buffer.substring(start, i), i)
    }

    private def readStringLiteral(buffer: String, start: Int): (String, Int) = {
      val sb = new StringBuilder
      var i = start
      var escaped = false
      while (i < buffer.length) {
        val c = buffer.charAt(i)
        sb.append(c)
        if (c == '"' && !escaped && i != start) {
          i += 1
          return (sb.toString(), i)
        }
        escaped = c == '\\' && !escaped
        i += 1
      }
      (sb.toString(), i)
    }
  }

  private def requiresContinuation(text: String): Boolean = {
    var braceCount = 0
    var parenCount = 0
    var bracketCount = 0
    var inString = false
    var inTripleString = false
    var i = 0

    while (i < text.length) {
      val c = text.charAt(i)
      if (inTripleString) {
        if (c == '"' && i + 2 < text.length && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') {
          inTripleString = false
          i += 2
        }
      } else if (inString) {
        if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
          inString = false
        }
      } else {
        if (c == '"' && i + 2 < text.length && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') {
          inTripleString = true
          i += 2
        } else if (c == '"') {
          inString = true
        } else {
          c match {
            case '{' => braceCount += 1
            case '}' => braceCount -= 1
            case '(' => parenCount += 1
            case ')' => parenCount -= 1
            case '[' => bracketCount += 1
            case ']' => bracketCount -= 1
            case _ =>
          }
        }
      }
      i += 1
    }

    if (braceCount > 0 || parenCount > 0 || bracketCount > 0 || inString || inTripleString) return true

    val trimmed = text.trim
    if (trimmed.isEmpty) return false
    if (trimmed.endsWith("++") || trimmed.endsWith("--")) return false

    val multiChar = Seq(
      ">>>=", ">>=", "<<=", "&&", "||", "==", "!=", "<=", ">=", "+=", "-=",
      "*=", "/=", "%=", "&=", "|=", "^=", ">>>", ">>", "<<", "::", "->"
    )
    if (multiChar.exists(trimmed.endsWith)) return true

    val singleChar = Set('+', '-', '*', '/', '%', '=', ',', '.', ':', '&', '|', '^')
    singleChar.contains(trimmed.last)
  }
}
