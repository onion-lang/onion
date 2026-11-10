### Fixed

- **Named arguments run in the order they are written (#2767).**
  - Before: `f(b = t("B"), a = t("A"))` ran `t("A")` first, in parameter order.
  - Now: `t("B")` runs first, as in Kotlin, Scala and C#. This covers method, `super`, safe (`?.`) and `new` calls; `extends A(..)`, `: this(..)` and `copy(..)` still use parameter order.
- **A local inside a field initializer no longer overwrites a constructor parameter.**
  - Before: `val s: String = if true { val t = "x"; t } else { "" }` in a class with `def this(n: String)` set `n` to `"x"`; with a `Long` parameter, `new` threw `VerifyError`.
  - Now: the initializer's locals get their own slots.
