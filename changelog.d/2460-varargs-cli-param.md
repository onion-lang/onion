### Fixed

- **A `String...` parameter on a top-level `main` or a `tool` is a compile error (#2460).**
  - Before: it compiled, but was read as one `String`: `files.length` was always 1, a second argument was rejected, and `--contract` said `"type":"String"`.
  - Now: E0094 for `main` (write `files: String[]` to collect the rest), E0081 for a `tool`. Varargs elsewhere, such as a class's `static def main(args: String...)`, are unchanged.
