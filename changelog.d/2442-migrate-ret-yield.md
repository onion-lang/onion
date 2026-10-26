### Added

- **`onion migrate <path>...` rewrites code for pre-1.0 breaking changes, in place (#2442).**
  - First rule: a `do` block's final `ret a + b` becomes `yield a + b`. Strings and comments are left alone.
  - The compiler decides: a file that compiles as written is never changed, and a rewrite is kept only if the file then compiles. Otherwise the place is printed, the file left alone, and the exit code is 1.
  - `--check` changes nothing and exits 1 if a file would change. `-classpath` names the classes a file uses.
  - A script named `migrate` with no extension now runs as `onion ./migrate`, as with `fmt`, `run` and `test`.
