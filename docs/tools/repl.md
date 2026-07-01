# REPL

The Onion REPL is implemented by `onion.tools.Repl` and is the primary interactive shell for the language.

## Starting the REPL

Use any of the following entry points:

```bash
# Development
sbt repl

# From a distribution or local bin/
onion repl
onion-repl
```

## Core Features

- Multi-line input with continuation prompts
- Session-scoped `module`, `import`, `val`, `var`, `def`, and class declarations
- Persistent `resN` bindings for evaluated expressions
- History and syntax highlighting through JLine 3
- Session compile caching for repeated inspections of the same source snapshot

## Commands

```text
:help                 Show help
:quit / :exit / :q    Exit the REPL
:clear                Clear the terminal
:history              Show input history
:imports              Show current module/imports
:type <expr>          Show the inferred type of an expression
:ast <expr>           Dump the parsed AST for an expression
:typed <expr>         Dump the typed AST summary for an expression
:bytecode <expr>      Dump generated JVM bytecode for an expression
:load <file>          Load a file into the current session
:time [on|off]        Toggle compile/eval timing
:classpath            Show the active classpath
:reset                Reset the session
:paste                Enter paste mode
```

## Examples

### Results and `resN`

```text
onion> 2 + 2
res0: Int = 4

onion> res0 * 10
res1: Int = 40
```

### Multi-line Definitions

```text
onion> def factorial(n: Int): Int {
     |   if n <= 1 {
     |     return 1;
     |   } else {
     |     return n * factorial(n - 1);
     |   }
     | }
```

### Inspecting Types and Bytecode

```text
onion> :type Math::max(1, 2)
Math::max(1, 2): Int

onion> :bytecode 1 + 2
== <generated class> ==
...
```

### Loading Files

```text
onion> :load run/Hello.on
Hello
```

## Notes

- `onion repl` and `onion-repl` start the same implementation.
- `:time` is useful when comparing REPL compile/eval latency while working on compiler performance.
- `:bytecode` renders class output with ASM tracing, so helper/closure classes may appear alongside the primary generated class.
