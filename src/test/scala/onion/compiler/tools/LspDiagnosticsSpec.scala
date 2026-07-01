package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

/**
 * Tests for LSP diagnostic publishing.
 */
class LspDiagnosticsSpec extends AnyFunSpec {

  private def newServer(client: LanguageClient): OnionLanguageServer = {
    val server = new OnionLanguageServer()
    server.connect(client)
    server
  }

  /** A fake LSP client that records published diagnostics. */
  private class RecordingClient extends LanguageClient {
    val diagnostics = new CopyOnWriteArrayList[PublishDiagnosticsParams]()

    override def publishDiagnostics(params: PublishDiagnosticsParams): Unit = {
      diagnostics.add(params)
    }

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

  private def openDocument(service: OnionTextDocumentService, uri: String, text: String): Unit = {
    val doc = new TextDocumentItem()
    doc.setUri(uri)
    doc.setLanguageId("onion")
    doc.setVersion(1)
    doc.setText(text)
    val params = new DidOpenTextDocumentParams(doc)
    service.didOpen(params)
  }

  describe("OnionTextDocumentService diagnostics") {

    it("publishes no diagnostics for a valid program") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "def main(): void { println(\"ok\") }")

      assert(client.diagnostics.size() == 1)
      val params = client.diagnostics.get(0)
      assert(params.getUri == "file:///test.on")
      assert(params.getDiagnostics.isEmpty)
    }

    it("publishes a diagnostic for a syntax error") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///bad.on", "def main(): void { val x: Int = \"hello\" }")

      assert(client.diagnostics.size() == 1)
      val params = client.diagnostics.get(0)
      assert(params.getUri == "file:///bad.on")
      assert(params.getDiagnostics.size() >= 1)

      val diag = params.getDiagnostics.get(0)
      assert(diag.getSeverity == DiagnosticSeverity.Error)
      assert(diag.getSource == "onion")
      assert(diag.getMessage != null)
      assert(diag.getRange.getStart.getLine == 0)
      assert(diag.getRange.getStart.getCharacter >= 0)
      assert(diag.getRange.getEnd.getCharacter > diag.getRange.getStart.getCharacter)
    }

    it("clears diagnostics when the document is closed") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///clear.on", "def main(): void { val x: Int = \"hello\" }")
      assert(client.diagnostics.size() == 1)
      assert(!client.diagnostics.get(0).getDiagnostics.isEmpty)

      val closeParams = new DidCloseTextDocumentParams()
      closeParams.setTextDocument(new TextDocumentIdentifier("file:///clear.on"))
      service.didClose(closeParams)

      assert(client.diagnostics.size() == 2)
      assert(client.diagnostics.get(1).getDiagnostics.isEmpty)
    }

    it("publishes diagnostics on incremental changes") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///change.on", "def main(): void { }")
      assert(client.diagnostics.get(0).getDiagnostics.isEmpty)

      val changeEvent = new TextDocumentContentChangeEvent()
      changeEvent.setText("def main(): void { val x: Int = \"oops\" }")
      val changeParams = new DidChangeTextDocumentParams()
      changeParams.setTextDocument(new VersionedTextDocumentIdentifier("file:///change.on", 2))
      changeParams.setContentChanges(java.util.Collections.singletonList(changeEvent))
      service.didChange(changeParams)

      assert(client.diagnostics.size() == 2)
      assert(!client.diagnostics.get(1).getDiagnostics.isEmpty)
    }
  }
}
