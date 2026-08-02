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

### `E0066` — Raw generic type not allowed

A generic type was used without type arguments.  Onion forbids raw generic
types; supply the type arguments explicitly.

```onion
val xs: List = [1]        // E0066: raw List
val ok: List[Int] = [1]   // fixed
```

### `E0067` — Missing return

A value-returning method has a block body that can complete without returning a
value.  Return on every path, or use an expression body.

```onion
class C {
public:
  static def f(): Int { 5 + 10 }   // E0067: value is never returned
}
```

Fixes:
- Add an explicit `return`: `{ return 5 + 10 }`.
- Use an expression body: `def f(): Int = 5 + 10`.

## Resolution errors

### `E0002` — Variable not found

A referenced local variable does not exist in scope.  The compiler suggests similar names when possible.

```onion
val userName = "ok"
println(usrName)   // E0002, suggests userName
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

### `E0073` — Map cannot be iterated directly

`foreach` walks an array or something with an `iterator()`; a `Map` has neither.

```onion
val m = ["a": 1]
foreach k: String in m { println(k) }   // E0073
```

Iterate the entries with the destructuring form, or pick a view:

```onion
foreach (k, v) in m { println(k + "=" + v) }
foreach k: String in m.keySet() { println(k) }
```

### `E0074` — A law's parameter type cannot be generated

`law` is checked at build time over generated sample values, so every parameter type
needs a generator. Generatable types are `String`, `Int`, `Long`, `Double`, `Float`,
`Boolean`, `Short`, `Byte`, and records whose components are all generatable.

```onion
record Dummy(v: Int)
  law overArray(xs: Int[]) { xs != null }   // E0074: no generator for Int[]
```

Anything else — arrays, `Map`, enums, interfaces, or a class with more than one
constructor — has no generator. Such a law used to be skipped silently, which made it
indistinguishable from a law that held; a check that cannot run must not look like one
that passed. Change the parameter type, or remove the law.

```onion
record Pt(x: Int, y: Int)
  law reflexive(p: Pt) { p == p }          // fine: a flat record is generatable
```

### `E0075` — A class declaring laws could not be loaded

Laws run against the compiled classes. If a class that declares `law` / `example`
clauses cannot be loaded, none of its checks ran — reported rather than passed over,
for the same reason as `E0074`.

### `E0076` — Unknown shape format

A `shape name = <format>` clause names a document format the compiler does not know.
Guessing would produce a shape that silently reads nothing, so it is an error listing
what is supported.

```onion
record Pt(x: Int, y: Int)
  shape doc = toml     // E0076: supported formats are json, yaml
```

For an inline pattern, write `shape name = re"..."`.

### `E0080` — A user-written `Shape` asserts no law

A concrete class implementing `onion.Shape[T]` directly (rather than deriving one via
`shape`/`from re"..."`) must assert at least one machine-checked `law` or `example` in
its file. A derived shape's round-trip is guaranteed by construction; a hand-written
one only *claims* it, so the claim must be checked.

```onion
class FixedWidth conforms Shape[Person] {
public:
  def this {}
  def parse(text: String, origin: Origin): Outcome[Person] { ... }
  def canPrint(): Boolean = true
  def print(v: Person): String { ... }
  def describe(): String = "fixed-width Person"
}
// E0080: nothing in this file asserts a law for FixedWidth
```

Fix: add a top-level `example` (or the class's own `law`) — the canonical claim is the
round-trip `s.parse(s.print(v)).get() == v`:

```onion
example fixedWidthL1 {
  val s = new FixedWidth()
  val v = new Person("KOTA", 42)
  s.parse(s.print(v)).get() == v
}
```

A parse-only shape (`canPrint(): false`) instead asserts whatever laws its reading
direction has. A false claim is `E0065`, the same as any other falsified `example`.

## Capability errors

A `tool` declaration draws a boundary with a `requires { ... }` clause; the compiler
infers the whole body's effects (transitively, through ordinary functions) and checks
the declaration against them.

### `E0077` — Tool performs an undeclared effect

The body performs an effect the `requires` clause does not cover. The diagnostic names
the missing effect, the callee, and the call site.

```onion
tool sneaky(src: String, dst: String): void
  requires { read(src) }
{
  Files::writeText(dst, Files::readText(src))   // E0077: performs `write` (writeText)
}
```

Fix: add the missing capability — `requires { read(src), write(dst) }`.

### `E0078` — Declared capability the body cannot perform

A capability in `requires` is unused: nothing in the body performs it. An honest
boundary lists only what actually happens.

```onion
tool overclaim(src: String): String
  requires { read(src), net }   // E0078: `net` is unused
{
  return Files::readText(src)
}
```

Fix: remove the unused capability.

### `E0079` — Invalid capability entry

A `requires` entry is not a valid capability: an unknown effect name, a parameter
argument on an effect that is always ambient, or an argument that does not name one of
the tool's parameters.

```onion
tool t(): Int requires { teleport } { return 0 }
  // E0079: `teleport` is not an effect

tool t2(x: String): Int requires { console(x) } { IO::println(x); return 0 }
  // E0079: `console` does not take a parameter argument

tool t3(src: String): String requires { read(nope) } { return Files::readText(src) }
  // E0079: `nope` does not name a parameter
```

The effect vocabulary is `read write net exec env clock rand console unknown`; only
`read`, `write`, `net`, and `exec` take a parameter argument.

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

### `E0081` — Tool parameter cannot be read from the command line

A `tool`'s parameters must all be readable from a command-line argument, since the CLI
is derived from them.

```onion
record P(x: Int)

tool takeRec(p: P): Int requires { console } { IO::println("" + p.x()); return 0 }
// E0081: parameter `p` has type P, which cannot be read from an argument.
//        Supported: String, Int, Long, Double, Float, Boolean, Short, Byte.
```

This used to be silent: CLI synthesis skipped such a script, which then compiled to a
program with no `main` and nothing to run (issue #424).

### `E0082` — Duplicate tool name

Two `tool`s share a name. Function overloading is fine, but the command line selects a
tool **by name alone**, so the second one can never be invoked and `--contract` lists
both under the same name with no way to address either.

```onion
tool same(a: Int): Int requires { console } { IO::println("A"); return 0 }
tool same(b: String): Int requires { console } { IO::println("B"); return 0 }
// E0082: duplicate tool name `same`.
```

## Try/catch errors

### `E0083` — Unreachable catch clause

A later `catch` clause whose exception type is already a subtype of (or the same as)
an earlier clause's type can never run — the earlier clause handles it first.

```onion
try {
  risky()
} catch e: RuntimeException {
  handle(e)
} catch e: IllegalArgumentException {
  // E0083: IllegalArgumentException is a RuntimeException, already caught above
}
```

Reorder so the more specific type comes first, or remove the unreachable clause. The
alternatives of a single multi-catch clause (`catch e: A | B`) are not checked against
each other.

## Pattern-matching errors

### `E0042` — Non-exhaustive pattern match

A `select` on a sealed type does not cover all cases.

```onion
sealed interface Shape {}
record Circle(r: Int) conforms Shape
record Rect(w: Int, h: Int) conforms Shape

select shape {
  case Circle(r): println("circle")
  // missing Rect case → E0042
}
```

## Parser errors

Parser errors do not carry `E` codes; they report the encountered token and the expected tokens.

```
Test.on:2:10: Syntax error. Encountered "{", but expecting ";"
```

## Every diagnostic code

The sections above explain the codes you are most likely to meet, with examples. This
table lists all of them, so a code seen in a build log can always be looked up.

| Code | Meaning |
|------|---------|
| `I0000` | internal compiler error — a bug in the compiler, not your program |
| `E0000` | type … is expected, but type … is used |
| `E0001` | operator … is not applicable for type … |
| `E0002` | variable not found |
| `E0003` | class not found |
| `E0004` | field ….… is not found |
| `E0005` | method applicable for ….…(…) is not found |
| `E0006` | ambiguous method |
| `E0007` | duplicated local variable definition … |
| `E0008` | duplicated class definition … |
| `E0009` | duplicated field definition ….… |
| `E0010` | duplicated method definition ….…(…) |
| `E0011` | duplicated global variable definition … |
| `E0012` | duplicated function definition …(…) |
| `E0013` | method ….…(…) is not accessible from class … |
| `E0014` | field ….… is not accessible from class … |
| `E0015` | class … is not accessible from class … |
| `E0016` | inheritance relations which includes … have cyclicity |
| `E0018` | class … do inheritance illegally |
| `E0019` | method ….… cannot be called |
| `E0020` | this method cannot return value |
| `E0021` | constructor not found |
| `E0022` | ambiguous constructor |
| `E0023` | interface required, but type … is used |
| `E0025` | duplicated constructor definition …(…) |
| `E0026` | duplicated generated method ….…(…) |
| `E0027` | type … is not boxable type |
| `E0028` | lvalue is required |
| `E0029` | duplicated type parameter definition … |
| `E0030` | type … does not take type arguments |
| `E0031` | type … expects … type arguments, but … are supplied |
| `E0032` | type argument … must be a reference type |
| `E0033` | method ….… does not take type arguments |
| `E0034` | method ….… expects … type arguments, but … are supplied |
| `E0035` | Erased JVM signature collision: ….…… |
| `E0036` | cannot assign to val … |
| `E0037` | class … must implement abstract method …(…) or be declared abstract |
| `E0038` | cannot instantiate abstract class … |
| `E0039` | method …(…) cannot override final method in … |
| `E0040` | cannot call method … on primitive type … |
| `E0041` | type … is not a valid method call target |
| `E0042` | non exhaustive pattern match |
| `E0043` | unknown parameter name: … |
| `E0044` | duplicate argument: … |
| `E0045` | positional argument after named argument is not allowed |
| `E0046` | wrong number of bindings in destructuring pattern |
| `E0047` | … is not a record type or does not exist |
| `E0048` | break is only allowed inside a loop |
| `E0049` | continue is only allowed inside a loop |
| `E0050` | current instance is not available in static context |
| `E0051` | return type is required for method … |
| `E0052` | lambda parameter … must specify a type |
| `E0053` | cyclic type alias detected: … |
| `E0054` | duplicate type alias: … |
| `E0055` | function … requires a body |
| `E0057` | value of type parameter … may be null and cannot be dereferenced directly |
| `E0058` | label … is not defined on any enclosing loop |
| `E0059` | invalid regular expression literal: … |
| `E0060` | the regex pattern has … capture group(s) but … binding(s) were given |
| `E0061` | record component … has type …, which cannot be derived from a `from re"..."` clause |
| `E0062` | record component … has type …, which derive! cannot serialize |
| `E0063` | unknown derive! marker `…` |
| `E0064` | law violation |
| `E0065` | example failed |
| `E0066` | raw type … is not allowed; supply type arguments (e.g |
| `E0067` | method … may reach the end of its body without returning a … |
| `E0068` | method …(…) is marked override but does not override any method in a base class or interface of … |
| `E0069` | local val … must be initialized at its declaration |
| `E0070` | value of type … may be null and cannot be dereferenced directly |
| `E0071` | … is a variable, not a type |
| `E0072` | abstract method … cannot have a body (the body would be silently ignored) |
| `E0073` | a Map (…) cannot be iterated directly by foreach |
| `E0074` | law parameter not generatable |
| `E0075` | law class not loadable |
| `E0076` | unknown shape format `…` |
| `E0077` | tool `…` performs `…` here (calling …) but does not declare it |
| `E0078` | tool `…` declares capability `…` but nothing in its body can perform it |
| `E0079` | tool `…` has an invalid capability `…`: … |
| `E0080` | class `…` implements onion.Shape but nothing in its file asserts a law |
| `E0081` | tool parameter not cli convertible |
| `E0082` | duplicate tool name `…` |
| `E0083` | this catch clause for … can never be reached: an earlier catch clause for … already handles it |
| `E0084` | duplicated extension method …(…) on … |

## See also

- [Language specification](specification.md)
- [Standard library](stdlib.md)
