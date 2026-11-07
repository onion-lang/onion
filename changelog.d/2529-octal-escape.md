### Fixed

- **`"\101"` is `"A"`, as in Java (#2529).**
  - Before: the escape compiled, but the digits stayed in the string (`"a\001b".length()` was 5).
  - Now: `\0`..`\377` decode in strings and character literals.
