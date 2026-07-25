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

### `E0066` — raw なジェネリック型は使用できない

ジェネリック型が型引数なしで使われました。Onion は raw なジェネリック型を禁止して
います。型引数を明示的に指定してください。

```onion
val xs: List = [1]        // E0066: raw な List
val ok: List[Int] = [1]   // 修正版
```

### `E0067` — return の欠落

値を返すメソッドのブロック本体が、値を返さずに末尾へ到達する可能性があります。
すべてのパスで値を返すか、式本体を使ってください。

```onion
class C {
public:
  static def f(): Int { 5 + 10 }   // E0067: 値が返されない
}
```

修正方法:
- 明示的な `return` を追加: `{ return 5 + 10 }`。
- 式本体を使う: `def f(): Int = 5 + 10`。

## 名前解決エラー

### `E0002` — 変数が見つからない

参照されたローカル変数がスコープ内に存在しません。可能な場合、コンパイラは類似した名前を提案します。

```onion
val userName = "ok"
println(usrName)   // E0002、userName を提案
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

### `E0073` — Map は直接反復できない

`foreach` は配列か `iterator()` を持つ値を走査しますが、`Map` はそのどちらでもありません。

```onion
val m = ["a": 1]
foreach k: String in m { println(k) }   // E0073
```

エントリを回す分割代入形式を使うか、ビューを選んでください。

```onion
foreach (k, v) in m { println(k + "=" + v) }
foreach k: String in m.keySet() { println(k) }
```

### `E0074` — law の引数型からサンプルを生成できない

`law` はビルド時に生成したサンプル値で検査されるため、すべての引数型に生成器が必要です。
生成できる型は `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte` と、
全成分が生成可能なレコードです。

```onion
record Dummy(v: Int)
  law overArray(xs: Int[]) { xs != null }   // E0074: Int[] の生成器がない
```

それ以外——配列、`Map`、enum、インターフェース、コンストラクタが複数あるクラス——には
生成器がありません。以前はこうした law が黙って読み飛ばされ、**成立した law と区別が
つきませんでした**。実行できない検査が「通った検査」に見えてはいけません。
引数の型を変えるか、law を削除してください。

```onion
record Pt(x: Int, y: Int)
  law reflexive(p: Pt) { p == p }          // OK: flat なレコードは生成可能
```

### `E0075` — law を持つクラスをロードできなかった

law はコンパイル済みクラスに対して実行されます。`law` / `example` 句を持つクラスを
ロードできなかった場合、その検査は1つも実行されていません。`E0074` と同じ理由で、
黙って見逃さずに報告します。

### `E0076` — 未知の shape フォーマット

`shape name = <format>` 句が、コンパイラの知らない文書フォーマットを指しています。
推測すると「何も読まない shape」が黙って出来上がるため、サポート対象を列挙した
エラーにしています。

```onion
record Pt(x: Int, y: Int)
  shape doc = toml     // E0076: サポートしているのは json, yaml
```

インラインのパターンを書く場合は `shape name = re"..."` としてください。

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
  case Circle(r): println("circle")
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
