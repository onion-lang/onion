### Fixed

- **Compiling no longer runs a `Shape` that prints or does I/O (#2733).**
  - Before: the `E0114` round-trip check called a user shape's `parse`/`print` on every sample, so `onionc` printed, wrote files or exited.
  - Now: a shape whose constructor, `canPrint`, `printable`, `parse`, `print` or sample value has an effect beyond `mutate` is not run, and a round-trip that does not finish in time is given up on. Warning `W0023` names why.
  - A `--warn error` build fails on it. Fix: move the effect out of the shape, or pass `--Wno W0023`.
- **A correct `Shape` with an extra one-argument overload (`def parse(n: Int)`) no longer gets a false `E0114`.**
  - Before: the check could call that overload, or a `static def parse`, instead of the shape's own `parse(String)`.
  - Now: it calls only the methods that implement `onion.Shape`.
