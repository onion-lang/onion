### Breaking

- **An array passed where both `f(x)` and `f(xs...)` apply now picks the same method as Java (#2955).**
  - Before: `H::s(strings)` ran `s(Object)`, `JList::of(sa).size()` was `1`, and `h.a(ints)` ran `a(Object...)`.
  - Now: a `String[]` takes `s(Object...)` and `JList::of(sa).size()` is `3`, the same at every call site; an `Int[]` takes `a(Object)`.
  - Migration: write the type argument to keep the old one-element list, `JList::of[String[]](sa)`. See `docs/migration/pre-1.0.md`.
