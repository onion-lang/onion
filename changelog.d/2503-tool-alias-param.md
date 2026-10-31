### Fixed

- **A `tool` or `main` parameter typed with a `type` alias works like the type it names (#2503).**
  - Before: `type Port = Int` then `tool serve(port: Port)` was E0081 (E0094 for `main`), naming `Port` as unsupported.
  - Now: it is read as an `Int`; `--help` and `--contract` say `Int`. An alias to `List[String]` gets the list-specific E0081.
