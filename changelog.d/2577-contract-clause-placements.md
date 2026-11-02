### Fixed

- **A contract clause on an extension method, an enum, an interface method or an `abstract def` is checked (#2577).**
  - Before: the clause was dropped. An extension method's or an enum's clause compiled even with contracts off, and with `--contracts check` an undefined name compiled in all four places.
  - Now: with contracts off each clause is E0105, as everywhere else. With `--contracts check` it is typed like a class method's clause (E0002, E0106, E0107).
