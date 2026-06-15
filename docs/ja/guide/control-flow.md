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

sealed な階層に対しては網羅性が検査されます（漏れは E0042）。

## break / continue

ラベル付きの `break` / `continue` も使えます。

```onion
while true {
  if done { break }
  if skip { continue }
}
```

## 例外処理

```onion
try {
  riskyOperation()
} catch e: Exception {
  IO::println(e.message())
} finally {
  cleanup()
}
```

複数の `catch`、multi-catch（`catch e: IOException | SQLException`）、try-with-resources（`try (val r = open()) { ... }`、逆順にクローズ）も書けます。

## Do記法（モナド合成）

```onion
do[Option] { a <- getA(); b <- getB(); ret a + b }
do[List]   { x <- [1, 2]; y <- ["a", "b"]; ret x + y }   // 内包表記
```

## 次のステップ

- [関数](functions.md) - 関数定義とラムダ
- [Null安全](null-safety.md) - nullable型とスマートキャスト
