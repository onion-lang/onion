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
record Access(ip: String, method: String, path: String, status: Int) {
  shape common = re"(\S+) (\w+) (\S+) (\d+)"
}
```

`shape name = ...` gives the boundary a name, so a record can carry as many as it needs:

```onion
record Access(ip: String, method: String, path: String, status: Int) {
  shape common = re"(\S+) (\w+) (\S+) (\d+)"
  shape tabbed = re"(\S+)\t(\w+)\t(\S+)\t(\d+)"
  shape doc    = json
}
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
record Pt(x: Int, y: Int) {
  shape loose = re"(-?\d+)\s+(-?\d+)"
}

Pt::loose().canPrint()     // false
```

This is a question you can ask, rather than a method that silently does not exist.

Some shapes can print but still refuse a particular value, when printing it would break
the round-trip. `lines()` and `sepBy(separator)` join each element's rendering with a
delimiter — a newline, or the separator text — so a real boundary can be told apart from
an element's own content on read-back. If an element's rendering already contains that
delimiter, joining would silently split it into a bogus extra element instead. Both throw
`IllegalArgumentException` naming the offending element's index rather than misrender:

```onion
val intList: Shape[List[Int]] = intShape.sepBy(",")
intList.print(evilInts)   // throws: element at index 1 contains the separator, ...
```

`shape name = config` has the same protection one level up: a `key = value` line runs to
the end of the line, so a value whose text contains a line break cannot be represented
either — `print` and `printLossless` throw naming the offending field instead of
injecting a fake extra entry.

## Documents

Naming a format instead of a pattern reads a structured document, with the component names
as keys:

```onion
record Person(name: String, age: Int) {
  shape doc = json
}

Person::doc().parse("{\"age\": 30}")
//   name: expected String, found absent
```

Supported formats are `json` and `yaml`. An unrecognised name is a compile error (E0076).

### Lists, nested records and absent keys

A `json` shape reads structure, not just scalars. Beyond the scalar component types, a
json shape's components may be:

- `List[S]` for a scalar `S` — a JSON array of that scalar
- `R`, a record that declares a json shape of its own — a nested JSON object
- `List[R]` of such a record — an array of objects
- `T?` of any of these — the key may be absent or `null`, and the component is then `null`

```onion
record Action(owner: String, task: String) {
  shape doc = json
}

record Summary(title: String, decisions: List[String], actions: List[Action], lead: Action?) {
  shape doc = json
  example nested {
    val v = new Summary("sync", ["ship"], [new Action("sato", "review")], null)
    Summary::doc().parse(Summary::doc().print(v)).get() == v
  }
}
```

A nested record is read through its **first** json shape in declaration order, so a record
with several json shapes puts the one it wants nested first. The record may be declared
anywhere, and may even contain itself (`record Node(name: String, kids: List[Node])`).

Failures still accumulate, and each names the whole path to where it happened. A bad
element never hides its siblings:

```onion
Summary::doc().parse("{\"title\": \"t\", \"decisions\": [\"a\", 3], \"actions\": [{\"owner\": \"o\"}, 5]}")
//   decisions[1]: expected String, found 3
//   actions[0].task: expected String, found absent
//   actions[1]: expected object, found 5
```

Only `json` reaches this far. A regex, `yaml` or `config` shape keeps to the scalars, and
because every shape of a record reads the same components, a record that mixes them does
too — E0061 names the clause that cannot read the component.

### A JSON Schema for structured output

A json shape can describe the documents it reads as a JSON Schema — the form an LLM's
structured-output mode takes. The record is then the one description of the answer, rather
than a record plus a hand-written schema that has to be kept in step with it:

```onion
Summary::doc().jsonSchema()
// {"type":"object",
//  "properties":{"title":{"type":"string"},
//                "decisions":{"type":"array","items":{"type":"string"}},
//                "actions":{"type":"array","items":{"type":"object", ... }},
//                "lead":{"anyOf":[{"type":"object", ... },{"type":"null"}]}},
//  "required":["title","decisions","actions"],
//  "additionalProperties":false}
```

Every object is closed (`additionalProperties: false`) and requires every non-nullable
component; a nullable one is left out of `required` and admits `null`. Integers become
`integer`, `Double`/`Float` become `number`. The schema is JSON text; `Json::parse` turns it
into a map to embed in a request body.

`hasJsonSchema()` answers whether a shape can do this — only a json shape can, and every
other shape's `jsonSchema()` throws rather than describe documents it does not read. A
record that contains itself has no finite inline schema, so its `jsonSchema()` throws too.

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

## Lossless shapes: L1 and L2

Every printing shape guarantees **L1**, the round-trip law: `parse(print(v)) == Ok(v)`.
The reverse law — **L2**, `print(parse(t)) == t` — is false in general, for ordinary
reasons: `"007"` is a perfectly good `Int` that prints back as `"7"`, and two documents
that differ only in whitespace parse alike. A shape that satisfies L2 as well is
**lossless**, and losslessness is what an edit-and-write-back is built on.

`shape name = config` is the first lossless shape: a commented `key = value` document.
`parseLossless` returns the value together with a `Residue` — everything the shape did
not consume: comments, blank lines, key order, spacing, unknown keys, and the original
spelling of every value. `printLossless` reassembles the two:

```onion
record Server(host: String, port: Int, debug: Boolean) {
  shape cfg = config
  example l2 {
    val t = "# prod\nhost = h\nport = 007\ndebug = true\n"
    val r = Server::cfg().parseLossless(t).get()
    Server::cfg().printLossless(r.value(), r.residue()) == t
  }
}
```

The `example` clause is the point: L2 is not a comment, it is machine-checked at build
time, and a shape that stopped satisfying it would stop compiling. Editing goes through
the same pair — change one component and only its value slot re-renders; an *unchanged*
component keeps its original spelling, so `007` stays `007` unless the program actually
changed the port.

Losslessness is a claim, not a default: `isLossless()` answers honestly, a lossy shape
refuses `parseLossless` instead of pretending with an empty residue, and a residue is
only accepted by the shape that produced it.

### Editing a file through the lens

`Lossless` is a lens: `edit` focuses an update on the value, `render` reassembles the
text through the residue. With `file"..."` supplying a byte-faithful read, "change one
key in the config without destroying it" — the task every ad-hoc config editor gets
wrong — is three lines, and the write-back is a declared, checked effect when it lives
in a `tool`:

```onion
val lens = file(path).readLossless(Server::cfg()).get()
val out  = lens.edit { v -> v.copy(port = 9090) }.render()
Files::writeText(path, out)
```

`diff` afterwards shows exactly one changed line. The demo (`run/ConfigEditDemo.on`)
runs this as a tool, so `--plan` shows the read and the write before anything happens.

## Checking a shape at build time

`law` runs at compile time, so the round-trip property can be machine-checked:

```onion
record Pt(x: Int, y: Int) {
  shape text = re"(-?\d+),(-?\d+)"
  law roundtrip(p: Pt) { Pt::text().parse(Pt::text().print(p)).get() == p }
}
```

A law whose parameter type cannot be sampled is an error (E0074), not a silent skip — a
check that does not run must not look like one that passed.

## Writing your own shape

The shipped derivations (`re""`, `json`, `yaml`, `config`) are a closed set on purpose —
their laws come with them. For a format the compiler has never heard of — fixed-width
records, binary framing, a bespoke wire protocol — implement `onion.Shape[T]` directly:

```onion
class FixedWidth conforms Shape[Person] {
public:
  def this {}
  def parse(text: String, origin: Origin): Outcome[Person] { ... }
  def canPrint(): Boolean = true
  def print(v: Person): String { ... }
  def describe(): String = "fixed-width Person"
}

example fixedWidthL1 {
  val s = new FixedWidth()
  val v = new Person("KOTA", 42)
  s.parse(s.print(v)).get() == v
}
```

The combinators — `eachLine`, `sepBy`, `xmap`, `orElse` — come for free, and failures
speak the same `Outcome`/`Defect` vocabulary as every shipped shape.

The `example` is not optional. A derived shape's laws are guaranteed by construction;
a user-written one *asserts* them instead, so a concrete class implementing
`onion.Shape` compiles only when its file states at least one machine-checked `law` or
`example` (**E0080** otherwise). The round-trip `s.parse(s.print(v)).get() == v` is
the canonical claim; top-level `example [name] { expr }` is the vehicle, and it runs
at build time like every other law — a false claim is E0065. A parse-only shape says
so through `canPrint(): false` and asserts whatever laws its reading direction has.

## When to reach for `Shapes` directly

`onion.Shapes` exposes the same four constructions `shape name = ...` derives, as
ordinary API, for a shape over a type you did not declare:

- `Shapes::regex` — the `re"..."` form
- `Shapes::json` — the `json` form; its five-argument overload takes the extended
  component tags (`List[Int]`, `Nested`, a trailing `?`) and one nested-shape thunk per
  component
- `Shapes::yaml` — the `yaml` form
- `Shapes::config` — the `config` form, lossless (`parseLossless`/`printLossless`) like
  `shape name = config`

## See also

- [Scripting](scripting.md) — scheme literals, `|>`, auto-CLI
- [Language specification](../reference/specification.md#records)
- `run/BrokenLogDemo.on` — the whole thing in one program
