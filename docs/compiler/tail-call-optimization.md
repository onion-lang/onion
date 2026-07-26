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
