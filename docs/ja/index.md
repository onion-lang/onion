# Onion プログラミング言語

![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)

**Onion** は、外部の雑なデータを検査済みで可逆な道具に変えるための静的型付き言語です。JVM 上で動き、Java を直接呼べます。

境界——ログ形式、JSON 文書、コマンドライン——を一度記述すれば、読み取り・書き戻し・失敗の報告・CLI がすべてその記述から出てきます。1000行のログに5行の壊れた行があれば、995行*と*読めなかった5行の両方が手に入ります。

## 主な特徴

- **静的型付け** - ローカル型推論によりコンパイル時にエラーを検出
- **Null安全** - Nullable型 (`T?`)、安全呼び出し (`?.`)・安全添字 (`?[]`)・Elvis (`?:`)・非nullアサーション (`!!`)、スマートキャスト、nullable対応ジェネリクス
- **データ型** - レコード（分解宣言・ジェネリクス対応）、データ付きenum、sealed階層の網羅性検査つきパターンマッチ
- **双方向レコード** - `record ... from re"..." derive!(Json, Yaml)` 一つの宣言から parse/format と JSON/YAML の相互変換を*両方向*とも導出
- **コンパイル時仕様** - レコードの `law` / `example` 句をコンパイラがビルド時に実行するので、`parse∘format == id` のような不変条件が機械検査される
- **型クラス** - `trait` / `instance` と、制約付きジェネリクスへの辞書渡し（`sum[T: Numeric]`）
- **簡潔なクラス定義** - プライマリコンストラクタ `class Point(val x: Int, val y: Int)`
- **オブジェクト指向** - クラス、継承、インターフェース、メソッドオーバーロード
- **関数型要素** - ラムダ式、クロージャ、第一級関数
- **モナド合成** - Option、Result、Future型のためのdo記法
- **非同期プログラミング** - 関数型コンビネータを備えた組み込みFuture型
- **Java相互運用** - Javaライブラリとフレームワークへの直接アクセス
- **JVMターゲット** - 効率的なJVMバイトコードにコンパイル
- **簡潔な構文** - 末尾ラムダをサポートするクリーンで読みやすいコード

## クイックサンプル

```onion
// Hello World
println("Hello, World!")

// 継承を伴うクラス定義
class Calculator extends JFrame conforms ActionListener {
  var result: Long

  public:
    def this {
      // コンストラクタロジック
      this.result = 0L;
    }

    def calculate(x: Long, y: Long): Long {
      return x + y;
    }
}

// ラムダ式
val filter: String -> Boolean = (line: String) -> {
  return line.startsWith("ERROR");
}

// selectによるパターンマッチング
val value = 3
select value {
  case 0, 1, 2:
    println("Low")
  case 3, 4, 5:
    println("Medium")
  else:
    println("High")
}

// モナド合成のためのdo記法
val result: Option[Int] = do[Option] {
  x <- parseNumber("42")
  y <- parseNumber("10")
  ret x + y
}

// Futureによる非同期プログラミング
val future: Future[String] = Future::async(() -> { return fetchData(); })
future.map { data -> processData(data) }
      .onSuccess { result -> println(result) }

// 安全呼び出し演算子によるNull安全
val name: String? = getUserName()  // Nullable型
val upper: Object? = name?.toUpperCase()  // 安全呼び出し - nameがnullならnullを返す
val display: String = name ?: "unknown"  // デフォルト値のためのエルビス演算子
```

## はじめに

- [インストールガイド](getting-started/installation.md) - システムにOnionをセットアップ
- [Hello Worldチュートリアル](getting-started/hello-world.md) - 最初のOnionプログラム
- [クイックスタート](getting-started/quick-start.md) - 基本的な言語機能

## 言語ガイド

- [言語概要](guide/overview.md) - 言語の設計思想
- [基本構文](guide/basic-syntax.md) - 変数、演算子、式
- [変数と型](guide/variables-and-types.md) - 型システムとキャスト
- [Null安全](guide/null-safety.md) - nullable型とスマートキャスト
- [制御フロー](guide/control-flow.md) - if、ループ、select、例外処理
- [関数](guide/functions.md) - 関数定義とラムダ
- [ラムダ式](guide/lambda-expressions.md) - クロージャと末尾ラムダ
- [クラスとオブジェクト](guide/classes-and-objects.md) - オブジェクト指向
- [継承](guide/inheritance.md) - サブクラスとインターフェース実装
- [コレクション](guide/collections.md) - リスト、マップ、組み込みパイプライン
- [型クラス](guide/type-classes.md) - `trait`、`instance`、制約付きジェネリクス
- [Javaとの相互運用](guide/java-interop.md) - Javaライブラリの利用
- [Shape](guide/shapes.md) - 名前付きの境界: パース・書き戻し・失敗した理由すべて
- [スクリプティング](guide/scripting.md) - リテラル、derive!、law/example、パイプライン

## リファレンス

- [言語仕様](reference/specification.md) - Onion言語の完全な仕様
- [標準ライブラリ](reference/stdlib.md) - IO、Option、Result、Future、Rand、Assert、Timing、Json、Yamlなど
- [コンパイラアーキテクチャ](reference/compiler-architecture.md) - コンパイラの内部構造
- [エラーコード](reference/error-codes.md) - よくあるコンパイルエラーと修正方法
- [効果](reference/effects.md) - 効果の語彙・効果表・`--effects`
- [末尾呼び出し最適化](compiler/tail-call-optimization.md) - TCOの検出と今後の変換

## サンプル集

- [サンプル集の概要](examples/index.md) - プログラム例の一覧
- [基本例](examples/basic.md) - Hello World、配列、ループ、入出力
- [オブジェクト指向の例](examples/oop.md) - クラス、継承、インターフェース、委譲
- [関数型の例](examples/functional.md) - ラムダ、クロージャ、再帰、do記法、Future
- [スクリプティングとCLIの例](examples/scripting.md) - コマンドライン引数、プロセス実行、ファイル入出力
- [JSONとHTTPの例](examples/json-http.md) - JSON/YAMLの解析、HTTPクライアント
- [非同期・並行処理の例](examples/async.md) - Future、非同期合成、do記法
- [エラーハンドリングの例](examples/error-handling.md) - Option、Result、検証パターン

## ツール

- [コンパイラ（onionc）](tools/compiler.md) - Onionソースファイルを.classファイルにコンパイル
- [スクリプトランナー（onion）](tools/script-runner.md) - Onionスクリプトを直接実行（コンパイルして実行）
- [プロジェクトCLI（onion new/build/run/test/clean）](tools/project-cli.md) - 設定より規約に基づく複数ファイルプロジェクト
- [REPL](tools/repl.md) - 対話型REPL

## コントリビューション

- [開発ガイド](contributing/development.md) - Onionへの貢献方法
- [ソースからビルド](contributing/building.md) - コンパイラをソースからビルド
- [リリース手順](RELEASING.md) - リリースプロセス

## デザインノート

- [型クラス](../design/type-classes.md) - `trait`/`instance`、coherence、辞書渡し
- [ジェネリクス設計](GENERICS_DESIGN.md) - 消去ベースジェネリクスの設計
- [パーサーリファクタリング](parser-refactoring.md) - Builderパターンによる文法とASTの分離
- [品質基準](quality-bar.md) - 実用的品質の測定可能な指標

## プロジェクト履歴

2005年にJavaで書かれたOnionは、パーサーにJavaCCを使用する以外は、Scala 3.3.7で完全に書き直されました。コンパイラは古典的なマルチフェーズアーキテクチャに従っています：

1. **パース** - JavaCC生成パーサーが型なしASTを生成
2. **書き換え** - ASTの正規化と変換
3. **型チェック** - 型推論・名前解決・オーバーロード解決・typed AST 構築
4. **末尾呼び出し最適化** - 自己末尾再帰のループ化
5. **相互再帰最適化** - `@TailRecursive` グループの状態機械化
6. **コード生成** - typed AST から ASM で直接バイトコード生成

## コミュニティ

- [GitHubリポジトリ](https://github.com/onion-lang/onion)
- [イシュートラッカー](https://github.com/onion-lang/onion/issues)
- [コントリビューションガイド](../contributing/development.md)

## ライセンス

Onionはオープンソースソフトウェアです。詳細は[LICENSE](https://github.com/onion-lang/onion/blob/main/LICENSE)をご覧ください。

このソフトウェアには[Apache Software Foundation](http://www.apache.org/)が開発したソフトウェアが含まれています。
