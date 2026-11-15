### Fixed

- **`@TailRecursive` on a lone self-recursive method no longer disables its loop (#2802).**
  - Before: `@TailRecursive def loop(n: Int): Int = ...` overflowed the stack at depth 1000000, with no warning.
  - Now: it becomes a loop, like the same method without the annotation. Mutual-recursion groups are unchanged.
