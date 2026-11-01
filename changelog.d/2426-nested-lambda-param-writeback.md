### Fixed

- **A nested lambda that assigns an outer lambda's parameter now writes it back (#2426).**
  - Before: `(k) -> { val f: () -> Int = () -> { k = k + 10; k }; f(); k }` called with 1 returned 1.
  - Now: it returns 11, as for a captured `var` or a method parameter.
  - The reverse holds too: a nested lambda reads the outer lambda's later writes.
