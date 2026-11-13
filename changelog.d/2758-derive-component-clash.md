### Fixed

- **A record component named `toJson`, `toMap` or `toYaml` with `derive!` is now an error.**
  - Before: it compiled, and `r.toJson()` returned the component, not JSON.
  - Now: E0026 at the record, naming the clash.
