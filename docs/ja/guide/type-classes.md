# 型クラス

型クラスは Onion に *アドホック多相* をもたらします。ジェネリックに対して「その型が
サポートすべき操作」を制約として課すと、コンパイラが呼び出しごとに適切な実装を渡して
くれます。Rust のトレイト風に、`trait`・`instance`・`[T: Trait]` 制約で宣言します。

## トレイトの宣言

`trait` は型パラメータに対する操作を記述します。

```onion
trait Numeric[T] {
  def zero(): T
  def plus(a: T, b: T): T
}
```

インターフェースと同様、デフォルトメソッドを持てます。

```onion
trait Greeter {
  def name(): String
  def greet(): String = "Hi " + name()
}
```

## インスタンスの提供

`instance` は具体型に対してトレイトを実装します。

```onion
trait Numeric[T] {
  def zero(): T
  def plus(a: T, b: T): T
}
instance Numeric[Integer] {
  def zero(): Integer = 0
  def plus(a: Integer, b: Integer): Integer = a + b
}
instance Numeric[Long] {
  def zero(): Long = 0L
  def plus(a: Long, b: Long): Long = a + b
}
```

`(trait, 型)` ごとにインスタンスは **1つまで**です（コヒーレンス）。プリミティブと
ボックス型は同じ型として扱われるので、`Numeric[Int]` と `Numeric[Integer]` は同一
インスタンスです。両方を宣言するとエラーになります。

## 制約付きジェネリック

`[T: Numeric]` と書くと「`T` は `Numeric` インスタンスを持つ」ことを要求できます。
本体では `Numeric[T]::method(...)` の形でトレイトのメソッドを呼びます。

```onion
trait Numeric[T] {
  def zero(): T
  def plus(a: T, b: T): T
}
instance Numeric[Integer] {
  def zero(): Integer = 0
  def plus(a: Integer, b: Integer): Integer = a + b
}
instance Numeric[Double] {
  def zero(): Double = 0.0
  def plus(a: Double, b: Double): Double = a + b
}

def sum[T: Numeric](xs: List[T]): T {
  var acc: T = Numeric[T]::zero()
  foreach x: T in xs { acc = Numeric[T]::plus(acc, x) }
  return acc
}

def main(args: String[]): void {
  println(sum([1, 2, 3, 4]))     // => 10
  println(sum([1.5, 2.5, 3.0]))  // => 7.0
}
```

コンパイラが、推論された `T` に対するインスタンスを呼び出しごとに解決して渡します。
利用側はインスタンスを一切書きません。インスタンスの無い型に対して制約付き関数を
呼ぶと、実行時ではなく**コンパイル時のエラー**になります。

制約は `extends` 上限境界と併用でき（`[T extends Comparable[T]: Numeric]`）、複数も
書けます（`[T: Numeric, U: Numeric]`）。

## 具体型への辞書アクセス

`Trait[具体型]::method(...)` はジェネリックの外でも直接使えます。

```onion
trait Numeric[T] {
  def plus(a: T, b: T): T
}
instance Numeric[Integer] {
  def plus(a: Integer, b: Integer): Integer = a + b
}

def main(args: String[]): void {
  println(Numeric[Integer]::plus(3, 4))  // => 7
}
```

## 制約付き関数どうしの呼び出し

制約付き関数は、同じ抽象型パラメータを持つ別の制約付き関数を呼べます。インスタンスは
自動的に転送されます。

```onion
trait Numeric[T] {
  def zero(): T
  def plus(a: T, b: T): T
}
instance Numeric[Integer] {
  def zero(): Integer = 0
  def plus(a: Integer, b: Integer): Integer = a + b
}

def sum[T: Numeric](xs: List[T]): T {
  var acc: T = Numeric[T]::zero()
  foreach x: T in xs { acc = Numeric[T]::plus(acc, x) }
  return acc
}
def sumTwice[T: Numeric](xs: List[T]): T = Numeric[T]::plus(sum(xs), sum(xs))

def main(args: String[]): void {
  println(sumTwice([1, 2, 3]))  // => 12
}
```

## 自作のトレイト

型クラスは数値に限りません。必要なトレイトを自由に定義できます。

```onion
trait Eq[T] {
  def eq(a: T, b: T): Boolean
}
instance Eq[Integer] {
  def eq(a: Integer, b: Integer): Boolean = a == b
}

def allSame[T: Eq](xs: List[T]): Boolean {
  if xs.size() < 2 { return true }
  val head: T = xs.get(0)
  foreach x: T in xs {
    if !Eq[T]::eq(head, x) { return false }
  }
  return true
}

def main(args: String[]): void {
  println(allSame([7, 7, 7]))  // => true
  println(allSame([7, 8, 7]))  // => false
}
```

## 現時点の制限

最初のリリースでは、よく使うケースに絞っています。

- 単一パラメータのトレイト（`trait C[T]`）。
- 関数・メソッドへの制約（`def f[T: C]`）。ジェネリック*クラス*への制約
  （`class Box[T: C]`）は構文としては通りますが、まだ強制されません。
- トレイトのメソッドは `Trait[T]::method(...)` で明示的に呼びます。UFCS の
  `value.method(...)` はまだ使えません。
- インスタンスは具体型に対するもの（`instance Numeric[Integer]`）で、パラメータ化
  （`instance Numeric[List[T]]`）はまだ対象外です。
- 組込の `Numeric`/`Eq`/`Ord` はまだありません。必要なトレイトは自分で定義します。
