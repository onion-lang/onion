### Fixed

- **A `static` or private `equals`/`hashCode`/`toString` in a record is now a compile error (E0010).**
  - Before: it compiled and dropped the generated method, so `==` threw `IncompatibleClassChangeError` or `IllegalAccessError`, and printing gave `R@80`.
  - Now: E0010 names the clash and says to remove `static` (or move the method under `public:`), or rename it. A public or protected instance method still replaces the generated one.
