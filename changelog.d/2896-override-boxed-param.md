### Fixed

- **`override` on a method that differs from the base one only by boxing is now E0068.**
  - Before: `override def dec(k: Int?)` over `dec(k: Int)` compiled, but never ran: calls went to the base method.
  - Now: E0068, as in Java. Through a generic supertype (`G[JInteger]`) the boxed form still overrides.
