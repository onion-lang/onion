### Fixed

- **`select` over a `List[Int]` element or a range's loop variable matches `case 2:` (#2933).**
  - Before: every primitive label was `E0000 type Int is expected, but type Int is used`, e.g. `foreach i in 1..6 { select i { case 2: ... } }`.
  - Now: the boxed value is compared by value, as `==` already does.
