### Fixed

- **`def main(s: Short = 3)` and `tool t(b: Byte = 2)` compile (#2506).**
  - Before: E0005 for `main(Int, Int, Int)`, a signature you never wrote.
  - Now: the Int-literal default is narrowed, as in an ordinary call.
