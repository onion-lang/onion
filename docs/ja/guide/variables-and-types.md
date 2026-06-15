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

`String`、クラス型、インターフェース型、配列。

```onion
val s: String = "hello"
val arr: Int[] = new Int[5]
arr[0] = 10
val len = arr.length       // length はメソッドではなくプロパティ
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

## 次のステップ

- [Null安全](null-safety.md) - nullable型とスマートキャスト
- [制御フロー](control-flow.md) - if、ループ、select
