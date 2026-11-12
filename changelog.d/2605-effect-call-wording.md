### Fixed

- **E0106, E0077 and W0023 name an instance call the way it is written (#2605).**
  - Before: `ensures xs.add(1)` was E0106 "this contract calls `java.util.List::add`", in static-call syntax.
  - Now: "calls `java.util.List.add`". A static call keeps `Owner::method` (`Loader::load`).
