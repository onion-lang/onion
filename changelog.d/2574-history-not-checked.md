### Fixed

- **A `history` on a `Long` or `Double` field is checked against writes, like one on an `Int` (#2574).**
  - Before: `t = t - 1L` against `history t >= old(t)` compiled with no error.
  - Now: E0109, as for `t = t - 1` on an `Int` field.

### Added

- **Warning `W0024` (`history-not-checked`): a write that no history check settled is named, not left silent (#2574).**
  - Before: `t = t + 1L` (overflow not checked) or `history old(open) == false || open` compiled exactly like a proved history.
  - Now: `` `Clock.tick` writes `t`, but nothing checks the history `t >= old(t)` for it, so it may not hold: the checker models only `Int` fields, and `t` is a `Long`. `` Under `--contracts check` only; `--Wno W0024` turns it off.
