### Fixed

- **A failed `await()` says why the task failed (#2771).**
  - Before: `Future::async { Integer::parseInt("nope") }.await()` reported `RuntimeException: Future.await() failed`.
  - Now: `Future.await() failed (For input string: "nope")`. `awaitTimeout(ms)` does the same; the cause is still the task's exception.
