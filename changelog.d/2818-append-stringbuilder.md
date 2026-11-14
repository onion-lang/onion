### Fixed

- **`a.append(b)` compiles when both are `StringBuilder`s.**
  - Before: E0013, "append(StringBuilder) is not accessible", a method you cannot see.
  - Now: `append(CharSequence)` is chosen, as in Java.
