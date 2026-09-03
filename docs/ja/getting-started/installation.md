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

   これにより現在の Scala 向け target ディレクトリ（現状は `target/scala-3.3.7/`）に `onion.jar` が作成されます

5. `~/.local/bin` にコマンドを入れたい場合はローカル installer を実行：
   ```bash
   ./install.sh
   ```

## インストールの確認

Onionが正しくインストールされたことを確認：

```bash
# 配布版を使用する場合
onionc --help
onion repl
onion-repl

# JARを直接使用する場合
java -jar onion.jar --help
```

## 起動時間と JVM フラグ

Onion は実行のたびに JVM を起動し、その大半はコンパイラのクラスロードに費やされます。
[AppCDS](https://openjdk.org/jeps/350) のアーカイブでクラスを共有すると、これがおよそ半分に
なります。JDK 25 での実測で、`onion run/Hello.on` が 0.73 秒から 0.38 秒になりました。

`curl | sh` のインストーラはアーカイブを自動で作ります。配布 zip から入れた場合は、展開後に
一度だけ次を実行してください。

```bash
ONION_GENERATE_CDS=1 onion run/Hello.on
```

`onion.jar` と同じ場所に `lib/onion.jsa`（約 15MB）が作られます。生成をランチャ経由にしている
のは意図的で、アーカイブは作成時と同じ classpath でしか使えないためです。Onion を更新したときや
JDK を切り替えたときは作り直してください。作り直すまでの間、JVM は古いアーカイブを黙って拒否し、
高速化なしで通常どおり動作します。

追加の JVM フラグは `ONION_JAVA_OPTS` で渡します。

```bash
ONION_JAVA_OPTS="-Xmx4g" onion big-job.on
ONION_JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" onion script.on
```

`ONION_DEBUG_STARTUP=1` を設定すると、ランチャが抑止している JVM のクラス共有メッセージが
表示されます。アーカイブが使われない理由はこれで分かります。

### コンパイルデーモン

`onionc` は毎回 JVM を起動し、コンパイラを読み込み、JIT で温めてからようやくファイルを
コンパイルします。`ONION_DAEMON=1` を設定すると、`onionc` はコマンドラインを常駐の
コンパイラプロセス（初回に自動起動、ユーザー・JDK・Onion のインストールごとに 1 つ）へ
渡し、その出力と終了コードを中継します。デーモンは温まったコンパイラを実行間で保持する
ので、典型的な 1 ファイルのコンパイルはずっと短い時間で終わります。

```bash
export ONION_DAEMON=1
onionc Hello.on            # 初回はデーモンを起動し、以後はそれを再利用する
```

デーモンは 30 分仕事が無ければ自分で終了します。手動では `java -cp onion.jar
onion.tools.daemon.DaemonClient stop`（または `status`）で操作できます。待ち受けは自分
だけが読めるディレクトリ（`$XDG_RUNTIME_DIR` または一時ディレクトリ。
`ONION_DAEMON_SOCKET` で上書き可）の Unix ドメインソケットで、Java 16 以降が必要です。
デーモンに到達・起動できないときは、`onionc` はこれまでどおりプロセス内でコンパイル
します。`ONION_DAEMON_JAVA_OPTS` はデーモン自身の JVM フラグです。`onion script.on` も
デーモンを使います。コンパイルはデーモンが行ってクラスを返し、プログラム自体はこれまで
どおり自分のプロセスで動きます。

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
