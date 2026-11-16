### Fixed

- **A bound inside a type-parameter bound is checked the same way in any declaration order.**
  - Before: `class Bad1[T extends Num[String]]` compiled when `class Num[T extends Number]` came later; `class A[T extends Box[MyNum]]` was E0000 though `MyNum extends Number`.
  - Now: the first is E0000 in every order (and across files); the second compiles.
