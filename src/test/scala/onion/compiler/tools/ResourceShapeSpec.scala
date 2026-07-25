package onion.compiler.tools

import onion.tools.Shell

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/**
 * `file"…".read(shape)` and `http"…".read(shape)` replace a fixed method menu with an
 * applied shape. (`read`, not `as`: `as` is the cast keyword.)
 *
 * `FileResource` exposes `text`, `lines`, `json`, `csv`, `csvRows` — the parse step is
 * chosen by which getter you call, so the set of things a file can be read as is closed
 * and a user cannot extend it. Applying a shape opens it, and carries the file's own path
 * into every defect, which is what makes a failure say *which file*.
 */
class ResourceShapeSpec extends AbstractShellSpec {

  private def withTempFile(content: String)(body: Path => Unit): Unit = {
    val f = Files.createTempFile("resource-shape", ".txt")
    try {
      Files.write(f, content.getBytes(StandardCharsets.UTF_8))
      body(f)
    } finally Files.deleteIfExists(f)
  }

  describe("reading a file through a shape") {
    it("parses the whole file") {
      withTempFile("""{"x": 3, "y": 4}""") { f =>
        val r = shell.run(
          s"""
             |record Pt(x: Int, y: Int)
             |  shape doc = json
             |class Test {
             |public:
             |  static def main(args: String[]): Int {
             |    val o = file"${f.toString.replace("\\", "\\\\")}".read(Pt::doc())
             |    if o.isBad() { return -1 }
             |    return o.get().x() * 10 + o.get().y()
             |  }
             |}
             |""".stripMargin, "None", Array())
        assert(Shell.Success(34) == r)
      }
    }

    it("names the file in a defect, so a failure says which one") {
      withTempFile("""{"y": 4}""") { f =>
        val r = shell.run(
          s"""
             |record Pt(x: Int, y: Int)
             |  shape doc = json
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    val o = file"${f.toString.replace("\\", "\\\\")}".read(Pt::doc())
             |    if o.isOk() { return "unexpectedly ok" }
             |    return (o.defects()[0] as Defect).origin().source()
             |  }
             |}
             |""".stripMargin, "None", Array())
        assert(Shell.Success(f.toString) == r)
      }
    }

    it("reads line-oriented files, keeping the lines it could not read") {
      withTempFile("1,2\nbroken\n3,4\n") { f =>
        val r = shell.run(
          s"""
             |record Pt(x: Int, y: Int)
             |  shape text = re"(-?\\d+),(-?\\d+)"
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    val each = file"${f.toString.replace("\\", "\\\\")}".eachLine(Pt::text())
             |    val bad = Outcome::defects(each)
             |    return Outcome::values(each).size + "/" + bad.size + "/" +
             |           (bad[0] as Defect).origin().line()
             |  }
             |}
             |""".stripMargin, "None", Array())
        assert(Shell.Success("2/1/2") == r)
      }
    }

    it("reports a missing file as a defect rather than throwing") {
      val r = shell.run(
        """
          |record Pt(x: Int, y: Int)
          |  shape doc = json
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = file"/definitely/not/here.json".read(Pt::doc())
          |    if o.isOk() { return "unexpectedly ok" }
          |    return (o.defects()[0] as Defect).expected()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("a readable file") == r)
    }
  }

  describe("the old menu still works") {
    it("keeps text() and lines()") {
      withTempFile("a\nb\n") { f =>
        val r = shell.run(
          s"""
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    return file"${f.toString.replace("\\", "\\\\")}".lines().size + ""
             |  }
             |}
             |""".stripMargin, "None", Array())
        assert(Shell.Success("2") == r)
      }
    }
  }
}
