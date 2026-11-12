### Fixed

- **`new ProcessBuilder("echo", "x")` compiles (#2839).**
  - Before: E0021 for any varargs constructor, Java or Onion (`def this(xs: String...)`), though the error listed it as available.
  - Now: trailing arguments are spread into the array, as for a varargs method. An Onion varargs constructor is also marked varargs for Java callers.
