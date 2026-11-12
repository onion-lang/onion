### Fixed

- **`"#{sb}"` with a `StringBuilder` part prints its text.**
  - Before: it compiled, then the class failed to load with `VerifyError: Bad type on operand stack`.
  - Now: `"[#{sb}]"` gives `[hi]` for `new StringBuilder("hi")`, as `"[" + sb + "]"` already did.
  - In a contract, `requires "#{sb}" != ""` now compiles, as an `Object` part does. Before, it was E0106 naming `AbstractStringBuilder::append`.
