# スクリプティング

Onion は bash では手に負えなくなったシェルスクリプトを置き換えるために作られています。
このページではスクリプティング用の機能を一通り紹介します。

## スクリプトの実行

```bash
onion script.on arg1 arg2     # メモリ上でコンパイルして実行
onion --watch script.on       # 保存のたびに再実行
onion repl                    # 対話的な REPL
```

スクリプトは shebang を使えます。

```onion
#!/usr/bin/env onion
println("hello")
```

トップレベルの文は上から順に実行され、`args` にコマンドライン引数が入ります。
コンパイルエラーが起きると非ゼロの終了ステータスで終了します。

## コマンドライン引数

```onion
val opts = Args::parse(args)

if opts.flag("verbose") { ... }          // --verbose または -v
val out = opts.option("output", "a.txt") // --output=x または --output x
val n = opts.intOption("count", 1)       // デフォルト付きの数値
val files = opts.positional()            // それ以外すべて
```

## 外部コマンドの実行

```onion
val branch = Proc::run("git", "branch", "--show-current")  // 標準出力を取得、失敗時は例外

val r = Proc::capture("sh", "-c", "ls missing")            // 例外を投げない
if r.failed() { println(r.stderr()) }

val code = Proc::exec("make", "build")                     // コンソールを継承、終了コードを返す
```

`Proc::runIn(dir, ...)` は作業ディレクトリを指定できるバリアントです。

## ファイルと glob

```onion
val text = Files::readText("config.txt")
Files::writeText("out.txt", text.toUpperCase())

foreach f: String in Files::glob(".", "*.on") {
  println(f)
}
Files::glob("src", "**/*.java")    // 再帰的に検索
```

## JSON と HTTP

```onion
val body = Http::get("https://api.github.com/repos/onion-lang/onion")
val v = Json::value(body)
println(v["name"].asString() + " stars=" + v["stargazers_count"].asInt())
```

`Json::value` はナビゲート可能な値を返します。オブジェクトや配列を添字アクセスした上で、
`asString` / `asInt` / `asDouble` / `asBoolean` で変換します。存在しないパスは例外ではなく
`isNull()` で判定できます。

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

## まとめて使う

```onion
#!/usr/bin/env onion
record Hit(ip: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"

def main(log: String, minStatus: Int = 500): void {
  Hit::parseAll(file(log).text())
    .filter { h => h.status() >= minStatus }
    .groupBy { h => h.path() }
    .mapValues { xs => xs.size }
    |> println
}
```

## 次のステップ

- [基本構文](basic-syntax.md) - 言語構文の詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みモジュール
