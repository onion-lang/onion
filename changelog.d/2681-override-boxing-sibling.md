### Fixed

- **`override` on a method that only matches a generic contract through boxing is E0068 when a sibling overload is the real override.**
  - Before: `override def n(x: Int)` next to `def n(x: JInteger)` in `class C conforms G[JInteger]` compiled silently.
  - Now: E0068 on `n(x: Int)`, as Java rejects `@Override` there.
