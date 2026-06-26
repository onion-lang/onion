# Onion コンパイラ エラーコード

Onion は、安定したコードを持つコンパイルエラーを報告します。このページでは最も一般的なコードを説明し、修正方法を提案します。

## 内部エラー

### `I0000` — 内部コンパイラエラー

コンパイラフェーズから予期しない例外が発生しました。これはコンパイラのバグであり、プログラムのバグではありません。ソースを最小限の再現例に減らして、イシューを報告してください。

## 型エラー

### `E0000` — 互換性のない型

ある型の値が、別の型が期待される場所で使われました。

```onion
val x: Int = "hello"   // E0000: Int が期待される場所で String
```

よくある修正方法:
- 期待される型を確認し、値を明示的に変換します（例: `Int::parseInt(s)`）。
- nullable から non-null への不一致の場合、コンパイラは `!!`、`?:`、または null チェックを提案するヒントを追加します。

```onion
val name: String? = lookup()
val sure: String = name     // E0000 + nullable ヒント
val fixed: String = name ?: ""
```

### `E0001` — 互換性のない被演算子の型

演算子がサポートしていない型に適用されました。

```onion
val b = true + 1   // E0001
```

## 名前解決エラー

### `E0002` — 変数が見つからない

参照されたローカル変数がスコープ内に存在しません。可能な場合、コンパイラは類似した名前を提案します。

```onion
val userName = "ok"
IO::println(usrName)   // E0002、userName を提案
```

### `E0003` — クラスが見つからない

型名を解決できません。スペルとインポートを確認してください。

```onion
val xs = new ArrayLst[String]()   // E0003、ArrayList を提案
```

### `E0005` — メソッドが見つからない

呼び出しに一致するメソッドがありません。同じ名前のメソッドが存在するが引数の型が異なる場合、コンパイラは利用可能なシグネチャを一覧表示します。

### `E0021` — コンストラクタが見つからない

引数に一致するコンストラクタがありません。コンパイラは利用可能なコンストラクタを一覧表示します。

## Null 安全エラー

### `E0057` — 型引数が null の可能性がある

裸の型引数 `[T]` は nullable な型で実体化される可能性があるため、null チェックまたは非 null 制限なしには直接デリファレンスできません。

```onion
class Box[T] {
public:
  def size(x: T): Int = x.toString().length()   // E0057
}
```

修正方法:
- `?.` / `?:` / `if x != null` を使う。
- 非 null 制限を宣言: `class Box[T extends Object]`。

## パターンマッチングエラー

### `E0042` — 網羅性のないパターンマッチング

sealed 型に対する `select` がすべてのケースを網羅していません。

```onion
sealed interface Shape {}
record Circle(r: Int) <: Shape
record Rect(w: Int, h: Int) <: Shape

select shape {
  case Circle(r): IO::println("circle")
  // Rect ケースが欠けている → E0042
}
```

## パーサーエラー

パーサーエラーには `E` コードは付きません。出現したトークンと期待されるトークンが報告されます。

```
Test.on:2:10: Syntax error. Encountered "{", but expecting ";"
```

## 関連項目

- [言語仕様](specification.md)
- [標準ライブラリ](stdlib.md)
