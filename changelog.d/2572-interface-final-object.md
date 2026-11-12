### Fixed

- **An interface method named like a final `Object` method is E0039 (#2572).**
  - Before: `interface N { def notify(): void }` compiled, then failed at load with `IncompatibleClassChangeError`.
  - Now: E0039 at the method, as a class and a record get.
