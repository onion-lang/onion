### Fixed

- **`try (val pool = Concurrent::pool(2)) { ... }` compiles, and the pool is closed at the end (#2772).**
  - Before: E0000 "type AutoCloseable is expected". Same for `Db::connect`, `Net::connect`, `Net::listen` and `Server::start`.
  - Now: these five types are `AutoCloseable`, like any resource.
