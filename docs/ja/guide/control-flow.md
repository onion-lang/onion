# 制御フロー

## 条件分岐

`if` / `else` は条件を括弧で囲みません。式としても使えます。

```onion
if condition { doThis() } else { doThat() }

val max = if a > b { a } else { b }      // if は式
```

`else if` のチェーンも書けます。条件内での代入は括弧で囲みます（`while (x = read()) != null`）。

## ループ

```onion
while cond { ... }

for var i: Int = 0; i < 10; i++ { ... }

do { ... } while cond

foreach x: Int in 0..10 { ... }          // 範囲: a..b は包含、a..<b は排他
foreach (k, v) in map { ... }            // Map のエントリ分解
```

## パターンマッチング（select）

`switch` ではなく `select` を使います。

```onion
select value {
case 1, 2, 3: handleSmall()
case s is String: handleString(s)        // 型パターン（: ではなく is）
case Point(x, y): handlePoint(x, y)      // レコード分解
case n when n > 0: handlePositive(n)     // ガード
case re"(\d+)" (num): handleNumber(num)  // 正規表現パターン（コンパイル時に検査）
else: handleOther()
}
```

パターンは入れ子にできます。型パターンも同様で、成分をその場で絞り込んで
絞り込んだ型のまま使えます。

```onion
sealed interface E {}
record Num(v: Int) conforms E
record Add(l: E, r: E) conforms E
record Wrap(x: Object)

select e {
  case Add(l, n is Num): println("added " + n.v())  // ここでの n は Num
  case Add(l, r):        println("added something else")
  case n is Num:         println("just " + n.v())
}

// 成分の宣言型がレコードでない場合にも使えます
// （入れ子のコンストラクタパターンでは表現できないケース）
select w {
  case Wrap(s is String): println(s.toUpperCase())
  case Wrap(o):           println("not a string")
}
```

sealed な階層に対しては網羅性が検査されます（漏れは E0042）。

### Java のネストした型にマッチする

型パターンには、Java のソースと同じくドット区切りでネストした Java クラスを書けます
（`Result.Ok`、`onion.Result.Ok`、`Map.Entry`）。標準ライブラリの直和型
`onion.Result`（`Ok`/`Err`）、`onion.Outcome`（`Ok`/`Bad`）、`onion.Option`（`Some`/`None`）
も、これでケースごとにマッチできます。

```onion
def describe(r: Result[Int, String]): String = select r {
  case o is Result.Ok:  "ok " + (o.value() + 1)    // o は Result.Ok[Int, String]
  case e is Result.Err: "failed: " + e.error()
}
```

Onion の `enum` と同じく、束縛は検査対象の型引数を引き継ぐので、`o.value()` は `Int`、
`e.error()` は `String` になります。この 3 つは `sealed` な Java インターフェースで、網羅性検査は
Java クラスの `permits` も読みます。上の例から `Result.Err` を外すと E0042 になり、両方そろって
いれば `else` は要りません。自作の jar にある sealed な階層でも同じです。

ネストしたクラスはドット区切りの名前でインポートでき（別名も可）、以後は単純名で使えます。

```onion
import {
  onion.Result.Ok
  onion.Result.Err as Failed
}
```

ドット区切りの名前は Java と同じ規則で解釈します。トップレベルクラスを指す最も長い接頭辞を
優先し、残りをその中のメンバークラスとみなします。つまり `a.b.C.D` は、クラス `a.b` の中の
`C.D` より先に、クラス `a.b.C` の中の `D` として解決されます。存在しないメンバー
（`Result.Okk`）は E0003 で、外側クラスのメンバーから候補を示します（`did you mean: Result.Ok`）。

分解パターン（`case Ok(v)`）は Onion のレコード専用です。Java のレコードは型でマッチして
アクセサ（`o.value()`）を呼んでください。

## break / continue

```onion
while true {
  if done { break }
  if skip { continue }
}
```

ループには `name: while ...` のようにラベルを付けられ（`for` / `foreach` /
`do...while` も同様）、`break name` / `continue name`
（ラベルは `break`/`continue` と同じ行に書く）で内側のループではなく
そのラベルの付いたループを対象にできます。外側のループを内側から抜けたり
次の周に進めたりできるのはこの形だけです。束縛されていないラベルの参照は
`[E0058]` になります。

```onion
outer: for var i: Int = 0; i < 3; i = i + 1 {
  for var j: Int = 0; j < 3; j = j + 1 {
    if i == 1 && j == 1 { break outer }      // outer の for ごと抜ける
    println(i + "," + j)
  }
}
```

ラベル付き `break`/`continue` は、それとラベルの付いたループの間にある
`synchronized` ブロックや `try`（try-with-resources を含む）も正しく巻き戻します
— モニタは解放され、リソースは通常どおり制御を抜けた場合と同じようにクローズ
されます。

## 例外処理

```onion
try {
  riskyOperation()
} catch e: Exception {
  println(e.message())
} finally {
  cleanup()
}
```

複数の `catch`、multi-catch（`catch e: IOException | SQLException`）、try-with-resources（`try (val r = open()) { ... }`、逆順にクローズ）も書けます。

## Do記法（モナド合成）

`Option`・`Result`・`Future` のようなモナド型に対する操作を連鎖させるための構文です。内部的には `flatMap`/`map` 呼び出しに展開されます。

```onion
do[Option] { a <- getA(); b <- getB(); ret a + b }
do[List]   { x <- [1, 2]; y <- ["a", "b"]; ret x + y }   // 内包表記
```

### エラーの早期終了（ショートサーキット）

途中のどれか一つが失敗すると、do ブロック全体がそこで打ち切られ、以降のバインドは実行されません：

```onion
val result: Option[Int] = do[Option] {
  x <- Option::some(10)    // 成功
  y <- Option::none()      // ここで失敗 - 打ち切られる
  z <- Option::some(30)    // 実行されない
  ret x + y + z
}
println(result.isEmpty())
```

Output:
```
true
```

## 次のステップ

- [関数](functions.md) - 関数定義とラムダ
- [Null安全](null-safety.md) - nullable型とスマートキャスト
