# Language Specification

This page summarizes the syntax and semantics of Onion as implemented by the
current compiler. The authoritative grammar is `grammar/JJOnionParser.jj`.

## Lexical Structure

### Keywords

```
abstract    break       case        catch       class       continue
def         do          else        enum        extends     extension
false       final       finally     for         foreach     forward
if          import      interface   is          module      new
null        override    private     protected   public      record
return      sealed      select      self        static      super
synchronized this        throw       true        try         type
val         var         when        while
```

Keywords can be used as identifiers by escaping with backticks: `` `class` ``.

### Identifiers

- Start with a letter or underscore, followed by letters, digits, underscores
- Case-sensitive

### Literals

**Integer literals** (underscores may group digits in any numeric literal):
- Decimal: `42`, `1_000_000`
- Hexadecimal: `0xFF`, `0xFF_FF`
- Binary: `0b1010`, `0b1010_1010`
- Octal: `077`

Suffixes select the type: `B` (Byte), `S` (Short), `L` (Long); unsuffixed
integers are `Int`.

**Floating point:** `3.14`, `1.23e10`, `1_234.5` — `Double` by default,
`f`/`F` for `Float`, `D` for explicit `Double`.

**Strings:** `"text"` with the usual escapes; `"""..."""` for multi-line.
String interpolation embeds expressions with `#{expr}` (nested string
literals inside the interpolation are allowed).

**Characters:** `'A'`, `'\n'`. **Booleans:** `true`, `false`. **Null:** `null`.

**Collection literals:** `[1, 2, 3]` builds a `List`; `["a": 1, "b": 2]`
builds an insertion-ordered `Map`; `[:]` is the empty map.

**Ranges:** `a..b` (inclusive) and `a..<b` (exclusive) — iterable in
`foreach` without materializing a collection.

## Type System

### Primitive Types

| Type | Size |
|------|------|
| `Byte` / `Short` / `Int` / `Long` | 8/16/32/64-bit integers |
| `Float` / `Double` | IEEE 754 |
| `Char` | 16-bit Unicode |
| `Boolean` | `true`/`false` |

Capitalized names denote the primitives; the boxed wrapper classes are
available as `JByte`, `JInteger`, `JDouble`, etc. Boxing into `Object`/
`Number` contexts is automatic.

### Reference Types

- **Class / interface types**, including applied generics: `List[String]`,
  `Box[T]`
- **Array types**: `T[]`, `T?[]` (nullable elements), multi-dimensional `T[][]`
- **Function types**: `Int -> Int`, `(Int, Int) -> String`
- **Nullable types**: `T?` — see below
- **Null type** (type of `null`) and **bottom type** (non-returning
  expressions such as `return`/`throw`/`break`/`continue`)

### Nullable Types and Null Safety

`T` cannot hold `null`; `T?` can. `T → T?` widens implicitly; `T? → T`
requires unwrapping:

- `x?.member` — safe call, result widened to nullable
- `xs?[i]` — safe indexing
- `x ?: default` — Elvis; when the fallback cannot be null the result type
  loses its nullability
- `x!!` — non-null assertion (throws `NullPointerException` when null)
- Smart casts: `if x != null { ... }` narrows immutable locals and
  never-assigned parameters; `is`-checks narrow similarly, and `!(cond)`
  swaps which branch narrows
- Assigning the `null` literal to a non-nullable type warns (W0012)
- `Object` accepts any nullable value (it is the top type, like Scala's `Any`)

### Generics

Erasure-based generics with `[]` syntax: `class Box[T]`, `def first[T](xs: List[T]): T`,
`record Pair[A, B](first: A, second: B)`. Wildcards `?`, `? extends T`,
`? super T` are accepted in type arguments.

Primitive types can be used as type arguments. They are boxed internally
(`Int` -> `Integer`), so Onion code can implement Java generic interfaces such as
`Comparator[Int]` using primitive parameter types. The compiler generates the
necessary bridges between the erased boxed contract and the primitive implementation.

```onion
class IntComparator <: Comparator[Int] {
public:
  def compare(a: Int, b: Int): Int {
    return a - b
  }
}

val nums: List[Int] = [5, 1, 3]
Collections::sort(nums, new IntComparator())
```

Type-parameter nullability follows Kotlin:

- Bare `[T]` accepts nullable type arguments (`Box[String?]`); values of
  type `T` inside the generic body cannot be dereferenced directly (E0057)
  until narrowed with `?.`, `?:`, `!!` or a null check
- `[T extends B]` restricts `T` to non-null types and permits direct
  dereference; `[T extends B?]` opts back into nullable with bound `B`
- Type variables from Java classes are *platform*: permissive in both
  directions

### Type Conversions

Widening (`Byte → Short → Int → Long → Float → Double`, `Char → Int`) is
automatic. Narrowing requires `as` (or the legacy `$`):

```onion
val i: Int = (3.14 as Int)
```

`as` is also the reference cast: `(obj as JButton).getText()`.
`expr is Type` tests the runtime type.

## Declarations

### Variables

```onion
val name: Type = expr     // immutable
var name: Type = expr     // mutable
val inferred = expr       // local type inference
val (a, b) = recordValue  // destructuring (records / Map.Entry)
```

### Functions

```onion
def name(param: Type, opt: Type = default): ReturnType { body }
def name(param: Type): ReturnType = expression      // expression body
def vararg(parts: String...): String { ... }        // varargs
def generic[T](x: T): T { ... }                     // method type params
```

Call sites may pass arguments by name (`f(b = 2, a = 1)`); omitted
parameters fill from defaults. Methods, constructors and records all
support named arguments and defaults.

### Classes

```onion
class Name [TypeParams] [(primary params)] [: Super[(args)]] [<: I1, I2] {
  sections
}
```

**Primary constructors:** `val`/`var` parameters declare public
(final/mutable) fields assigned automatically; plain parameters exist only
in the constructor (e.g. to feed `: Super(args)`). Class bodies are
optional.

```onion
class Point(val x: Int, val y: Int)
class Dog(name: String, val breed: String) : Animal(name)
```

**Classic constructors** remain available:

```onion
def this(params) { body }
def this(params): (superArgs) { body }
```

Members default to private; `public:` / `protected:` / `private:` sections
set accessibility. `static def` declares static methods; `static val`
constants. Field initializers run in declaration order.

### Interfaces

```onion
interface Greeter {
  def name(): String                                  // abstract
  def greet(): String { return "Hello, " + this.name() }  // default method
  def shout(): String = "HEY " + this.name()          // expression body
}
```

Methods with bodies compile to JVM default methods. `sealed interface`
restricts implementations to the compilation unit and enables
exhaustiveness checking in `select`.

### Records

```onion
record Point(x: Int, y: Int)
record Pair[A, B](first: A, second: B) <: SomeInterface
```

Components become private final fields with public accessor *methods*
(`p.x()`); `equals`/`hashCode`/`toString`/`copy` are generated. `copy`
supports full clones, positional and named partial copies
(`p.copy(y = 9)`). Records destructure in `val (a, b) = p` and in `select`
patterns.

#### Pattern-attached records (`from re"..."`)

A record can derive a typed parser from its shape by attaching a regex
literal right after the component list (before any `<:` supertypes). `from`
is a soft keyword, recognized only when immediately followed by a regex
literal, so it stays usable as an ordinary identifier:

```onion
record Access(time: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"
```

Two static methods are synthesized:

- `Access::parse(s: String): Access?` — ANCHORED match of the whole string.
  Each capture group is converted to its component's declared type; the
  record is returned on success. On no-match **or** a conversion failure
  (e.g. a non-numeric `status`), it returns `null` — which composes with
  smart casts and the elvis operator.
- `Access::parseAll(text: String): List[Access]` — splits `text` into lines,
  parses each, drops the `null`s, and returns the matches.

Supported component types are `String` (identity) and the wrapper-parsable
primitives `Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte`.
The capture-group count must equal the component count and the regex must be
valid — both are checked at compile time (group/count mismatch is **E0060**,
a malformed regex is **E0059**), and an unsupported component type is
**E0061**.

#### `derive!` — record serde derivation

Adding `derive!(Format, ...)` after a record's component list (or after a
`from re"..."` clause, before any `<:` supertypes) instructs the compiler to
synthesize serialization methods from the record's shape. The `!` suffix
signals macro-style expansion rather than a type-class constraint.

```onion
record User(name: String, age: Int) derive!(Json)
record Config(host: String, port: Int, debug: Boolean) derive!(Json, Yaml)
record Point(x: Int, y: Int) from re"(-?\d+),(-?\d+)" derive!(Json, Yaml)
  <: Printable
```

Supported markers are **`Json`** and **`Yaml`**; an unrecognized marker is
**E0063**. For each marker the compiler synthesizes two static methods on the
record:

| Marker | Synthesized method | Synthesized method |
|--------|-------------------|-------------------|
| `Json` | `R::fromJson(s: String): R?` | `R::toJson(v: R): String` |
| `Yaml` | `R::fromYaml(s: String): R?` | `R::toYaml(v: R): String` |

All four formats share a single internal `toMap` / `fromMap` core: `toJson`
and `toYaml` both call `toMap` and forward the resulting `Map` to
`Json::stringify` or `Yaml::stringify`; `fromJson` and `fromYaml` parse the
text into an intermediate `Map` (via `Json::parse` or `Yaml::parse`) and pass
it to `fromMap`. This means the core serialization logic is written once and
adding a new format requires only registering its `parse` / `stringify` pair
in the stdlib — the macro itself does not change.

`from-R?` methods return `null` on parse failure or a type-conversion error;
they never throw. `to-R` methods always produce a well-formed string.
Round-trips hold for scalar components: `fromJson(toJson(v)) == v`.

Supported component types are the eight scalar primitives: `String`, `Int`,
`Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte`. A component of any
other type is **E0062**. `derive!` may coexist with `from re"..."` on the
same record; the two features are independent.

#### `law` / `example` — compile-time specification checks

A record declaration may be followed by `law` and `example` clauses. The
compiler executes these at build time (during the `LawCheckPhase` that runs
after type-checking). A failing check is a **compile error**, not a
runtime error.

```onion
record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)"
  law roundtrip(p: Pt) { Pt::parse(Pt::format(p)) == p }
  example { Pt::parse("3,4") == new Pt(3, 4) }

record User(name: String, age: Int) derive!(Json)
  law jsonRoundtrip(u: User) { User::fromJson(User::toJson(u)) == u }
  example { new User("ko", 3).name() == "ko" }
```

**`example { boolExpr }`** — a concrete assertion. The compiler evaluates
`boolExpr` and requires it to be `true`. If it is `false`, compilation fails
with **E0065** and a message showing the expression that returned `false`.

**`law name(p: T) { boolExpr }`** — a property. The compiler generates a set
of sample values for the parameter `p` (covering boundary values, negatives,
and random cases) and checks `boolExpr` for each. If any sample falsifies the
law, compilation fails with **E0064** and a report that includes the
counterexample. Parameters may be the same scalar types supported by `derive!`
(`String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte`) or
the record type itself.

Multiple `law` and `example` clauses may appear on a single record, in any
order. They coexist freely with `from re"..."` and `derive!(...)`:

```onion
record R(x: Int, y: Int) from re"(-?\d+),(-?\d+)" derive!(Json)
  law textRoundtrip(r: R) { R::parse(R::format(r)) == r }
  law jsonRoundtrip(r: R) { R::fromJson(R::toJson(r)) == r }
  example { R::parse("0,0") == new R(0, 0) }
```

The purpose of `law` / `example` is to bring specifications — not just
documentation, but machine-verified contracts — inside the language itself.
The `parse∘format == id` invariant above is checked on every compile, so a
grammar change that breaks the round-trip fails the build immediately.

### Enums

```onion
enum Color { RED, GREEN, BLUE }

enum Planet(mass: Double) {
  MERCURY(3.3e23),
  EARTH(5.97e24)
public:
  def heavierThan(other: Planet): Boolean = this.mass() > other.mass()
}
```

Enums compile to JVM enums: `name()`, `ordinal()`, `values()`,
`valueOf(String)` work. Record-style parameters become final fields with
accessors; access sections after the constant list declare methods.
`select` over an enum checks exhaustiveness when no `else` is present.

### Extensions

```onion
extension String {
  def shout(): String { return this.toUpperCase() + "!" }
}
```

### Type Aliases

```onion
type Names = List[String]
```

### Delegation

```onion
class MyClass <: Interface {
  forward val member: Interface
  ...
}
```

`forward` auto-delegates the interface's methods to the member.

## Statements and Expressions

Control-flow forms are expressions: blocks evaluate to their last
expression, `if`/`select`/`try` produce values, loops evaluate to `void`,
and `return`/`throw`/`break`/`continue` are bottom-typed.

### Conditionals

```onion
if cond { ... } else if cond2 { ... } else { ... }
val label = if ok { "yes" } else { "no" }
```

Assignment inside a parenthesized condition is allowed:
`while (line = reader.readLine()) != null { ... }`.

### Loops

```onion
while cond { ... }
do { ... } while cond                  // body-first
for var i = 0; i < n; i += 1 { ... }
foreach x: Type in collection { ... }
foreach i: Int in 0..<n { ... }
foreach (k, v) in map { ... }
break / continue
```

### Select (Pattern Matching)

```onion
select expr {
case 1, 2:                ...    // value cases (any expressions)
case Color::RED:          ...    // enum constants (exhaustiveness-checked)
case s is String:         ...    // type pattern, binds s narrowed
case Circle(r) when r > 9: ...   // record destructuring + guard
case Rect(w, _):          ...    // _ ignores a component
case re"(\w+)@(\w+)" (u, h): ... // regex pattern: binds capture groups
else:                     ...
}
```

Matches over `sealed` interfaces and enums are exhaustiveness-checked
(E0042) when no `else` is present; exhaustive matches can be used as
expressions.

Regex patterns match a `String` subject against an **anchored** regex
literal (the whole subject must match) and bind its capture groups as
`String` locals; guards may use the bound groups. Because the pattern is a
literal, it is validated at compile time: a malformed regex is E0059 and a
capture-group / binding count mismatch is E0060.

### Exceptions

```onion
try { ... }
catch e: IllegalArgumentException | IllegalStateException { ... }  // multi-catch
catch e: Exception { ... }
finally { ... }

try (val r = open(); val w = openOther()) { ... }   // try-with-resources
throw new IllegalStateException("message")
```

Resources close automatically in reverse declaration order.

### Lambdas and Function Values

```onion
val f: Int -> Int = x -> x * 2          // bare param, expression body
val g = (a: Int, b: Int) -> a + b
list.map { x => x * 2 }                 // trailing lambda
Future::async(() -> { return compute() })
val r: Runnable = () -> println("hi")   // SAM conversion
```

### Do Notation

```onion
do[Option] { a <- getA(); b <- getB(); ret a + b }
do[Future] { x <- fetch(); ret x.size() }
do[List]   { x <- [1, 2]; y <- ["a", "b"]; ret x + y }   // comprehension
```

`x <- e; rest` desugars to `e.bind((x) -> rest)`; a binding followed
directly by the final `ret e2` becomes `e.map((x) -> e2)`. Any type with
`bind`/`map` (instance or extension methods) is do-able: `Option`,
`Result`, `Future`, `List` (comprehension), and user types. Works at the
script top level too.

### Scheme-Prefixed Literals

```onion
val p    = re"\d+-\d+"                        // compiled Pattern (RAW: no \\ escaping)
val text = file"notes.txt".text()             // FileResource: text/lines/csv/csvRows/json/write/append
val rows = file"data.csv".csvRows()           // List of Map (header -> value), RFC 4180
val body = http"https://api.example.com".get() // HttpResource: get/getJson/post/postJson/put/delete
```

`prefix"raw"` is sugar for the unqualified call `prefix("raw")` resolved
through the default static imports (`onion.Resources`), so the literal and
the function form (`file(path)` for dynamic values) are equivalent. The
body is raw — backslashes pass through verbatim; `\"` escapes a quote
without consuming the backslash.

### Pipeline Operator

```onion
5 |> double               // double(5)
5 |> add(3)               // add(5, 3) — first-argument injection
xs.map { x => x * 2 }
  |> println              // a newline before |> continues the pipeline
```

`e |> f(args...)` injects `e` as the first argument; the right-hand side
may be a bare function name, an unqualified or method/static call. Sits
between assignment and `||` in precedence.

### Operators

Precedence (highest first):

1. Member access / static access / indexing: `.` `?.` `::` `[]` `?[]`
2. Postfix: `++` `--` `!!` / cast `as`, `$`
3. Unary: `!` `-` `+` `~`
4. Multiplicative: `*` `/` `%`
5. Additive: `+` `-`
6. Shifts: `<<` `>>` `>>>` (`<<` on collections appends)
7. Relational: `<` `>` `<=` `>=` `is`
8. Equality: `==` `!=` (value), `===` `!==` (reference)
9. Bitwise: `&` `^` `|`
10. Logical: `&&` `||`
11. Elvis: `?:`
12. Assignment: `=` and compound `+=` `-=` `*=` `/=` `%=` `&=` `|=` `^=` `<<=` `>>=` `>>>=`

`==` on objects is value equality (`equals`); `===` compares references.
`+` performs string concatenation when either operand is a `String`.

## Imports and Modules

```onion
module my.pkg

import {
  java.util.*
  java.lang.Long as JLong;
}
```

`onion.*`, `java.lang.*`, `java.io.*`, `java.util.*` are imported by
default (with `onion.*` taking priority). Single-class imports of unknown
classes are compile errors.

## Entry Points

For `onion`/`runScript`, the entry point is, in order: an explicit class
with a `main` method; the `main` method of the top class; otherwise the
first top-level statement. Top-level statements, `def` functions and `val`/
`var` declarations form implicit scripts; `args: String[]` is available.

### Auto-CLI

A top-level `main` whose parameters are CLI-convertible types (`String`,
`Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte`) is invoked
automatically with arguments parsed from the command line:

```onion
def main(name: String, count: Int = 3, loud: Boolean = false): void { ... }
// $ onion greet.on world --count 5 --loud
```

Required parameters are positional; defaulted parameters become `--name`
flags (`Boolean` defaults become presence switches). Values are converted
to the declared types, default expressions are evaluated when the flag is
absent, and a usage line is derived from the signature on error.
Everything after the script file on the `onion` command line is passed to
the script verbatim.

## Warnings

`--warn off|on|error` sets the level; `--Wno codes` suppresses specific
warnings. Notable: W0006 unused parameter, W0012 null literal flowing into
a non-nullable type.

## Current Limitations

1. Generics are erasure-based (no variance or reified type info), though
   type-parameter nullability is tracked at compile time
2. Tail-call optimization covers direct self-recursion (plus a
   mutual-recursion optimization pass); not general CPS
3. Some diagnostics are still reported later in the pipeline than ideal

## Grammar Reference

The complete grammar is `grammar/JJOnionParser.jj` (JavaCC).

## Next Steps

- [Standard Library](stdlib.md) - Built-in functions and classes
- [Compiler Architecture](compiler-architecture.md) - How the compiler works
- [Examples](../examples/overview.md) - Code examples
