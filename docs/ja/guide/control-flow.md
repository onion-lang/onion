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
