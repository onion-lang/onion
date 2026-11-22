### Breaking

- **An `if`/`select`/`try` branch whose type arguments differ from the target's is E0000 (#3241).**
  - Before: `val c: Option[Int] = if b { other } else { tail }` with `other: Option[String]` compiled, and reading `c` threw `ClassCastException`.
  - Now: E0000 at `other`. Give that branch the target's type. No `onion migrate` rule; see `docs/migration/pre-1.0.md`.
