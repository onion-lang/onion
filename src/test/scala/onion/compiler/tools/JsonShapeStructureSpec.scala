package onion.compiler.tools

import onion.tools.Shell

/**
 * `shape name = json` over structured components: `List[S]` of a scalar, a nested record
 * that declares a json shape of its own, `List[R]` of such records, and `T?` for a key that
 * may be absent or null -- the shapes an LLM's structured output actually has.
 *
 * Reading keeps the Outcome/Defect contract all the way down: a defect inside a structure
 * names the whole path to it (`actions[2].owner`), and a bad element never hides another.
 */
class JsonShapeStructureSpec extends AbstractShellSpec {

  private val records =
    """
      |record Action(owner: String, task: String) {
      |  shape doc = json
      |}
      |record Summary(title: String, decisions: List[String], actions: List[Action], risk: Int,
      |               lead: Action?, note: String?, scores: List[Int]?) {
      |  shape doc = json
      |  example nestedRoundTrip {
      |    val v = new Summary("t", ["a", "b"], [new Action("sato", "x"), new Action("suzuki", "y")],
      |                        2, new Action("tanaka", "z"), null, [1, 2])
      |    Summary::doc().parse(Summary::doc().print(v)).get() == v
      |  }
      |}
      |""".stripMargin

  private def run(body: String, returns: String): Shell.Result =
    shell.run(
      records +
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): $returns {
          |$body
          |  }
          |}
          |""".stripMargin, "None", Array())

  describe("reading") {
    it("reads lists, nested records, lists of records and nullables") {
      val r = run(
        """    val o = Summary::doc().parse("{\"title\": \"T\", \"decisions\": [\"d1\", \"d2\"], \"actions\": [{\"owner\": \"a\", \"task\": \"b\"}], \"risk\": 2, \"lead\": {\"owner\": \"x\", \"task\": \"y\"}, \"scores\": [3, 4]}")
          |    if o.isBad() { return o.describe() }
          |    val s = o.get()
          |    return s.decisions()[1] + "|" + (s.actions()[0] as Action).task() + "|" + s.lead()?.owner() + "|" + s.note() + "|" + s.scores()
          |""".stripMargin, "String")
      assert(Shell.Success("d2|b|x|null|[3, 4]") == r)
    }

    it("reads an absent key and an explicit null alike, as null, for a nullable component") {
      val r = run(
        """    val a = Summary::doc().parse("{\"title\": \"T\", \"decisions\": [], \"actions\": [], \"risk\": 0}")
          |    val b = Summary::doc().parse("{\"title\": \"T\", \"decisions\": [], \"actions\": [], \"risk\": 0, \"lead\": null, \"note\": null, \"scores\": null}")
          |    return a.isOk() && b.isOk() && a.get() == b.get() && a.get().lead() == null && a.get().scores() == null
          |""".stripMargin, "Boolean")
      assert(Shell.Success(true) == r)
    }

    it("still reports an absent non-nullable list as a defect") {
      val r = run(
        """    val o = Summary::doc().parse("{\"title\": \"T\", \"actions\": [], \"risk\": 0}")
          |    val d = o.defects()[0] as Defect
          |    return d.path() + ": expected " + d.expected() + ", found " + d.actual()
          |""".stripMargin, "String")
      assert(Shell.Success("decisions: expected List[String], found absent") == r)
    }
  }

  describe("defects") {
    it("accumulates every defect with its path, and one bad element never hides another") {
      val r = run(
        """    val o = Summary::doc().parse("{\"title\": 3, \"decisions\": [\"d1\", 3, null], \"actions\": [{\"owner\": \"a\"}, 5, {\"owner\": 1, \"task\": \"t\"}], \"risk\": 0, \"lead\": {\"task\": 1}, \"scores\": \"x\"}")
          |    var out = ""
          |    foreach d: Defect in o.defects() { out = out + d.path() + ": expected " + d.expected() + ", found " + d.actual() + "\n" }
          |    return out
          |""".stripMargin, "String")
      val expected = Seq(
        "title: expected String, found 3",
        "decisions[1]: expected String, found 3",
        "decisions[2]: expected String, found null",
        "actions[0].task: expected String, found absent",
        "actions[1]: expected object, found 5",
        "actions[2].owner: expected String, found 1",
        "lead.owner: expected String, found absent",
        "lead.task: expected String, found 1",
        "scores: expected List[Int], found \"x\""
      ).mkString("", "\n", "\n")
      assert(Shell.Success(expected) == r)
    }

    it("reports a scalar where a list belongs, and a list where an object belongs") {
      val r = run(
        """    val o = Summary::doc().parse("{\"title\": \"T\", \"decisions\": \"one\", \"actions\": [], \"risk\": 0, \"lead\": [1]}")
          |    var out = ""
          |    foreach d: Defect in o.defects() { out = out + d.path() + "/" + d.actual() + ";" }
          |    return out
          |""".stripMargin, "String")
      assert(Shell.Success("decisions/\"one\";lead/an array;") == r)
    }

    it("keeps the origin on a nested defect") {
      val r = run(
        """    val o = Summary::doc().parse("{\"title\": \"T\", \"decisions\": [], \"actions\": [{\"owner\": 1, \"task\": \"t\"}], \"risk\": 0}", Origin::atLine("reply.json", 1))
          |    return (o.defects()[0] as Defect).describe()
          |""".stripMargin, "String")
      assert(Shell.Success("reply.json:1: actions[0].owner: expected String, found 1") == r)
    }
  }

  describe("printing") {
    it("round-trips lists, nested records and nulls: parse(print(v)) == Ok(v)") {
      val r = run(
        """    val s = Summary::doc()
          |    val full = new Summary("t", ["a"], [new Action("o", "t")], 3, new Action("l", "m"), "n", [7])
          |    val sparse = new Summary("t", [], [], 0, null, null, null)
          |    return s.parse(s.print(full)).get() == full && s.parse(s.print(sparse)).get() == sparse
          |""".stripMargin, "Boolean")
      assert(Shell.Success(true) == r)
    }

    it("prints nested records as objects and lists as arrays") {
      val r = run(
        """    return Summary::doc().print(new Summary("t", ["a", "b"], [new Action("o", "t")], 1, null, null, [2]))
          |""".stripMargin, "String")
      assert(Shell.Success(
        """{"title":"t","decisions":["a","b"],"actions":[{"owner":"o","task":"t"}],"risk":1,"lead":null,"note":null,"scores":[2]}""") == r)
    }

    it("reads a record that contains itself") {
      val r = shell.run(
        """
          |record Node(name: String, kids: List[Node]) {
          |  shape doc = json
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = Node::doc()
          |    val t = new Node("root", [new Node("a", []), new Node("b", [new Node("c", [])])])
          |    if s.parse(s.print(t)).get() != t { return "round-trip broke" }
          |    val bad = s.parse("{\"name\": \"r\", \"kids\": [{\"name\": \"a\", \"kids\": [{\"name\": 3, \"kids\": []}]}]}")
          |    return (bad.defects()[0] as Defect).path()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("kids[0].kids[0].name") == r)
    }
  }

  describe("nesting") {
    it("reads a nested record through its first json shape, declared after its user") {
      val r = shell.run(
        """
          |record Outer(inner: Inner) {
          |  shape doc = json
          |}
          |record Inner(x: Long) {
          |  shape first = json
          |  shape second = json
          |  shape line = re"(\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Long {
          |    return Outer::doc().parse("{\"inner\": {\"x\": 42}}").get().inner().x()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42L) == r)
    }

    it("leaves an all-scalar json shape and its sibling regex shape as they were") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape doc = json
          |  shape text = re"(-?\d+),(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    return Pt::doc().parse("{\"x\": 1, \"y\": 2}").get() == Pt::text().parse("1,2").get()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r)
    }
  }

  describe("JSON Schema") {
    def schemaOf(program: String, expr: String): Any = {
      val r = shell.run(program +
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): String { return $expr }
          |}
          |""".stripMargin, "None", Array())
      r match {
        case Shell.Success(text: String) => onion.Json.parse(text)
        case other                       => fail(s"expected a schema, got $other")
      }
    }

    it("describes lists, nested objects and nullables, requiring every non-nullable component") {
      val actual = schemaOf(records, "Summary::doc().jsonSchema()")
      val action =
        """{"type": "object", "properties": {"owner": {"type": "string"}, "task": {"type": "string"}},
          | "required": ["owner", "task"], "additionalProperties": false}""".stripMargin
      val expected = onion.Json.parse(
        s"""{
           |  "type": "object",
           |  "properties": {
           |    "title": {"type": "string"},
           |    "decisions": {"type": "array", "items": {"type": "string"}},
           |    "actions": {"type": "array", "items": $action},
           |    "risk": {"type": "integer"},
           |    "lead": {"anyOf": [$action, {"type": "null"}]},
           |    "note": {"type": ["string", "null"]},
           |    "scores": {"type": ["array", "null"], "items": {"type": "integer"}}
           |  },
           |  "required": ["title", "decisions", "actions", "risk"],
           |  "additionalProperties": false
           |}""".stripMargin)
      assert(actual == expected)
    }

    it("maps every scalar kind to its JSON Schema type") {
      val actual = schemaOf(
        """
          |record K(s: String, i: Int, l: Long, d: Double, f: Float, b: Boolean, h: Short, y: Byte) {
          |  shape doc = json
          |}
          |""".stripMargin, "K::doc().jsonSchema()")
      val props = actual.asInstanceOf[java.util.Map[String, Any]].get("properties").asInstanceOf[java.util.Map[String, Any]]
      def typeOf(k: String) = props.get(k).asInstanceOf[java.util.Map[String, Any]].get("type")
      assert(Seq("s", "i", "l", "d", "f", "b", "h", "y").map(typeOf) ==
        Seq("string", "integer", "integer", "number", "number", "boolean", "integer", "integer"))
    }

    it("is answered only by a json shape") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  shape doc = json
          |  shape yml = yaml
          |  shape text = re"(-?\d+),(-?\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var refused = false
          |    try { Pt::text().jsonSchema() } catch e: UnsupportedOperationException { refused = true }
          |    return "" + Pt::doc().hasJsonSchema() + Pt::yml().hasJsonSchema() + Pt::text().hasJsonSchema() + refused
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("truefalsefalsetrue") == r)
    }

    it("describes a self-recursive shape via $defs/$ref instead of overflowing the stack") {
      val r = shell.run(
        """
          |record Node(name: String, kids: List[Node]) {
          |  shape doc = json
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Node::doc().jsonSchema()
          |  }
          |}
          |""".stripMargin, "None", Array())
      r match {
        case Shell.Success(schema: String) =>
          assert(schema.contains("\"$defs\""))
          assert(schema.contains("\"$ref\""))
          // The schema must itself be valid, parseable JSON -- not just a string containing
          // the right substrings -- and the $ref must point at a $defs entry that exists.
          val parsed = onion.Json.parse(schema).asInstanceOf[java.util.Map[String, Object]]
          val defs = parsed.get("$defs").asInstanceOf[java.util.Map[String, Object]]
          assert(defs != null && !defs.isEmpty)
          val kids = parsed.get("properties").asInstanceOf[java.util.Map[String, Object]].get("kids")
            .asInstanceOf[java.util.Map[String, Object]]
          val ref = kids.get("items").asInstanceOf[java.util.Map[String, Object]].get("$ref").asInstanceOf[String]
          assert(ref != null && ref.startsWith("#/$defs/"))
          assert(defs.containsKey(ref.stripPrefix("#/$defs/")))
        case other => fail(s"expected a schema, got $other")
      }
    }

    it("describes a mutually-recursive pair of shapes via $defs/$ref") {
      val r = shell.run(
        """
          |record Branch(label: String, leaf: Leaf?) {
          |  shape doc = json
          |}
          |record Leaf(value: Int, back: Branch?) {
          |  shape doc = json
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Branch::doc().jsonSchema()
          |  }
          |}
          |""".stripMargin, "None", Array())
      r match {
        case Shell.Success(schema: String) =>
          val parsed = onion.Json.parse(schema)
          assert(parsed != null)
          assert(schema.contains("\"$ref\""))
        case other => fail(s"expected a schema, got $other")
      }
    }
  }
}
