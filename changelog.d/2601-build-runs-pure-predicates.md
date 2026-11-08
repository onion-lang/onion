### Fixed

- **Compiling no longer runs a contract predicate that prints, does I/O or never ends (#2601).**
  - Before: `ensures result` predicates and `E0112` replays ran whatever they called, including overrides the library calls back (`toString`, `fillInStackTrace`). `onionc` printed, exited, or hung.
  - Now: only a predicate whose effects are at most `mutate` runs. Others, and any run past 10 seconds, get the new warning `W0023`, which names the call, static initializer or parameter class that has the effect. Making a sample argument of a program class counts too.

### Breaking

- **A `tool`'s `requires` is checked against more of what its body runs.** The error names the capability to add or remove.
  - `E0077`: a call that can dispatch to a program override, a static initializer the tool's code triggers, and a try-with-resources `close()` now count.
  - `E0078`: `new Box[String]()` of a program class is no longer an unlisted call, so an `unknown` kept only for it is now unused.
