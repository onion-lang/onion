### Fixed

- **Compiling no longer runs a contract predicate that prints, does I/O or never ends (#2601).**
  - Before: `ensures result` predicates and `E0112` replays ran whatever they called. `onionc` printed, wrote files, exited, or hung.
  - Now: only a predicate whose effects are at most `mutate` runs. Others, and any run past 10 seconds, get the new warning `W0023` naming the predicate and the effect.
  - The effects include every same-arity overload, overrides, interface implementations, what the library calls back (`toString` from `+`, `hashCode` from a map literal, `equals` from `==`), and superclass static initializers.
  - When the effect is not in the predicate's body, `W0023` names the call or the class whose static initializer has it.
