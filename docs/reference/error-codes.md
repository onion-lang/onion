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

## See also

- [Language specification](specification.md)
- [Standard library](stdlib.md)
