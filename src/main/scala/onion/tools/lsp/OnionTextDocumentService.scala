/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.      *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.lsp

import onion.compiler._
import onion.compiler.CompilationOutcome.{Failure, Success}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.jsonrpc.messages.{Either => LspEither}
import org.eclipse.lsp4j.services.TextDocumentService

import java.io.StringReader
import java.net.URI
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap}
import scala.jdk.CollectionConverters._

/**
 * Represents a symbol definition with its location.
 */
case class SymbolDefinition(
  name: String,
  kind: SymbolKind,
  uri: String,
  line: Int,
  startChar: Int,
  endChar: Int
) {
  def toLocation: org.eclipse.lsp4j.Location = {
    val range = new Range(
      new Position(line, startChar),
      new Position(line, endChar)
    )
    new org.eclipse.lsp4j.Location(uri, range)
  }
}

/**
 * Symbol table that stores all symbol definitions for quick lookup.
 */
class SymbolTable {
  private val symbols = new ConcurrentHashMap[String, java.util.List[SymbolDefinition]]()

  def add(symbol: SymbolDefinition): Unit = {
    symbols.computeIfAbsent(symbol.name, _ => new java.util.ArrayList[SymbolDefinition]())
    symbols.get(symbol.name).add(symbol)
  }

  def lookup(name: String): Seq[SymbolDefinition] = {
    val defs = symbols.get(name)
    if (defs != null) defs.asScala.toSeq else Seq.empty
  }

  def lookupInUri(name: String, uri: String): Seq[SymbolDefinition] = {
    lookup(name).filter(_.uri == uri)
  }

  def clear(uri: String): Unit = {
    symbols.forEach { (_, defs) =>
      defs.removeIf(_.uri == uri)
    }
    // Remove empty entries
    symbols.entrySet().removeIf(_.getValue.isEmpty)
  }

  def allSymbols: Seq[SymbolDefinition] = {
    symbols.values().asScala.flatMap(_.asScala).toSeq
  }
}

/**
 * Handles text document operations for the Onion Language Server.
 * Provides diagnostics, hover, completion, and go-to-definition.
 */
class OnionTextDocumentService(server: OnionLanguageServer) extends TextDocumentService {
  private var client: org.eclipse.lsp4j.services.LanguageClient = _
  private val documents = new ConcurrentHashMap[String, DocumentState]()
  private val symbolTable = new SymbolTable()

  // Onion keywords for completion
  private val keywords = Array(
    "class", "interface", "abstract", "final", "static", "public", "private", "protected",
    "def", "var", "val", "if", "else", "while", "for", "return", "break", "continue",
    "new", "this", "super", "null", "true", "false", "import", "package",
    "try", "catch", "finally", "throw", "throws", "enum", "record", "sealed",
    "select", "case", "otherwise", "extends", "implements"
  )

  // Built-in types
  private val builtinTypes = Array(
    "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String",
    "Unit", "Void", "Object", "Array", "List", "Map", "Set", "Option", "Result"
  )

  // Common standard library classes
  private val stdLibClasses = Array(
    "IO", "Strings", "Files", "DateTime", "Http", "Json", "Regex", "Math"
  )

  def connect(client: org.eclipse.lsp4j.services.LanguageClient): Unit = {
    this.client = client
  }

  override def didOpen(params: DidOpenTextDocumentParams): Unit = {
    val uri = params.getTextDocument.getUri
    val content = params.getTextDocument.getText
    documents.put(uri, DocumentState(content, params.getTextDocument.getVersion))
    updateSymbolTable(uri, content)
    validateDocument(uri, content)
  }

  override def didChange(params: DidChangeTextDocumentParams): Unit = {
    val uri = params.getTextDocument.getUri
    val changes = params.getContentChanges.asScala
    if (changes.nonEmpty) {
      val content = changes.last.getText
      documents.put(uri, DocumentState(content, params.getTextDocument.getVersion))
      updateSymbolTable(uri, content)
      validateDocument(uri, content)
    }
  }

  override def didClose(params: DidCloseTextDocumentParams): Unit = {
    val uri = params.getTextDocument.getUri
    documents.remove(uri)
    symbolTable.clear(uri)
    // Clear diagnostics
    client.publishDiagnostics(new PublishDiagnosticsParams(uri, java.util.Collections.emptyList()))
  }

  override def didSave(params: DidSaveTextDocumentParams): Unit = {
    val uri = params.getTextDocument.getUri
    val state = documents.get(uri)
    if (state != null) {
      validateDocument(uri, state.content)
    }
  }

  override def completion(params: CompletionParams): CompletableFuture[LspEither[java.util.List[CompletionItem], CompletionList]] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val position = params.getPosition
      val state = documents.get(uri)

      val items = new java.util.ArrayList[CompletionItem]()

      if (state != null) {
        val line = getLineAt(state.content, position.getLine)
        val prefix = getWordBeforeCursor(line, position.getCharacter)

        // Add keyword completions
        keywords.filter(_.startsWith(prefix)).foreach { kw =>
          val item = new CompletionItem(kw)
          item.setKind(CompletionItemKind.Keyword)
          item.setDetail("keyword")
          items.add(item)
        }

        // Add type completions
        builtinTypes.filter(_.toLowerCase.startsWith(prefix.toLowerCase)).foreach { t =>
          val item = new CompletionItem(t)
          item.setKind(CompletionItemKind.Class)
          item.setDetail("type")
          items.add(item)
        }

        // Add standard library completions
        stdLibClasses.filter(_.toLowerCase.startsWith(prefix.toLowerCase)).foreach { c =>
          val item = new CompletionItem(c)
          item.setKind(CompletionItemKind.Module)
          item.setDetail("standard library")
          items.add(item)
        }

        // Add IO method completions when typing "IO::"
        if (line.contains("IO::") || prefix.startsWith("IO::")) {
          val ioMethods = Array(
            ("println", "Prints a line to stdout"),
            ("print", "Prints to stdout without newline"),
            ("readLine", "Reads a line from stdin")
          )
          ioMethods.foreach { case (name, doc) =>
            val item = new CompletionItem(s"IO::$name")
            item.setKind(CompletionItemKind.Method)
            item.setDetail("IO method")
            item.setDocumentation(doc)
            items.add(item)
          }
        }
      }

      LspEither.forLeft(items)
    }
  }

  override def hover(params: HoverParams): CompletableFuture[Hover] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val position = params.getPosition
      val state = documents.get(uri)

      if (state != null) {
        val line = getLineAt(state.content, position.getLine)
        val word = getWordAtPosition(line, position.getCharacter)

        val hoverText = getHoverInfo(word)
        if (hoverText != null) {
          val content = new MarkupContent()
          content.setKind(MarkupKind.MARKDOWN)
          content.setValue(hoverText)
          new Hover(content)
        } else {
          null
        }
      } else {
        null
      }
    }
  }

  override def definition(params: DefinitionParams): CompletableFuture[LspEither[java.util.List[_ <: org.eclipse.lsp4j.Location], java.util.List[_ <: LocationLink]]] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val position = params.getPosition
      val state = documents.get(uri)

      val locations = new java.util.ArrayList[org.eclipse.lsp4j.Location]()

      if (state != null) {
        val line = getLineAt(state.content, position.getLine)
        val word = getWordAtPosition(line, position.getCharacter)

        if (word.nonEmpty) {
          // First, look for definitions in the current document
          val localDefs = symbolTable.lookupInUri(word, uri)
          if (localDefs.nonEmpty) {
            localDefs.foreach(d => locations.add(d.toLocation))
          } else {
            // Then look in all documents
            val allDefs = symbolTable.lookup(word)
            allDefs.foreach(d => locations.add(d.toLocation))
          }
        }
      }

      LspEither.forLeft(locations)
    }
  }

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[java.util.List[LspEither[SymbolInformation, DocumentSymbol]]] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val state = documents.get(uri)
      val symbols = new java.util.ArrayList[LspEither[SymbolInformation, DocumentSymbol]]()

      if (state != null) {
        // Simple regex-based symbol extraction
        val classPattern = """(?:abstract\s+)?(?:sealed\s+)?class\s+(\w+)""".r
        val interfacePattern = """interface\s+(\w+)""".r
        val methodPattern = """def\s+(\w+)\s*\(""".r
        val enumPattern = """enum\s+(\w+)""".r

        val lines = state.content.split("\n")
        lines.zipWithIndex.foreach { case (line, lineNum) =>
          classPattern.findFirstMatchIn(line).foreach { m =>
            val symbol = createSymbol(m.group(1), SymbolKind.Class, uri, lineNum, m.start(1), m.end(1))
            symbols.add(LspEither.forRight(symbol))
          }
          interfacePattern.findFirstMatchIn(line).foreach { m =>
            val symbol = createSymbol(m.group(1), SymbolKind.Interface, uri, lineNum, m.start(1), m.end(1))
            symbols.add(LspEither.forRight(symbol))
          }
          methodPattern.findFirstMatchIn(line).foreach { m =>
            val symbol = createSymbol(m.group(1), SymbolKind.Method, uri, lineNum, m.start(1), m.end(1))
            symbols.add(LspEither.forRight(symbol))
          }
          enumPattern.findFirstMatchIn(line).foreach { m =>
            val symbol = createSymbol(m.group(1), SymbolKind.Enum, uri, lineNum, m.start(1), m.end(1))
            symbols.add(LspEither.forRight(symbol))
          }
        }
      }

      symbols
    }
  }

  private def createSymbol(name: String, kind: SymbolKind, uri: String, line: Int, startChar: Int, endChar: Int): DocumentSymbol = {
    val range = new Range(new Position(line, startChar), new Position(line, endChar))
    val symbol = new DocumentSymbol(name, kind, range, range)
    symbol
  }

  /**
   * Updates the symbol table with all definitions from the document.
   * Extracts classes, interfaces, methods, fields, and local variables.
   */
  private def updateSymbolTable(uri: String, content: String): Unit = {
    // Clear existing symbols for this document
    symbolTable.clear(uri)

    val lines = content.split("\n")

    // Patterns for symbol extraction
    val classPattern = """(?:abstract\s+)?(?:sealed\s+)?(?:final\s+)?class\s+(\w+)""".r
    val interfacePattern = """interface\s+(\w+)""".r
    val enumPattern = """enum\s+(\w+)""".r
    val recordPattern = """record\s+(\w+)""".r
    val methodPattern = """def\s+(\w+)\s*[\[(]""".r
    val fieldPattern = """(?:static\s+)?(?:val|var)\s+(\w+)\s*:""".r
    val argumentPattern = """def\s+\w+\s*\(\s*([^)]+)\)""".r
    val localVarPattern = """^\s*(?:val|var)\s+(\w+)\s*:""".r

    lines.zipWithIndex.foreach { case (line, lineNum) =>
      // Class definitions
      classPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Class, uri, lineNum, m.start(1), m.end(1)))
      }

      // Interface definitions
      interfacePattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Interface, uri, lineNum, m.start(1), m.end(1)))
      }

      // Enum definitions
      enumPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Enum, uri, lineNum, m.start(1), m.end(1)))
      }

      // Record definitions
      recordPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Struct, uri, lineNum, m.start(1), m.end(1)))
      }

      // Method definitions
      methodPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Method, uri, lineNum, m.start(1), m.end(1)))
      }

      // Field definitions (class level val/var)
      // Only match if not inside a method (heuristic: line starts with whitespace but not too much)
      if (line.matches("""^\s{2,6}(?:static\s+)?(?:val|var)\s+.*""")) {
        fieldPattern.findAllMatchIn(line).foreach { m =>
          symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Field, uri, lineNum, m.start(1), m.end(1)))
        }
      }

      // Local variable definitions (inside methods - more indentation)
      if (line.matches("""^\s{4,}(?:val|var)\s+.*""")) {
        localVarPattern.findAllMatchIn(line).foreach { m =>
          symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Variable, uri, lineNum, m.start(1), m.end(1)))
        }
      }

      // Method arguments
      argumentPattern.findAllMatchIn(line).foreach { m =>
        val args = m.group(1)
        // Parse individual arguments: name: Type
        val argPattern = """(\w+)\s*:""".r
        argPattern.findAllMatchIn(args).foreach { argMatch =>
          val argName = argMatch.group(1)
          // Calculate position relative to the line
          val argStart = m.start(1) + argMatch.start(1)
          val argEnd = m.start(1) + argMatch.end(1)
          symbolTable.add(SymbolDefinition(argName, SymbolKind.Variable, uri, lineNum, argStart, argEnd))
        }
      }
    }
  }

  private def validateDocument(uri: String, content: String): Unit = {
    val fileName = extractFileName(uri)
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 100)
    val compiler = new OnionCompiler(config)

    val outcome = compiler.compile(Seq(new StreamInputSource(new StringReader(content), fileName)))

    val diagnostics = outcome match {
      case Success(_) =>
        java.util.Collections.emptyList[Diagnostic]()
      case Failure(errors) =>
        errors.map(errorToDiagnostic).asJava
    }

    client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics))
  }

  private def errorToDiagnostic(error: CompileError): Diagnostic = {
    val location = error.location
    val line = if (location != null) Math.max(0, location.line - 1) else 0
    val column = if (location != null) Math.max(0, location.column - 1) else 0

    val range = new Range(
      new Position(line, column),
      new Position(line, column + 10) // Approximate end
    )

    val diagnostic = new Diagnostic()
    diagnostic.setRange(range)
    diagnostic.setSeverity(DiagnosticSeverity.Error)
    diagnostic.setSource("onion")
    diagnostic.setMessage(error.message)
    diagnostic
  }

  private def extractFileName(uri: String): String = {
    try {
      val path = new URI(uri).getPath
      if (path != null) path.split("/").last else "unknown.on"
    } catch {
      case _: Exception => "unknown.on"
    }
  }

  private def getLineAt(content: String, lineNum: Int): String = {
    val lines = content.split("\n", -1)
    if (lineNum >= 0 && lineNum < lines.length) lines(lineNum) else ""
  }

  private def getWordBeforeCursor(line: String, column: Int): String = {
    if (column <= 0 || column > line.length) return ""
    val before = line.substring(0, column)
    val wordMatch = """[\w:]+$""".r.findFirstIn(before)
    wordMatch.getOrElse("")
  }

  private def getWordAtPosition(line: String, column: Int): String = {
    if (line.isEmpty || column < 0 || column >= line.length) return ""

    var start = column
    var end = column

    // Find word boundaries
    while (start > 0 && (line(start - 1).isLetterOrDigit || line(start - 1) == '_')) {
      start -= 1
    }
    while (end < line.length && (line(end).isLetterOrDigit || line(end) == '_')) {
      end += 1
    }

    if (start < end) line.substring(start, end) else ""
  }

  private def getHoverInfo(word: String): String = {
    if (keywords.contains(word)) {
      s"**Keyword**: `$word`\n\n${getKeywordDescription(word)}"
    } else if (builtinTypes.contains(word)) {
      s"**Type**: `$word`\n\n${getTypeDescription(word)}"
    } else if (stdLibClasses.contains(word)) {
      s"**Module**: `$word`\n\n${getModuleDescription(word)}"
    } else {
      null
    }
  }

  private def getKeywordDescription(keyword: String): String = keyword match {
    case "class" => "Declares a class type."
    case "interface" => "Declares an interface type."
    case "abstract" => "Marks a class or method as abstract."
    case "final" => "Prevents inheritance or overriding."
    case "static" => "Declares a static member."
    case "def" => "Declares a method or function."
    case "var" => "Declares a mutable variable."
    case "val" => "Declares an immutable variable."
    case "if" => "Conditional branching."
    case "else" => "Alternative branch in conditional."
    case "while" => "Loop construct."
    case "for" => "For loop construct."
    case "return" => "Returns a value from a method."
    case "new" => "Creates a new instance."
    case "enum" => "Declares an enumeration type."
    case "record" => "Declares a record type."
    case "sealed" => "Restricts subclassing."
    case "select" => "Pattern matching expression."
    case _ => s"Onion language keyword."
  }

  private def getTypeDescription(typeName: String): String = typeName match {
    case "Int" => "32-bit signed integer."
    case "Long" => "64-bit signed integer."
    case "Short" => "16-bit signed integer."
    case "Byte" => "8-bit signed integer."
    case "Float" => "32-bit floating point."
    case "Double" => "64-bit floating point."
    case "Boolean" => "True or false value."
    case "Char" => "16-bit Unicode character."
    case "String" => "Immutable sequence of characters."
    case "Unit" => "No meaningful value (like void)."
    case "Option" => "Optional value: Some(value) or None."
    case "Result" => "Success or error: Ok(value) or Err(error)."
    case _ => s"Built-in type."
  }

  private def getModuleDescription(module: String): String = module match {
    case "IO" => "Input/output operations.\n- `IO::println(msg)` - Print with newline\n- `IO::print(msg)` - Print without newline\n- `IO::readLine()` - Read line from stdin"
    case "Strings" => "String manipulation utilities."
    case "Files" => "File system operations."
    case "DateTime" => "Date and time utilities."
    case "Http" => "HTTP client operations."
    case "Json" => "JSON parsing and serialization."
    case "Regex" => "Regular expression operations."
    case "Math" => "Mathematical functions."
    case _ => s"Standard library module."
  }

  private case class DocumentState(content: String, version: Int)
}
