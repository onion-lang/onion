# Tail Call Optimization

## Current Status

**Detection: ✅ Implemented**
**Transformation: ✅ Implemented**

The Onion compiler detects tail-recursive methods during compilation and rewrites
them into a `while(true)` loop, so a self-recursive call in tail position no longer
grows the JVM call stack (preventing `StackOverflowError` for deep recursion, e.g.
10,000+ calls).

Only **direct self-recursion** is rewritten by this phase; mutual recursion between
two or more methods is handled separately by `MutualRecursionOptimization`, which
runs immediately afterward in the pipeline.

## How It Works

### Detection Phase

The compiler analyzes each method to identify tail-recursive calls:

1. **Tail Position Analysis**: Checks if the last statement (or statements in
   control-flow branches) is a self-call
2. **Recursive Search**: Searches through `StatementBlock` and `IfStatement` nodes
   to find tail calls
3. **Method Matching**: Verifies that the call target matches the current method
   (same name, class, and parameter types)
4. **Dispatch Safety**: Only methods that cannot be overridden — private, static, or
   final — are eligible. A self-call in a public overridable method could be
   dynamically dispatched to a subclass override at runtime, so rewriting its body
   into a loop would silently change behavior; such methods are skipped.
5. **Mutual-Recursion Exclusion**: A method annotated `@TailRecursive` (used to mark
   participants in mutual recursion) is skipped here and left for
   `MutualRecursionOptimization`.

### Supported Patterns

The detector recognizes tail recursion in:

- Direct tail calls: `return method(args)`
- Conditional branches: both `then` and `else` branches of `if` statements
- Nested blocks: recursive search through statement blocks

### Example

```onion
def factorial(n: Int, acc: Int): Int {
  if (n <= 1) {
    return acc
  }
  return factorial(n - 1, n * acc)  // ✅ detected and transformed
}
```

### Transformation Phase

Once a method is confirmed tail-recursive and eligible, it is rewritten:

1. **Loop Variable Allocation**: Each parameter gets a corresponding loop variable
2. **Parameter Rewriting**: Every reference to a parameter within the body is
   rewritten to reference its loop variable instead
3. **Loop Construction**: The (rewritten) body is wrapped in a `while(true)` loop
4. **Tail Call Replacement**: Each tail call is replaced with loop-variable updates
   followed by the loop continuing (no `continue` keyword needed — falling through
   to the end of the `while(true)` body re-enters it)

```onion
// Before
def factorial(n: Int, acc: Int): Int {
  if (n <= 1) return acc
  return factorial(n - 1, n * acc)
}

// After (conceptually)
def factorial(n: Int, acc: Int): Int {
  while (true) {
    if (n <= 1) return acc
    val n_next = n - 1
    val acc_next = n * acc
    n = n_next
    acc = acc_next
    // loop restarts
  }
}
```

## Mutual Recursion with `@TailRecursive`

Two or more methods that tail-call each other (`isEven` calling `isOdd`, `isOdd`
calling `isEven`) are not rewritten by the detection phase above — each call
targets a *different* method, so the "self-call" check never matches. Mark every
method in the group `@TailRecursive` and `MutualRecursionOptimization` merges them
into one state-machine method instead:

```onion
class Parity {
private:
  @TailRecursive
  def isEven(n: Int): Boolean {
    if n == 0 { return true }
    return isOdd(n - 1)
  }

  @TailRecursive
  def isOdd(n: Int): Boolean {
    if n == 0 { return false }
    return isEven(n - 1)
  }
public:
  def check(n: Int): Boolean = isEven(n)
}
```

The group is optimized only when **all** of the following hold — `MutualRecursionOptimization.validateGroup`:

- every method in the group is **private** (same dispatch-safety reasoning as
  direct TCO: a public method could be overridden, so rewriting it into a shared
  loop could silently change behavior)
- every method has the **same return type**
- every method has the **same parameter count and parameter types**
- every tail call from a group member targets another member of the same group
  (a tail call out of the group leaves it ineligible)

**A group that fails validation is not an error — the annotation is simply
ineffective**, and the methods keep calling each other as ordinary (non-tail)
calls. That is easy to miss, since nothing points at the annotation until deep
enough recursion overflows the JVM stack at runtime. To catch this at compile
time instead, the compiler emits **`W0016`** for every method in a `@TailRecursive`
group that could not be optimized, naming the failed requirement (e.g. "All
methods must be private for mutual recursion optimization"). See
[`--Wno`](../tools/compiler.md#--wno-codes) to suppress it if the group is
intentionally left unoptimized.

## Viewing the Optimization

Compile with the `--verbose` flag to trace which methods are transformed and why
others are skipped:

```bash
sbt 'runScript --verbose your_program.on'
```

Sample output:
```
[TCO] Method YourClass.factorial: hasTailCall=true
[TCO] Optimizing tail-recursive method: YourClass.factorial
[TCO] Skipping overridable method: YourClass.someOverridableMethod
[TCO] Skipping @TailRecursive annotated method: YourClass.mutuallyRecursiveMethod
```

## Implementation Details

### File Location

- Source: `src/main/scala/onion/compiler/optimization/TailCallOptimization.scala`
- Pipeline Integration: runs between `Typing` and `AsmCodeGeneration`, immediately
  before `MutualRecursionOptimization`

### Compiler Pipeline

```
Parsing → Rewriting → Typing → [TailCallOptimization] → MutualRecursionOptimization → AsmCodeGeneration
```

## Testing

- Spec: `src/test/scala/onion/compiler/tools/TailCallOptimizationSpec.scala`
- Example programs in `src/test/run/`: `tail_recursion_factorial.on`,
  `tail_recursion_simple.on`, `tail_recursion_direct.on`,
  `tail_recursion_private.on`, `tail_recursion_public.on`, `tail_recursion_test.on`

## References

- [Tail Call Optimization (Wikipedia)](https://en.wikipedia.org/wiki/Tail_call)
- Related implementation: Scala's `@tailrec` annotation
- Similar optimization in functional languages: Haskell, Scheme, OCaml
