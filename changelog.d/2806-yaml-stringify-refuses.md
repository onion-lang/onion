### Fixed

- **`Yaml::stringify` refuses text that `Yaml::parse` cannot read back (#2806).**
  - Before: `["apple", "banana"]`, a list with `[:]`, and `NaN` were written, then failed (or came back as a String) on reload.
  - Now: each throws `IllegalArgumentException`, as a nested Map already did.
