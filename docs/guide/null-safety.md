# Null Safety

Onion provides null safety features inspired by Kotlin, helping you avoid `NullPointerException` at compile time.

## Nullable Types

By default, types in Onion cannot hold `null`. To allow `null`, use the `?` suffix:

```onion
// Non-nullable: cannot be null
val name: String = "Alice"
// val name: String = null  // Compile error!

// Nullable: can be null
val maybeName: String? = null  // OK
val anotherName: String? = "Bob"  // Also OK
```

### Type Compatibility

- `T` to `T?` is allowed (widening)
- `T?` to `T` is **not** allowed (requires explicit handling)

```onion
val s: String = "hello"
val nullable: String? = s  // OK: String → String?

val maybeS: String? = "world"
// val nonNull: String = maybeS  // Compile error: String? → String
```

## Safe Call Operator (`?.`)

The safe call operator allows you to safely access methods or properties on a nullable value. If the target is `null`, the entire expression returns `null` instead of throwing an exception.

### Basic Usage

```onion
val s: String? = null
val upper: Object? = s?.toUpperCase()  // Returns null, no exception!

val s2: String? = "hello"
val upper2: Object? = s2?.toUpperCase()  // Returns "HELLO"
```

### Method Calls with Arguments

```onion
val s: String? = "hello world"
val sub: Object? = s?.substring(0, 5)  // Returns "hello"

val nullS: String? = null
val nullSub: Object? = nullS?.substring(0, 5)  // Returns null
```

### Safe Call on Non-Nullable Types

You can also use `?.` on non-nullable types (though it's less useful since they can't be `null`):

```onion
val s: String = "hello"
val upper: Object? = s?.toUpperCase()  // Works, returns "HELLO"
```

## Combining with Elvis Operator (`?:`)

The safe call operator works great with the Elvis operator for providing default values:

```onion
val name: String? = null

// If name is null, use "unknown"
val displayName: String = name ?: "unknown"

// Chain safe call with Elvis
val s: String? = null
val upper: String = s?.toUpperCase() ?: "DEFAULT"  // Returns "DEFAULT"

val s2: String? = "hello"
val upper2: String = s2?.toUpperCase() ?: "DEFAULT"  // Returns "HELLO"
```

## Null Checking with `if`

You can check for `null` using standard `if` statements:

```onion
val name: String? = getUserName()

if name != null {
  // Inside this block, we know name is not null
  IO::println("Hello, " + name)
} else {
  IO::println("Hello, guest")
}
```

## Return Type of Safe Calls

The return type of a safe call expression is always nullable (`T?`), since it can return `null` when the target is `null`:

```onion
val s: String? = "hello"

// toUpperCase() returns String, but s?.toUpperCase() returns String?
val upper: Object? = s?.toUpperCase()
```

For primitive return types, they are boxed (e.g., `Int` becomes `Integer?`):

```onion
val s: String? = "hello"
val len: Object? = s?.length()  // Returns boxed Integer (5), not primitive int
```

## Complete Example

```onion
class Main {
public:
  static def main(args: String[]): Unit {
    // Nullable type declaration
    val maybeString: String? = null
    IO::println("maybeString is null: " + (maybeString == null))

    val definiteString: String? = "hello"
    IO::println("definiteString is null: " + (definiteString == null))

    // Safe call returns nullable result
    val upper: Object? = definiteString?.toUpperCase()
    if upper != null {
      IO::println("Upper: " + upper)
    }

    // Safe call on null returns null
    val nullResult: Object? = maybeString?.toUpperCase()
    if nullResult == null {
      IO::println("nullResult is null as expected")
    }

    // Safe call with arguments
    val sub: Object? = definiteString?.substring(0, 3)
    if sub != null {
      IO::println("Substring: " + sub)
    }
  }
}
```

Output:
```
maybeString is null: true
definiteString is null: false
Upper: HELLO
nullResult is null as expected
Substring: hel
```

## Best Practices

1. **Prefer non-nullable types** - Use `T?` only when `null` is a meaningful value
2. **Use `?.` for chains** - Avoid nested null checks with safe call chains
3. **Provide defaults with `?:`** - Use Elvis operator to provide sensible defaults
4. **Check early** - Validate nullable inputs at function boundaries

## Comparison with Other Languages

| Feature | Onion | Kotlin | Java |
|---------|-------|--------|------|
| Nullable type | `T?` | `T?` | `@Nullable T` |
| Safe call | `?.` | `?.` | N/A |
| Elvis operator | `?:` | `?:` | N/A |
| Non-null assertion | N/A | `!!` | N/A |

## Next Steps

- [Control Flow](control-flow.md) - if, while, for, select
- [Functions](functions.md) - Function definitions and lambdas
- [Variables and Types](variables-and-types.md) - Type system overview
