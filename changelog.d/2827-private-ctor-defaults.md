### Fixed

- **`new A(7)` on a private constructor is E0013 when a default, a named argument or a lambda argument picks it.**
  - Before: it compiled, then threw `IllegalAccessError` at run time.
  - Now: E0013 at compile time, as a plain `new A(7, 2)` already gave. Calls inside the class still work.
