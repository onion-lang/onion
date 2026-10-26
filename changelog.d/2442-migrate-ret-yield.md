### Added

- **`onion migrate <path>...` rewrites code for pre-1.0 breaking changes, in place (#2442).**
  - First rule: a `do` block's final `ret a + b` becomes `yield a + b`. A variable or method named `ret`, strings and comments are left alone.
  - `--check` changes nothing and exits 1 if a file would change. A place it cannot decide, such as a `ret` that is not the last clause, is printed, not guessed.
