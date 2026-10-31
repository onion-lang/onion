### Fixed

- **`extends P(null)` and `: this(a, null)` warn W0012 for a non-null parameter.**
  - Before: only `new P(null)` warned; these two forms stayed silent.
  - Now: all three warn. A `String?` parameter, a bare `[T]` parameter or a Java
    superclass stays silent; `extends G[String?](null)` is silent too.
