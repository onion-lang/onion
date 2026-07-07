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
ages.get("alice")                     // 12
ages["alice"]                         // 12  — 添字での読み取り（get と同じ）
ages["carol"] = 9                     // 添字での代入（put にコンパイルされる）
ages["alice"] += 1                    // 添字を介した複合代入
foreach k: String in ages.keySet() { println(k) }
```

## 空リテラル

`[]` と `[:]` は自身の要素型を持たないため、コンパイラは周囲の文脈から型を決定します。これは `val` 宣言・戻り値・フィールド初期化子ではすでに機能していましたが、**引数位置**でも動作するようになりました。期待される引数の型が要素型を与えます：

```onion
import {
  java.util.List
  java.util.Map
}

def size(xs: List[String]): Int = xs.size()
def count(m: Map[String, Integer]): Int = m.size()

size([])      // 0  - [] は List[String] のパラメータ型を受け取る
count([:])    // 0  - [:] は Map[String, Integer] のパラメータ型を受け取る
```

期待型は**分岐やオペランドの位置**にも伝わります。`if`/`else` や `select` の分岐、`try`/`catch` の分岐、`?:` の右オペランド、`do` ブロックの `ret` にある空リテラルも、エラーにならず要素型を推論します：

```onion
def pick(b: Boolean): List[Int] = if b { [] } else { [1] }   // [] は List[Int]
def orEmpty(o: List[Int]?): List[Int] = o ?: []              // [] は List[Int]
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

バッチ分割・ウィンドウ化のヘルパーも同様にチェインできます：

```onion
val nums = [1, 2, 3, 4, 5, 6, 7]
nums.chunked(3)      // [[1,2,3], [4,5,6], [7]]  — 固定サイズのバッチ
nums.windowed(3)     // [[1,2,3], [2,3,4], ...]  — スライディングウィンドウ
nums.slice(1, 4)     // [2, 3, 4]                — 範囲をクランプする部分リスト
```

## 範囲（Range）

`a..b`（両端含む）と `a..<b`（末端を含まない）で整数範囲を作成できます：

```onion
foreach i: Int in 1..5 { println(i) }         // 1 2 3 4 5
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
  println(name + " is " + age)
}

import { java.util.Map }
foreach e: Map.Entry in ages.entrySet() {  // 明示的エントリ形式
  println(e.getKey() + " is " + e.getValue())
}
```

## 次のステップ

- [基本構文](basic-syntax.md) - 配列と基本的な構文
- [Javaとの相互運用](java-interop.md) - Java コレクションクラスの詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みユーティリティ
