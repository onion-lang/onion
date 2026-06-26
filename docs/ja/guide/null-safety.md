# Null安全

OnionはKotlin風のnull安全機能を持ち、`NullPointerException` をコンパイル時に防ぎます。

## Nullable型

デフォルトでは型は `null` を保持できません。`?` 接尾辞で `null` を許可します。

```onion
val name: String = "Alice"       // 非null（null を代入するとコンパイルエラー）
val maybeName: String? = null    // nullable: OK
```

- `T` → `T?` は許可されます（widening）
- `T?` → `T` は許可されません（明示的な処理が必要）

## セーフコール演算子 `?.`

レシーバが `null` のとき、式全体が `null` を返します（例外を投げません）。

```onion
val s: String? = null
val upper = s?.toUpperCase()     // null
```

## Elvis演算子 `?:`

`null` のときのデフォルト値を与えます。セーフコールと組み合わせると便利です。

```onion
val name: String? = null
val display: String = name ?: "unknown"
val u: String = s?.toUpperCase() ?: "DEFAULT"
```

## nullチェックとスマートキャスト

`if x != null` のブロック内では `x` が非null型に絞り込まれます（スマートキャスト）。**関数内でもトップレベルの `val` でも効きます。**

```onion
val a: String? = lookup()
if a != null {
  IO::println(a.length)          // a はここでは String に絞られている
}
```

`!(cond)` は絞り込まれる分岐を入れ替えるので、早期リターンに使えます。

```onion
def f(s: String?): String {
  if !(s != null) { return "nil" }
  return "n" + s.length()        // s はここで String
}
```

## nullableに対する `==` はnull安全な値等価

`==` は静的に nullable なレシーバに対しても **値等価**（`java.util.Objects.equals` 相当）です。両方 null なら等しい、片方だけ null なら非等価、それ以外は `equals` で比較します。**事前の null チェックは不要です。**

```onion
val a: String? = compute()
if a == "expected" { IO::println("match") }    // a が null でも安全
```

参照同値が必要なときは `===` を使います。

## 非null表明 `!!` とセーフインデックス `?[]`

```onion
val s: String? = definitelyThere()
IO::println(s!!.length())        // null なら NullPointerException

val xs: List? = loadOrNull()
val first = xs?[0]               // xs が null なら null
```

`!!` は「型システムより自分が確実に分かっている」ときに使い、通常は `?.` / `?:` / null チェックを優先します。

## Nullable対応ジェネリクス

裸の `[T]` は nullable な型引数も受け付け（`new Box[String?](...)`）、`[T extends B]` は非nullに制限します。詳細は[仕様](../../reference/specification.md)を参照してください。

## 次のステップ

- [基本構文](basic-syntax.md) - 言語構文の詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みモジュールとユーティリティ
