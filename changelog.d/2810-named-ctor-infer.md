### Fixed

- **`new Box(b = 2, a = 1)` infers `Box[Int]`, as `new Box(1, 2)` does (#2810).**
  - Before: E0066 "raw type Box is not allowed" when the arguments were named.
  - Now: type arguments are inferred from named arguments too.
