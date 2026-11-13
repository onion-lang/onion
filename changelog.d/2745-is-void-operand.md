### Fixed

- **`u() is Object` on a `void` call is a compile error (#2745).**
  - Before: it compiled, then the JVM rejected the class with `VerifyError: Operand stack underflow`.
  - Now: E0027 at the `is`, as for other `void` values with no value to use.
