### Fixed

- **A cyclic class or interface hierarchy that code uses reports E0016, not a crash (#2975).**
  - Before: `interface I conforms J` with `interface J conforms I`, plus a class or call that used them, crashed `onionc` with `StackOverflowError`. So did `class A extends B` / `class B extends A` with `new A().f()`.
  - Now: only `[E0016] inheritance relations which include I have cyclicity.`, and the rest of the file is still checked.
