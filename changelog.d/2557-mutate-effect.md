### Changed

- **`onionc --effects` now says when a method writes to memory: a new `mutate` effect (#2557).**
  - Before: a setter, or a method that only calls `list.add(x)`, was `pure`.
  - Now: it is `mutate`. A constructor that only fills in its own `this` stays `pure`.
  - `mutate` is not a capability. A `tool` does not declare it and is never asked to, and `requires { mutate }` is E0079.
  - Library classes with changeable state (`List`, `Map`, `Set`, `StringBuilder`, ...) default to `mutate`; only methods known to leave the receiver alone are `pure`.
