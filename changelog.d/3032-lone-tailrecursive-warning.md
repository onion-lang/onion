### Fixed

- **A lone `@TailRecursive` method that cannot become a loop now warns W0016 (#3032).**
  - Before: a public, overridable `@TailRecursive def sum(..)` compiled silently, then overflowed the stack.
  - Now: W0016 says why (for example, make it private, static or final).
