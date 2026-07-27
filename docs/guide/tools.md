# Tools and Capabilities

A `tool` is a function with a boundary: it says up front what it is allowed to do to
the world outside the program, and the compiler holds it to that.

```onion
tool ingest(src: String, dst: String): Int
  requires { read(src), write(dst) }
{
  val data = Files::readText(src)
  Files::writeText(dst, data)
  return 0
}
```

The `requires` clause lists *capabilities*: effects the body may perform, each
optionally tied to the parameter it acts through. If the body — or anything it calls,
however indirectly — performs an effect the clause does not declare, the program does
not compile.

## The deal: inference everywhere, declaration at the boundary

Ordinary functions carry no effect annotations, ever. The compiler infers what each one
can do by looking at its body and joining across calls (the [effects
reference](../reference/effects.md) describes the vocabulary and the analysis). You
declare effects in exactly one place — a `tool` boundary — and the check compares the
declaration against the inference:

```onion
def helper(msg: String): void {
  IO::println(msg)          // inferred: console — no annotation needed
}

tool speak(msg: String): Int
  requires { console }      // declared once, at the boundary
{
  helper(msg)               // covered: helper's inferred console is inside the clause
  return 0
}
```

There is no effect polymorphism, no effect variables in ordinary signatures, no
handlers. That restraint is deliberate: the point is a checkable promise at the edge of
a program, not a whole-language effect system.

## What a violation looks like

A tool that declares only `read` and then writes fails to compile, and the diagnostic
names the effect, the callee, and the exact call that introduced it:

```
ingest.on:5:8: [E0077] tool `sneaky` performs `write` here (calling
onion.Files::writeText) but does not declare it. Add `write` to its
`requires { ... }` clause.
  5 |   Files::writeText(dst, data)
    |        ~~
```

The check is honest in the other direction too. A capability the body cannot exercise
is an error, not decoration — a tool that claims `net` and never touches the network is
overclaiming, and gets `E0078`. And a capability that is not in the vocabulary, or that
names a parameter the tool does not have, is `E0079`.

## `unknown` must be admitted, not assumed away

A Java method the effect table cannot vouch for has *unknown* effects — no table will
ever cover the JDK plus every jar on a classpath. Treating unknown as forbidden would
kill interop; treating it as harmless would make the guarantee a lie. So `unknown`
propagates like any other effect and must be admitted explicitly at the boundary:

```onion
import { java.util.Random; }

tool roll(): Int
  requires { unknown }      // says out loud: this body calls code I cannot vouch for
{
  return new Random().nextInt()
}
```

Without the clause, the call site gets `E0077` naming `unknown`. With it, the tool's
contract is truthful: "this does something the analysis cannot bound."

## What a tool costs at runtime: nothing

Effects are checked during type checking and erased there. A `tool` compiles to
exactly the bytecode of the equivalent function — the compiler's test suite pins this
by disassembling both and comparing instruction for instruction. The boundary is a
compile-time promise, not a runtime sandbox: it costs nothing to call a tool, and it
stops nothing at runtime. What it guarantees is narrower and more useful — *a tool that
compiles cannot quietly do more than its declaration says*.

## Two details worth knowing

A closure's effects are charged to the function that *creates* it, not the one that
eventually invokes it. If a tool builds a lambda that prints, the tool needs `console`
even if the lambda is only invoked elsewhere — the creation site is the one the checker
can always see.

And `tool` / `requires` are soft keywords: only `tool name(` opens a declaration and
only `requires {` opens the clause, so both remain usable as ordinary identifiers in
existing code.

## From declaration to command line

A script whose top level declares tools — and has neither its own `main` nor top-level
statements — *is* a command-line program. The compiler derives everything from the
declarations:

```bash
$ onion ingest.on --contract
[{"tool":"ingest",
  "params":[{"name":"src","type":"String","role":"positional"},
            {"name":"dst","type":"String","role":"positional"},
            {"name":"count","type":"Int","role":"flag","default":"3"},
            {"name":"loud","type":"Boolean","role":"switch","default":"false"}],
  "returns":"Int",
  "capabilities":["read(src)","write(dst)","console"]}]

$ onion ingest.on --help
usage: ingest.on <src> <dst> [--count <Int>] [--loud]
  <src>                   String
  <dst>                   String
  --count <int>           Int (default: 3)
  --loud                  Boolean (default: false)
  requires: read(src), write(dst), console
```

The contract is the single source. `--contract` prints it verbatim for an agent to
read; `--help`, flag parsing, typed conversion and error messages are all derived from
it at runtime, and the typed call into your tool is derived from the same declaration
at compile time. Required parameters are positionals; defaulted parameters become
`--name` flags (`--count 5` or `--count=5`); `Boolean` defaults become switches. A
default that is absent on the command line is evaluated as the original expression, in
the language — it is never round-tripped through a string.

A bare `--` ends the options: everything after it is a value, however it is spelled, so
a tool can be handed an argument that starts with `--`. The three mode flags
(`--help`, `--contract`, `--plan`) are mutually exclusive and passing two is an error
rather than a silent choice between them. Tool names must be distinct (`E0082`), since
the name is the only selector a command line has.

Failures are exit codes, not `System.exit`: a missing positional, a value that does not
parse as its declared type, or an unknown option each print a message naming the
argument and the expected type, then return `1` from `main`. When a script declares
several tools, the first argument selects one by name, subcommand-style, and the
contract carries one entry per tool.

Every parameter of every `tool` must be one of the CLI-convertible types — `String`,
`Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte` — since there is no general
way to parse an arbitrary type from a command-line string. A `tool` with a parameter of
any other type (a record, for instance) fails to compile, naming the offending tool and
parameter, rather than silently compiling to a script that never calls it.

The capability line in `--help` and the `capabilities` field in the contract are not
documentation — they are the checked declaration from the section above. What the
contract says the tool may do is what the compiler proved it cannot exceed.

## `--plan`: what a run would do, without doing it

The payoff of checked capabilities. Because the compiler knows what the body can do,
the CLI can report what a *particular invocation* would do — the declared effect set
instantiated with the actual argument values — and exit without performing any of it:

```bash
$ onion ingest.on access.log /backup/access.log --plan
plan: `ingest` would
  read    src = access.log
  write   dst = /backup/access.log
  console
(nothing was executed)
```

A capability binds an effect to a *parameter*, so `write derived from dst = …` says the
write goes through `dst` — not that the run touches exactly that path, since a body is
free to build `dst + ".1"` out of it. Arguments are parsed exactly as a real run would
parse them — a bad value fails the
plan the same way it would fail the run — and an operand left to its default shows the
default from the contract. The honesty rules are strict in both directions. An ambient
effect (`console`, `clock`, `env`, `rand`) is named bare. An operand the analysis
cannot tie to a parameter is reported as `(operand not statically known)`, never
guessed. And a body carrying `unknown` says so out loud:

```
  unknown  — calls code the analysis cannot characterize; this plan is a lower bound
```

A plan that quietly omitted what it could not characterize would be worse than no
plan. This is what makes the dry run *trustworthy* rather than decorative: a CLI
derived from a signature is commodity; a plan derived from the checked effect set
requires knowing what the body does.

