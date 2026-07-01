import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;
let runOutputChannel: vscode.OutputChannel | undefined;

export function activate(context: vscode.ExtensionContext) {
    console.log('Onion Language extension is now active');

    // Syntax highlighting works purely from the TextMate grammar and language
    // configuration -- no running process required. The language server
    // (onion-lsp) is optional: we only start it when an executable is actually
    // present, so highlighting-only users never see "server not found" errors.
    startLanguageClient();

    const restartCommand = vscode.commands.registerCommand('onion.restartServer', async () => {
        if (client) {
            await client.stop();
            client = undefined;
        }
        const started = startLanguageClient();
        if (started) {
            vscode.window.showInformationMessage('Onion Language Server restarted');
        } else {
            vscode.window.showWarningMessage(
                'Onion Language Server not found. Set onion.serverPath or ONION_HOME to enable it. Syntax highlighting still works without it.'
            );
        }
    });

    const runScriptCommand = vscode.commands.registerCommand('onion.runScript', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showWarningMessage('No active Onion script to run.');
            return;
        }
        const document = editor.document;
        if (document.languageId !== 'onion') {
            vscode.window.showWarningMessage('The active file is not an Onion script.');
            return;
        }
        if (document.isUntitled) {
            vscode.window.showWarningMessage('Save the Onion script before running it.');
            return;
        }

        const scriptPath = document.fileName;
        const onionPath = resolveOnionPath();
        if (!onionPath) {
            vscode.window.showWarningMessage(
                'Onion runner (onion) not found. Set ONION_HOME or ensure onion is on your PATH.'
            );
            return;
        }

        if (!runOutputChannel) {
            runOutputChannel = vscode.window.createOutputChannel('Onion Script');
        }
        runOutputChannel.show(true);
        runOutputChannel.appendLine(`Running: ${scriptPath}`);
        runOutputChannel.appendLine('');

        const task = new vscode.Task(
            { type: 'onion', task: 'run' },
            vscode.TaskScope.Workspace,
            'Run Onion Script',
            'onion',
            new vscode.ShellExecution(`"${onionPath}" "${scriptPath}"`)
        );
        task.presentationOptions = {
            reveal: vscode.TaskRevealKind.Always,
            panel: vscode.TaskPanelKind.Shared,
            clear: true
        };

        try {
            const execution = await vscode.tasks.executeTask(task);
            const disposable = vscode.tasks.onDidEndTaskProcess(e => {
                if (e.execution === execution) {
                    disposable.dispose();
                    if (e.exitCode !== 0) {
                        runOutputChannel?.appendLine(`\nProcess exited with code ${e.exitCode}`);
                    } else {
                        runOutputChannel?.appendLine('\nDone.');
                    }
                }
            });
            context.subscriptions.push(disposable);
        } catch (e) {
            vscode.window.showErrorMessage(`Failed to run Onion script: ${e}`);
        }
    });

    context.subscriptions.push(restartCommand, runScriptCommand);
}

/** Resolve the onion-lsp executable, or undefined if none is configured/present. */
function resolveServerPath(): string | undefined {
    const configured = vscode.workspace.getConfiguration('onion').get<string>('serverPath');
    if (configured && configured.trim() !== '') {
        return fs.existsSync(configured) ? configured : undefined;
    }

    const candidates: string[] = [];
    const onionHome = process.env['ONION_HOME'];
    if (onionHome) {
        candidates.push(path.join(onionHome, 'bin', 'onion-lsp'));
    }
    candidates.push(
        '/usr/local/bin/onion-lsp',
        '/usr/bin/onion-lsp',
        path.join(process.env['HOME'] || '', '.local', 'bin', 'onion-lsp')
    );

    return candidates.find(p => fs.existsSync(p));
}

/** Resolve the onion script runner, or undefined if none is present. */
function resolveOnionPath(): string | undefined {
    const candidates: string[] = [];
    const onionHome = process.env['ONION_HOME'];
    if (onionHome) {
        candidates.push(path.join(onionHome, 'bin', 'onion'));
    }
    candidates.push(
        '/usr/local/bin/onion',
        '/usr/bin/onion',
        path.join(process.env['HOME'] || '', '.local', 'bin', 'onion')
    );

    return candidates.find(p => fs.existsSync(p));
}

/** Start the language client if a server executable exists. Returns true if started. */
function startLanguageClient(): boolean {
    const serverPath = resolveServerPath();
    if (!serverPath) {
        // No server available -- highlighting-only mode. Stay quiet.
        console.log('Onion: no onion-lsp executable found; running in syntax-highlighting-only mode.');
        return false;
    }

    const serverOptions: ServerOptions = {
        run: { command: serverPath, transport: TransportKind.stdio },
        debug: { command: serverPath, transport: TransportKind.stdio }
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'onion' }],
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.on')
        },
        outputChannelName: 'Onion Language Server'
    };

    client = new LanguageClient(
        'onionLanguageServer',
        'Onion Language Server',
        serverOptions,
        clientOptions
    );
    client.start();
    return true;
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
