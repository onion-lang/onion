package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

/**
 * Tests for LSP signature help functionality.
 */
class LspSignatureHelpSpec extends AnyFunSpec {

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

  private def signatureHelp(service: OnionTextDocumentService, uri: String, line: Int, character: Int): SignatureHelp = {
    val params = new SignatureHelpParams()
    params.setTextDocument(new TextDocumentIdentifier(uri))
    params.setPosition(new Position(line, character))
    service.signatureHelp(params).get()
  }

  describe("OnionTextDocumentService signature help") {

    it("advertises signature help support in initialize result") {
      val client = new RecordingClient()
      val server = newServer(client)

      val result = server.initialize(new InitializeParams()).get()
      assert(result.getCapabilities.getSignatureHelpProvider != null)
    }

    it("returns the method signature when the cursor is inside a call") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Calc {\n  def add(a: Int, b: Int): Int = a + b\n}\nval c = new Calc()\nc.add(1, 2)")
      val help = signatureHelp(service, "file:///test.on", 4, 8)

      assert(help != null)
      val sigs = help.getSignatures.asScala
      assert(sigs.exists(_.getLabel.contains("def add(a: Int, b: Int): Int")))
    }

    it("sets the active parameter based on comma count") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Calc {\n  def add(a: Int, b: Int): Int = a + b\n}\nval c = new Calc()\nc.add(1, 2)")
      val help = signatureHelp(service, "file:///test.on", 4, 10)

      assert(help != null)
      assert(help.getActiveParameter == 1)
    }

    it("returns null when not inside a method call") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "def main(): void { }\n")
      val help = signatureHelp(service, "file:///test.on", 1, 0)

      assert(help == null)
    }
  }
}
