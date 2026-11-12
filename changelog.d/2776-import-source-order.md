### Fixed

- **`onionc app.on lib.on` finds `import { shapes.Plain }` even when `lib.on` comes second (#2776).**
  - Before: E0003 "type shapes.Plain is not found" unless the declaring file was listed first.
  - Now: file order does not matter. `import { shapes.* }` also prefers the module's own `Timer` or `Box` over the JDK class of the same name.
