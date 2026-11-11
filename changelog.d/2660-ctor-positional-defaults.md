### Fixed

- **`extends A(7)`, `: this(4)` and `new A(7)` fill an omitted constructor default.**
  - Before: E0021 for the two delegations; E0022 for `new A(7)` next to an `A(s: String)` constructor.
  - Now: `b` takes its default, and the constructor whose parameter types fit the arguments is chosen, as for a method call.
