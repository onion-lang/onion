### Fixed

- **`class C extends A(1)` with a private `A` constructor is a compile error (#2662).**
  - Before: it compiled, then crashed with `IllegalAccessError`.
  - Now: `E0013`, as for `new A(1)`, with the hint to put the constructor under `protected:` or `public:`.
