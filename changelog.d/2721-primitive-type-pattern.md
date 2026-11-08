### Fixed

- **`case n is Long:` works in `select`, like `o is Long` (#2721).**
  - Before: E0000 "type Object is expected, but type Long is used".
  - Now: it matches a boxed `Long` and binds `n` as a `Long`. Same for `Int`, `Double` and the other primitive names.
