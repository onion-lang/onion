package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

/**
 * Tests for LSP document symbol (outline) functionality.
 */
class LspDocumentSymbolSpec extends AnyFunSpec {

  private class RecordingClient extends LanguageClient {
    val diagnostics = new CopyOnWriteArrayList[PublishDiagnosticsParams]()
    override def publishDiagnostics(params: PublishDiagnosticsParams): Unit = diagnostics.add(params)
    override def showMessage(messageParams: MessageParams): Unit = ()
    override def showMessageRequest(messageParams: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
      CompletableFuture.completedFuture(new MessageActionItem())
    override def logMessage(messageParams: MessageParams): Unit = ()
    override def telemetryEvent(`object`: Any): Unit = ()
    override def registerCapability(params: RegistrationParams): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
    override def unregisterCapability(params: UnregistrationParams): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
    override def showDocument(params: ShowDocumentParams): CompletableFuture[ShowDocumentResult] =
      CompletableFuture.completedFuture(new ShowDocumentResult(true))
    override def createProgress(params: WorkDoneProgressCreateParams): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
    override def refreshSemanticTokens(): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
    override def refreshInlayHints(): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
    override def refreshInlineValues(): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
    override def refreshCodeLenses(): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
    override def refreshDiagnostics(): CompletableFuture[Void] =
      CompletableFuture.completedFuture(null)
  }

  private def newServer(client: LanguageClient): OnionLanguageServer = {
    val server = new OnionLanguageServer()
    server.connect(client)
    server
  }

  private def openDocument(service: OnionTextDocumentService, uri: String, text: String): Unit = {
    val doc = new TextDocumentItem()
    doc.setUri(uri)
    doc.setLanguageId("onion")
    doc.setVersion(1)
    doc.setText(text)
    val params = new DidOpenTextDocumentParams(doc)
    service.didOpen(params)
  }

  private def documentSymbols(service: OnionTextDocumentService, uri: String): Seq[DocumentSymbol] = {
    val params = new DocumentSymbolParams()
    params.setTextDocument(new TextDocumentIdentifier(uri))
    service.documentSymbol(params).get().asScala.toSeq.map(_.getRight)
  }

  describe("OnionTextDocumentService document symbols") {

    it("advertises document symbol support in initialize result") {
      val client = new RecordingClient()
      val server = newServer(client)

      val result = server.initialize(new InitializeParams()).get()
      assert(result.getCapabilities.getDocumentSymbolProvider != null)
    }

    it("returns classes, methods, and enums") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on",
        """class Shape {
          |  def area(): Int = 0
          |}
          |enum Color {
          |  Red, Green, Blue
          |}
          |""".stripMargin)

      val symbols = documentSymbols(service, "file:///test.on")
      val names = symbols.map(_.getName)
      assert(names.contains("Shape"))
      assert(names.contains("area"))
      assert(names.contains("Color"))
    }

    it("returns class-level fields") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on",
        """class Point {
          |  val x: Int = 0
          |  var y: Int = 0
          |  def move(dx: Int, dy: Int): void { }
          |}
          |""".stripMargin)

      val symbols = documentSymbols(service, "file:///test.on")
      val names = symbols.map(_.getName)
      assert(names.contains("Point"))
      assert(names.contains("x"))
      assert(names.contains("y"))
      assert(names.contains("move"))
    }

    it("does not include deeply indented local variables") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on",
        """class Counter {
          |  def count(): Int {
          |    val local: Int = 0
          |    return local
          |  }
          |}
          |""".stripMargin)

      val symbols = documentSymbols(service, "file:///test.on")
      val names = symbols.map(_.getName)
      assert(names.contains("Counter"))
      assert(names.contains("count"))
      assert(!names.contains("local"))
    }
  }
}
