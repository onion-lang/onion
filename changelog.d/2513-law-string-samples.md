### Fixed

- **A `law` over a `String` now tries delimiters and control characters (#2513).**
  - Before: samples never held `,` `;` `=` `|` `"`, a tab or a newline, so `Row::parse(Row::format(r)) == r` passed for `from re"([^,]*),(-?\d+)"` while `Row("Smith, J", 3)` parses to `null`.
  - Now: such a law fails with E0064 and the counterexample, here a `name` of `","`. A law that passed before may now fail; the counterexample shows the input the shape loses.
- **An E0064 counterexample shows a `String` as a quoted, escaped Onion literal.**
  - Before: `Row(name=,, n=-415012931)`, and a newline split the diagnostic over two lines.
  - Now: `Row(name=",", n=-415012931)` and `Row(name="\n", n=1991376913)`, on one line, ready to paste into an `example`.
