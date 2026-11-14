### Fixed

- **E0106 underlines the call that has the effect, even when an earlier call has the same name (#2847).**
  - Before: in `requires p.add(1) > 0 && xs.add(1)`, the pure `p.add(1)` was underlined while the message named `List.add`.
  - Now: `xs.add(1)` is underlined.
