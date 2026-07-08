# ソースからビルド

Onionコンパイラをソースからビルドする完全ガイドです。

## 前提条件

### 必要なソフトウェア

1. **Java Development Kit (JDK) 17 以降**

   バージョン確認:
   ```bash
   java -version
   javac -version
   ```

   インストール:
   - **macOS**: `brew install openjdk@17`
   - **Ubuntu/Debian**: `sudo apt install openjdk-17-jdk`
   - **Windows**: [Adoptium](https://adoptium.net/) からダウンロード

2. **SBT（Scala Build Tool）**

   バージョン確認:
   ```bash
   sbt version
   ```

   インストール:
   - **macOS**: `brew install sbt`
   - **Ubuntu/Debian**: [公式ガイド](https://www.scala-sbt.org/download.html) に従う
   - **Windows**: MSIインストーラをダウンロード

3. **Git**

   ```bash
   git --version
   ```

## リポジトリのクローン

```bash
git clone https://github.com/onion-lang/onion.git
cd onion
```

## ビルド

### 基本的なコンパイル

プロジェクト全体をコンパイルします。

```bash
sbt compile
```

これにより以下が行われます。
1. 依存関係のダウンロード（初回のみ）
2. JavaCC文法からのパーサー生成
3. ScalaおよびJavaソースファイルのコンパイル

想定される出力:
```
[info] compiling ... Scala sources and ... Java sources to target/scala-3.3.7/classes ...
[success] Total time: 45 s
```

### クリーンビルド

生成されたファイルをすべて削除してから再ビルドします。

```bash
sbt clean compile
```

### インクリメンタルコンパイル

SBTは自動的にインクリメンタルコンパイルを行います。変更したファイルのみが再コンパイルされます。

```bash
# ファイルを編集
vim src/main/scala/onion/compiler/Typing.scala

# 高速な再コンパイル
sbt compile
```

## テスト

### すべてのテストを実行

```bash
sbt test
```

### 特定のテストスイートを実行

```bash
sbt 'testOnly *HelloWorldSpec'
sbt 'testOnly *FactorialSpec'
```

### パターンに一致するテストを実行

```bash
sbt 'testOnly *String*'
```

## ディストリビューションの作成

### スタンドアロンJAR

すべての依存関係を含むfat JARを作成します。

```bash
sbt assembly
```

出力: `target/scala-3.3.7/onion.jar`

JAR のデフォルトメインクラスはコンパイラ（`onionc`）なので、`java -jar` はソースを
`.class` に**コンパイル**します:

```bash
java -jar target/scala-3.3.7/onion.jar Hello.on   # Hello.on をコンパイル
```

スクリプトを**実行**するには `ScriptRunner` メインクラスを呼びます（`onion` ランチャが
行っているのと同じです）:

```bash
java -cp target/scala-3.3.7/onion.jar onion.tools.ScriptRunner Hello.on
```

### ディストリビューションパッケージ

完全なZIPディストリビューションを作成します。

```bash
sbt dist
```

出力: `target/onion-dist.zip`

内容:
```
onion-dist/
├── onion.jar          # メインコンパイラJAR
├── lib/               # 依存関係
│   ├── asm-9.8.jar
│   ├── scala-library-3.3.7.jar
│   └── ...
├── bin/               # 実行ファイル
│   ├── onionc
│   ├── onion
│   └── onion-repl
├── run/               # サンプルプログラム
│   ├── Hello.on
│   ├── Array.on
│   └── ...
└── README.md
```

展開して使う:
```bash
unzip target/onion-dist.zip
cd onion-dist
export PATH=$PATH:$PWD/bin
onionc run/Hello.on
```

## 開発ビルド

### 継続的コンパイル

変更を検知して自動的に再コンパイルします。

```bash
sbt ~compile
```

### SBTから実行

JARを作成せずに実行します。

```bash
sbt 'run Hello.on'
```

スクリプトランナーを実行:

```bash
sbt 'runScript run/Hello.on'
```

推奨REPLを実行:

```bash
sbt repl
```

ベンチマークスイートを実行:

```bash
sbt bench
```

コンパイルプロファイルを出力:

```bash
sbt 'runMain onion.tools.CompilerFrontend --profile-compile run/Hello.on'
```

### 対話型SBT

SBTシェルを起動:

```bash
sbt
```

その後、コマンドを実行:
```
> compile
> test
> run Hello.on
> ~compile  # 継続的コンパイル
> exit
```

## パーサー開発

### 文法の修正

1. JavaCC文法を編集:
   ```bash
   vim grammar/JJOnionParser.jj
   ```

2. パーサーを再生成:
   ```bash
   sbt clean compile
   ```

パーサーは以下の場合に自動的に再生成されます。
- 文法ファイルが生成済みパーサーより新しい場合
- `sbt clean` を実行した場合

### パーサーの場所

生成されたパーサー:
```
target/scala-3.3.7/src_managed/main/java/onion/compiler/parser/
├── JJOnionParser.java
├── Token.java
├── TokenManager.java
└── ...
```

## IDE設定

### IntelliJ IDEA

1. Scalaプラグインをインストール
2. プロジェクトを開く: `File > Open > build.sbt を選択`
3. インデックス作成が完了するまで待つ
4. ビルド: `Build > Build Project`

実行設定:
- メインクラス: `onion.tools.CompilerFrontend`
- プログラム引数: `path/to/source.on`
- 作業ディレクトリ: `$PROJECT_DIR$`

### Visual Studio Code

1. Metals拡張機能をインストール
2. プロジェクトフォルダを開く
3. インポートが完了するまで待つ
4. コマンドパレットからビルド: `Metals: Compile workspace`

## トラブルシューティング

### パーサー生成に失敗する

```bash
# クリーンして再ビルド
sbt clean
rm -rf target
sbt compile
```

### メモリ不足

SBTのメモリを増やします。
```bash
export SBT_OPTS="-Xmx2G"
sbt compile
```

または `.sbtopts` を編集:
```
-Xmx2G
-Xss2M
```

### 依存関係の問題

Ivyキャッシュをクリア:
```bash
rm -rf ~/.ivy2/cache
sbt update
sbt compile
```

### コンパイルエラー

ScalaとJavaのバージョンを確認:
```bash
sbt scalaVersion
java -version
```

要件を満たしていることを確認:
- Scala 3.3.7
- Java 17+

## ビルド設定

### build.sbt

主な設定:

```scala
// バージョンは sbt-dynver によって git タグから導出される
scalaVersion := "3.3.7"
name := "onion"
organization := "org.onion_lang"

// 依存関係
libraryDependencies ++= Seq(
  "org.ow2.asm" % "asm" % "9.8",
  "net.java.dev.javacc" % "javacc" % "5.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test"
)

// メインクラス
mainClass := Some("onion.tools.CompilerFrontend")
```

### コンパイラオプション

Scalaコンパイラオプション:
```scala
scalacOptions ++= Seq(
  "-encoding", "utf8",
  "-unchecked",
  "-deprecation",
  "-feature"
)
```

Javaコンパイラオプション:
```scala
javacOptions ++= Seq(
  "-source", "17",
  "-Xlint:unchecked"
)
```

## パフォーマンスのヒント

### より速いビルド

1. インクリメンタルコンパイルを活用（デフォルト）
2. 必要な場合以外は clean を避ける
3. JVMメモリを増やす
4. 複数のコマンドはSBTシェル内で実行

### 並列コンパイル

SBTはデフォルトで並列コンパイルを行います。スレッド数を調整する場合:

```scala
Global / concurrentRestrictions := Seq(
  Tags.limitAll(4)
)
```

## プラットフォーム固有の注意

### macOS

`JAVA_HOME` の設定が必要な場合があります。
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Linux

十分なメモリがあることを確認:
```bash
free -h
# 必要に応じてスワップを増やす
```

### Windows

PowerShellまたはGit Bashを使用します。パスはバックスラッシュを使います。
```powershell
sbt assembly
# ソースをコンパイル:
java -jar target\scala-3.3.7\onion.jar Hello.on
# またはスクリプトを実行:
java -cp target\scala-3.3.7\onion.jar onion.tools.ScriptRunner Hello.on
```

## 次のステップ

- [開発ガイド](development.md) - Onionへの貢献
- [コンパイラアーキテクチャ](../../reference/compiler-architecture.md) - 内部構造
- [テストの実行](development.md#testing-strategy) - テストガイド
