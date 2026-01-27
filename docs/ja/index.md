# Onion プログラミング言語

![Build Status](https://github.com/onion-lang/onion/actions/workflows/scala.yml/badge.svg?branch=main)

**Onion** は、JVMバイトコードにコンパイルされる静的型付けのオブジェクト指向プログラミング言語です。静的型付けの堅牢性と、簡潔で表現力豊かな構文、そしてJavaとのシームレスな相互運用性を兼ね備えています。

## 主な特徴

- **静的型付け** - ローカル型推論によりコンパイル時にエラーを検出
- **Null安全** - Nullable型 (`T?`) と安全呼び出し演算子 (`?.`) でヌルポインタ例外を防止
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
IO::println("Hello, World!")

// 継承を伴うクラス定義
class Calculator : JFrame <: ActionListener {
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
    IO::println("Low")
  case 3, 4, 5:
    IO::println("Medium")
  else:
    IO::println("High")
}

// モナド合成のためのdo記法
val result: Option[Int] = do[Option] {
  x <- parseNumber("42")
  y <- parseNumber("10")
  ret x + y
}

// Futureによる非同期プログラミング
val future: Future[String] = Future::async(() -> { return fetchData(); })
future.map { data => processData(data) }
      .onSuccess { result => IO::println(result) }

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

- [基本構文](guide/basic-syntax.md) - 変数、演算子、式

## リファレンス

- [標準ライブラリ](reference/stdlib.md) - IO、Option、Result、Future、Rand、Assert、Timingなど

## ツール

- **onionc** - Onionソースファイルを.classファイルにコンパイル
- **onion** - Onionスクリプトを直接実行（コンパイルして実行）
- **Shell** - 対話型REPL

## プロジェクト履歴

2005年にJavaで書かれたOnionは、パーサーにJavaCCを使用する以外は、Scala 3.3.7で完全に書き直されました。コンパイラは古典的なマルチフェーズアーキテクチャに従っています：

1. **パース** - JavaCC生成パーサーが型なしASTを生成
2. **書き換え** - ASTの正規化と変換
3. **型チェック** - 型推論と検証
4. **コード生成** - ASMベースのバイトコード生成

## コミュニティ

- [GitHubリポジトリ](https://github.com/onion-lang/onion)
- [イシュートラッカー](https://github.com/onion-lang/onion/issues)
- [コントリビューションガイド](../contributing/development.md)

## ライセンス

Onionはオープンソースソフトウェアです。詳細は[LICENSE](https://github.com/onion-lang/onion/blob/main/LICENSE)をご覧ください。

このソフトウェアには[Apache Software Foundation](http://www.apache.org/)が開発したソフトウェアが含まれています。
