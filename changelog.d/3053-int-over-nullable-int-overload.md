### Breaking

- **`override def f(k: Int): String` over a base `f(k: Int?): String` is E0068 (#3053).**
  - Before: it compiled as an override, and `b.f(null)` through the base type threw inside the generated bridge.
  - Now: it is an overload for every return type, as it already was for `void` and primitives. With `override` it is **E0068**; make the parameter `Int?` to override, or drop `override` to keep the overload.
  - No `onion migrate` rule: only you know whether it was meant to replace the base method. See `docs/migration/pre-1.0.md`.
