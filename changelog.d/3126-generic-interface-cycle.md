### Fixed

- **`interface I[T] conforms I[List[T]]` is E0016 when a class conforms to it (#3126).**
  - Before: the compiler died with `StackOverflowError`.
  - Now: `[E0016]` names `I`, as it does without the class.
