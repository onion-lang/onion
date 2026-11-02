### Fixed

- **`xs[i] = u` into a `List[T]` is checked like `xs.set(i, u)` (#2332).**
  - Before: an unrelated `U`, an `Object` or a `String` compiled into a `List[T]` or a `Map[K, T]` through `[]`, then threw ClassCastException where the `T` was read.
  - Now: E0000, the same error `xs.set(i, u)` and `m.put(k, u)` already gave. A map key is checked too.
