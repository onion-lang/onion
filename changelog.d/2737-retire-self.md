### Breaking

- **`self` is retired; write `this` (#2737, RFC #2788).**
  - Before: `self` and `this` both meant the current instance.
  - Now: `self` is error E0117, at every use. `onion migrate <path>` rewrites each one, inside `#{...}` too.
