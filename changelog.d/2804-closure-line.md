### Fixed

- **An error thrown inside a lambda is reported at the lambda's own line (#2804).**
  - Before: `repro.on:4: error: ...` pointed at the line that called the lambda, and `--stacktrace` showed its frames as `Unknown Source`.
  - Now: the report shows the line that threw (`repro.on:2`), and every lambda frame names the script file. This also covers `map`/`filter` blocks and `Server` handlers.
