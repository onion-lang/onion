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
