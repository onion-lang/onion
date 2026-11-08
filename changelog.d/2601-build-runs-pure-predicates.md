### Fixed

- **Compiling no longer runs a contract predicate that prints, does I/O or never ends (#2601).**
  - Before: `ensures result` predicates and `E0112` replays ran whatever they called, including overrides the library calls back (`toString`, `fillInStackTrace`). `onionc` printed, exited, or hung.
  - Now: only a predicate whose effects are at most `mutate` runs. Others, and any run past 10 seconds, get the new warning `W0023`, which names the call or static initializer that has the effect.

### Breaking

- **A `tool` that lists `unknown` only because it builds its own generic class is now `E0078`.**
  - `new Box[String]()` of a program class is now seen as the program's own constructor, not an unlisted call. Remove `unknown` from `requires`.
