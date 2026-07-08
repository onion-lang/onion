# スクリプティング

Onion は bash を超えたスクリプトを書くための機能を備えています。

## スキームプレフィックス付きリテラル

`re"..."` / `file"..."` / `http"..."` は RAW 文字列リテラル（バックスラッシュをそのまま通す）で、`re(...)` / `file(...)` / `http(...)` の呼び出しに展開されます。

```onion
val p    = re"\d+-\d+"                   // コンパイル済み Pattern
val text = file"notes.txt".text()         // lines() / json() / csv() なども
val body = http"https://api.example.com".get()
```

プレフィックスは `re`/`file`/`http` に限りません。**任意の識別子**に直接続く RAW 文字列は
`prefix("...")` に展開されるスキームリテラルなので、その名前の関数を定義するだけで
自分のプレフィックスを作れます（新しい機構は不要）：

```onion
def sql(query: String): String = "[SQL] " + query.trim()
def money(raw: String): Double = Double::parseDouble(raw.substring(1))

sql"SELECT * FROM users WHERE id = 5"   // -> sql("SELECT ...")
money"$19.99"                            // -> money("$19.99") -> 19.99
```

識別子は引用符に直接隣接している必要があります（`prefix"..."`）。空白があると
（`prefix "..."`）通常の識別子＋文字列になります。予約語に直接続く文字列は影響を
受けません（`return"x"` は `return "x"` のままでスキーム呼び出しになりません）。
未定義のプレフィックスは lexer エラーではなく通常の「メソッドが見つかりません」エラーです。

## パターン付きレコード（from re"..."）

レコードに正規表現を付けると、その形から型付きパーサが導出されます。キャプチャグループが各成分の型に変換されます。

```onion
record Access(time: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"

val hits = Access::parseAll(file"access.log".text())   // List[Access]、不正行はスキップ
val one  = Access::parse(line)                          // Access?（不一致なら null）
```

パターンが可逆（リテラル断片＋グループのみ）なら `format` も導出され、`parse∘format == id` が成り立ちます。

## シリアライズの導出（derive!）

`derive!(Json)` / `derive!(Yaml)` でレコードの形から双方向のシリアライズを導出します。`derive!` は型クラスではなくマクロです（`!` が展開の印）。

```onion
record User(name: String, age: Int) derive!(Json, Yaml)

User::toJson(u)            // {"name":"ko","age":3}
val a = User::fromJson(s)  // User?
User::toYaml(u)            // name: ko\nage: 3
val b = User::fromYaml(s)  // User?
```

全フォーマットが `toMap` / `fromMap` のコアを共有するので、新フォーマットは stdlib に `parse` / `stringify` を足すだけで済みます。成分は scalar 型のみ（非対応は E0062、未知のマーカーは E0063）。`from re"..."` と1つのレコードに共存できます。

## コンパイル時の法則・用例（law / example）

レコードに `law` / `example` を書くと、**コンパイラがビルド時に実行検証**します。仕様をテストやドキュメントではなく言語の中に置けます。

```onion
record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)"
  law roundtrip(p: Pt) { Pt::parse(Pt::format(p)) == p }
  example { Pt::parse("3,4") == new Pt(3, 4) }
```

`example` は `true` でなければコンパイルエラー（E0065）。`law` は生成された値で property 検査され、反例が見つかればコンパイルエラー（E0064、反例つき）。`parse∘format == id` が機械検証される仕組みです。

## パイプライン演算子

`e |> f` は `f(e)`、`e |> f(a)` は `f(e, a)` です。

```onion
xs.map { x => x * 2 } |> println
```

## auto-CLI

トップレベル `main` の型付き引数から、コマンドライン引数の解析が導出されます。必須引数は位置引数、デフォルト付きは `--name` フラグになります。

```onion
def main(name: String, count: Int = 3, loud: Boolean = false): void { ... }
// $ onion script.on world --count 5 --loud
// $ onion script.on world --count=5 --loud   （--name=value 形式も可）
```

フラグは `--name value` と `--name=value` の両形式を受け付けます。`--help`（または `-h`）で生成された usage を表示して終了します。

## 次のステップ

- [基本構文](basic-syntax.md) - 言語構文の詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みモジュール
