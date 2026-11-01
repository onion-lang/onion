### Fixed

- **A class's, record's or method's own `[T]` wins over a module `type T = ...` alias (#2510).**
  - Before: inside `class Box[T]`, `T` meant the alias, so `new Box[String]("x")` was E0021 against a `Box(Int)` nobody wrote.
  - Now: `T` is the type parameter wherever `[T]` is in scope, static members included; outside the declaration, the alias still applies.
- **An alias means the same thing wherever it is used (#2510).**
  - Before: `type Wrap = List[S]` used inside `class C[S]` read `S` as `C`'s parameter, and `type Pair[A] = Map[String, A]` used inside `class C[A]` or `def f[A]` leaked its `A` into `C`'s or `f`'s. Valid calls were rejected with E0005 or E0000.
  - Now: an alias's target is read where the alias is declared, with only its own type parameters, which are replaced by the arguments.
