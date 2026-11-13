### Breaking

- **A `protected` constructor picked by a default or named arguments is E0013 outside its subclasses.**
  - Before: `new A(9)` from an unrelated class compiled and ran, while `new A(9, 2)` was already E0013.
  - Now: both are E0013. Move the constructor under `public:`; see `docs/migration/pre-1.0.md`.

### Fixed

- **`new A(7)` on a private constructor is E0013 when a default, a named argument or a lambda argument picks it.**
  - Before: it compiled, then threw `IllegalAccessError` at run time.
  - Now: E0013 at compile time, as a plain `new A(7, 2)` already gave. Calls inside the class still work.
