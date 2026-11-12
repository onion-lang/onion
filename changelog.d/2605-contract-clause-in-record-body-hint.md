### Fixed

- **`requires`/`ensures` written in a record or enum body now get a hint (#2605).**
  - Before: a bare syntax error, `Encountered "requires", but expecting "}"`.
  - Now: the hint shows the clause after a method's signature (`public: def half(): Int requires n > 0 = n / 2`), or `invariant n >= 0` under `public:` for a condition that always holds.
