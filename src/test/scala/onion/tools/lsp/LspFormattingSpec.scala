package onion.tools.lsp

import org.eclipse.lsp4j.{DocumentFormattingParams, TextDocumentIdentifier, TextDocumentItem}
import org.eclipse.lsp4j.{DidOpenTextDocumentParams, MessageActionItem, MessageParams, PublishDiagnosticsParams, ShowMessageRequestParams}
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters.*

/**
 * The editor's format-on-save, sharing `OnionFormatter` with `onion fmt` so the two cannot
 * disagree about what formatted means — a difference there shows up as a file that changes
 * every time it crosses between an editor and CI.
 */
class LspFormattingSpec extends AnyFunSpec {

  private val Uri = "file:///tmp/format-demo.on"

  /** Opening a document publishes diagnostics, so a client has to be there to receive them. */
  private val silentClient = new org.eclipse.lsp4j.services.LanguageClient {
    override def telemetryEvent(o: Object): Unit = ()
    override def publishDiagnostics(p: PublishDiagnosticsParams): Unit = ()
    override def showMessage(p: MessageParams): Unit = ()
    override def showMessageRequest(
      p: ShowMessageRequestParams
    ): java.util.concurrent.CompletableFuture[MessageActionItem] =
      java.util.concurrent.CompletableFuture.completedFuture(null)
    override def logMessage(p: MessageParams): Unit = ()
  }

  private def service(content: String): OnionTextDocumentService = {
    val service = new OnionTextDocumentService(null)
    service.connect(silentClient)
    val item = new TextDocumentItem(Uri, "onion", 1, content)
    service.didOpen(new DidOpenTextDocumentParams(item))
    service
  }

  private def edits(content: String) = {
    val params = new DocumentFormattingParams()
    params.setTextDocument(new TextDocumentIdentifier(Uri))
    service(content).formatting(params).get().asScala.toList
  }

  it("returns one edit replacing the document, so the buffer cannot be left half-formatted") {
    val result = edits("IO::println(\"hi\" , 1)\n")
    assert(result.size == 1)
    assert(result.head.getNewText == "IO::println(\"hi\", 1)\n")
    assert(result.head.getRange.getStart.getLine == 0)
    assert(result.head.getRange.getStart.getCharacter == 0)
  }

  it("covers the final line, so the tail of the file is not left behind") {
    val content = "IO::println(1 , 2)\nIO::println(3 , 4)\n"
    val range = edits(content).head.getRange
    // The document ends on an empty line after the trailing newline.
    assert(range.getEnd.getLine == 2)
    assert(range.getEnd.getCharacter == 0)
  }

  it("offers no edit when the document is already formatted") {
    // An editor showing "1 change" on every save of an unchanged file trains people to
    // ignore it.
    assert(edits("IO::println(\"hi\", 1)\n").isEmpty)
  }

  it("offers no edit for a document it does not have open") {
    val params = new DocumentFormattingParams()
    params.setTextDocument(new TextDocumentIdentifier("file:///tmp/never-opened.on"))
    assert(new OnionTextDocumentService(null).formatting(params).get().isEmpty)
  }

  it("agrees with onion fmt exactly") {
    val content = "def f(x: Int): Int = g(x , 1)\n"
    assert(edits(content).head.getNewText == onion.tools.format.OnionFormatter.format(content).text)
  }
}
