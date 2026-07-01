package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

/**
 * Tests for LSP completion functionality.
 */
class LspCompletionSpec extends AnyFunSpec {

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

  private def completion(service: OnionTextDocumentService, uri: String, line: Int, character: Int): java.util.List[CompletionItem] = {
    val params = new CompletionParams()
    params.setTextDocument(new TextDocumentIdentifier(uri))
    params.setPosition(new Position(line, character))
    service.completion(params).get().getLeft
  }

  describe("OnionTextDocumentService completion") {

    it("includes keyword completions") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "cla")
      val items = completion(service, "file:///test.on", 0, 3)
      val labels = items.asScala.map(_.getLabel).toSeq
      assert(labels.contains("class"))
    }

    it("includes user-defined class symbols") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Customer { }\nval c = new Cust")
      val items = completion(service, "file:///test.on", 1, 18)
      val labels = items.asScala.map(_.getLabel).toSeq
      assert(labels.contains("Customer"))
    }

    it("includes user-defined method symbols") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Calc {\n  def add(a: Int, b: Int): Int = a + b\n}\nval c = new Calc()\nc.ad")
      val items = completion(service, "file:///test.on", 4, 4)
      val labels = items.asScala.map(_.getLabel).toSeq
      assert(labels.contains("add"))
    }

    it("includes user-defined variable symbols") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "def main(): void {\n  val counter: Int = 0\n  count\n}")
      val items = completion(service, "file:///test.on", 2, 7)
      val labels = items.asScala.map(_.getLabel).toSeq
      assert(labels.contains("counter"))
    }

    it("filters completions by prefix") {
      val client = new RecordingClient()
      val server = newServer(client)
      val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]

      openDocument(service, "file:///test.on", "class Alpha { }\nclass Beta { }\nval a = new Al")
      val items = completion(service, "file:///test.on", 2, 14)
      val labels = items.asScala.map(_.getLabel).toSeq
      assert(labels.contains("Alpha"))
      assert(!labels.contains("Beta"))
    }
  }
}
