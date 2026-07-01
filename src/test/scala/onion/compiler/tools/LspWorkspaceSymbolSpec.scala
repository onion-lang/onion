package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

/**
 * Tests for LSP workspace symbol functionality.
 */
class LspWorkspaceSymbolSpec extends AnyFunSpec {

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

  private def workspaceSymbol(server: OnionLanguageServer, query: String): java.util.List[? <: org.eclipse.lsp4j.WorkspaceSymbol] = {
    val params = new WorkspaceSymbolParams(query)
    server.getWorkspaceService.symbol(params).get().getRight
  }

  describe("OnionLanguageServer workspace symbols") {

    it("advertises workspace symbol support in initialize result") {
      val client = new RecordingClient()
      val server = newServer(client)

      val initParams = new InitializeParams()
      val result = server.initialize(initParams).get()

      assert(result.getCapabilities.getWorkspaceSymbolProvider != null)
    }

    it("returns workspace symbols from open documents") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///a.on", "class Calculator {\n  def add(a: Int, b: Int): Int = a + b\n}")
      openDocument(service, "file:///b.on", "class Logger {\n  def log(msg: String): void { }\n}")

      val params = new WorkspaceSymbolParams("Calc")
      val symbols = server.getWorkspaceService.symbol(params).get().getRight.asScala

      assert(symbols.exists(_.getName == "Calculator"))
      assert(!symbols.exists(_.getName == "Logger"))
    }

    it("returns all symbols for an empty query") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///a.on", "class Box { }\n")

      val params = new WorkspaceSymbolParams("")
      val symbols = server.getWorkspaceService.symbol(params).get().getRight.asScala

      assert(symbols.exists(_.getName == "Box"))
    }
  }
}
