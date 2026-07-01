package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

class LspRenameSpec extends AnyFunSpec {

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

  private def rename(service: OnionTextDocumentService, uri: String, line: Int, character: Int, newName: String): WorkspaceEdit = {
    val params = new RenameParams()
    params.setTextDocument(new TextDocumentIdentifier(uri))
    params.setPosition(new Position(line, character))
    params.setNewName(newName)
    service.rename(params).get()
  }

  describe("OnionTextDocumentService rename") {

    it("advertises rename support in initialize result") {
      val client = new RecordingClient()
      val server = newServer(client)

      val result = server.initialize(new InitializeParams()).get()
      assert(result.getCapabilities.getRenameProvider != null)
    }

    it("renames all occurrences of a symbol in the document") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on",
        """class Counter {
          |  def count(): Int = 0
          |}
          |val c = new Counter()
          |c.count()
          |""".stripMargin)

      val edit = rename(service, "file:///test.on", 0, 8, "Ticker")

      assert(edit != null)
      val changes = edit.getChanges.asScala
      assert(changes.contains("file:///test.on"))
      val edits = changes("file:///test.on").asScala
      assert(edits.exists(e => e.getRange.getStart.getLine == 0 && e.getNewText == "Ticker"))
      assert(edits.exists(e => e.getRange.getStart.getLine == 3 && e.getNewText == "Ticker"))
    }

    it("returns null when cursor is not on a word") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class C { }")
      val edit = rename(service, "file:///test.on", 0, 7, "X")

      assert(edit == null)
    }
  }
}
