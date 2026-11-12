### Fixed

- **`onionc app.on lib.on` finds `import { shapes.Plain }` even when `lib.on` comes second (#2776).**
  - Before: E0003 "type shapes.Plain is not found" unless the declaring file was listed first.
  - Now: file order does not matter. `import { shapes.* }` also prefers a `Timer` or `Box` declared in the files being compiled over the JDK class of the same name. A class from a jar on the classpath binds as before.
