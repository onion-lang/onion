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

### NullableプリミティブでのElvis

Elvis演算子は nullable な**プリミティブ**型にも使えます。結果はアンボックスされたプリミティブなので、非nullの `Int` / `Long` / `Double` などにそのまま代入できます。

```onion
val n: Int? = null
val v: Int = n ?: -1        // -1

val m: Int? = 42
val w: Int = m ?: -1        // 42
```

プリミティブを返すメソッドへのセーフコールと組み合わせると自然です。呼び出しは `Integer?` を返し、レシーバが `null` のとき `?:` がプリミティブのフォールバックを与えます。

```onion
val s: String? = null
val len: Int = s?.length() ?: -1    // -1（s が null）

val s2: String? = "hello"
val len2: Int = s2?.length() ?: -1  // 5
```

### 右辺に制御式を書けるElvis

`?:` の右辺には `throw` や `return` などの制御式も書けます。値が欠けているときに早期に失敗・脱出する簡潔な書き方になります。

```onion
def firstName(s: String?): String {
  val name: String = s ?: throw new RuntimeException("nil")
  return name
}

def lenOrDefault(s: String?, default: Int): Int {
  val v: String = s ?: return default
  return v.length()
}
```

## nullチェックとスマートキャスト

`if x != null` のブロック内では `x` が非null型に絞り込まれます（スマートキャスト）。**関数内でもトップレベルの `val` でも効きます。**

```onion
val a: String? = lookup()
if a != null {
  println(a.length)          // a はここでは String に絞られている
}
```

`!(cond)` は絞り込まれる分岐を入れ替えるので、早期リターンに使えます。

```onion
def f(s: String?): String {
  if !(s != null) { return "nil" }
  return "n" + s.length()        // s はここで String
}
```

### nullableフィールドのスマートキャスト

null チェックはローカル変数だけでなく、イミュータブル（`val`）な nullable フィールドも絞り込みます。`if field != null { ... }` のブロック内では、`T?` 型の `val` フィールドは `T` として扱われ、そのままメソッドを呼べます。

```onion
class Person {
  val name: String?
public:
  def this(name: String?) { this.name = name }
  def nameLength(): Int {
    if name != null {
      return name.length()   // ここで name は String に絞られる
    } else {
      return -1
    }
  }
}

println(new Person("Alice").nameLength())  // 5
println(new Person(null).nameLength())     // -1
```

ミュータブル（`var`）なフィールドは絞り込まれません（チェックと使用の間に値が変わりうるため。使うと E0041）。ローカルの `val` にスナップショットしてから使います。ローカルは常に絞り込まれます。

```onion
class Counter {
  var label: String?
public:
  def this(label: String?) { this.label = label }
  def show(): Int {
    val l = label            // ローカルの val にスナップショット
    if l != null {
      return l.length()      // l は String に絞られる
    } else {
      return -1
    }
  }
}

println(new Counter("hi").show())   // 2
println(new Counter(null).show())   // -1
```

ミュータブル（`var`）な**ローカル変数**も、チェックと使用の間で再代入されない限り絞り込まれます。一度だけ代入する `var` や、別の場所でのみ再代入する `var` は、必要な箇所ではちゃんと絞り込まれます。

```onion
def firstNonEmpty(lines: List[String]): String {
  var found: String? = null
  foreach line: String in lines {
    if found == null && line.length() > 0 {
      found = line
    }
  }
  if found != null {
    return found            // found（var）はここで String に絞られる
  }
  return "(none)"
}
```

定番の読み取りループ `while (line = next()) != null { ... }` も同様で、`line` はループ本体の先頭で絞り込まれます。使用の**後**の再代入は絞り込みを取り消しませんが、クロージャに捕捉された `var` はクロージャ内では nullable のままです（クロージャは変数が変わった後に実行されうるため）。

## nullableに対する `==` はnull安全な値等価

`==` は静的に nullable なレシーバに対しても **値等価**（`java.util.Objects.equals` 相当）です。両方 null なら等しい、片方だけ null なら非等価、それ以外は `equals` で比較します。**事前の null チェックは不要です。**

```onion
val a: String? = compute()
if a == "expected" { println("match") }    // a が null でも安全
```

参照同値が必要なときは `===` を使います。

## 非null表明 `!!` とセーフインデックス `?[]`

```onion
val s: String? = definitelyThere()
println(s!!.length())        // null なら NullPointerException

val xs: List[Int]? = loadOrNull()
val first = xs?[0]               // xs が null なら null
```

`!!` は「型システムより自分が確実に分かっている」ときに使い、通常は `?.` / `?:` / null チェックを優先します。

## Nullable対応ジェネリクス

裸の `[T]` は nullable な型引数も受け付け（`new Box[String?](...)`）、`[T extends B]` は非nullに制限します。詳細は[仕様](../../reference/specification.md)を参照してください。

## 次のステップ

- [基本構文](basic-syntax.md) - 言語構文の詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みモジュールとユーティリティ
