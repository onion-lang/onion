# コレクション

OnionはJavaのコレクションクラスをそのまま使用し、リテラル構文・パイプライン・範囲イテレーションを追加しています。

## リストリテラル

```onion
val xs = [1, 2, 3]   // java.util.List（可変 ArrayList）
val empty = []
xs[0]                 // インデックス読み取り
xs[1] = 42            // インデックス書き込み
xs[2] += 1            // 複合代入
```

## マップリテラル

```onion
val ages = ["alice": 12, "bob": 34]   // 挿入順を保持する LinkedHashMap
val none = [:]                        // 空マップ
ages.get("alice")
foreach k: String in ages.keySet() { IO::println(k) }
```

## パイプライン

`java.util.List` と `java.lang.Iterable` には `onion.Colls` / `onion.Iterables` のヘルパーが追加されており、トレイリングラムダでチェインできます：

```onion
val lines = ["alpha beta", "gamma", "alpha delta"]

val lengths = lines
  .filter { s => s.contains("alpha") }
  .map { s => s.length() }       // [10, 11]
```

静的呼び出し形式でも使えます：

```onion
Colls::map(xs, (x: Integer) -> (x as Int) * 2)
```

## 範囲（Range）

`a..b`（両端含む）と `a..<b`（末端を含まない）で整数範囲を作成できます：

```onion
foreach i: Int in 1..5 { IO::println(i) }         // 1 2 3 4 5
foreach i: Int in 0..<xs.size() { use(xs[i]) }    // インデックスイテレーション

val r = 2..4
r.size()       // 3
r.contains(3)  // true
```

## ソート

`Comparator` が期待される箇所にラムダを渡せます：

```onion
val xs = Colls::mutableListOf(3, 1, 2)
Collections::sort(xs, (a, b) -> (a as Int) - (b as Int))
```

## マップのイテレーション

エントリの分割代入と明示的なエントリ型の両方が使えます：

```onion
foreach (name, age) in ages {          // エントリ分割代入
  IO::println(name + " is " + age)
}

import { java.util.Map }
foreach e: Map.Entry in ages.entrySet() {  // 明示的エントリ形式
  IO::println(e.getKey() + " is " + e.getValue())
}
```

## 次のステップ

- [基本構文](basic-syntax.md) - 配列と基本的な構文
- [Javaとの相互運用](java-interop.md) - Java コレクションクラスの詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みユーティリティ
