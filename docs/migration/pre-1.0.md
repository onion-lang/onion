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
- **Update a whole project:**
  ```bash
  grep -rl '\bret\b' --include='*.on' . | xargs perl -pi -e 's/\bret\b/yield/g'
  ```
  Check the result if you use `ret` as a variable name.

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
- **What you see:** `integer literal out of range for Byte (-128..127, or a bit pattern up to 0b11111111 or 0xFF) -- ...`, at the literal (`... for Short ...` with the `S` suffix).
- **Still fine:** a binary or hex bit pattern, such as `0b11111111B`, `0xFFB` or `0xFFFFS`.
- **Watch out:** a hex literal that ends in `B` is a `Byte`, even when you meant the hex digit `B`. `0xFFB` is the Byte -1 and `0xAB` is the Byte 10, not the Int 4091 or 171. For such an Int, write it in decimal (`4091`) or binary.

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
