### Breaking

- **`Http.Response` has no public fields; `status()`, `body()` and `headers()` are its one spelling (#2738).**
  - Before: `r.status` read a public field and `r.status()` called a method that returned it.
  - Now: the fields are private. Onion source is unchanged: `r.status` calls `status()`. Recompile `.class` files built by an older `onionc` that read the fields, and change Java code from `r.status` to `r.status()`.
