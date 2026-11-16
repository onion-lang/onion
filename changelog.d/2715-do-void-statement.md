### Fixed

- **A bare `void` statement in a `do[M]` block gets an error about the do block (#2715).**
  - Before: `E0040 cannot call method bind on primitive type void`, naming a call you never wrote.
  - Now: `E0040 a do block statement must be x <- expr or a value of the monad`, with a hint to put the side effect in `yield { println(..); x }`.
