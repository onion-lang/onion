### Fixed

- **A `@TailRecursive` mutual group written as `= if ... else ...` no longer overflows the stack (#3013).**
  - Before: when one member also called itself, the group was rewritten as a lone loop and `a(1000000)` died with a stack overflow, with no W0016.
  - Now: the group is optimized together, as it is with `return` statements.
