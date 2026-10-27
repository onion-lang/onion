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

## A lambda passed to overloaded extension methods picks like a class method

When an `extension` declares the same method more than once, a lambda argument now picks the
overload a class method would. The choice no longer depends on declaration order. Five calls
that used to compile now mean something else:

| Overloads | Call | Before | Now | To keep the old one |
|---|---|---|---|---|
| `z(f: (Object) -> void)` on `Object`, `z(o: Object)` on `String` | `"abc".z((x: Object) -> …)` | `String`'s `z(Object)` | E0006 | rename one method |
| `c(n: Long, g: (Long) -> Long)`, `c(n: Object, g: (Long) -> Long)` | `"a".c(5, (x: Long) -> x * 2L)` | `obj` or `long:10`, by order | E0006 | for `long:10`, `"a".c(5L, …)`; for `obj`, `"a".c(5 as Object, …)` |
| `k(s: Supplier[String])`, `k(c: Callable[String])` | `"a".k { "v" }` | `call` | E0006 | `val c: Callable[String] = () -> "v"`, then `"a".k(c)` |
| `f(o: Object, g)` on `String`, `f(s: String, g)` on `CharSequence` | `"a".f("q", (x: Int) -> x)` | `strobj1` | E0006 | `"a".f("q" as Object, …)` |
| `outer(f: (Int) -> Int)`, `outer(f: Function[String, String])` | `"a".outer { p -> ((q: Int) -> p).call(1) }` | `os:s` | `oi:1` | pass a typed `Function[String, String]` value |

- **What changed:** the overloads the lambda fits are ranked as for class methods. The rule is
  written on issue #2447.
- **What you see:** `E0006 method call is applicable for both …`, with a hint naming the fixes
  in the last column.
- **Still fine:** a call with one matching overload, or with no lambda argument.
