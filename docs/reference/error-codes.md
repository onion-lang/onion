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

### `E0027` — Type is not boxable

A `void`-typed expression (the result of calling a `void` method) was used
where a value is required, e.g. as an operand of string concatenation. `void`
has no boxed form, so there is nothing to box.

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val s = "a" + IO::println("b")   // E0027: IO::println returns void
    return 0
  }
}
```

Fix: don't use the result of a `void` call as a value — call it as its own
statement instead.

### `E0028` — Lvalue required

The left-hand side of an assignment is not an assignable location (a local
variable, field, or array element) — e.g. a method call result or a literal.

```onion
class Test {
public:
  static def main(args: String[]): Int {
    "a".length() = 3   // E0028: the left side isn't an assignable location
    return 0
  }
}
```

Fix: assign to a variable, field, or array element instead.

### `E0030` — Type is not generic

Type arguments were supplied for a type that takes none.

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val x: String[Int] = null   // E0030: String takes no type arguments
    return 0
  }
}
```

Fix: drop the type arguments (`String`, not `String[Int]`).

### `E0031` — Type argument arity mismatch

A generic type was applied with the wrong number of type arguments.

```onion
class Box[T] { public: def this {} }
class Test {
public:
  static def main(args: String[]): Int {
    val b = new Box[String, String]()   // E0031: Box takes 1, not 2
    return 0
  }
}
```

This also fires when the mismatch is written inside a nullable type
annotation, e.g. `val b: Box[String, String]? = null`.

### `E0033` — Method is not generic

Type arguments were supplied at a call site for a method that takes none.

```onion
class Test {
public:
  static def main(args: String[]): Int { return "abc".length[String]() }   // E0033
}
```

Fix: drop the type arguments from the call (`"abc".length()`).

### `E0034` — Method type argument arity mismatch

A generic method call supplied the wrong number of explicit type arguments.

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val l = java.util.Collections::emptyList[String, String]()   // E0034: expects 1, not 2
    return 0
  }
}
```

### `E0035` — Erased JVM signature collision

Two overloads have parameter types that differ in Onion's type system but erase
to the same JVM method descriptor (generics are erasure-based, so `List[String]`
and `List[Integer]` are indistinguishable at the bytecode level), so the class
file cannot carry both.

```onion
class C {
public:
  def this {}
  def f(x: java.util.List[String]): Int = 1
  def f(x: java.util.List[Integer]): Int = 2   // E0035: both erase to f(List)I
}
```

Fix: give the overloads different erased signatures (e.g. different arity, or
a parameter type that doesn't erase to the same class), or merge them into one
method that dispatches internally.

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

### `E0004` — Field not found

No field with that name exists on the target type.  Check spelling and field visibility.

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val s = "a".noSuchField   // E0004
    return 0
  }
}
```

### `E0005` — Method not found

No method matches the call.  If a method with the same name exists but the argument types differ, the compiler lists the available signatures.

### `E0006` — Ambiguous method

Two overloads are equally applicable to the call and neither is more specific than
the other — often because an argument's static type (e.g. `null`, or a common
supertype) fits multiple unrelated parameter types at once.

```onion
class A {}
class B {}
class Test {
public:
  static def foo(x: A): Int = 1
  static def foo(x: B): Int = 2
  static def main(args: String[]): Int {
    foo(null)   // E0006: applicable to both foo(A) and foo(B)
    return 0
  }
}
```

Fix: cast the argument to pin the overload (`foo(null as A)`), or rename/merge one
of the overloads so only one candidate matches.

### `E0011` — Duplicated global variable definition

A top-level `var`/`val` is declared twice with the same name — whether both are
modifier-qualified (`static var x = ...` twice) or bare (no modifier; a bare
top-level `var`/`val` is promoted to a `public static` field of the script's
synthetic class, so it is just as global as the modifier-qualified form).

```onion
static var x: Int = 1
static var x: Int = 2   // E0011: x already defined
```

### `E0012` — Duplicated function definition

Two top-level `def` functions share the same name and the same parameter types.
Overloading by different parameter types is still allowed.

```onion
def foo(x: Int): Int { return x }
def foo(x: Int): Int { return x + 1 }   // E0012: foo(Int) already defined

def bar(x: Int): Int { return x }
def bar(x: String): String { return x }   // fine: different parameter types
```

### `E0013` — Method not accessible

A `private` (or otherwise insufficiently visible) method, constructor, or static
method is called from outside the class that can see it.

```onion
class C {
private:
  static def s(): Int = 1
}
def main(args: String[]): void { IO::println(C::s()) }   // E0013
```

### `E0014` — Field not accessible

A non-public field is read or written from outside its declaring class.

```onion
class Plain {
  var value: String
public:
  def this(v: String) { value = v }
}
val p = new Plain("orig")
p.value = "changed"   // E0014: value is not public
```

### `E0015` — Class not accessible

A member is selected on a value whose static type is an `internal` class declared
in a different module. Accessibility is module-scoped: an `internal` class is
visible only within the module (the dotted prefix before the class name) that
declares it.

```onion
module pkg.a
internal class Hidden {
public:
  var n: Int
  def this { n = 1 }
}
```

```onion
module pkg.b
import { pkg.a.Hidden }
class UseIt {
public:
  static def main(args: String[]): Int {
    val h: Hidden = null
    return h.n   // E0015: Hidden is internal to pkg.a
  }
}
```

### `E0016` — Cyclic inheritance

A class's or interface's supertype chain (`extends`/`conforms`) loops back on
itself, directly or through an intermediate type.

```onion
class A extends B {
}
class B extends A {   // E0016: A -> B -> A cyclicity
}
```

Fix: break the cycle — one of the two must not extend the other.

### `E0018` — Illegal inheritance

A class or interface `extends`/`conforms` a supertype it isn't allowed to use —
either the supertype is `final`, or an interface position holds a class (or vice
versa).

```onion
class A extends java.lang.String { public: def this {} }   // E0018: String is final
```

Fix: extend a non-`final` class, or use `conforms` only with interfaces and
`extends` only with a class.

### `E0019` — Illegal method call

A `static` method was called through an instance receiver (`obj.m(...)` or
`obj?.m(...)`) instead of the class itself.

```onion
class A {
public:
  def this {}
  static def s(): Int = 1
}
class Test {
public:
  static def main(args: String[]): Int { return new A().s() }   // E0019
}
```

Fix: call it through the class, `A::s()`.

### `E0020` — Cannot return value

A bare `return;` appears in a method whose declared return type isn't `void`,
or a `return` yields a `void`-typed expression where a value was expected.

```onion
class Test {
public:
  static def f(): Int { return }   // E0020: f must return an Int
  static def main(args: String[]): Int { return 0 }
}
```

Fix: return a value of the declared type (`return 0`), or change the method's
return type to `void`.

### `E0021` — Constructor not found

No constructor matches the arguments.  The compiler lists available constructors.

### `E0022` — Ambiguous constructor

Two constructor overloads are equally applicable to a `new` call — often because
an argument's static type (e.g. `null`) fits multiple unrelated parameter types
at once.

```onion
class C {
public:
  def this(a: String) {}
  def this(b: StringBuilder) {}
}
class Test {
public:
  static def main(args: String[]): Int { val c = new C(null); return 0 }   // E0022
}
```

Fix: cast the argument to pin the overload (`new C(null as String)`), or
rename/merge one of the constructors so only one candidate matches.

### `E0023` — Interface required

A position that requires an interface type — a `forward`-delegated field's
declared type, or a `{ ... }` closure literal's target type — was given a
non-interface (e.g. class) type instead.

```onion
class A {
  forward val x: String   // E0023: String is a class, not an interface
public:
  def this { x = "a" }
}
```

Fix: declare the field/target with an interface type, or drop `forward` if
delegation isn't actually needed.

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

## Extension method errors

### `E0084` — Duplicate extension method

Two methods with the same name and parameter types are declared in the same
`extension` block. Left unchecked, the generated container class would carry two
methods with an identical JVM signature, which only fails once something loads the
class (a `ClassFormatError`, surfaced as an internal I0000 error) rather than at
compile time.

```onion
extension Double {
  def pct(): String = "" + self
  def pct(): String = "" + self   // E0084: duplicated extension method pct() on Double
}
```

Fix: remove or rename the duplicate. Two extension methods with the same name but
different parameter types are unaffected — that's ordinary overloading.

## Declaration errors

### `E0007` — Duplicate local variable

The same name is declared twice as a local variable in the same scope.

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val x = 1
    val x = 2   // E0007: x is already declared in this scope
    return 0
  }
}
```

Fix: rename one of the variables, or drop the redundant declaration.

### `E0008` — Duplicate class

Two top-level classes (or interfaces/records/enums) share the same name.

```onion
class A { public: def this {} }
class A { public: def this {} }   // E0008: A is already declared
```

Fix: rename or remove one of the declarations.

### `E0009` — Duplicate field

The same field name is declared twice on a class.

```onion
class A {
  var x: Int
  var x: Int   // E0009: x is already declared on A
public:
  def this {}
}
```

Fix: remove the duplicate field, or give it a different name.

### `E0010` — Duplicate method

Two methods on the same class share an identical name and parameter list, so
neither is a valid overload of the other.

```onion
class Test {
public:
  def m(): Int = 1
  def m(): Int = 2   // E0010: m() is already declared on Test
  static def main(args: String[]): Int { return 0 }
}
```

Fix: rename one method, or give it different parameter types to make it a genuine
overload.

### `E0025` — Duplicate constructor

Two constructors on the same class have identical parameter types, so
neither is a valid overload of the other.

```onion
class A {
public:
  def this(x: Int) {}
  def this(y: Int) {}   // E0025: this(Int) is already declared on A
}
```

Fix: give one constructor different parameter types, or remove the duplicate.

### `E0026` — Duplicate generated method

A record's `law`/`example` clauses synthesize a check method per clause,
named after the clause. Two `law` (or two `example`) clauses with the same
name and the same parameter types mangle to the same method, colliding at
codegen. Without this check the collision surfaced later as a raw JVM
`ClassFormatError` instead of a normal diagnostic.

```onion
record Point(x: Int, y: Int) from re"(-?\d+),(-?\d+)"
  law roundtrip(p: Point) { Point::parse(Point::format(p)) == p }
  law roundtrip(p: Point) { Point::parse(Point::format(p)) == p }   // E0026
```

Fix: rename one of the clauses, or remove the duplicate.

### `E0029` — Duplicate type parameter

The same type parameter name appears twice in one class, interface, or
method's type parameter list.

```onion
class Box[T, T] { public: def this {} }   // E0029: T is already declared
```

Fix: give the type parameters distinct names.

### `E0085` — Static method with no body

A method declared `static` has no body (no `{ ... }` and no `= expr`). The grammar
also accepts a bodyless method as an abstract/interface-style declaration, but `static`
and abstract are contradictory — a static method cannot be overridden, so it cannot be
abstract either. Generating a class file for one would need both `ACC_STATIC` and
`ACC_ABSTRACT`, which the JVM rejects.

```onion
class LineFilter {
public:
  static def main(args: String[]): void
  // E0085: static method main must have a body
}
```

Fix: add a body, or drop `static` to declare an ordinary abstract instance method.

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
| `E0085` | static method … must have a body |

## See also

- [Language specification](specification.md)
- [Standard library](stdlib.md)
