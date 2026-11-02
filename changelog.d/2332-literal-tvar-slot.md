### Breaking

- **A literal `[u]` or `["k": u]` is no longer a `List[T]` or `Map[K, T]` when `u` is not a `T` (#2332).**
  - Before: `xs = [u]`, `take([t, u])` or `m = ["k": u]` compiled, then threw `ClassCastException` where a `T` was read back.
  - Now: `E0000 type List[T] is expected, but type List[U] is used.` (`List[Object]` for a mix). Write `[(u as T)]` if the value really is a `T`.
  - No `onion migrate` rule: the value is not a `T`, so an inserted cast would only bring back the same exception. See `docs/migration/pre-1.0.md`.
