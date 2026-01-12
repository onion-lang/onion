/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.      *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.lsp

import org.eclipse.lsp4j._
import org.eclipse.lsp4j.services.WorkspaceService

/**
 * Handles workspace operations for the Onion Language Server.
 */
class OnionWorkspaceService(server: OnionLanguageServer) extends WorkspaceService {

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    // Handle configuration changes if needed
    val client = server.getClient
    if (client != null) {
      client.logMessage(new MessageParams(MessageType.Info, "Configuration updated"))
    }
  }

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit = {
    // Handle file system changes
    val client = server.getClient
    if (client != null) {
      val changes = params.getChanges
      if (changes != null && !changes.isEmpty) {
        client.logMessage(new MessageParams(MessageType.Info, s"Watched files changed: ${changes.size} files"))
      }
    }
  }
}
