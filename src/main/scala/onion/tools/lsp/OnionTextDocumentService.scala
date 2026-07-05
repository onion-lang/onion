/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.      *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.lsp

import onion.compiler._
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
  endChar: Int,
  lineContent: String = "",
  signature: String = ""
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

        // Add user-defined symbols from the symbol table
        symbolTable.allSymbols.foreach { symbol =>
          if (symbol.name.toLowerCase.startsWith(prefix.toLowerCase)) {
            val item = new CompletionItem(symbol.name)
            item.setKind(symbolKindToCompletionItemKind(symbol.kind))
            item.setDetail(symbolKindLabel(symbol.kind))
            items.add(item)
          }
        }
      }

      LspEither.forLeft(items)
    }
  }

  private def symbolKindToCompletionItemKind(kind: SymbolKind): CompletionItemKind = kind match {
    case SymbolKind.Class => CompletionItemKind.Class
    case SymbolKind.Interface => CompletionItemKind.Interface
    case SymbolKind.Enum => CompletionItemKind.Enum
    case SymbolKind.Struct => CompletionItemKind.Struct
    case SymbolKind.Method => CompletionItemKind.Method
    case SymbolKind.Field => CompletionItemKind.Field
    case SymbolKind.Variable => CompletionItemKind.Variable
    case _ => CompletionItemKind.Text
  }

  private def symbolKindLabel(kind: SymbolKind): String = kind match {
    case SymbolKind.Class => "class"
    case SymbolKind.Interface => "interface"
    case SymbolKind.Enum => "enum"
    case SymbolKind.Struct => "record"
    case SymbolKind.Method => "method"
    case SymbolKind.Field => "field"
    case SymbolKind.Variable => "variable"
    case _ => "symbol"
  }

  override def hover(params: HoverParams): CompletableFuture[Hover] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val position = params.getPosition
      val state = documents.get(uri)

      if (state != null) {
        val line = getLineAt(state.content, position.getLine)
        val word = getWordAtPosition(line, position.getCharacter)

        val hoverText = getHoverInfo(word, uri)
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

  override def definition(params: DefinitionParams): CompletableFuture[LspEither[java.util.List[? <: org.eclipse.lsp4j.Location], java.util.List[? <: LocationLink]]] = {
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

  override def signatureHelp(params: SignatureHelpParams): CompletableFuture[SignatureHelp] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val position = params.getPosition
      val state = documents.get(uri)

      if (state != null) {
        val line = getLineAt(state.content, position.getLine)
        val methodName = findMethodNameBeforePosition(line, position.getCharacter)

        if (methodName.nonEmpty) {
          val defs = symbolTable.lookupInUri(methodName, uri)
          val methodDefs = defs.filter(d => d.kind == SymbolKind.Method && d.signature.nonEmpty)
          if (methodDefs.nonEmpty) {
            val signatures = methodDefs.map { d =>
              val sigInfo = new SignatureInformation(d.signature)
              sigInfo.setParameters(countParameters(d.signature).map(p => new ParameterInformation(p)).asJava)
              sigInfo
            }
            val help = new SignatureHelp()
            help.setSignatures(signatures.asJava)
            help.setActiveSignature(0)
            help.setActiveParameter(activeParameter(line, position.getCharacter))
            help
          } else {
            null
          }
        } else {
          null
        }
      } else {
        null
      }
    }
  }

  private def findMethodNameBeforePosition(line: String, char: Int): String = {
    var parenDepth = 0
    var i = math.min(char, line.length) - 1
    while (i >= 0) {
      val c = line(i)
      if (c == ')') parenDepth += 1
      else if (c == '(') {
        if (parenDepth == 0) {
          // Find the identifier immediately before '('
          var j = i - 1
          while (j >= 0 && line(j).isWhitespace) j -= 1
          var start = j
          while (start >= 0 && (line(start).isLetterOrDigit || line(start) == '_')) start -= 1
          return line.substring(start + 1, j + 1).trim
        } else {
          parenDepth -= 1
        }
      }
      i -= 1
    }
    ""
  }

  private def countParameters(signature: String): Seq[String] = {
    val parenIdx = signature.indexOf('(')
    if (parenIdx < 0) return Seq.empty
    val closeIdx = signature.indexOf(')', parenIdx)
    if (closeIdx < 0) return Seq.empty
    val params = signature.substring(parenIdx + 1, closeIdx).trim
    if (params.isEmpty) Seq.empty
    else params.split(',').map(_.trim).toSeq
  }

  private def activeParameter(line: String, char: Int): Integer = {
    var depth = 0
    var count = 0
    var i = math.min(char, line.length) - 1
    while (i >= 0) {
      val c = line(i)
      if (c == ')') depth += 1
      else if (c == '(') {
        if (depth == 0) return count
        else depth -= 1
      } else if (c == ',' && depth == 0) {
        count += 1
      }
      i -= 1
    }
    count
  }

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[java.util.List[LspEither[SymbolInformation, DocumentSymbol]]] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val state = documents.get(uri)
      val symbols = new java.util.ArrayList[LspEither[SymbolInformation, DocumentSymbol]]()

      if (state != null) {
        val classPattern = """(?:abstract\s+)?(?:sealed\s+)?(?:final\s+)?class\s+(\w+)""".r
        val interfacePattern = """interface\s+(\w+)""".r
        val enumPattern = """enum\s+(\w+)""".r
        val recordPattern = """record\s+(\w+)""".r
        val methodPattern = """def\s+(\w+)\s*[\[(]""".r
        val fieldPattern = """(?:static\s+)?(?:val|var)\s+(\w+)\s*:""".r

        val lines = state.content.split("\n")
        lines.zipWithIndex.foreach { case (line, lineNum) =>
          classPattern.findFirstMatchIn(line).foreach { m =>
            symbols.add(LspEither.forRight(createSymbol(m.group(1), SymbolKind.Class, uri, lineNum, m.start(1), m.end(1))))
          }
          interfacePattern.findFirstMatchIn(line).foreach { m =>
            symbols.add(LspEither.forRight(createSymbol(m.group(1), SymbolKind.Interface, uri, lineNum, m.start(1), m.end(1))))
          }
          enumPattern.findFirstMatchIn(line).foreach { m =>
            symbols.add(LspEither.forRight(createSymbol(m.group(1), SymbolKind.Enum, uri, lineNum, m.start(1), m.end(1))))
          }
          recordPattern.findFirstMatchIn(line).foreach { m =>
            symbols.add(LspEither.forRight(createSymbol(m.group(1), SymbolKind.Struct, uri, lineNum, m.start(1), m.end(1))))
          }
          methodPattern.findFirstMatchIn(line).foreach { m =>
            symbols.add(LspEither.forRight(createSymbol(m.group(1), SymbolKind.Method, uri, lineNum, m.start(1), m.end(1))))
          }
          // Class-level fields only (heuristic: 2-space indentation inside a class)
          if (line.matches("""^\s{2,3}(?:static\s+)?(?:val|var)\s+.*""")) {
            fieldPattern.findFirstMatchIn(line).foreach { m =>
              symbols.add(LspEither.forRight(createSymbol(m.group(1), SymbolKind.Field, uri, lineNum, m.start(1), m.end(1))))
            }
          }
        }
      }

      symbols
    }
  }

  override def rename(params: RenameParams): CompletableFuture[WorkspaceEdit] = {
    CompletableFuture.supplyAsync { () =>
      val uri = params.getTextDocument.getUri
      val state = documents.get(uri)
      val newName = params.getNewName

      if (state == null || newName == null || newName.isEmpty) {
        null
      } else {
        val position = params.getPosition
        val line = getLineAt(state.content, position.getLine)
        val word = extractWordAt(line, position.getCharacter)
        if (word.isEmpty) {
          null
        } else {
          val changes = new java.util.HashMap[String, java.util.List[TextEdit]]()
          val edits = new java.util.ArrayList[TextEdit]()
          val lines = state.content.split("\n")
          val wordPattern = ("""(?<![A-Za-z0-9_])""" + java.util.regex.Pattern.quote(word) + """(?![A-Za-z0-9_])""").r

          lines.zipWithIndex.foreach { case (lineText, lineNum) =>
            wordPattern.findAllMatchIn(lineText).foreach { m =>
              val range = new Range(
                new Position(lineNum, m.start),
                new Position(lineNum, m.end)
              )
              edits.add(new TextEdit(range, newName))
            }
          }

          if (edits.isEmpty) {
            null
          } else {
            changes.put(uri, edits)
            val edit = new WorkspaceEdit()
            edit.setChanges(changes)
            edit
          }
        }
      }
    }
  }

  private def extractWordAt(line: String, char: Int): String = {
    if (char < 0 || char >= line.length) {
      ""
    } else if (!line(char).isLetterOrDigit && line(char) != '_') {
      ""
    } else {
      var start = char
      while (start > 0 && (line(start - 1).isLetterOrDigit || line(start - 1) == '_')) {
        start -= 1
      }
      var end = char
      while (end < line.length && (line(end).isLetterOrDigit || line(end) == '_')) {
        end += 1
      }
      line.substring(start, end)
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
      val trimmedLine = line.trim

      // Class definitions
      classPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Class, uri, lineNum, m.start(1), m.end(1), trimmedLine))
      }

      // Interface definitions
      interfacePattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Interface, uri, lineNum, m.start(1), m.end(1), trimmedLine))
      }

      // Enum definitions
      enumPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Enum, uri, lineNum, m.start(1), m.end(1), trimmedLine))
      }

      // Record definitions
      recordPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Struct, uri, lineNum, m.start(1), m.end(1), trimmedLine))
      }

      // Method definitions
      methodPattern.findAllMatchIn(line).foreach { m =>
        symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Method, uri, lineNum, m.start(1), m.end(1), trimmedLine, trimmedLine))
      }

      // Field definitions (class level val/var)
      // Only match if not inside a method (heuristic: line starts with whitespace but not too much)
      if (line.matches("""^\s{2,3}(?:static\s+)?(?:val|var)\s+.*""")) {
        fieldPattern.findAllMatchIn(line).foreach { m =>
          symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Field, uri, lineNum, m.start(1), m.end(1), trimmedLine))
        }
      }

      // Local variable definitions (inside methods - more indentation)
      if (line.matches("""^\s{4,}(?:val|var)\s+.*""")) {
        localVarPattern.findAllMatchIn(line).foreach { m =>
          symbolTable.add(SymbolDefinition(m.group(1), SymbolKind.Variable, uri, lineNum, m.start(1), m.end(1), trimmedLine))
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
          symbolTable.add(SymbolDefinition(argName, SymbolKind.Variable, uri, lineNum, argStart, argEnd, trimmedLine))
        }
      }
    }
  }

  /**
   * Search all indexed symbols (across all open documents) for workspace symbol
   * requests. Returns symbols whose names contain the query string.
   */
  def searchSymbols(query: String): Seq[SymbolDefinition] = {
    val lowerQuery = query.toLowerCase
    symbolTable.allSymbols.filter(_.name.toLowerCase.contains(lowerQuery))
  }

  private def validateDocument(uri: String, content: String): Unit = {
    val fileName = extractFileName(uri)
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 100)
    val compiler = new OnionCompiler(config)

    val diagnostics =
      try {
        val result = compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(content), fileName)))
        result.diagnostics.errors.map(errorToDiagnostic(_, content)) ++
          result.diagnostics.warnings.map(warningToDiagnostic(_, content))
      } catch {
        case e: Throwable =>
          // Compiler crashes should never bring down the LSP server.
          Seq(internalErrorToDiagnostic(e, content))
      }

    client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics.asJava))
  }

  private def errorToDiagnostic(error: CompileError, content: String): Diagnostic = {
    val range = locationToRange(error.location, content)
    val diagnostic = new Diagnostic()
    diagnostic.setRange(range)
    diagnostic.setSeverity(DiagnosticSeverity.Error)
    diagnostic.setSource("onion")
    diagnostic.setMessage(error.message)
    diagnostic
  }

  private def warningToDiagnostic(warning: CompileWarning, content: String): Diagnostic = {
    val range = locationToRange(warning.location, content)
    val diagnostic = new Diagnostic()
    diagnostic.setRange(range)
    diagnostic.setSeverity(DiagnosticSeverity.Warning)
    diagnostic.setSource("onion")
    diagnostic.setCode(warning.category.code)
    diagnostic.setMessage(warning.message)
    diagnostic
  }

  private def internalErrorToDiagnostic(error: Throwable, content: String): Diagnostic = {
    val diagnostic = new Diagnostic()
    diagnostic.setRange(new Range(new Position(0, 0), new Position(0, 0)))
    diagnostic.setSeverity(DiagnosticSeverity.Error)
    diagnostic.setSource("onion")
    diagnostic.setMessage(s"Internal compiler error: ${error.getClass.getSimpleName}: ${error.getMessage}")
    diagnostic
  }

  private def locationToRange(location: onion.compiler.Location, content: String): Range = {
    val line = if (location != null) Math.max(0, location.line - 1) else 0
    val lineText = getLineAt(content, line)
    // `location.column` is 1-based and TAB-EXPANDED (JavaCC SimpleCharStream,
    // tabSize 8); convert it to a 0-based character index into `lineText` so
    // diagnostics land on the right token on tab-indented lines (issue #293).
    val column = if (location != null) charColumnFromExpanded(lineText, location.column) else 0
    val (start, end) = tokenRangeAt(lineText, column)
    new Range(new Position(line, start), new Position(line, end))
  }

  /** Maps a 1-based tab-expanded column (tabSize 8) to a 0-based character index. */
  private def charColumnFromExpanded(lineText: String, expandedColumn1Based: Int): Int = {
    val target = Math.max(0, expandedColumn1Based - 1)
    var expanded = 0
    var charIdx = 0
    while (charIdx < lineText.length && expanded < target) {
      if (lineText.charAt(charIdx) == '\t') expanded += 8 - (expanded % 8) else expanded += 1
      charIdx += 1
    }
    charIdx
  }

  private def tokenRangeAt(line: String, column: Int): (Int, Int) = {
    if (line.isEmpty || column < 0 || column >= line.length) return (column, column)
    var start = column
    while (start > 0 && isIdentifierPart(line(start - 1))) start -= 1
    var end = column
    while (end < line.length && isIdentifierPart(line(end))) end += 1
    if (start == end) {
      // No identifier at the position; highlight a single non-whitespace character.
      if (line(column).isWhitespace) (column, column)
      else (column, column + 1)
    } else {
      (start, end)
    }
  }

  private def isIdentifierPart(c: Char): Boolean =
    c.isLetterOrDigit || c == '_' || c == ':' || c == '.'

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

  private def getHoverInfo(word: String, uri: String): String = {
    if (keywords.contains(word)) {
      s"**Keyword**: `$word`\n\n${getKeywordDescription(word)}"
    } else if (builtinTypes.contains(word)) {
      s"**Type**: `$word`\n\n${getTypeDescription(word)}"
    } else if (stdLibClasses.contains(word)) {
      s"**Module**: `$word`\n\n${getModuleDescription(word)}"
    } else {
      val localDefs = symbolTable.lookupInUri(word, uri)
      if (localDefs.nonEmpty) {
        val defn = localDefs.head
        val line = if (defn.lineContent.nonEmpty) s"\n\n```onion\n${defn.lineContent}\n```" else ""
        s"**${symbolKindName(defn.kind)}**: `$defn.name`$line"
      } else {
        null
      }
    }
  }

  private def symbolKindName(kind: SymbolKind): String = kind match {
    case SymbolKind.Class => "Class"
    case SymbolKind.Interface => "Interface"
    case SymbolKind.Enum => "Enum"
    case SymbolKind.Struct => "Record"
    case SymbolKind.Method => "Method"
    case SymbolKind.Field => "Field"
    case SymbolKind.Variable => "Variable"
    case _ => "Symbol"
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
