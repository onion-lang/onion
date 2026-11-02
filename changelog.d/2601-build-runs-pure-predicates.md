### Fixed

- **Compiling no longer runs a contract predicate that prints, does I/O or never ends (#2601).**
  - Before: `ensures result` predicates and `E0112` replays ran whatever they called. `onionc` printed, wrote files, exited, or hung.
  - Now: only a predicate whose effects are at most `mutate` runs. Others, and any run past 10 seconds, get the new warning `W0023` naming the predicate and the effect.
