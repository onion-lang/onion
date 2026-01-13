# Language Specification

Formal specification of the Onion programming language.

## Lexical Structure

### Keywords

Reserved words in Onion:

```
break       case        catch       class       continue
def         else        false       finally     for
foreach     forward     if          import      interface
module      new         null        private     protected
public      record      return      select      self
static      super       this        throw       true
try         val         var         while
```

### Identifiers

- Start with a letter or underscore
- Followed by letters, digits, or underscores
- Case-sensitive

Valid identifiers:
- `name`, `value`, `_temp`, `count123`, `myVariable`

Invalid identifiers:
- `123abc` (starts with digit)
- `my-variable` (contains hyphen)
- `class` (reserved keyword)

### Literals

**Integer Literals:**
- Decimal: `42`, `0`, `123`
- Hexadecimal: `0xFF`, `0x1A2B`
- Octal: `077`, `0123`

**Long Literals:**
- `42L`, `1234567890L`

**Floating Point:**
- `3.14`, `0.5`, `1.23e10`

**Float Literals:**
- `3.14f`, `0.5f`

**String Literals:**
- Double quotes: `"Hello, World!"`
- Escape sequences: `\n`, `\t`, `\\`, `\"`

**Character Literals:**
- Single quotes: `'A'`, `'1'`, `'\n'`

**Boolean Literals:**
- `true`, `false`

**Null Literal:**
- `null`

## Type System

### Primitive Types

| Type | Size | Range |
|------|------|-------|
| `Byte` | 8-bit | -128 to 127 |
| `Short` | 16-bit | -32768 to 32767 |
| `Int` | 32-bit | -2³¹ to 2³¹-1 |
| `Long` | 64-bit | -2⁶³ to 2⁶³-1 |
| `Float` | 32-bit | IEEE 754 |
| `Double` | 64-bit | IEEE 754 |
| `Char` | 16-bit | Unicode character |
| `Boolean` | N/A | `true` or `false` |

### Reference Types

- **Class types**: `String`, `Object`, user-defined classes
- **Interface types**: Java interfaces
- **Array types**: `Type[]`
- **Null type**: Type of `null` literal
- **Bottom type**: `Nothing` for non-returning expressions

### Type Annotations

Types are written after a colon in declarations (e.g., `val name: String`). Local `val` / `var` declarations can omit the type when an initializer is present.

```onion
val name: String
val age: Int
val scores: Int[]
val inferred = "Alice"
```

## Declarations

### Variable Declaration

```onion
val identifier: Type = expression
var identifier: Type = expression
val identifier = expression       // local only
var identifier = expression       // local only
```

### Function Declaration

```onion
def identifier(param :Type, ...) :ReturnType {
  body
}
```

### Class Declaration

```onion
class ClassName {
  members
}

class ClassName : ParentClass {
  members
}

class ClassName <: Interface {
  members
}

class ClassName : ParentClass <: Interface1, Interface2 {
  members
}
```

### Member Variables

```onion
class Example {
  val memberName: Type

  public:
    var publicMember: Type
}
```

### Constructors

```onion
def this(params) {
  body
}

def this(params): (superArgs) {
  body
}
```

### Methods

```onion
def methodName(params) :ReturnType {
  body
}

static def staticMethod(params) :ReturnType {
  body
}
```

## Statements

Control-flow forms are expressions. Blocks evaluate to the last expression, `if`/`select`/`try` can appear where an expression is expected, and loops evaluate to `void`. `return`/`throw`/`break`/`continue` are bottom-typed (they never produce a value).

### Expression Statement

```onion
expression;
```

### Block Statement

```onion
{
  statements
}
```

### If Statement

```onion
if condition {
  body
} else if condition {
  body
} else {
  body
}
```

### While Loop

```onion
while condition {
  body
}
```

### For Loop

```onion
for init; condition; update {
  body
}
```

### Foreach Loop

```onion
foreach variable :Type in collection {
  body
}
```

### Select Statement

```onion
select expression {
  case value1, value2:
    body
  case value3:
    body
  else:
    body
}
```

### Try-Catch Statement

```onion
try {
  body
} catch variable :ExceptionType {
  handler
}
```

### Return Statement

```onion
return
return expression
```

### Break and Continue

```onion
break
continue
```

## Expressions

### Operators

**Precedence (highest to lowest):**

1. Member access: `.`, `::`
2. Postfix: `++`, `--`
3. Unary: `!`, `-`, `+`
4. Type cast: `$`
5. Multiplicative: `*`, `/`, `%`
6. Additive: `+`, `-`
7. Relational: `<`, `>`, `<=`, `>=`
8. Equality: `==`, `!=`
9. Logical AND: `&&`
10. Logical OR: `||`
11. Assignment: `=`
12. List append: `<<`

### Lambda Expressions

```onion
(param :Type, ...) -> { body }
```

### Type Casting

```onion
expression$TargetType
```

### Object Creation

```onion
new ClassName(args)
new Type[size]
```

### Method Calls

```onion
object.method(args)
Class::staticMethod(args)
```

### Array Access

```onion
array[index]
```

## Import System

```onion
import {
  package.ClassName;
  package.OtherClass;
}
```

## Module System

```onion
module package.name

// Class definitions
```

## Visibility Modifiers

- **Private (default)**: Members are private unless marked public
- **Public**: Declared in `public:` section

```onion
class Example {
  var privateMember: Int

  public:
    var publicMember: Int

    def publicMethod {
      // ...
    }
}
```

## Delegation

```onion
class MyClass <: Interface {
  forward val member: Interface;

  public:
    def this {
      this.member = new Implementation;
    }
}
```

The `forward` directive automatically delegates interface methods to the specified member.

## Type Conversions

### Widening Conversions (Automatic)

- `Byte` → `Short` → `Int` → `Long` → `Float` → `Double`
- `Char` → `Int`

### Narrowing Conversions (Explicit)

Require explicit cast using `$` operator:

```onion
val d: Double = 3.14
val i: Int = d$Int
```

## Current Limitations

As documented in the README:

1. **Edge cases**: The compiler may still crash on certain patterns
2. **Erasure generics**: No variance, wildcards, or reified type info
3. **Diagnostics**: Some errors are reported later in the pipeline

## Grammar Reference

The complete grammar is defined in `grammar/JJOnionParser.jj` using JavaCC syntax.

## Next Steps

- [Standard Library](stdlib.md) - Built-in functions and classes
- [Compiler Architecture](compiler-architecture.md) - How the compiler works
- [Examples](../examples/overview.md) - Code examples
