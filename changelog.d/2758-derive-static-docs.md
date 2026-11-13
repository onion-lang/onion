### Fixed

- **The `derive!` docs say the derived methods are static: call `R::toJson(v)`, not `v.toJson()`.**
  - A component named `toJson` stays legal: `p.toJson()` is the component, `P::toJson(p)` the serializer.
