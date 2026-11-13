### Fixed

- **`Future.await()` no longer hangs when the failure's cause chain loops (#2784).**
  - Before: an exception whose causes pointed back at each other froze `await()` forever.
  - Now: it throws as usual, naming the last cause before the loop. A `getMessage()` that throws falls back to the exception's type name.
