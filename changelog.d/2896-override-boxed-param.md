### Breaking

- **`override` must match the base method's types up to `?`; otherwise it is E0068.**
  - Before: `override def dec(k: JInteger): void` over `dec(k: Int): void`, or `override def f(k: Int?): Int?` over `f(k: Int): Int`, compiled as a silent overload that calls through the base type never reached.
  - Now: E0068, and the error names the base signature. Use the base types to override, or remove `override` to keep the overload. Through a generic supertype (`G[JInteger]`) a boxed parameter still overrides. See `docs/migration/pre-1.0.md`.

- **A method whose parameters differ from a base method's only by `?` overrides it, with or without `override`.**
  - Before: unmarked `def dec(k: Int?): void` beside a base `dec(k: Int): void` was an overload; with a `String` return it overrode.
  - Now: it overrides for every return type; over a `final` base method it is E0039. Rename it to keep a separate overload.

### Fixed

- **An override whose parameter differs from the base one only by `?` now runs.**
  - Before: `override def dec(k: Int?): void` over `dec(k: Int): void` compiled, but calls through `Base` ran the base method. With a `String` return the same override did run.
  - Now: it overrides for any return type both share, also in a chain (`f(Int)`, then `f(Int?)`, then `f(Int)` again). It also fills an abstract `f(k: Int)` beside another type's `f(k: Int?)`, abstract or not, so that no longer throws `AbstractMethodError`. A base that declares both `f(k: Int)` and `f(k: Int?)` keeps its `f(k: Int)` when a subclass overrides only `f(k: Int?)`.
  - Still open (#3053): with a `?` base parameter and a plain override (`f(k: Int?)` in the base, `f(k: Int)` in the subclass), passing `null` through the base type throws, for every return type.
