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

### Elvis with Nullable Primitives

The Elvis operator also works on nullable *primitive* types. The result is an
unboxed primitive, so it can be assigned straight to a non-nullable `Int`,
`Long`, `Double`, etc.:

```onion
val n: Int? = null
val v: Int = n ?: -1        // -1

val m: Int? = 42
val w: Int = m ?: -1        // 42
```

This pairs naturally with a safe call to a primitive-returning method: the call
yields a boxed `Integer?`, and `?:` supplies the primitive fallback when the
receiver is `null`:

```onion
val s: String? = null
val len: Int = s?.length() ?: -1    // -1 (s is null)

val s2: String? = "hello"
val len2: Int = s2?.length() ?: -1  // 5
```

### Elvis with a Control Expression on the Right

The right-hand side of `?:` can be any expression, including control-flow
expressions such as `throw` and `return`. This makes `?:` a concise way to fail
fast or exit early when a value is missing:

```onion
def firstName(s: String?): String {
  // If s is null, throw instead of producing a value
  val name: String = s ?: throw new RuntimeException("nil")
  return name
}

def lenOrDefault(s: String?, default: Int): Int {
  // If s is null, return early from the method
  val v: String = s ?: return default
  return v.length()
}
```

## Null Checking with `if`

You can check for `null` using standard `if` statements:

```onion
val name: String? = getUserName()

if name != null {
  // Inside this block, we know name is not null
  println("Hello, " + name)
} else {
  println("Hello, guest")
}
```

### Negated Conditions

`!(cond)` swaps which branch narrows — useful for early-exit style:

```onion
def f(s: String?): String {
  if !(s != null) {
    return "nil"
  } else {
    return "n" + s.length()   // s narrowed to String here
  }
}

if !(o is String) { return "not a string" }
```

### Smart-Casting Nullable Fields

A null check narrows not only local variables but also immutable (`val`)
nullable fields. Inside an `if field != null { ... }` block, a `val` field of
type `T?` is treated as `T`, so you can call methods on it directly:

```onion
class Person {
  val name: String?
public:
  def this(name: String?) { this.name = name }
  def nameLength(): Int {
    if name != null {
      return name.length()   // name narrowed to String here
    } else {
      return -1
    }
  }
}

println(new Person("Alice").nameLength())  // 5
println(new Person(null).nameLength())     // -1
```

A mutable (`var`) field is **not** narrowed, because its value could change
between the check and the use (E0041 if you try). Snapshot it into a local
`val` first — locals are always narrowed:

```onion
class Counter {
  var label: String?
public:
  def this(label: String?) { this.label = label }
  def show(): Int {
    val l = label            // snapshot into a local val
    if l != null {
      return l.length()      // l narrowed to String
    } else {
      return -1
    }
  }
}

println(new Counter("hi").show())   // 2
println(new Counter(null).show())   // -1
```

A mutable (`var`) **local** is narrowed as well, as long as it is not reassigned
between the check and the use — so a `var` you assign once, or only reassign
elsewhere, still narrows where it matters:

```onion
def firstNonEmpty(lines: List[String]): String {
  var found: String? = null
  foreach line: String in lines {
    if found == null && line.length() > 0 {
      found = line
    }
  }
  if found != null {
    return found            // found (a var) narrowed to String here
  }
  return "(none)"
}
```

This also covers the read-loop idiom `while (line = next()) != null { ... }`,
where `line` is narrowed at the top of the loop body. A reassignment *after* the
use does not undo the narrowing, but a `var` captured by a closure stays nullable
inside the closure, since the closure may run after the variable has changed.

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
  static def main(args: String[]): void {
    // Nullable type declaration
    val maybeString: String? = null
    println("maybeString is null: " + (maybeString == null))

    val definiteString: String? = "hello"
    println("definiteString is null: " + (definiteString == null))

    // Safe call returns nullable result
    val upper: Object? = definiteString?.toUpperCase()
    if upper != null {
      println("Upper: " + upper)
    }

    // Safe call on null returns null
    val nullResult: Object? = maybeString?.toUpperCase()
    if nullResult == null {
      println("nullResult is null as expected")
    }

    // Safe call with arguments
    val sub: Object? = definiteString?.substring(0, 3)
    if sub != null {
      println("Substring: " + sub)
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
| Safe indexing | `?[i]` | N/A | N/A |
| Elvis operator | `?:` | `?:` | N/A |
| Non-null assertion | `!!` | `!!` | N/A |

## Null Literal Checking (W0012)

Assigning the `null` literal to a non-nullable type warns:

```onion
val s: String = null     // W0012: declare as String? or avoid null
val ok: String? = null   // fine
```

The warning covers declarations, assignments, arguments and returns.
Promote it to an error with `--warn error`, or suppress it with
`--Wno null-to-non-nullable`. Values coming from Java APIs are not
checked (their nullness is unknown to the compiler).

## Safe Indexing (`?[]`) and Non-Null Assertion (`!!`)

`xs?[i]` indexes a nullable receiver: null when `xs` is null, the element
(widened to nullable) otherwise. Works for arrays, `List` and maps, and
chains with `?.`:

```onion
val xs: List[String]? = loadOrNull()
val first = xs?[0]
val len = names?[0]?.length()
```

`expr!!` asserts the value is not null: the static type loses one level
of nullability, and a `NullPointerException` is thrown at the assertion
site if the value is actually null. Prefer `?.`/`?:`/null checks; use
`!!` when you know better than the type system:

```onion
val s: String? = definitelyThere()
println(s!!.length())
```

## Nullable-Aware Generics

Type parameters interact with nullability the same way Kotlin's do.

### Bare `[T]` ranges over nullable types

A type parameter declared without a bound accepts both `String` and
`String?` as type arguments:

```onion
class Box[T] {
  val item: T
public:
  def this(item: T) { this.item = item }
  def get(): T { return this.item }
}

val maybe: String? = null
val box = new Box[String?](maybe)   // OK: T := String?
val out: String? = box.get()
```

Because `T` may be instantiated with a nullable type, values of type `T`
cannot be dereferenced directly inside the generic body (E0057):

```onion
def show(): String {
  return this.item.toString()       // E0057: item may be null
}
```

Use safe navigation, the Elvis operator, or a null check — the same
tools that work on `T?`:

```onion
def safe(): String? { return this.item?.toString() }
def fallback(): String { return (this.item ?: "fb").toString() }
def checked(): String {
  val it = this.item
  if it != null { return it.toString() } else { return "<null>" }
}
```

### `[T extends B]` keeps T non-null

Declaring a bound restricts the parameter to non-null types: nullable
type arguments are rejected, and values of `T` can be dereferenced
freely inside the body:

```onion
class Sorted[T extends Comparable] {
  val item: T
public:
  def this(item: T) { this.item = item }
  def show(): String { return this.item.toString() }  // OK
}

new Sorted[String]("ok")      // OK
new Sorted[String?](maybe)    // error: String? does not satisfy the bound
```

This also applies to `[T extends Object]` — unlike a bare `[T]`, it
rejects nullable arguments.

### `[T extends B?]` opts back into nullable with a bound

```onion
class Cache[T extends Comparable?] { ... }   // accepts String and String?
```

### Inference

Type-argument inference binds nullable types when the arguments require
it, and merges mixed nullability:

```onion
static def first[T](a: T, b: T): T { return a }

first(maybe, "solid")   // T := String?  (String? + String merge)
```

Type variables coming from Java classes are *platform* parameters:
their nullability is unknown, so they accept nullable arguments and
remain dereferenceable (matching how Java values are treated
elsewhere).

## Next Steps

- [Control Flow](control-flow.md) - if, while, for, select
- [Functions](functions.md) - Function definitions and lambdas
- [Variables and Types](variables-and-types.md) - Type system overview
