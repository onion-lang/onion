# サンプル集

このセクションでは、Onionプログラミング言語の機能を紹介するサンプルプログラムを掲載しています。

## サンプルの種類

- [基本例](basic.md) - Hello World、配列、ループ、入出力などの基本的なプログラム
- [オブジェクト指向の例](oop.md) - クラス、継承、インターフェース、委譲、JavaBean、GUI
- [関数型の例](functional.md) - ラムダ、クロージャ、再帰、高階関数、do記法、Future

## サンプルの実行

`run/` ディレクトリには英語ドキュメントで紹介されているサンプルと同様のプログラムが入っています。これらを `onion` コマンドで直接実行できます。

```bash
onion run/Hello.on
onion run/Array.on
onion run/Factorial.on
```

または、`onionc` でコンパイルしてからJavaで実行します。

```bash
onionc -d build run/Hello.on
java -cp build Hello
```

## 次のステップ

- [基本例](basic.md) から始める
- [言語ガイド](../guide/basic-syntax.md) で各機能の詳細を学ぶ
- [標準ライブラリ](../reference/stdlib.md) を参照する
