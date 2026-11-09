### Fixed

- **`class C extends A` with no constructor is a compile error when `A` has no no-arg constructor (#2664).**
  - Before: it compiled, then `new C()` threw `NoSuchMethodError`. This happened when every parameter of `A`'s only constructor had a default.
  - Now: E0021 at the class (E0013 if `A()` is private), as an explicit `def this { }` already reports. To pass arguments, write `extends A(...)`.
