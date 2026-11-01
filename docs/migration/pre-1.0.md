# Migrating to 1.0

Onion is cleaning up its syntax and APIs before 1.0, while breaking a few programs is still
cheap. Each breaking change is listed here with how to update your code.

After 1.0, Onion keeps a compatibility promise: a program that compiles on 1.x keeps
compiling and means the same thing on later 1.x releases.

## `ret` is now `yield`

A `do` block's final value is written with `yield`.

```onion
// Before
val sum = do[Option] { a <- getA(); b <- getB(); ret a + b }

// Now
val sum = do[Option] { a <- getA(); b <- getB(); yield a + b }
```

- **What changed:** `yield` is a reserved word; `ret` is an ordinary identifier again.
- **What you see:** old code reports `local variable ret is not found.`, with a hint to write `yield`.
- **A Java method named `yield`:** call it with backticks, e.g. ``Thread::`yield`()``.
- **Update a whole project:** `onion migrate src/ tests/` (or any files and directories).
  It rewrites only a `do` block's `ret`, never a variable named `ret`, a string or a comment.

## `2147483648` needs a `-` directly in front

The literal `2147483648` (and `9223372036854775808L`) is valid only as the whole operand of a
unary `-`, as in `-2147483648`. Anywhere else it used to wrap silently to the minimum value.

```onion
// Before: compiled, with a surprising value
val a = 1 - 2147483648          // -2147483647, by wraparound
val b = -2147483648 as Long     // 2147483648: `as` binds tighter than `-`
val c = -2147483648 / 2         // 1073741824: `/` binds tighter than `-`

// Now
val a = 1 - 2147483648L         // or 1L - 2147483648L
val b = -2147483648L            // or (-2147483648) as Long
val c = (-2147483648) / 2       // -1073741824
```

- **What changed:** the magnitude of the minimum value is checked where it is negated, not at the token.
- **What you see:** `integer literal out of range for Int (-2147483648..2147483647) -- add an L suffix to make it a Long literal.`, at the literal (`... for Long ...` with the `L` suffix).
- **Still fine:** `-2147483648`, `x + -2147483648`, `3 * -2147483648`, `-9223372036854775808L`, and hex or binary bit patterns such as `0x80000000`.

## A decimal `B`/`S` literal must fit the signed range

A decimal `Byte` literal now takes -128..127 and a decimal `Short` literal -32768..32767,
as in Java. Larger values used to wrap silently.

```onion
// Before: compiled, with a surprising value
val mask: Byte = 255B           // -1
val port: Short = 40000S        // -25536
val n = -128B as Int            // 128: `as` binds tighter than `-`

// Now
val mask: Byte = 0b11111111B    // -1, written as a bit pattern
val port: Int = 40000           // a Short cannot hold 40000
val n = (-128B) as Int          // -128
```

- **What changed:** like `2147483648`, the magnitudes `128B` and `32768S` are valid only as the whole operand of a `-`.
- **What you see:** `integer literal out of range for Byte (-128..127, or a bit pattern up to 0b11111111) -- ...`, at the literal (`... for Short ...` with the `S` suffix).
- **Still fine:** a binary bit pattern such as `0b11111111B`, or a hex one with the `S` suffix such as `0xFFFFS`.
- **Hex and `B`:** in a hex literal `B` is a digit, never a suffix. `0xAB` is the Int 171 and `0xFFB` is the Int 4091. For a Byte, write `0b11111111B` or `-1B`.
  - Where such a literal no longer fits a Byte (`val x: Byte = 0xFFB`, `f(0xFFB)` with a Byte parameter), the error carries a hint that says so.
  - Where it still fits, nothing is reported and the value changes: `bs[0] = 0x1B` stores 27 (was 1), and `val y = 0x7FB` is 2043 (was the Byte 127). Search your code for hex literals ending in `B` (`0x...B`).

## A quoted `String` in a `config` file is read without its quotes

A `shape name = config` record now reads a `String` value written in double quotes as quoted
text. This lets a value keep leading or trailing spaces through `print` and `parse`.

```text
# Before: the quotes were part of the value
name = "Alice"          # read as "Alice"  (7 characters)

# Now
name = "Alice"          # read as Alice    (5 characters)
name = "  Alice  "      # read as   Alice  (spaces kept)
name =   Alice          # read as Alice    (unquoted values are still trimmed)
name = "\"Alice\""      # read as "Alice"  (to keep literal quotes)
```

- **What changed:** inside the quotes, `\"` is a quote and `\\` is a backslash. Any other
  backslash is kept as it is, so `"C:\temp"` reads as `C:\temp`.
- **What you see:** a file that already relied on literal quotes still parses, so there is no
  error or hint; the value just loses its outer quotes. Search your config files for values
  that start with `"`. A value with no closing `"`, or with a bare `"` inside it (`"a" and "b"`),
  is now a `Defect` naming the line.
- **Only `String` components:** an `Int`, `Boolean` or other scalar is read exactly as before.

## A slot typed `T` accepts only a `T`

Inside a generic class, assigning to a field, local or parameter typed by a type parameter `T`,
initializing one, or returning from a method declared `: T` now accepts only a value of type `T`,
as in Java. It used to accept anything that fit `T`'s bound,
and the program then threw a `ClassCastException` on the next read.

```onion
class S[T, U] {
  var t: T
  var u: U
public:
  def this(x: T, y: U) { t = x; u = y }

  // Before: compiled, then `new S[String, Integer]("a", 1)` threw on the next read of t
  def f(): void { t = u }

  // Now: write the unchecked cast yourself, if the value really is a T
  def g(): void { t = (u as T) }
}
```

- **What changed:** a value of an unrelated type variable (`U`), of the bound (`Object`,
  `CharSequence`), or of a concrete type (`"x"`) no longer fits a `T` slot.
- **What you see:** `E0000 type T is expected, but type U is used.` at the value.
- **Still fine:** a `T`; a variable bounded by `T` (`[T, U extends T]`, then `t = u`); `null`;
  and an argument to a Java method on a raw Java receiver (`(o as ArrayList).add(u)`). A value
  read back through one (`t = (o as ArrayList).get(0)`) is not a `T`, so it needs a cast.
- **Two parameters named `T`:** a method's `[T]` inside `class K[T]` is a different `T`. The
  error then adds a hint saying so. To use the class's `T`, drop the method's `[T]`.
- **Calls:** an argument is checked when the method is inherited (`set(o)` inside
  `Mid[U] extends Box[U]`) or the receiver is typed with your variable (`xs.add(o)` with
  `xs: List[T]`, `b.set(o)` with `b: Box[T]`).
- **Not covered yet:** a call to the class's own method (`put(u)` where `def put(x: T)`), a
  constructor argument (`new Box[T](u)`), a `T?` slot, `xs[0] = u`, `m["k"] = u` and a literal `[u]` still accept a `U`, and still throw
  at run time (#2332). Cast there too, so the code keeps compiling once they are checked.
- **Inherited members are now `U`:** inside `class Mid[U] extends Box[U]`, an inherited
  `get(): T` is a `U` (it was `Object`). `U` may be null, so `get().toString()` is now `E0057`,
  as it already was inside `Box`. Write `get()?.toString()`, `get() ?: fallback`, or give `U` a
  non-null bound: `class Mid[U extends Object]`. A Java superclass is the same: inside
  `class MyList[T] extends ArrayList[T]`, `get(0).toString()` is now `E0057`.
- **`this` is a `Mid[U]`:** inside `class Mid[U]`, `this` keeps `U`, also through
  `val me = this`, `id(this)`, `[this]` or `() -> this`. So `same(this)` fits an inherited
  `same(o: Box[T])`, and `me.get()` is a `U` (it was raw `Mid`, so `me.get()` was an `Object`).
- **Branches with different type arguments:** `if b { [t] } else { [u] }` is a raw `List`.
  It compiles where an `Object` is expected; where a `List[T]` is expected, the `[u]` branch is
  `E0000`.
- **No `onion migrate` rule, on purpose:** the only mechanical rewrite is inserting
  `(u as T)`, which would hide exactly the bugs this check finds. Read each error and decide.
## An empty `[]` or `[:]` takes the type of its siblings

In a list literal, or in the values of a map literal, an empty `[]` or `[:]` now has the
type the other elements agree on. Before, it widened the whole literal to `Object`.

```onion
val adj = [[1, 2], []]

// Before: List[Object], so adj[1].add(5) was E0005, and this compiled
adj.add("header")

// Now: List[List[Int]], so adj[1].add(5) compiles, and this is E0005
adj.add("header")

// To keep the old type, annotate it
val xs: List[Object] = [[1, 2], []]
```

- **What changed:** the empty literal is typed after its siblings, from their type. Elements are still evaluated in source order.
- **What you see:** `E0005` naming `List[List[Int]].add(String)` (or `Map[String, List[Int]].set(...)`) where the code mixed element kinds, or passed the value to a `List[Object]` parameter.
- **Still fine:** an explicit annotation wins, and a literal with no typed sibling (`[[], []]`) is typed as before.

## A lambda passed to overloaded extension methods no longer depends on declaration order

When an `extension` declares the same method more than once, a lambda argument now picks the
same overload whatever order the methods are declared in. Before, such a call often ran
whichever overload was declared first or last. In general:

- **A call that compiled may now run a different overload**, the one a class method would pick
  (with one difference, below). This mostly affects lambdas with untyped parameters.
- **A call that compiled may now be E0006**, where two overloads fit equally.
- **To keep the old overload:** give the lambda's parameters their types, pass the other
  argument with `as` (`5 as Object`, `2 as Double`), pass the lambda as a typed value, or
  rename one method.

Examples:

| Overloads | Call | Before | Now | To keep the old result |
|---|---|---|---|---|
| `z(f: (Object) -> void)` on `Object`, `z(o: Object)` on `String` | `"abc".z((x: Object) -> …)` | `String`'s `z(Object)` | E0006 | rename one method |
| `c(n: Long, g: (Long) -> Long)`, `c(n: Object, g: (Long) -> Long)` | `"a".c(5, (x: Long) -> x * 2L)` | `obj` or `long:10`, by order | E0006 | for `long:10`, `"a".c(5L, …)`; for `obj`, `"a".c(5 as Object, …)` |
| `k(s: Supplier[String])`, `k(c: Callable[String])` | `"a".k { "v" }` | `call` or `sup`, by order | E0006 | for `call`, `val c: Callable[String] = () -> "v"`, then `"a".k(c)`; for `sup`, the same with `val s: Supplier[String]` |
| `f(o: Object, g)` on `String`, `f(s: String, g)` on `CharSequence` | `"a".f("q", (x: Int) -> x)` | `strobj1` | E0006 | `"a".f("q" as Object, …)` |
| `outer(f: (Int) -> Int)`, `outer(f: Function[String, String])` (`import { java.util.function.Function }`) | `"a".outer { p -> ((q: Int) -> p).call(1) }` | `os:s` or `oi:1`, by order | `oi:1` | `val g: Function[String, String] = (p: String) -> …`, then `"a".outer(g)` |
| `r(f: () -> void)`, `r(o: Object)` | `"a".r { IO::print("") }` | `obj` or `fn`, by order | `fn` | `val h: () -> void = () -> …`, then `"a".r(h as Object)` |
| `m(n: Int, f: (Int) -> Int)`, `m(n: Object, f: (Object) -> Object)` | `"a".m(1, { x -> x })` | `obj:1` or `int:1`, by order | `int:1` | `"a".m(1 as Object, { x -> x })` |
| `k(n: Int, f: (Int) -> Int): Int`, `k(n: Double, f: (Double) -> Double): Double` | `"a".k(2, { x -> x * 2 })` | `4.0` (the `Double` one) | `4` | `"a".k(2, (x: Double) -> x * 2.0)` |
| `run(r: Runnable)`, `run(c: Callable[Object])` | `"a".run { IO::print("") }` | `callable:null` | `runnable` | pass a typed `Callable[Object]` value |

- **What changed:** the overloads the lambda fits are ranked by one rule, written on issue
  #2447. It is the class-method rule, with one addition: a function-type parameter beats a SAM
  interface such as `Function` or `Comparator` (the `outer` row). A class method still reports
  E0006 there; #2472 tracks bringing classes onto the same rule.
- **What you see:** `E0006 method call is applicable for both …`, with a hint naming the fixes
  in the last column.
- **Still fine:** a call with one matching overload, or with no lambda argument.

## `map` and `filter` on any iterable return a `List`

`map` and `filter` on a range, on `map.values()` or on any other `Iterable` that is not a
`List` or a `Set` now return a `List`. Before, the result was typed `Iterable`, though it was
already a list.

```onion
val evens = (1..10).filter { x -> x % 2 == 0 }
evens.size                         // Before: E0004. Now: 5

var xs = (1..3).map { x -> x + 1 }
xs = new java.util.TreeSet[Int]()  // Before: compiled. Now: E0000

// To keep a variable that holds any iterable, declare its type
var ys: Iterable[Int] = (1..3).map { x -> x + 1 }
ys = new java.util.TreeSet[Int]()
```

- **What changed:** `Iterables::map(Iterable, f)` and `Iterables::filter(Iterable, p)` return
  `List[T]`. A list's or a set's own `map`/`filter` is unchanged.
- **What you see:** `E0000 type List[Int] is expected, but type TreeSet[Int] is used`, with a
  hint to declare `var xs: Iterable[Int] = ...`.
- **Runs differently:** with an overload or an extension on both `Iterable` and `List`, a
  call on the result now picks the `List` one. Rename one of them to keep the old choice.
- **Recompile:** a class compiled against an older `onion.jar` that calls these methods
  throws `NoSuchMethodError` until it is compiled again.
- **Still fine:** `.toList()` after such a pipeline still works, and the result still fits an
  `Iterable` parameter or variable.
