### Fixed

- **`super.greet()` reaches a trait's or interface's default method (#2675).**
  - Before: in `class Loud conforms Greeter`, it was E0005 "not found", while listing `greet()` as available.
  - Now: it calls `Greeter`'s default, like Java's `Greeter.super.greet()`. A superclass method still wins. Two different defaults are E0006.
- **`super.g()` through an abstract superclass runs (#2702).**
  - Before: when `Base conforms I` inherited `I`'s default `g()`, the call compiled, then threw `IncompatibleClassChangeError`. A Java default such as `super.stream()` from an `AbstractList` subclass failed the same way.
  - Now: it calls the default, as in Java.
