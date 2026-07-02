# Type Classes

Type classes give Onion *ad-hoc polymorphism*: you can constrain a generic on the
operations a type must support, and the compiler supplies the right implementation
at each call. They are declared Rust-trait style, with `trait`, `instance`, and a
`[T: Trait]` context bound.

## Declaring a trait

A `trait` describes operations over a type parameter:

```onion
trait Numeric[T] {
  def zero(): T
  def plus(a: T, b: T): T
}
```

A trait may have default methods, just like an interface:

```onion
trait Greeter {
  def name(): String
  def greet(): String = "Hi " + name()
}
```

## Providing instances

An `instance` implements a trait for a concrete type:

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

There is at most **one instance per `(trait, type)`** (coherence). Primitive and
boxed forms are the same type, so `Numeric[Int]` and `Numeric[Integer]` are one
instance — declaring both is an error.

## Constrained generics

Write `[T: Numeric]` to require that `T` has a `Numeric` instance. Inside the body,
call the trait's methods through `Numeric[T]::method(...)`:

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

The compiler resolves the instance for the inferred `T` at each call and passes it
in for you — you never mention it. Calling a constrained function for a type with
no instance is a compile error, not a runtime failure.

You can combine a context bound with an `extends` upper bound and use several,
`[T extends Comparable[T]: Numeric]` or `[T: Numeric, U: Numeric]`.

## Ground dictionary access

`Trait[ConcreteType]::method(...)` works directly, outside any generic:

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

## Constrained functions calling constrained functions

A constrained function can call another with the same abstract type parameter; the
instance is forwarded automatically:

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

## Your own traits

Type classes are not limited to numbers — define any trait you need:

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

## Current limitations

The first release focuses on the common cases:

- Single-parameter traits (`trait C[T]`).
- Context bounds on functions and methods (`def f[T: C]`); a bound on a generic
  *class* (`class Box[T: C]`) parses but is not enforced yet.
- Trait methods are called explicitly via `Trait[T]::method(...)`; UFCS
  `value.method(...)` is not yet available.
- Instances are for ground types (`instance Numeric[Integer]`), not parameterized
  (`instance Numeric[List[T]]`).
- There is no built-in `Numeric`/`Eq`/`Ord` yet — define the traits you need.
