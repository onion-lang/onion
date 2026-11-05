### Fixed

- **The contracts docs match the shipped behaviour.**
  - Before: error-codes said `--contracts off` is the default, and the E0109/E0111 samples did not compile.
  - Now: the default is named `check`, the samples compile as written, and W0023 is in the warnings table.
