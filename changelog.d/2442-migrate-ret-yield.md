### Added

- **`onion migrate <path>...` rewrites code for pre-1.0 breaking changes, in place (#2442).**
  - First rule: a `do` block's final `ret a + b` becomes `yield a + b`. A variable or method named `ret`, strings and comments are left alone.
  - `--check` changes nothing and exits 1 if a file would change. A place it cannot decide, such as a final `ret(x)` that may call a method named `ret`, is printed and its file left alone; the summary counts it and the exit code is 1.
  - A script named `migrate` with no extension now runs as `onion ./migrate`, as with `fmt`, `run` and `test`.
