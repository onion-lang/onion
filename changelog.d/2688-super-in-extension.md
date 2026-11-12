### Fixed

- **`super` in an `extension` method gets an error that names the real reason (#2688).**
  - Before: E0050 said "current instance is not available in a static context", though `this` works there.
  - Now: still E0050, but it says `super` is not available in an `extension` method and the receiver is `this`.
