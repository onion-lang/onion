# サンプル集

このセクションでは、Onionプログラミング言語のさまざまな機能とプログラミングパターンを示す、実践的なサンプルプログラムを紹介します。

## サンプルプログラム

すべてのサンプルはOnionリポジトリの `run/` ディレクトリにあります。

### 基本的なプログラム

- [Hello World](basic.md) - シンプルな出力
- [配列](basic.md) - 配列操作
- [ユーザー入力](basic.md) - 入力の読み込み

### オブジェクト指向の例

- [クラスとオブジェクト](oop.md) - 基本的なOOP
- [継承](oop.md) - クラスの拡張
- [インターフェース](oop.md) - インターフェースの実装

### 関数型プログラミング

- [ラムダ式](functional.md) - 無名関数
- [クロージャ](functional.md) - 変数のキャプチャ
- [再帰](functional.md) - 再帰的アルゴリズム

### 実用的なユースケース

- [スクリプティングとCLI](scripting.md) - コマンドライン引数、プロセス実行、ファイル入出力
- [JSONとHTTP](json-http.md) - JSON/YAMLの解析、HTTPクライアントの基礎
- [非同期・並行処理](async.md) - Future、非同期合成、do記法
- [エラーハンドリング](error-handling.md) - Option、Result、検証パターン

## サンプルの実行

### スクリプトランナーを使う

```bash
cd run
onion Hello.on
```

### コンパイルして実行する

```bash
onionc Hello.on
java Hello
```

## サンプル一覧

| ファイル | 説明 | 扱っているトピック |
|------|-------------|----------------|
| `Hello.on` | Hello World | 基本的な入出力 |
| `Array.on` | 配列操作 | 配列、ループ |
| `StringCat.on` | 文字列の連結 | 文字列、配列 |
| `ReadLine.on` | ユーザー入力 | 入出力、文字列 |
| `List.on` | 動的リスト | ArrayList、`<<` 演算子 |
| `Foreach.on` | Foreachループ | イテレーション、コレクション |
| `Select.on` | パターンマッチング | select式 |
| `LineFilter.on` | ラムダによるフィルタリング | ラムダ、クロージャ |
| `LineCounter.on` | ファイル処理 | 再帰、入出力 |
| `Factorial.on` | 再帰 | 再帰関数 |
| `Primes.on` | エラトステネスの篩と素因数分解 | 真偽値配列、for/whileループ |
| `Delegation.on` | 委譲パターン | インターフェース、forward |
| `Calculator.on` | GUI電卓 | Swing、イベント処理 |
| `Bean.on` | JavaBeanパターン | シリアライズ、getter/setter |
| `OrderReport.on` | より大きなデータパイプライン | レコード、enum、コレクションパイプライン |
| `DeptReport.on` | CSVのグループ集計パイプライン | Csv、レコード、`Maps::groupBy`、Stats |
| `ExprEval.on` | 式評価器 | インターフェース、多態性 |
| `AdtExpr.on` | 式評価器（ADT enumとして実装） | `enum` の `case`、select、網羅性検査 |
| `StatsApp.on` | 統計処理 | ジェネリクス、拡張メソッド |
| `TodoManager.on` | タスク管理 | レコード、enum、拡張メソッド |
| `ShapeProcessor.on` | 図形ジオメトリ | 継承、拡張メソッド |
| `TextAnalyzer.on` | テキスト統計 | 文字列/リストの拡張メソッド |
| `WordStats.on` | 単語頻度のテキスト統計 | 文字列、`Maps::countBy`、レコード |
| `LogSummary.on` | shape-firstなログ分析 | `record ... from re"..."`、`Maps::countBy` |
| `ConfigApp.on` | CLI + 設定ファイル | 引数、YAML、`derive!(Yaml)` |
| `JsonApiClient.on` | JSON + HTTPクライアント | Http、Json、レコード |
| `AsyncDownloader.on` | 並行Future | Future、do記法 |
| `ShellPipeline.on` | シェルプロセスのパイプライン | Proc、ファイル入出力 |
| `ResultValidation.on` | エラーを伴う検証 | Option、Result |
| `StaticImports.on` | 静的メソッドのインポート | `import { Class::method }` |
| `UnitConverter.on` | CLI単位変換ツール | 拡張メソッド、Args |
| `SetOperations.on` | 集合演算 | `onion.Sets`（和集合、積集合、差集合） |
| `PrimitivePredicate.on` | プリミティブ述語によるフィルタリング | `java.util.function.Predicate[Int]`、`removeIf` |
| `PrimitiveFunctionalInterfaces.on` | プリミティブ型のJava関数型インターフェース | プリミティブ型引数を持つ `Predicate`、`Supplier`、`Function` |
| `SortWithPrimitiveComparator.on` | プリミティブ比較器によるソート | `java.util.Comparator[Int]`、プリミティブなラムダのブリッジ |
| `CollectionUtilities.on` | Map/listのユーティリティ | `onion.Maps`、`onion.Iterables`、カスタムソート |
| `FizzBuzz.on` | 定番のFizzBuzz | ループ、剰余、条件分岐 |
| `Fibonacci.on` | 再帰版と反復版のフィボナッチ数列 | 再帰、ループ、`Timing` |
| `DataClass.on` | レコードの `equals`/`hashCode`/`copy` デモ | レコード、`toString`、`copy` |
| `Bidirectional.on` | あらゆる方向に変換できる1つのレコード | `from re"..."`、`derive!(Json, Yaml)`、`example` |
| `CsvProcessor.on` | CSVヘッダの解析と集計 | `Csv::parseWithHeader` |
| `CliArgsDemo.on` | 手動のCLI引数解析 | `onion.Args` |
| `FileWordCounter.on` | ファイルからの単語数カウント | ファイル入出力、`BufferedReader` |
| `Generics.on` | ジェネリックなBoxクラス | ジェネリッククラス、型パラメータ |
| `PairSample.on` | ジェネリックなPairクラス | ジェネリッククラス、複数の型パラメータ |
| `Extension.on` | 組み込み型への拡張メソッド | `extension` ブロック |
| `NullSafety.on` | Nullable型と安全なナビゲーション | `T?`、`?.` safe call演算子 |
| `ValVarInference.on` | `val`/`var` の型推論 | ローカル型推論、可変/不変 |
| `PrimitivePrint.on` | プリミティブ値の出力 | `IO::println`/`IO::print` |
| `FunctionTypesSample.on` | 関数型パラメータ | 関数型、ラムダ |
| `JavaCollectionsSample.on` | Javaコレクションとの相互運用 | `java.util.ArrayList`、`Collections` |
| `JavaGenerics.on` | Javaジェネリクスとの相互運用 | `java.util.ArrayList[String]` |
| `GuessNumber.on` | 数当てゲーム | `BufferedReader`、制御フロー |
| `TodoApp.on` | 対話型CLI ToDoリスト | `ArrayList`、コンソール入出力 |
| `HttpJsonClient.on` | HTTP + JSONクライアント | `onion.Http` |
| `RegexLogParser.on` | ログ行の解析 | `re"..."` 正規表現によるselectパターン |
| `RecordLaws.on` | レコードの `law`/`example` 節 | `law`、`example`、コンパイル時検査 |
| `SchemePrefix.on` | ユーザー定義スキームプレフィックス文字列 | `prefix"..."` raw文字列リテラル |
| `ShapeFirst.on` | shape-firstスクリプティングのツアー | `re"..."`、pipeline、do記法、auto-CLI |
| `FixedWidthDemo.on` | ユーザー定義の `Shape` インスタンス | `onion.Shape`、Outcome/Defect、`law` |
| `JsonYamlShapeDemo.on` | レコードに対する名前付きJSON/YAML shape | `shape name = json`/`yaml` |
| `ConfigEditDemo.on` | ロスレスな設定編集 | Lens、`Residue`、コメント/順序の保持 |
| `BrokenLogDemo.on` | 部分的な失敗を許容するログ解析 | `onion.Shape`、`Outcome::values`/`defects` |
| `ToolDemo.on` | `tool` 宣言から生成されるCLI | `tool`、capability、`--help`/`--contract`/`--plan` |
| `ArchiveDemo.on` | zip・unzip・gzipの往復 | `onion.Archive` |

## 学習の道のり

### 初級

1. `Hello.on` から始める - 基本構文
2. `Array.on` を試す - データ構造
3. `Foreach.on` を試す - イテレーション

### 中級

1. `Factorial.on` を学ぶ - 再帰
2. `LineFilter.on` を学ぶ - ラムダ
3. `Bean.on` を復習する - OOPパターン
4. `UnitConverter.on` を試す - 拡張メソッドとCLI引数

### 上級

1. `Calculator.on` を分析する - GUIプログラミング
2. `Delegation.on` を理解する - デザインパターン
3. `LineCounter.on` を調べる - ファイル処理

### 実践

1. `ConfigApp.on` - CLI駆動の設定ローダーを構築する
2. `JsonApiClient.on` - JSONを解析しHTTP APIを呼び出す
3. `AsyncDownloader.on` - タスクを並行実行する
4. `ShellPipeline.on` - シェルコマンドをオーケストレーションする
5. `ResultValidation.on` - 関数型スタイルでエラーを処理する

## 次のステップ

- [基本例](basic.md) - シンプルなプログラム
- [オブジェクト指向の例](oop.md) - オブジェクト指向のコード
- [関数型の例](functional.md) - 関数型プログラミング
- [スクリプティングの例](scripting.md) - CLIとプロセス自動化
- [JSONとHTTPの例](json-http.md) - ネットワークとデータフォーマット
- [非同期の例](async.md) - 並行プログラミング
- [エラーハンドリングの例](error-handling.md) - 検証とエラー
