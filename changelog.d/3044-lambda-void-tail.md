### Fixed

- **A block lambda typed `(Int) -> Int` that ends in a void call is now a compile error.**
  - Before: `(n) -> { println(n) }` compiled, returned null, and failed later as an NPE.
  - Now: E0000 at the last expression. A `void`, `Object`, nullable (`String?`), `Void`, Java-generic (`Supplier[T]`, `Map.computeIfAbsent`) or inferred result still allows it, as does a tail that never ends (`while true`).
