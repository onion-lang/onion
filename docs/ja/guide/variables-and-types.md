# 変数と型

## 変数宣言

`val` は不変、`var` は可変です。型は推論されますが、明示もできます。

```onion
val x: Int = 42
var name: String = "ko"
val inferred = 3.14        // 型推論で Double
```

## 型システム

### プリミティブ型

先頭が大文字です：`Int` / `Long` / `Double` / `Float` / `Boolean` / `Byte` / `Short` / `Char`。

```onion
val i: Int = 42
val l: Long = 100L         // L 接尾辞
val d: Double = 3.14
val f: Float = 2.5f        // f/F 接尾辞（d/D も可）
```

`Double` / `Int` などはプリミティブとして直接書け、`Object` / `Number` のスロットへは自動的にボクシングされます（ラッパークラスは `JDouble` / `JInteger` …）。

### 参照型・配列型

`String`、クラス型、インターフェース型、そして `List[T]` / `Map[K, V]`。後の2つには
リテラル形式があるので、型を書かずに済むことがほとんどです。

```onion
val s: String = "hello"
val names = ["ann", "bo"]              // List[String] と推論される
val ages  = ["ann": 30, "bo": 25]      // Map[String, Int] と推論される
val empty: List[String] = []           // 推論の手がかりが無いときは型を書く
```

標準ライブラリはリストを受け取り・返すので、プログラムを流れるのはこれらの型です。

配列もありますが、使うのは Java と話すときです——`main(args: String[])`、バイナリ I/O の
`byte[]`、可変長引数。

```onion
val arr: Int[] = new Int[5]
arr[0] = 10
val len = arr.length       // 配列は .length、リストは .size
```

### Nullable型

`T?` で `null` を許可します。詳細は [Null安全](null-safety.md) を参照してください。

```onion
val maybe: String? = null
```

## 型キャスト

`as` 演算子を使います。メソッドチェーンには括弧が必要です。

```onion
val btn = (event.source as JButton)
val text = (obj as JButton).getText()
```

- 拡大変換（`Int` → `Long` → `Double`）は自動です
- 縮小変換は明示的な `as` が必要です

## 変数スコープ

ローカル変数、フィールド、static 変数があります。トップレベルの `val` はスクリプトの合成クラスのフィールドに昇格されつつ、`if x != null` のスマートキャストも効きます。

## ジェネリック型

ジェネリック型は `[]` で型引数を指定します（raw 型は禁止 = E0066）。型引数にはプリミティブ型（`Int` など）も使え、JVM の消去に伴い境界で自動的にボックス化／アンボックス化されます：

```onion
val list: ArrayList[Int] = new ArrayList[Int]
list.add(1)
val x: Int = list.get(0)
```

### 型引数の不変性

型引数は**不変**です。`Dog` が `Animal` の派生型であっても、`Box[Dog]` は `Box[Animal]` に代入できません（ヒープ汚染を防ぐため）。互換なのは同一のパラメータ化だけです：

```onion
class Animal { public: def this {} }
class Dog extends Animal { public: def this {} }

class Box[T] {
  val v: T
public:
  def this(x: T) { v = x }
  def get(): T = v
}

val bd: Box[Dog] = new Box(new Dog())
val same: Box[Dog] = bd        // OK: 同一のパラメータ化
// val wide: Box[Animal] = bd  // エラー E0000: Box[Dog] は Box[Animal] ではない
```

不変性は型*引数*に適用されるもので、ジェネリッククラス自体は通常の派生関係に従います（例: `ArrayList[String]` は `List[String]`）。

## 次のステップ

- [Null安全](null-safety.md) - nullable型とスマートキャスト
- [制御フロー](control-flow.md) - if、ループ、select
