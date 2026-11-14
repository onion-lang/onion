### Fixed

- **A `HEAD` request to a `Server` route no longer makes the JDK log a warning (#2808).**
  - Before: every `HEAD` printed `WARNING: sendResponseHeaders: being invoked with a content length for a HEAD request`.
  - Now: the reply has the headers and `Content-Length` of the GET body, no body, and no warning.
