### Fixed

- **`extends A(7)`, `: this(4)` and `new A(7)` fill an omitted constructor default.** With `def this(a: Int = 1, b: Int = 2)`:
  - Before: E0021 for the two delegations; E0022 for `new A(7)` next to an `A(s: String)` constructor.
  - Now: `b` takes its default `2`, and the constructor whose parameter types fit the arguments is chosen.
  - E0021 for a wrong-typed argument, such as `new A("x")`, names the call's types (`A(String)`), not the constructor's.
