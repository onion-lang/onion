import * as path from 'path';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext) {
    console.log('Onion Language extension is now active');

    // Start the language client
    startLanguageClient(context);

    // Register the restart command
    const restartCommand = vscode.commands.registerCommand('onion.restartServer', async () => {
        if (client) {
            await client.stop();
        }
        startLanguageClient(context);
        vscode.window.showInformationMessage('Onion Language Server restarted');
    });

    context.subscriptions.push(restartCommand);
}

function startLanguageClient(context: vscode.ExtensionContext) {
    const config = vscode.workspace.getConfiguration('onion');

    // Get server path from configuration or environment
    let serverPath = config.get<string>('serverPath');

    if (!serverPath || serverPath === '') {
        // Try to find onion-lsp from ONION_HOME
        const onionHome = process.env['ONION_HOME'];
        if (onionHome) {
            serverPath = path.join(onionHome, 'bin', 'onion-lsp');
        } else {
            // Try common installation paths
            const possiblePaths = [
                '/usr/local/bin/onion-lsp',
                '/usr/bin/onion-lsp',
                path.join(process.env['HOME'] || '', '.local', 'bin', 'onion-lsp')
            ];

            for (const p of possiblePaths) {
                // We'll just use the first one; proper file existence check would need fs
                serverPath = p;
                break;
            }
        }
    }

    if (!serverPath) {
        vscode.window.showErrorMessage(
            'Onion Language Server not found. Please set ONION_HOME environment variable or configure onion.serverPath.'
        );
        return;
    }

    // Server options - run onion-lsp as a process
    const serverOptions: ServerOptions = {
        run: {
            command: serverPath,
            transport: TransportKind.stdio
        },
        debug: {
            command: serverPath,
            transport: TransportKind.stdio
        }
    };

    // Client options
    const clientOptions: LanguageClientOptions = {
        // Register the server for Onion files
        documentSelector: [{ scheme: 'file', language: 'onion' }],
        synchronize: {
            // Notify the server about file changes to .on files
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.on')
        },
        outputChannelName: 'Onion Language Server'
    };

    // Create and start the language client
    client = new LanguageClient(
        'onionLanguageServer',
        'Onion Language Server',
        serverOptions,
        clientOptions
    );

    // Start the client (also starts the server)
    client.start();

    context.subscriptions.push({
        dispose: () => {
            if (client) {
                client.stop();
            }
        }
    });
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
