### Fixed

- **`val xs: List[Long?] = [1, 2]` holds Longs, so `xs[0]` reads back as a `Long?`.**
  - Before: it compiled, then threw `ClassCastException` (Integer → Long) on the first read.
  - Now: each `Int` element is widened, as in `val a: Long? = 1`. Map literal keys and values (`Map[String, Long?]`) and `Double?` slots work the same way.
