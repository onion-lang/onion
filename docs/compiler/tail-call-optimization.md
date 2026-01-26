# Tail Call Optimization

## Current Status (2026-01-26)

**Detection: ✅ Implemented**
**Transformation: 🚧 Planned**

The Onion compiler now includes a tail call detection system that identifies tail-recursive methods during compilation.

## How It Works

### Detection Phase

The compiler analyzes methods to identify tail-recursive calls:

1. **Tail Position Analysis**: Checks if the last statement (or statements in control flow branches) is a self-call
2. **Recursive Search**: Searches through `StatementBlock` and `IfStatement` nodes to find tail calls
3. **Method Matching**: Verifies that the call target matches the current method (same name, class, and parameter types)

### Supported Patterns

The detector recognizes tail recursion in:

- Direct tail calls: `return method(args)`
- Conditional branches: Both `then` and `else` branches of if statements
- Nested blocks: Recursive search through statement blocks

### Example

```onion
def factorial(n: Int, acc: Int): Int {
  if (n <= 1) {
    return acc
  }
  return factorial(n - 1, n * acc)  // ✅ Detected as tail call
}
```

## Viewing Detected Tail Calls

Compile with the `--verbose` flag to see which methods are tail-recursive:

```bash
sbt 'runScript --verbose your_program.on'
```

Output:
```
[TCO] Detected tail-recursive method: YourClass.factorial
[TCO] Note: Tail call optimization is not yet fully implemented
```

## Implementation Details

### File Location

- Source: `src/main/scala/onion/compiler/optimization/TailCallOptimization.scala`
- Pipeline Integration: Added between `Typing` and `CodeGeneration` phases

### Compiler Pipeline

```
Parsing → Rewriting → Typing → [TailCallOptimization] → CodeGeneration
```

## Future Work

### Planned Transformations

The transformation phase (converting tail recursion to loops) requires:

1. **Local Variable Allocation**: Properly allocating temporary variables using `LocalFrame`
2. **Parameter Rewriting**: Converting parameter references to temporary variable references throughout the method body
3. **Loop Construction**: Wrapping the method body in a `while(true)` loop
4. **Tail Call Replacement**: Replacing tail calls with variable assignments + continue

### Example Transformation (Planned)

```onion
// Original
def factorial(n: Int, acc: Int): Int {
  if (n <= 1) {
    return acc
  }
  return factorial(n - 1, n * acc)
}
```

Would be transformed to (conceptually):

```onion
def factorial(n: Int, acc: Int): Int {
  var n_temp: Int = n
  var acc_temp: Int = acc
  while (true) {
    if (n_temp <= 1) {
      return acc_temp
    }
    val n_next = n_temp - 1
    val acc_next = n_temp * acc_temp
    n_temp = n_next
    acc_temp = acc_next
    // loop continues
  }
}
```

## Testing

Test files are located in `src/test/run/`:
- `tail_recursion_factorial.on` - Factorial with tail recursion
- `tail_recursion_simple.on` - Simple countdown example
- `tail_recursion_direct.on` - Direct infinite recursion (for testing)

## Contributing

If you'd like to contribute to implementing the transformation phase:

1. Understand `TypedAST` node structure (especially `LocalFrame`, `RefLocal`, `SetLocal`)
2. Study `LocalVarContext` in `src/main/scala/onion/compiler/bytecode/LocalVarContext.scala`
3. Implement recursive statement rewriting to replace parameter references
4. Add comprehensive tests for various tail-recursive patterns

## References

- [Tail Call Optimization (Wikipedia)](https://en.wikipedia.org/wiki/Tail_call)
- Related implementation: Scala's `@tailrec` annotation
- Similar optimization in functional languages: Haskell, Scheme, OCaml
