### Fixed

- **Reading a superclass's private static field by its bare name is a compile error (#3155).**
  - Before: `def run(): Int = K` compiled, then threw `IllegalAccessError` when run. `A::K` was already an error.
  - Now: E0014, with the same hint as `A::K`.
