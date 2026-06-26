# インストール

このガイドでは、Onionプログラミング言語をシステムにインストールしてセットアップする方法を説明します。

## 前提条件

OnionはJVM上で動作し、以下が必要です。

- **Java Development Kit (JDK) 17以降**

## クイックインストール（推奨）

インストールスクリプトを実行します。最新の `onion.jar` を GitHub Releases からダウンロードし、`onion`、`onionc`、`onion-repl` コマンドを `~/.local/bin` にインストールします。

```bash
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh
```

`~/.local/bin` が PATH に含まれていることを確認してください。

```bash
export PATH="$HOME/.local/bin:$PATH"
```

## インストールの確認

Onionが正しくインストールされたことを確認します。

```bash
onionc --help
onion repl
onion-repl
```

## その他のインストール方法

### 特定のバージョンをインストール

```bash
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | sh -s -- --version=v0.2.0-M14
```

### ソースチェックアウトからビルド

**SBT（Scala Build Tool）** が必要です。

```bash
git clone https://github.com/onion-lang/onion && cd onion && ./install.sh --from-source
```

### カスタム場所にインストール

デフォルトのインストール先は `~/.local` です。`ONION_INSTALL_DIR` で変更できます。

```bash
curl -fsSL https://raw.githubusercontent.com/onion-lang/onion/develop/install.sh | ONION_INSTALL_DIR=/opt/onion sh
```

### ビルド済み配布版をダウンロード

1. [GitHubリリースページ](https://github.com/onion-lang/onion/releases)から最新リリースをダウンロード
2. アーカイブを展開:
   ```bash
   unzip onion-dist.zip
   cd onion-dist
   ```
3. `bin`ディレクトリをPATHに追加:
   ```bash
   export PATH=$PATH:/path/to/onion-dist/bin
   ```

## IDEセットアップ

### Visual Studio Code

公式のOnion拡張機能はまだありませんが、以下を使用できます。

- 類似言語用の汎用シンタックスハイライト
- 依存関係管理用のJava/Scala拡張機能

### IntelliJ IDEA

Onionコンパイラ自体を開発する場合:

1. Scalaプラグインをインストール
2. プロジェクトをSBTプロジェクトとしてインポート
3. IDEが自動的に依存関係をダウンロード

## 次のステップ

- [Hello Worldチュートリアル](hello-world.md) - 最初のOnionプログラムを書く
- [クイックスタートガイド](quick-start.md) - 基本機能を学ぶ
