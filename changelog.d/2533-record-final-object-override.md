### Fixed

- **A record method that overrides a final `Object` method is E0039, as in a class.**
  - Before: `record R(x: Int) { public: def notify(): void { } }` compiled, then failed to load with `IncompatibleClassChangeError`. Same for `wait`, `notifyAll` and `getClass(): Class`.
  - Now: E0039 at the method. A same-named method with other parameters or another return type (`def notify(): Int`) still runs.
