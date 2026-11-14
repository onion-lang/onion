### Fixed

- **`val o: Object = Collections::max(xs, c)` compiles (#2857).**
  - Before: E0000 "type String is expected, but type Object is used", although `xs` and `c` already fix `T = String`.
  - Now: the arguments decide `T`, and the result is assigned to the wider target, as in Java.
