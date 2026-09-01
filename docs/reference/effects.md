# Effects

Onion tracks what a call can do to the world outside the program. Every method has an
*effect set*, computed without running anything, and `--effects` prints it:

```bash
$ onion --effects report.on
Report#readCfg(java.lang.String): read
Report#main(java.lang.String[]): console,read
```

This page is the reference for the vocabulary, where the facts come from, and exactly
what the analysis does and does not promise. The capability layer built on top of it
(`tool` declarations, `requires`, `--plan`) is described in the guide once it lands;
this data layer shipped first.

## The vocabulary

An effect names one way of touching the outside world. A call has a *set* of them; the
empty set prints as `pure`.

`read` is a filesystem read and `write` a filesystem write — an operation that does
both, such as `Files::copy`, carries both. `net` is network traffic in either
direction. `exec` covers starting, replacing or terminating a process, which is why
`System::exit` carries it as well as `Proc::run`. `env` is a read of the process
environment: environment variables, system properties, the working directory, JVM
statistics. `clock` covers both reading the clock and blocking on it, so `Timing::sleep`
and `DateTime::now` share it. `rand` is a draw from a random source, and `console` is
any use of the attached stdin, stdout or stderr.

The ninth member is different in kind: `unknown` means *the table cannot vouch for this
call*. An unlisted Java method is not known to be pure, and pretending otherwise would
make every guarantee built on this vocabulary a lie. `unknown` is therefore an effect
that propagates like any other, and the capability layer treats it as "needs an
explicit allow", never as "harmless".

## Where the facts come from

Effects cannot ride on annotations in this compiler — Java methods reach the type
checker without theirs, nothing is emitted to bytecode, and the optimizer rebuilds
methods without them — so the facts live out of band, in a resource shipped with the
compiler (`onion/effect-table.txt`). Each line classifies one method, or a whole class
via a wildcard, and a specific entry beats its class's wildcard, which is how a
mostly-pure class declares its exceptions:

```
onion.DateTime#*=pure
onion.DateTime#now=clock
onion.Files#copy=read,write
onion.Cli#parse=console,exec
```

The table ships with the whole effectful surface of the standard library classified —
`Files`, `FileResource`, `Proc`, `Http`, `HttpResource`, `Config`, `IO`, `Cli`,
`DateTime`, `Timing`, `Rand`, `OnionMath`, `Archive`, `Db`, `Net`, `Server`, `Future`,
`Concurrent`, `ToolCli` — plus the pure remainder of the stdlib, a
pure baseline for common JDK value and collection types, and the JDK's well-known
effect points (`System::getenv`, `System::exit`, `Runtime::exec`, `Thread::sleep`,
`PrintStream` as the type of `System::out`).

Granularity is per-type, not per-instance. A `PrintStream` over a file would be a
`write`, but in Onion code a `PrintStream` is `System::out` or `System::err`, so the
type is classified `console`. When that distinction matters, the stdlib's own entry
points (`Files`, `IO`) are the precise path.

## How a method's set is computed

For a method defined in your program, the compiler walks the typed body and takes the
union over every call in it: the table's verdict for external calls, and the callee's
own inferred set for calls to other methods in the program, joined transitively.
Recursion and mutual recursion converge by fixed point. Constructors participate the
same way — `new Loud()` carries whatever `Loud`'s constructor does, including its
superclass chain.

Two rules are deliberate over-approximations, chosen to be sound for boundary checking
rather than maximally precise. A closure's body is charged to the method that *creates*
the closure, not the one that eventually invokes it — invoking a function value is
`pure` in the table — because the creation site is the one the checker can always see.
And any call that neither the table nor the compiled sources can account for
contributes `unknown` rather than being dropped.

The result is an over-approximation in the other direction too: the analysis reports
what a body *can* do, not what it will do on a given run. A branch that never executes
still contributes its effects. That is the correct direction for the question the
vocabulary exists to answer — "what do I authorize when I run this?" — where missing an
effect would be a hole and listing an extra one is merely conservative.

## What this does not promise

Reflection, `ClassLoader` tricks and native code are outside the model; they surface as
`unknown` at the call that reaches them, not as clairvoyance about what they do. The
table classifies the stdlib as shipped — a user-supplied class named `onion.Files` is
not consulted against its source. And an effect set is not a security sandbox: nothing
stops a program from running; the set is the honest input to the layers that decide
whether it *should*.
