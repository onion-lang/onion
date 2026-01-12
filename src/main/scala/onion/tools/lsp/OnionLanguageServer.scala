/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.      *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.lsp

import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services._
import org.eclipse.lsp4j.jsonrpc.Launcher

import java.io.{InputStream, OutputStream}
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters._

/**
 * Onion Language Server implementing LSP protocol.
 * Provides IDE features: diagnostics, hover, completion, go-to-definition.
 */
class OnionLanguageServer extends LanguageServer with LanguageClientAware {
  private var client: LanguageClient = _
  private val textDocumentService = new OnionTextDocumentService(this)
  private val workspaceService = new OnionWorkspaceService(this)
  private var shutdownRequested = false

  def getClient: LanguageClient = client

  override def connect(client: LanguageClient): Unit = {
    this.client = client
    textDocumentService.connect(client)
  }

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] = {
    CompletableFuture.supplyAsync { () =>
      val capabilities = new ServerCapabilities()

      // Text document sync - full document sync for simplicity
      val syncOptions = new TextDocumentSyncOptions()
      syncOptions.setOpenClose(true)
      syncOptions.setChange(TextDocumentSyncKind.Full)
      syncOptions.setSave(new SaveOptions(true))
      capabilities.setTextDocumentSync(syncOptions)

      // Completion support
      val completionOptions = new CompletionOptions()
      completionOptions.setTriggerCharacters(java.util.Arrays.asList(".", ":", "<"))
      completionOptions.setResolveProvider(false)
      capabilities.setCompletionProvider(completionOptions)

      // Hover support
      capabilities.setHoverProvider(true)

      // Go to definition
      capabilities.setDefinitionProvider(true)

      // Document symbols (outline)
      capabilities.setDocumentSymbolProvider(true)

      val result = new InitializeResult(capabilities)
      val serverInfo = new ServerInfo("Onion Language Server", "1.0.0")
      result.setServerInfo(serverInfo)
      result
    }
  }

  override def initialized(params: InitializedParams): Unit = {
    client.logMessage(new MessageParams(MessageType.Info, "Onion Language Server initialized"))
  }

  override def shutdown(): CompletableFuture[Object] = {
    shutdownRequested = true
    CompletableFuture.completedFuture(null)
  }

  override def exit(): Unit = {
    System.exit(if (shutdownRequested) 0 else 1)
  }

  override def getTextDocumentService: TextDocumentService = textDocumentService

  override def getWorkspaceService: WorkspaceService = workspaceService
}

object OnionLanguageServer {
  def main(args: Array[String]): Unit = {
    startServer(System.in, System.out)
  }

  def startServer(in: InputStream, out: OutputStream): Unit = {
    val server = new OnionLanguageServer()
    val launcher = Launcher.createLauncher(server, classOf[LanguageClient], in, out)
    server.connect(launcher.getRemoteProxy)
    launcher.startListening()
  }
}
