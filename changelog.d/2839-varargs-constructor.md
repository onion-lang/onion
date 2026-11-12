### Fixed

- **`new ProcessBuilder("echo", "x")` compiles (#2839).**
  - Before: E0021 for `new` with a varargs constructor, Java or Onion (`def this(xs: String...)`), though the error listed it as available.
  - Now: `new` spreads trailing arguments into the array, as a varargs method call does. A constructor with default parameters still wins when it fits, as for methods. An Onion varargs constructor is also marked varargs for Java callers.
