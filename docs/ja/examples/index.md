# サンプル集

このセクションでは、Onionプログラミング言語の機能を紹介するサンプルプログラムを掲載しています。

## サンプルの種類

- [基本例](basic.md) - Hello World、配列、ループ、入出力などの基本的なプログラム
- [オブジェクト指向の例](oop.md) - クラス、継承、インターフェース、委譲、JavaBean、GUI
- [関数型の例](functional.md) - ラムダ、クロージャ、再帰、高階関数、do記法、Future
- [スクリプティングとCLIの例](scripting.md) - コマンドライン引数、プロセス実行、ファイル入出力
- [JSONとHTTPの例](json-http.md) - JSON/YAMLの解析、HTTPクライアント
- [非同期・並行処理の例](async.md) - Future、非同期合成、do記法
- [エラーハンドリングの例](error-handling.md) - Option、Result、検証パターン

## サンプルの実行

`run/` ディレクトリには英語ドキュメントで紹介されているサンプルと同様のプログラムが入っています。これらを `onion` コマンドで直接実行できます。

```bash
onion run/Hello.on
onion run/ConfigApp.on
onion run/JsonApiClient.on
onion run/AsyncDownloader.on
```

または、`onionc` でコンパイルしてからJavaで実行します。

```bash
onionc -d build run/Hello.on
java -cp build Hello
```

## 次のステップ

- [基本例](basic.md) から始める
- [実用的な例](scripting.md) でCLIやHTTP、非同期処理を学ぶ
- [言語ガイド](../guide/basic-syntax.md) で各機能の詳細を学ぶ
- [標準ライブラリ](../reference/stdlib.md) を参照する
