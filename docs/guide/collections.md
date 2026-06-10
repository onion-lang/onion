# Collections

Onion uses the Java collection classes directly and adds literals,
pipelines and range iteration on top.

## List Literals

```onion
val xs = [1, 2, 3]          // java.util.List (mutable ArrayList)
val empty = []
xs[0]                        // indexed read
xs[1] = 42                   // indexed write
xs[2] += 1                   // compound assignment
```

## Map Literals

```onion
val ages = ["alice": 12, "bob": 34]   // insertion-ordered LinkedHashMap
val none = [:]                        // empty map
ages.get("alice")
foreach k: String in ages.keySet() { IO::println(k) }
```

## Pipelines

`java.util.List` and `java.lang.Iterable` are extended with the helpers
from `onion.Colls` / `onion.Iterables`, so transformations chain with
trailing lambdas:

```onion
val lines = ["alpha beta", "gamma", "alpha delta"]

val lengths = lines
  .filter { s => s.contains("alpha") }
  .map { s => s.length() }       // [10, 11]
```

The same helpers are available as plain static calls:

```onion
Colls::map(xs, (x: Integer) -> (x as Int) * 2)
```

## Ranges

`a..b` (inclusive) and `a..<b` (exclusive) create iterable integer ranges:

```onion
foreach i: Int in 1..5 { IO::println(i) }          // 1 2 3 4 5
foreach i: Int in 0..<xs.size() { use(xs[i]) }     // index iteration

val r = 2..4
r.size()        // 3
r.contains(3)   // true
```

## Sorting

Pass a lambda where a `Comparator` is expected:

```onion
val xs = Colls::mutableListOf(3, 1, 2)
Collections::sort(xs, (a, b) -> (a as Int) - (b as Int))
```

## Map Iteration

Nested class names work in type positions:

```onion
foreach (name, age) in ages {            // entry destructuring
  IO::println(name + " is " + age)
}

import { java.util.Map }
foreach e: Map.Entry in ages.entrySet() { // explicit entry form
  IO::println(e.getKey() + " is " + e.getValue())
}
```
