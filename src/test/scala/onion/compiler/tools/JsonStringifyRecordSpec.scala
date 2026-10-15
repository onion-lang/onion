package onion.compiler.tools

import onion.tools.Shell

/**
 * `Json::stringify(obj)` only special-cased String/Number/Boolean/null/Map/List; any other
 * object (an Onion `record` among them) fell through to `stringifyString(obj.toString())`,
 * so a record silently serialized as a JSON *string* holding its `toString()` text instead
 * of a JSON object of its fields (#2072). `Json::stringify` now recognizes a record value
 * (every Onion record implements the runtime marker `onion.OnionRecord`) and serializes it
 * as a JSON object, keyed by component name in declaration order, recursing into each
 * component the same way a Map's values already do -- so a record nested inside a record,
 * a List, or a Map all serialize correctly with no special-casing of this record vs. any
 * other value.
 */
class JsonStringifyRecordSpec extends AbstractShellSpec {
  it("serializes a flat record as a JSON object, not its toString()") {
    val result = shell.run(
      """
        |import { onion.Json; }
        |record Point(x: Int, y: Int)
        | static def main(args: String[]): String {
        |   return Json::stringify(new Point(1, 2))
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("""{"x":1,"y":2}""") == result)
  }

  it("round-trips a record's fields through parse(stringify(...))") {
    val result = shell.run(
      """
        |import { onion.Json; }
        |record Point(x: Int, y: Int)
        | static def main(args: String[]): String {
        |   val back = Json::parse(Json::stringify(new Point(1, 2))) as Map
        |   return back["x"] + "|" + back["y"]
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("1|2") == result)
  }

  it("serializes a record nested inside another record") {
    val result = shell.run(
      """
        |import { onion.Json; }
        |record Point(x: Int, y: Int)
        |record Line(from: Point, to: Point)
        | static def main(args: String[]): String {
        |   return Json::stringify(new Line(new Point(1, 2), new Point(3, 4)))
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("""{"from":{"x":1,"y":2},"to":{"x":3,"y":4}}""") == result)
  }

  it("serializes a List of records as a JSON array of objects") {
    val result = shell.run(
      """
        |import { onion.Json; }
        |record Point(x: Int, y: Int)
        | static def main(args: String[]): String {
        |   return Json::stringify([new Point(1, 2), new Point(3, 4)])
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("""[{"x":1,"y":2},{"x":3,"y":4}]""") == result)
  }

  it("still serializes a non-record, non-collection value by falling back to its toString()") {
    val result = shell.run(
      """
        |import { onion.Json; java.lang.StringBuilder; }
        | static def main(args: String[]): String {
        |   val sb = new StringBuilder()
        |   sb.append("hi")
        |   return Json::stringify(sb)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success("\"hi\"") == result)
  }
}
