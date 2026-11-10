### Added

- **`onion migrate` rewrites `law` and `example` into contract predicates (#2740).**
  - Before: a script from 0.150.0 or older that used them stopped compiling, and `onion migrate` left it alone.
  - Now: `law n(p: T) { e }` becomes `static def n(p: T): Boolean ensures result = e`, and a top-level `example` becomes a `def`. Run `onion migrate src/`.
