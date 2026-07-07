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
ages.get("alice")                     // 12
ages["alice"]                         // 12  — index read (same as get)
ages["carol"] = 9                     // index assignment (compiles to put)
ages["alice"] += 1                    // compound assignment through the index
foreach k: String in ages.keySet() { println(k) }
```

## Empty Literals

`[]` and `[:]` have no element types of their own, so the compiler takes
the type from the surrounding context — `val` declarations, returns, field
initializers, and **argument position**, where the expected parameter type
supplies the element types:

```onion
import {
  java.util.List
  java.util.Map
}

def size(xs: List[String]): Int = xs.size()
def count(m: Map[String, Integer]): Int = m.size()

size([])      // 0  - [] takes the List[String] parameter type
count([:])    // 0  - [:] takes the Map[String, Integer] parameter type
```

The expected type also reaches into **branch and operand positions**, so an
empty literal in an `if`/`else` or `select` branch, a `try`/`catch` branch, the
right operand of `?:`, or a `do` block's `ret` infers its element type instead
of failing:

```onion
def pick(b: Boolean): List[Int] = if b { [] } else { [1] }   // [] is List[Int]
def orEmpty(o: List[Int]?): List[Int] = o ?: []              // [] is List[Int]
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

Batching and windowing helpers chain the same way:

```onion
val nums = [1, 2, 3, 4, 5, 6, 7]
nums.chunked(3)      // [[1,2,3], [4,5,6], [7]]  — fixed-size batches
nums.windowed(3)     // [[1,2,3], [2,3,4], ...]  — sliding windows
nums.slice(1, 4)     // [2, 3, 4]                — bounds-clamping sublist
```

## Ranges

`a..b` (inclusive) and `a..<b` (exclusive) create iterable integer ranges:

```onion
foreach i: Int in 1..5 { println(i) }          // 1 2 3 4 5
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
  println(name + " is " + age)
}

import { java.util.Map }
foreach e: Map.Entry[String, Integer] in ages.entrySet() { // explicit entry form
  println(e.getKey() + " is " + e.getValue())
}
```
