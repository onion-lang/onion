### Fixed

- **`onion migrate` on a big directory no longer says an old file "compiles as written" when the same error repeats (#3214).**
  - Before: an error reported twice (a lambda checked against two overloads) counted twice toward the cap but was stored once, so the cap went unnoticed and `--check` exited 0 on a tree with old syntax.
  - Now: the cap counts the errors that are kept, so it is detected and the file counts as "need a manual change". `-maxErrorReport n` likewise counts distinct errors.
