### Fixed

- **`Result::trying { ... }.getOrThrow()` rethrows the original exception, so `catch e: IOException` fires.**
  - Before: a `Throwable` error was wrapped in a `RuntimeException`, and `get()` on an `Err` said only `Err.get()`.
  - Now: the error is thrown as itself, as the docs said; `get()` says `Err.get(): <error>`.
