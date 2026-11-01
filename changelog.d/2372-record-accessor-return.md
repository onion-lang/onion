### Fixed

- **An override that changes a primitive return type is `E0104` (#2372).**
  - Before: `record Circle(area: Int) conforms Shape` with `Shape.area(): Double` or `area(): JInteger` compiled. Calls through `Shape` ran the other method, or threw `AbstractMethodError`.
  - Now: `E0104` at the component, naming both methods. The same rule applies to a class method (`size(): Int` over `size(): Long`), as in Java.
