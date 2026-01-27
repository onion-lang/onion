# インストール

このガイドでは、Onionプログラミング言語をシステムにインストールしてセットアップする方法を説明します。

## 前提条件

OnionはJVM上で動作し、以下が必要です：

- **Java Development Kit (JDK) 17以降**
- **SBT (Scala Build Tool)** - ソースからビルドする場合

## インストール方法

### 方法1: ビルド済みバイナリをダウンロード（推奨）

1. [GitHubリリースページ](https://github.com/onion-lang/onion/releases)から最新リリースをダウンロード
2. アーカイブを展開：
   ```bash
   unzip onion-dist.zip
   cd onion-dist
   ```
3. `bin`ディレクトリをPATHに追加：
   ```bash
   export PATH=$PATH:/path/to/onion-dist/bin
   ```

### 方法2: ソースからビルド

1. リポジトリをクローン：
   ```bash
   git clone https://github.com/onion-lang/onion.git
   cd onion
   ```

2. SBTでプロジェクトをビルド：
   ```bash
   sbt compile
   ```

3. 配布パッケージを作成：
   ```bash
   sbt dist
   ```

   これにより`target/onion-dist.zip`に配布用ZIPが作成されます

4. または、スタンドアロンJARをビルド：
   ```bash
   sbt assembly
   ```

   これにより`target/scala-3.3.7/`ディレクトリに`onion.jar`が作成されます

## インストールの確認

Onionが正しくインストールされたことを確認：

```bash
# 配布版を使用する場合
onionc --help

# JARを直接使用する場合
java -jar onion.jar --help
```

## IDEセットアップ

### Visual Studio Code

公式のOnion拡張機能はまだありませんが、以下を使用できます：

- 類似言語用の汎用シンタックスハイライト
- 依存関係管理用のJava/Scala拡張機能

### IntelliJ IDEA

Onionコンパイラ自体を開発する場合：

1. Scalaプラグインをインストール
2. プロジェクトをSBTプロジェクトとしてインポート
3. IDEが自動的に依存関係をダウンロード

## 次のステップ

- [Hello Worldチュートリアル](hello-world.md) - 最初のOnionプログラムを書く
- [クイックスタートガイド](quick-start.md) - 基本機能を学ぶ
