### Breaking

- **`override` must match the base method's types up to `?`; otherwise it is E0068.**
  - Before: `override def dec(k: JInteger): void` over `dec(k: Int): void`, or `override def f(k: Int?): Int?` over `f(k: Int): Int`, compiled as a silent overload that calls through the base type never reached.
  - Now: E0068, and the error names the base signature. Use the base types to override, or remove `override` to keep the overload. Through a generic supertype (`G[JInteger]`) a boxed parameter still overrides. See `docs/migration/pre-1.0.md`.

- **A method that adds `?` to a base method's parameter overrides it, with or without `override`.**
  - Before: unmarked `def dec(k: Int?): void` beside a base `dec(k: Int): void` was an overload; with a `String` return it overrode.
  - Now: it overrides for every return type, and without `override` it warns W0029. Over a `final` base method it is E0039. Rename it to keep a separate overload.
  - The other direction is unchanged: an unmarked `f(k: Int): void` beside a base `f(k: Int?): void` stays an overload, so a `null` through the base still reaches the base. With `override` it is E0068.

### Fixed

- **An override whose parameter differs from the base one only by `?` now runs.**
  - Before: `override def dec(k: Int?): void` over `dec(k: Int): void` compiled, but calls through `Base` ran the base method. With a `String` return the same override did run.
  - Now: it overrides for any return type both share, also in a chain (`f(Int)`, then `f(Int?)`, then `f(Int)` again). It also fills an abstract `f(k: Int)` beside another type's `f(k: Int?)`, abstract or not, so that no longer throws `AbstractMethodError`. A base that declares both `f(k: Int)` and `f(k: Int?)` keeps its `f(k: Int)` when a subclass overrides only `f(k: Int?)`.
  - `super.f(x)` in a subclass of such an override runs that override. It used to run the subclass's own `f` again until the stack overflowed.
  - Still open (#3053): with a `?` base parameter and a plain override returning a reference (`f(k: Int?): String` in the base, `f(k: Int): String` in the subclass), passing `null` through the base type throws, as before.
