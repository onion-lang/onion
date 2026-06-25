# Onion Compiler Error Codes

Onion reports compilation errors with stable codes.  This page explains the most common codes and suggests how to fix them.

## Internal errors

### `I0000` — Internal compiler error

An unexpected exception escaped from a compiler phase.  This is a bug in the compiler, not in your program.  Please reduce the source to a minimal reproducer and file an issue.

## Type errors

### `E0000` — Incompatible type

A value of one type was used where a different type was expected.

```onion
val x: Int = "hello"   // E0000: String where Int expected
```

Common fixes:
- Check the expected type and convert the value explicitly (e.g. `Int::parseInt(s)`).
- For nullable-to-non-null mismatches, the compiler now appends a hint suggesting `!!`, `?:`, or a null-check.

```onion
val name: String? = lookup()
val sure: String = name     // E0000 + nullable hint
val fixed: String = name ?: ""
```

### `E0001` — Incompatible operand type

An operator was applied to a type it does not support.

```onion
val b = true + 1   // E0001
```

## Resolution errors

### `E0002` — Variable not found

A referenced local variable does not exist in scope.  The compiler suggests similar names when possible.

```onion
val userName = "ok"
IO::println(usrName)   // E0002, suggests userName
```

### `E0003` — Class not found

A type name could not be resolved.  Check spelling and imports.

```onion
val xs = new ArrayLst[String]()   // E0003, suggests ArrayList
```

### `E0005` — Method not found

No method matches the call.  If a method with the same name exists but the argument types differ, the compiler lists the available signatures.

### `E0021` — Constructor not found

No constructor matches the arguments.  The compiler lists available constructors.

## Null-safety errors

### `E0057` — Type parameter may be null

A bare type parameter `[T]` can be instantiated with a nullable type, so direct dereference is not allowed without a null-check or a non-null bound.

```onion
class Box[T] {
public:
  def size(x: T): Int = x.toString().length()   // E0057
}
```

Fixes:
- Use `?.` / `?:` / `if x != null`.
- Declare a non-null bound: `class Box[T extends Object]`.

## Pattern-matching errors

### `E0042` — Non-exhaustive pattern match

A `select` on a sealed type does not cover all cases.

```onion
sealed interface Shape {}
record Circle(r: Int) <: Shape
record Rect(w: Int, h: Int) <: Shape

select shape {
  case Circle(r): IO::println("circle")
  // missing Rect case → E0042
}
```

## Parser errors

Parser errors do not carry `E` codes; they report the encountered token and the expected tokens.

```
Test.on:2:10: Syntax error. Encountered "{", but expecting ";"
```

## See also

- [Language specification](specification.md)
- [Standard library](stdlib.md)
