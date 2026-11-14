### Fixed

- **A generic class from a later source file can be used with type arguments (#2853).**
  - Before: `onionc app.on lib.on` was E0030 "does not take type arguments" on `Crate[Int]` when `lib.on` declares `class Crate[T]`; `onionc lib.on app.on` compiled.
  - Now: both orders compile, and bound and arity errors are reported the same way in either order.
