package onion.compiler.tools

import onion.tools.lsp.{OnionLanguageServer, OnionTextDocumentService}
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.LanguageClient
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.{CompletableFuture, CopyOnWriteArrayList}
import scala.jdk.CollectionConverters._

/**
 * Issue #293: the compiler's column is tab-expanded (tabSize 8), so an LSP
 * diagnostic on a tab-indented line must be mapped back to a character column,
 * or it lands past the real token.
 */
class LspTabDiagnosticSpec extends AnyFunSpec {

  private class RecordingClient extends LanguageClient {
    val diagnostics = new CopyOnWriteArrayList[PublishDiagnosticsParams]()
    override def publishDiagnostics(params: PublishDiagnosticsParams): Unit = diagnostics.add(params)
    override def showMessage(m: MessageParams): Unit = ()
    override def showMessageRequest(m: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
      CompletableFuture.completedFuture(new MessageActionItem())
    override def logMessage(m: MessageParams): Unit = ()
    override def telemetryEvent(o: Any): Unit = ()
    override def registerCapability(p: RegistrationParams): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
    override def unregisterCapability(p: UnregistrationParams): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
    override def showDocument(p: ShowDocumentParams): CompletableFuture[ShowDocumentResult] = CompletableFuture.completedFuture(new ShowDocumentResult(true))
    override def createProgress(p: WorkDoneProgressCreateParams): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
    override def refreshSemanticTokens(): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
    override def refreshInlayHints(): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
    override def refreshInlineValues(): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
    override def refreshCodeLenses(): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
    override def refreshDiagnostics(): CompletableFuture[Void] = CompletableFuture.completedFuture(null)
  }

  private def diagnosticsFor(text: String): Seq[Diagnostic] = {
    val client = new RecordingClient()
    val server = new OnionLanguageServer()
    server.connect(client)
    val service = server.getTextDocumentService.asInstanceOf[OnionTextDocumentService]
    val doc = new TextDocumentItem()
    doc.setUri("file:///tab.on")
    doc.setLanguageId("onion")
    doc.setVersion(1)
    doc.setText(text)
    service.didOpen(new DidOpenTextDocumentParams(doc))
    client.diagnostics.asScala.flatMap(_.getDiagnostics.asScala).toSeq
  }

  describe("LSP diagnostics on tab-indented lines (#293)") {
    it("maps a tab-expanded column back to a character position") {
      // The line is "\tval x: Int = \"s\"": the string literal that triggers the
      // type error sits at characters 14..16, while its tab-expanded column is
      // ~21. The diagnostic must point into the character line, not past it.
      val diags = diagnosticsFor("\tval x: Int = \"s\"\n")
      assert(diags.nonEmpty, "expected a type error diagnostic")
      val ch = diags.head.getRange.getStart.getCharacter
      assert(ch <= 16, s"diagnostic char column $ch overshoots the tab-indented token (line is 17 chars)")
    }

    it("still positions a diagnostic correctly on a space-indented line") {
      val diags = diagnosticsFor("  val x: Int = \"s\"\n")
      assert(diags.nonEmpty)
      val ch = diags.head.getRange.getStart.getCharacter
      assert(ch >= 13 && ch <= 17, s"unexpected char column $ch on a space-indented line")
    }
  }
}
