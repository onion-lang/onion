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
- **A `T?` slot:** it takes what a `T` slot takes, plus `null` and a `T?`. So `o = u`,
  `o = uo` (`uo: U?`) and `o = "x"` into `o: T?` are `E0000` too (#2332). A constructor
  argument into a `T?` parameter (`new Box[T](u)` with `def this(x: T?)`) is `E0021`, as it
  already was for a `T` parameter.
- **Indexed assignments and literals:** `xs[0] = u` and `m["k"] = u` into a `List[T]` or a
  `Map[K, T]` are `E0000`, as `xs.set(0, u)` already was (#2332). So is a literal `[u]`,
  `[t, u]` or `["k": u]` used as a `List[T]` or `Map[K, T]`; the error names the literal's
  type (`List[U]`, or `List[Object?]` for a mix). Under a `List[Object]` target, `[t, u]` is
  still fine. Write `[(u as T)]` if the value really is a `T`.
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

## `example` and `law` are now contract predicates

A check that runs when the build runs is a `static def` that returns `Boolean` and states
`ensures result`. A predicate with parameters is sampled, as `law` was.

```onion
// Before
record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)" {
  law roundtrip(p: Pt) { Pt::parse(Pt::format(p)) == p }
  example { Pt::parse("3,4") == new Pt(3, 4) }
}

// Now
record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)" {
public:
  static def roundtrip(p: Pt): Boolean
    ensures result
    = Pt::parse(Pt::format(p)) == p
  static def parsesThreeFour(): Boolean
    ensures result
    = Pt::parse("3,4") == new Pt(3, 4)
}
```

- **What changed:** `example` and `law` are removed. At the top level, write `def name(): Boolean ensures result = expr`
  (no `static`). A body with several statements is a block that ends with `return`.
- **What you see:** a false predicate is **E0113**. A parameter that cannot be sampled is also E0113,
  not a silent skip. `--contracts` is on by default (`check`).
- **Seed and samples:** `--law-seed` and `--law-samples` are now `--contract-seed` and `--contract-samples`; `--no-check-laws` is gone (use `--contracts off`).
- **`onion.Shape`:** a class that implements it no longer needs a predicate in its file (E0080 is retired).
- **Update a whole project:** `onion migrate src/` rewrites each `law` and `example` into this form.
  - A body of one expression becomes `= expr`; a longer body keeps its block, with `return` before the last statement.
  - An `example` with no name is named `example1`, `example2`, ... Rename it if you like.
  - A file that would not compile after the rewrite is left as written, and the command names the line.

## A field an `invariant` or `history` names is written only through `this`

Contracts are a preview feature (RFC #2557). A field that a class's `invariant` or `history`
names may be written only by that class's own code, through `this`. A write through any other
object is E0110.

```onion
class Clock {
public:
  var t: Int
  history t >= old(t)
  def this { t = 0 }
  def this(start: Int) { t = start }
  def advanceTo(k: Int): void { if k > t { t = k } }
}

// Before: compiled. Now: E0110 at `c`
//   val c = new Clock(); c.t = 5
// Now: pass the value to a constructor
val c = new Clock(5)

// Before: compiled. Now: E0110 at `o`
//   def sync(o: Clock): void { o.t = 9 }
// Now: call a method of that object; its own writes are checked
def sync(o: Clock): void { o.advanceTo(9) }
sync(c)
println(c.t)   // 9
```

- **What changed:** each object keeps its own `invariant` and `history`, checked one call at a
  time. A write from outside that object's methods is one no such check can follow.
- **What you see:** `E0110`, with the caret on the receiver (`o`, `c`, `cs[0]`). The message
  says to call a method of that object or set the value in a constructor.
- **Still fine:** `t = v`, `this.t = v`, `t++` and `t += 1` in the class's own
  methods, also inside a lambda. Reading `o.t` from anywhere is fine too.
- **No `onion migrate` rule, on purpose:** the fix adds a method or a constructor to the
  receiver's class, and which one fits is a design choice.

## A `tool`'s `requires` sees more of what it runs

`onionc` checks a `tool`'s `requires { ... }` clause against the effects its body has. It now
counts four things it missed before. A tool that relied on a miss gets `E0077` (an effect
missing from `requires`) or `E0078` (a capability nothing uses).

```onion
class Base {
public:
  def this {}
  def run(): Int = 1
}
class Loud extends Base {
public:
  def this {}
  override def run(): Int { IO::println("loud"); return 2 }
}

// Before: compiled. Now: E0077 at `b.run()`, "calling Loud.run"
//   tool go(): Int { val b: Base = new Base(); return b.run() }
// Now: declare what an override can do
tool go(): Int requires { console } {
  val b: Base = new Base()
  return b.run()
}
```

| What the tool does | Before | Now |
|---|---|---|
| Calls a method a program class overrides (`b.run()` above) | only the called method counted | every program override counts too (`E0077`) |
| Uses a class whose static initializer has an effect | not counted | counted (`E0077`) |
| `try (val r = new R()) { }` where `R` is a program class | `close()` not counted | `R::close` counts (`E0077`) |
| `new Box[String]()` of a program class | `unknown` | the constructor's own effects; an `unknown` kept only for it is `E0078` |

- **What you see:** `E0077` names the call and the capability to add. `E0078` names the
  capability to remove.
- **Not affected:** what a library method might call back on an object you pass it
  (`list.add(item)` and `Item::toString`). Only the build's own check of contract predicates
  counts that.
- **No `onion migrate` rule, on purpose:** the fix depends on what the called code does.
  Each error names the edit: add that capability, or remove the unused one.

## A record component named `hashCode`, `toString` or `getClass` must not change that method's type

`record R(hashCode: Long)` gave one object two `hashCode()` answers: the accessor, and the
`Int` one a `HashMap` uses. `getClass: Int` did the same to `getClass()`. Both are now errors,
as `hashCode: Int`, `toString: String` and `getClass: Class` were.

```onion
// Before: compiled, r.hashCode() = 5, (r as Object).hashCode() = 36
record R(hashCode: Long)

// Now: E0010. Rename the component.
record R(code: Long)
```

- **What you see:** **E0010** at the component, for `hashCode`, `toString` or `getClass` of any
  type, in a record or an `enum` case.
- **No `onion migrate` rule:** the new name and the code that reads it are your choice.

## A default on an abstract or interface method is type-checked

A default on an `abstract def` or an interface method now works: a call may omit that
argument. So the default is now type-checked like any other default. Before, it was never used,
and a wrong one compiled.

```onion
// Before: compiled, because the default was never used
abstract class A {
public:
  def this {}
  abstract def f(n: Int = "x"): Int
}

// Now: E0000 at "x". Give a default of the parameter's type.
abstract class A {
public:
  def this {}
  abstract def f(n: Int = 0): Int
}
```

- **What you see:** **E0000** (wrong type) or **E0002** (unknown name) at the default.
- **No `onion migrate` rule:** only you know which default was meant. Fix it, or remove it.

## A record method that overrides `toString`, `hashCode` or `equals` must keep its type

A record body could redefine an `Object` method with another return type. A class could not.
`toString(): Int` silently dropped the generated `toString()`, and `toString(): String?` let a
`null` reach `println`. A record now follows the class rule.

```onion
// Before: compiled; println(new R("")) printed null
record R(name: String) {
public:
  def toString(): String? = if name == "" { null } else { name }
}

// Now: E0000 at toString. Return a String.
record R(name: String) {
public:
  def toString(): String = if name == "" { "(none)" } else { name }
}
```

- **What you see:**
  - **E0000** at the method when the return type is another reference type (`String?`,
    `Object`, `CharSequence`). Its hint suggests `?:` for a nullable value.
  - **E0104** when either return type is primitive (`toString(): Int`, `hashCode(): Long`).
- **No `onion migrate` rule:** what to return instead of `null` is your choice.

## `Http.Response` reads through `status()`, `body()` and `headers()` only

`Http.Response` had public fields and accessor methods with the same names. The fields are
now private, as in `Server.Response` and `Proc.Result`.

```onion
val r = Http::getResponse("https://example.com")
println(r.status)     // still compiles: calls status()
println(r.status())   // the same call
```

- **Onion source:** nothing to change. With no field to read, `r.status` calls `status()`.
- **What you see:** an `IllegalAccessError` when you run a `.class` file built by an older
  `onionc` that read the field. Recompile it.
- **Java code:** write `r.status()`, `r.body()` and `r.headers()`.
- **No `onion migrate` rule:** every Onion spelling still compiles, so there is nothing to rewrite.

## One instance per trait and type, across modules

An `instance` of one trait for one type may be declared only once in the whole program.
Before, a second one in another `module` compiled, and only the one in a file without a
`module` line was ever used. Now the second one is an error.

```onion
// shapes.on
module shapes;
trait Show[T] {
  def show(x: T): String
}
instance Show[Int] {
  def show(x: Int): String = "lib " + x
}

// app.on: Before, compiled. Now E0097. Delete this instance, or the one in shapes.on.
module app;
import { shapes.Show }
instance Show[Int] {
  def show(x: Int): String = "app " + x
}
```

- **What you see:** **E0097** `instance shapes.Show[java.lang.Integer] is already defined`, at
  the second instance.
- **Fix:** keep one instance and delete the other.
- **Two files in the same package** (both without a `module` line) give the same **E0097**.
- **Type arguments of the type:** E0097 compares only the outer type, so `Show[List[String]]`
  and `Show[List[Integer]]` are one instance and clash.
- **No `onion migrate` rule:** which instance to keep is your choice.

## A call above overloads with defaults picks what a call below them picks

A call can now omit a default declared later in the file (#2653). So a call written above a
set of overloads now sees their defaults, and picks the overload a call below them picks.

```onion
def run(): String = pick()
def pick(): String = "zero"
def pick(n: Int = 1): String = "one"
println(run())   // Before: zero. Now: one, as when run() is declared below pick
```

| Overloads (declared below the call) | Call | Before | Now | To keep the old result |
|---|---|---|---|---|
| `pick()`, `pick(n: Int = 1)` | `pick()` | `zero` | `one` | rename one of the two methods |
| `f(n: Long)`, `f(n: Int, a: Int = 1)`, `f(n: Int, b: String = "")` | `f(1)` | `long` | E0006 | `f(1L)` |

- **What you see:** a different overload runs with no message, or **E0006**
  `method call is applicable for both …` at the call.
- **Still fine:** a call below the overloads, and a call that passes every argument.
- **No `onion migrate` rule:** only you know which overload the call meant. Pass the argument
  (`f(1L)`, `f(1, 1)`) or rename one method.

## A `protected` constructor is checked the same way however the call reaches it

A call that omits a default, passes named arguments, or passes an untyped lambda argument
(`new A((x) -> x + 1)`) now gets the same access check as a call that passes every argument
(#2827). So an unrelated class can no longer reach a `protected` constructor that way.

```onion
class A {
protected:
  def this(a: Int, b: Int = 2) { }
}
class Other {
public:
  def mk(): A = new A(9)          // Before: compiled and ran. Now: E0013
  def all(): A = new A(9, 2)      // E0013 before and now
}
```

- **What you see:** **E0013** `method A.new(Int, Int) is not accessible from class Other.` at the `new`.
- **Still fine:** the same call from inside `A` or from a subclass of `A`.
- **To update:** move the constructor under `public:`, or add a `public` factory method to `A`.
- **No `onion migrate` rule:** widening a constructor's access is a design choice only you can
  make, so the hint shows the edit instead.

## `Strings::split` and `Regex::split` keep trailing empty fields

Splitting used Java's `String.split`, which drops empty fields at the end. Now they are kept,
as in Python and JavaScript, so a row with an empty last column keeps that column.

```onion
val row = Strings::split("alice,30,", ",")
println(row.size)   // Before: 2, so row[2] threw. Now: 3
println(row[2])     // Now: "" (an empty string)
```

- **What changed:** `Strings::split`, `Strings::splitRegex`, and `Regex::split` with a
  `String` or a `Pattern` keep every field after the last delimiter.
- **Unchanged:** an input that does not end with the delimiter; an empty delimiter
  (`Strings::split("abc", "")` is still `[a, b, c]`); `Regex::split(s, p, limit)`;
  and Java's own `s.split(",")`, which still drops trailing empties.
- **What you see:** no error. A text that ends with the delimiter gives one more `""` for each
  delimiter at the end. Splitting lines is the common case: `Strings::split(text, "\n")` on a
  text that ends with `"\n"` now ends with an empty line.
- **To keep the old result:**
  - For lines, call `Strings::lines(text)`. `Strings::lines("l1\nl2\n")` gives `[l1, l2]`, as
    `split` did before.
  - For any other delimiter, call `Regex::split(s, pattern, 0)`. It drops every trailing empty
    field, as before. Its delimiter is a regex, so escape `.` and `|`:
    `Regex::split("a.b..", "\\.", 0)` gives `[a, b]`.
- **No `onion migrate` rule:** most calls split text that does not end with the delimiter and
  give the same result. Only you know whether an empty last field is data (a CSV column) or
  noise (a final newline), so a rewrite would be wrong for one of them.

## `null` into a type argument that is a primitive is an error

`Int` is never `null` in Onion. A generic slot whose type argument is a primitive
(`List[Int]`, `(Int) -> T`, `Map[String, Int]`) now rejects `null`, as an `Int` method
parameter always did (#2872).

```onion
val f: (Int) -> String = (i) -> "v"
f(null)                     // Before: W0012, then a crash at run time. Now: E0000

val xs: List[Int] = [1]
xs.add(null)                // Before: W0012; null was stored. Now: E0000

val ys: List[Int?] = [1]
ys.add(null)                // fine: the type argument says the slot may be null

val zs: List[Int] = [5, null]   // Before: compiled; the read of zs[1] crashed. Now: E0000 (#2895)
```

- **What you see:** **E0000** `type Int is expected, but type null is used.` at the `null`,
  with a hint to write the type argument as `Int?`.
- **Where:** a method parameter declared with the class's type variable: `f(null)` and
  `f.call(null)` on a function value, `add`/`set`/`put` on `List` and `Map`, `apply` on a
  Java functional interface, and a method of your own generic class (`Box[Int].put(null)`).
  Also a `null` element in a list or map literal written for such a type
  (`val zs: List[Int] = [5, null]`, `val m: Map[String, Int] = ["b": null]`), and the other
  ways into the same slot (#2991): a constructor (`new Box[Int](null)`, `new R[Int](null)`),
  and an indexed assignment (`xs[0] = null`, `m["b"] = null`).
- **To update:** if `null` is a value you mean to store, declare the type argument nullable:
  `List[Int?]`, `Map[String, Int?]`, `(Int?) -> T`. Reads then give an `Int?`, so handle
  `null` there (`?:` or `!!`). Otherwise, pass a real value instead of `null`.
- **No `onion migrate` rule:** the fix is either a different declared type or a different
  value, and only you know which one the code means.

## A `protected` method is checked the same way however the call reaches it

A method call that passes named arguments, or an untyped lambda argument
(`a.lam((x) -> x + 1)`, `a.lam { x -> x + 1 }`), now gets the same access check as a call
that passes every argument positionally (#2916). So an unrelated class can no longer reach a
`protected` method that way. This is the method twin of the constructor change above.

```onion
class A {
public:
  def this() { }
protected:
  def pim(a: Int, b: Int = 2): Int = a + b
}
println(new A().pim(b = 3, a = 7))   // Before: printed 10. Now: E0013
println(new A().pim(7, 3))           // E0013 before and now
```

- **What you see:** **E0013** `method A.pim(Int, Int) is not accessible from class ...` at the call.
- **Still fine:** the same call from inside `A` or from a subclass of `A`.
- **To update:** move the method under `public:`, or call it from a subclass.
- **No `onion migrate` rule:** widening a method's access is a design choice only you can
  make, so the hint shows the edit instead.

## `Yaml::stringify` writes only what `Yaml::parse` reads back

`Yaml::stringify` used to write a list of scalars, an empty Map element and NaN. Other YAML
tools could read some of that text, but `Yaml::parse` could not read it back. Now
`Yaml::stringify` throws instead, as it already did for a nested Map (#2806).

```onion
import { onion.Yaml; onion.Json }
Yaml::stringify(["apple", "banana"])   // Before: "- apple\n- banana\n". Now: throws
Json::stringify(["apple", "banana"])   // ["apple","banana"]; JSON is also valid YAML 1.2
Yaml::stringify(["apple", "banana"].map { x -> ["v": x] })   // - v: apple ...
```

- **What you see:** `IllegalArgumentException` at run time. Its message names the
  problem and suggests the edit.
- **Where:**
  - a List element that is not a Map: a String, a number, `null`, a List
  - an empty Map element (`[[:]]`)
  - a `Double` or `Float` that is NaN or an infinity, anywhere in the value
  - a record's `toYaml` (`derive!(Yaml)`) and a `shape ... = yaml` `print`, for a NaN or
    infinite component
- **Unchanged:** a flat Map, and a List of non-empty flat Maps. Both are written as before.
- **To update:**
  - For a list of scalars, call `Json::stringify` (YAML 1.2 tools read JSON), or wrap each
    element in a Map: `xs.map { x -> ["v": x] }`.
  - Leave empty Maps out of the list.
  - Replace NaN or an infinity with a value you choose (`null`, a sentinel) before writing.
- **No `onion migrate` rule:** the refused input is a value at run time, not a spelling in the
  source, so no rewrite can find it. The exception's message shows the edit instead.

## `Args` `intOption` rejects a value that is not an `Int`

`opts.intOption(name, default)` used to return the default when the value was not an integer.
So a typo such as `--count 10O` (a letter O) ran as if nothing were wrong. Now it throws, as
the auto-CLI (`def main(count: Int = 3)`) already did for the same input (#2881).

```onion
import { onion.Args }
def main(args: String[]): void {
  val opts = Args::parse(args)
  println(opts.intOption("count", 3))   // --count abc  Before: 3. Now: throws
}
```

- **What you see:** `IllegalArgumentException: invalid value for --count: 'abc' (expected Int)`
  at run time, and exit code 1. The message names the option and the value.
- **Unchanged:** an absent option still gives the default, and a valid value (`--count 7`,
  `--count -5`) still parses.
- **To keep the old result:** catch the exception and use the default:
  `val n = try { opts.intOption("count", 3) } catch e: IllegalArgumentException { 3 }`.
- **No `onion migrate` rule:** the old behaviour swallowed an error at run time. The source
  has nothing to rewrite, and only you know whether a bad value should stop the program.

## `self` is now `this`

`self` was a second spelling of `this`. Onion keeps one spelling: `this` (RFC #2788).

```onion
// Before
def this(name: String) { self.name = name }
def greet(): String = "Hi, #{self.name}"

// Now
def this(name: String) { this.name = name }
def greet(): String = "Hi, #{this.name}"
```

- **What you see:** `E0117: `self` is retired; write `this`.` at every `self`, all at once.
  In static code it replaces E0050.
- **Still a reserved word:** `self` is not a free name now. A field written `` `self` `` keeps
  working.
- **Update a whole project:** `onion migrate src/ tests/`. It rewrites every `self` in code
  and inside `#{...}` in strings. It leaves comments, plain string text, `\#{self}` and
  `` `self` `` alone.

## `override` must match the base method's types up to `?`

An `override` whose parameter is `JInteger` where the base has `Int`, or whose return is
`Int?` where the base returns `Int`, never overrode anything. It compiled as a new overload,
and a call through the base type still ran the base method. Now it is **E0068** (#2896).

```onion
class Base {
public:
  def this {}
  def dec(k: Int): void { println("Base") }
}
class Up extends Base {
public:
  def this {}
  override def dec(k: JInteger): void { println("Up") }   // Before: compiled. Now: E0068
}
val b: Base = new Up(); b.dec(1)                           // Before: printed Base
```

- **What you see:** **E0068** `method dec(JInteger) is marked override but does not override
  any method ...`, followed by the base signature (`dec(Int): void`).
- **Still fine:** `override def dec(k: Int?)` over `dec(k: Int)`, where the parameter differs
  only by `?`, now really overrides. A boxed parameter through a generic supertype
  (`G[JInteger]`) overrides too.
- **To update:** to override, write the base types (`dec(k: Int)`, or `dec(k: Int?)`). To keep
  a separate overload, remove `override` and give it parameter types that differ by more
  than `?`, or another name.
- **No `onion migrate` rule:** whether the method was meant to override or to overload is
  your choice, and the two edits run different code. The error shows the base signature
  instead.

A method whose parameters differ from a base method's **only by `?`** is the same method
now, with or without `override`:

- **Before:** an unmarked `def dec(k: Int?): void` beside a base `dec(k: Int): void` was a
  separate overload, and `b.dec(1)` through `Base` ran the base. With a reference return
  (`String`), the same method already overrode. `override` changed nothing for a reference
  return but decided the outcome for `void` and primitive returns.
- **Now:** it overrides for every return type. `override` is a check, as for exact types,
  and never decides which method runs. Without `override`, the compiler warns **W0029**
  (`'dec(Int?)' overrides 'dec(Int): void' of Base ...`). Over a `final` base method this is
  **E0039**, as for the exact types (`setDaemon(on: Boolean?)` in a `Thread` subclass included).
- **The other direction is unchanged:** an own `f(k: Int): void` beside a base
  `f(k: Int?): void` stays an overload, so `null` through `Base` still reaches the base
  method. Marked `override`, it is **E0068**; make the parameter `Int?` to override.
- **To silence W0029:** add `override` if the method is meant to replace the base one.
- **To keep a separate overload:** rename the method (`decOrSkip(k: Int?)`). No `onion
  migrate` rule: only you know whether the method was meant to replace the base one.

## `OnionMath::randomInt` is removed; use `Rand::nextInt`

Onion had two ways to get a random `Int` in a range, with opposite upper bounds (#3069).
`Rand::nextInt(min, max)` stays. Its `max` is exclusive, as in `a..<b`, Java and Kotlin.

```onion
val die = OnionMath::randomInt(1, 6)   // Before: 1 to 6. Now: E0005
val die = Rand::nextInt(1, 7)          // 1 to 6
```

- **What you see:** `E0005`, with a hint that shows `Rand::nextInt(min, max + 1)`.
- **Unchanged:** `Rand::nextInt(min, max)` and `OnionMath::random()`.
- **`onion migrate` rewrites it.** It writes `Rand::nextInt(min, max + 1)`, so every value the
  old call could return stays possible:
  - `OnionMath::randomInt(1, 6)` becomes `Rand::nextInt(1, 7)`.
  - `OnionMath::randomInt(0, xs.size - 1)` becomes `Rand::nextInt(0, xs.size)`.
  - `OnionMath::randomInt(a, b)` becomes `Rand::nextInt(a, b + 1)`.
  - Calls inside `"#{...}"`, and a call inside another call's arguments, are rewritten too.
  - So is a call through an import: `randomInt(1, 6)` after `import { onion.OnionMath::* }`,
    or `OM::randomInt(1, 6)` after `import { onion.OnionMath as OM; }`. The import itself
    still compiles, so it is left as written.
  - When the file, or another file named with it, has its own `Rand` (a class, an import, a
    variable), migrate writes `onion.Rand::nextInt`, so the call keeps meaning Onion's `Rand`.
- **Only calls the compiler reports are rewritten.** A `randomInt` that still compiles is
  your own (a class of yours named `OnionMath`, an inherited method), so it is left as written.
- **Which `max` gets `+ 1`:** a plain number below `Int.MAX_VALUE` (`6`), or a `max` that
  reads a variable, a parameter, a field or a call (`n`, `xs.size`, `Math::abs(n)`).
- **Other constant maxes are noted; edit them by hand.** That covers `2147483647` and any
  expression of constants (`1 << 4`, `Integer::MAX_VALUE`). Migrate leaves the file as
  written. Write `Rand::nextInt(min, (max) + 1)`, then run migrate again. When max is
  `Int.MAX_VALUE`, write `Rand::nextInt(min - 1, max) + 1` instead.
- **A bare `randomInt(a, b)` in a file that also names its own `randomInt`** may be either
  one, so migrate notes it and leaves it as written.
- **A bare `randomInt(a, b)` with no `import { onion.OnionMath::* }`** is not the removed
  method, so migrate leaves it alone. Its hint shows the edit to make by hand.
- **Check a max held in a variable.** Migrate cannot know its value. If it can be
  `Int.MAX_VALUE` at run time, `max + 1` overflows there and the call throws
  `IllegalArgumentException`. For that case, write `Rand::nextInt(min - 1, max) + 1`.
- **`Rand::nextInt()` replaces a call only when min is `Int.MIN_VALUE` and max is
  `Int.MAX_VALUE`.** It returns any `Int`, negatives included, so it does not replace
  `randomInt(0, Int.MAX_VALUE)`.
- **Run it on one program at a time.** Unrelated scripts named together can hide each other's
  errors, and then a call is left as written.

## A generic overload competes with a plain one, as in Java

A plain class now reaches a generic parameter through an interface it implements (#2845).
`String` is a `Comparable[String]`, so `g[T](c: Comparable[T])` now fits `g("a")`. Where a
plain overload fits too, Java's rules pick between them.

```onion
import { java.lang.CharSequence }
def g(c: CharSequence): String = "charseq"
def g[T](c: Comparable[T]): String = "generic"
IO::println(g("a"))                    // Before: charseq. Now: E0006
IO::println(g("a" as CharSequence))    // charseq, before and now
```

| Overloads | Call | Before | Now | To keep the old result |
|---|---|---|---|---|
| `g(CharSequence)`, `g[T](Comparable[T])` | `g("a")` | `charseq` | E0006 | `g("a" as CharSequence)` |
| `f(Serializable)`, `f[T](Comparable[T])` | `f("a")` | `serializable` | E0006 | `f("a" as Serializable)` |
| `f(Object)`, `f[T](Comparable[T])` | `f("a")` | `object` | `generic` | `f("a" as Object)` |
| `show(Int)`, `show[T](Comparable[T])`, with `xs = [1, 2, 3]` | `show(xs[0])` | `int 1` | `comparable` | `show(xs[0] as Int)` |
| `log(String, CharSequence)`, `log[T](T, Comparable[T])` | `log("t", "m")` | `plain` | E0006 | `log("t", "m" as CharSequence)` |

- **What you see:** **E0006** `method call is applicable for both …`, with a hint naming the
  `as` cast. Or, beside `f(Object)` or `show(Int)`, the generic overload runs with no message.
- **Which argument the hint names:** the first one whose cast rules the generic overload
  out (the 2nd in `log("t", "m")`). A varargs argument is cast to the element type:
  `g("a" as CharSequence)` for `g(c: CharSequence...)`.
- **Still fine:** a call whose argument class does not implement the generic parameter's
  interface, and a call to only one of the overloads.
- **No `onion migrate` rule:** finding the affected calls needs overload resolution, and
  only you know which overload a call meant. The hint shows the cast instead.

## A list literal with a `null` element is a list of nullable (#2998)

`[x, null]` with no expected type used to be a list of the non-null type. It is now a list of
the nullable type, so the `null` is visible to the checker.

```onion
val ys = ["a", null]
IO::println(ys[0].length())     // Before: 1. Now: E0070
IO::println(ys[0]?.length())    // 1, before and now
```

| Program | Before | Now | To fix it |
|---|---|---|---|
| `val ys = ["a", null]; ys[0].length()` | `1` | E0070 | `ys[0]?.length()` or `ys[0]!!.length()` |
| `total([1, null])` with `total(xs: List[Int])` | compiled, crashed on the `null` | E0005 | take `List[Int?]`, then handle each `null` |
| `val n: Int = xs[0]` with `xs = [1, null]` | compiled, crashed on the `null` | error | `xs[0] ?: 0` |
| `[[1, null], [2]]` | `List[List[Int]]` | `List[Object]` | write the inner lists with one element type |
| `["a", null] + ["b"]` | compiled | E0001 | give both lists the same type: `List[String?]` |

- **What you see:** E0070 (`may be null and cannot be dereferenced directly`), or E0005 when
  the list is passed to a `List[Int]` parameter. The cause is the `null` in the literal.
- **A target type still works:** `val ys: List[Int?] = [1, null]`. `List[Int]` is rejected, as the `null` does not fit.
- **Unchanged:** `[null]`, `[null, null]`, and a literal whose expected type is already known.
- **No `onion migrate` rule:** deciding what to do with a `null` (skip it, default it,
  reject it) depends on the program. The edits above show each choice.
