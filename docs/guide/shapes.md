# Shapes

A **shape** describes how text and a typed value correspond. You declare it once; parsing,
printing and the failure channel all come from that one description.

## The problem it solves

Reading a log line into a record has always been possible:

```onion
record Access(ip: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"

val rows = Access::parseAll(logText)
```

Run that over a thousand lines where five are corrupted, and you get 995 rows. The other
five are gone — not counted, not reported, and indistinguishable from a file that only ever
had 995 lines. `Access::parse` on one of them returns `null`, which is also what it returns
for a line that is not an access log entry at all.

## Declaring a shape

```onion
record Access(ip: String, method: String, path: String, status: Int)
  shape common = re"(\S+) (\w+) (\S+) (\d+)"
```

`shape name = ...` gives the boundary a name, so a record can carry as many as it needs:

```onion
record Access(ip: String, method: String, path: String, status: Int)
  shape common = re"(\S+) (\w+) (\S+) (\d+)"
  shape tabbed = re"(\S+)\t(\w+)\t(\S+)\t(\d+)"
  shape doc    = json
```

`shape` is a soft keyword — it stays usable as an ordinary identifier.

## Reading

`parse` returns an `Outcome`: a value, or **every** reason there is not one.

```onion
val o = Access::common().parse(line)
if o.isOk() { println(o.get().path()) }
else        { println(o.describe()) }
```

A defect knows where it came from and what was expected:

```onion
Access::common().parse("10.0.0.1 GET")
// ip: ... no. The whole line did not match:
//   expected "match of /(\S+) (\w+) (\S+) (\d+)/", found "10.0.0.1 GET"

Access::common().parse("10.0.0.1 GET /x abc")
//   status: expected Int, found abc      <- a broken field, not a non-match
```

That distinction is the one `from re"..."` cannot express: it returns `null` for both.

## Reading many lines

```onion
val each = Access::common().eachLine(logText)

val rows    = Outcome::values(each)     // the 995 that read
val defects = Outcome::defects(each)    // the 5 that did not, with line numbers

foreach d: Defect in defects {
  println("line " + d.origin().line() + ": " + d.expected())
}
```

Use `lines()` instead when a partial result is meaningless — it is all-or-nothing, and
reports every bad line's defect together.

## Writing

A shape prints as well as parses, and the two agree:

```onion
val s = Access::common()
s.parse(s.print(row)).get() == row     // true
```

Not every pattern can print. `\s+` as a separator has no unique rendering — how many
spaces? — so such a shape is read-only and says so:

```onion
record Pt(x: Int, y: Int)
  shape loose = re"(-?\d+)\s+(-?\d+)"

Pt::loose().canPrint()     // false
```

This is a question you can ask, rather than a method that silently does not exist.

## Documents

Naming a format instead of a pattern reads a structured document, with the component names
as keys:

```onion
record Person(name: String, age: Int)
  shape doc = json

Person::doc().parse("{\"age\": 30}")
//   name: expected String, found absent
```

Supported formats are `json` and `yaml`. An unrecognised name is a compile error (E0076).

## Files and URLs

```onion
val one  = file"person.json".read(Person::doc())
val many = file"access.log".eachLine(Access::common())
val api  = http"https://example.com/p".read(Person::doc())
```

Every defect carries the path or URL, so a failure says *which* resource. An unreadable
file is a defect too, not an exception — reading something that might not be there is the
ordinary case at a boundary.

The method is `read`, not `as`: `as` is the cast keyword.

## Checking a shape at build time

`law` runs at compile time, so the round-trip property can be machine-checked:

```onion
record Pt(x: Int, y: Int)
  shape text = re"(-?\d+),(-?\d+)"
  law roundtrip(p: Pt) { Pt::text().parse(Pt::text().print(p)).get() == p }
```

A law whose parameter type cannot be sampled is an error (E0074), not a silent skip — a
check that does not run must not look like one that passed.

## When to reach for `Shapes` directly

`onion.Shapes::regex` and `::json` are the same construction as ordinary API, for a shape
over a type you did not declare.

## See also

- [Scripting](scripting.md) — scheme literals, `|>`, auto-CLI
- [Language specification](../reference/specification.md#records)
- `run/BrokenLogDemo.on` — the whole thing in one program
