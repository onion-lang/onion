package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}

/**
 * Tests for LSP hover functionality.
 */
class LspHoverSpec extends AnyFunSpec {

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

  private def hover(service: OnionTextDocumentService, uri: String, line: Int, character: Int): Hover = {
    val params = new HoverParams()
    params.setTextDocument(new TextDocumentIdentifier(uri))
    params.setPosition(new Position(line, character))
    service.hover(params).get()
  }

  describe("OnionTextDocumentService hover") {

    it("returns hover for a built-in keyword") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "def main(): void { }")
      val result = hover(service, "file:///test.on", 0, 0)

      assert(result != null)
      assert(result.getContents.getRight.getValue.contains("Keyword"))
    }

    it("returns hover for a built-in type") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "val x: Int = 1")
      val result = hover(service, "file:///test.on", 0, 8)

      assert(result != null)
      assert(result.getContents.getRight.getValue.contains("Type"))
    }

    it("returns hover for a user-defined class") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Point { }\nval p = new Point()")
      val result = hover(service, "file:///test.on", 1, 15)

      assert(result != null)
      assert(result.getContents.getRight.getValue.contains("Class"))
      assert(result.getContents.getRight.getValue.contains("Point"))
    }

    it("returns hover for a user-defined method") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Point {\n  def area(): Int = 42\n}\nval p = new Point()\nprintln(p.area())")
      val result = hover(service, "file:///test.on", 4, 12)

      assert(result != null)
      assert(result.getContents.getRight.getValue.contains("Method"))
      assert(result.getContents.getRight.getValue.contains("area"))
    }

    it("returns null for unknown words") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "def main(): void { }")
      val result = hover(service, "file:///test.on", 0, 25)

      assert(result == null)
    }

    it("shows the defining line in hover for user-defined symbols") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Point {\n  def area(): Int = 42\n}\nval p = new Point()\nprintln(p.area())")
      val result = hover(service, "file:///test.on", 4, 12)

      assert(result != null)
      val value = result.getContents.getRight.getValue
      assert(value.contains("def area(): Int = 42"))
    }
  }
}
