### Breaking

- **A `protected` method called with named arguments or an untyped lambda is E0013 outside its subclasses.**
  - Before: `new A().pim(b = 3, a = 7)` from an unrelated class compiled and ran, while `new A().pim(7, 3)` was already E0013.
  - Now: both are E0013. Move the method under `public:`; see `docs/migration/pre-1.0.md`.

### Fixed

- **A private method called with named arguments or an untyped lambda is E0013.**
  - Before: `new A().pim(b = 3, a = 7)`, `new A().lam((x) -> x + 1)`, `a?.pim(b = 3)` and an inherited `pim(b = 3)` in a subclass compiled, then threw `IllegalAccessError` at run time.
  - Now: E0013 at compile time, as `new A().pim(7, 3)` already gave. Calls inside the class still work.
