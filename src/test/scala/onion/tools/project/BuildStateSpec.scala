package onion.tools.project

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import org.scalatest.EitherValues.convertEitherToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BuildStateSpec extends AnyFunSuite with Matchers:
  private val fingerprint = "a" * 64
  private val main = EntryPoint("MainMain", "src/main.on", 1, 1)
  private val widget = EntryPoint("demo.Widget", "src/widget.on", 3, 5)
  private val canonicalState = BuildState(
    BuildFingerprint.SchemaVersion,
    fingerprint,
    Vector("MainMain", "demo.Widget"),
    Vector(main, widget)
  )

  private val canonicalJson =
    s"""{
       |  "schemaVersion": ${BuildFingerprint.SchemaVersion},
       |  "fingerprint": "$fingerprint",
       |  "classes": [
       |    "MainMain",
       |    "demo.Widget"
       |  ],
       |  "entryPoints": [
       |    {
       |      "className": "MainMain",
       |      "source": "src/main.on",
       |      "line": 1,
       |      "column": 1
       |    },
       |    {
       |      "className": "demo.Widget",
       |      "source": "src/widget.on",
       |      "line": 3,
       |      "column": 5
       |    }
       |  ]
       |}""".stripMargin

  private def json(
    schemaVersion: String = BuildFingerprint.SchemaVersion.toString,
    fingerprintValue: String = s"\"$fingerprint\"",
    classes: String = """["MainMain", "demo.Widget"]""",
    entryPoints: String =
      """[
        |  {
        |    "className": "MainMain",
        |    "source": "src/main.on",
        |    "line": 1,
        |    "column": 1
        |  }
        |]""".stripMargin
  ): String =
    s"""{
       |  "schemaVersion": $schemaVersion,
       |  "fingerprint": $fingerprintValue,
       |  "classes": $classes,
       |  "entryPoints": $entryPoints
       |}""".stripMargin

  private def entry(
    className: String = "\"MainMain\"",
    source: String = "\"src/main.on\"",
    line: String = "1",
    column: String = "1",
    extra: String = ""
  ): String =
    s"""[{
       |  "className": $className,
       |  "source": $source,
       |  "line": $line,
       |  "column": $column$extra
       |}]""".stripMargin

  test("encodes deterministic pretty JSON and defensively sorts vectors"):
    val unordered = canonicalState.copy(
      classes = canonicalState.classes.reverse,
      entryPoints = canonicalState.entryPoints.reverse
    )

    BuildState.encode(unordered) shouldBe canonicalJson

  test("round trips canonical state"):
    BuildState.decode(BuildState.encode(canonicalState)).value shouldBe canonicalState

  test("rejects malformed JSON and non-object roots"):
    BuildState.decode("{").isLeft shouldBe true
    BuildState.decode("[]").isLeft shouldBe true

  test("rejects unknown and missing root fields"):
    BuildState.decode(json().dropRight(1) + """, "unknown": true}""").isLeft shouldBe true
    BuildState.decode(
      s"""{
         |  "schemaVersion": ${BuildFingerprint.SchemaVersion},
         |  "fingerprint": "$fingerprint",
         |  "classes": ["MainMain"]
         |}""".stripMargin
    ).isLeft shouldBe true

  test("rejects wrong root field types"):
    Vector(
      json(schemaVersion = "\"" + BuildFingerprint.SchemaVersion + "\""),
      json(fingerprintValue = "1"),
      json(classes = "\"MainMain\""),
      json(classes = "[1]"),
      json(entryPoints = "{}"),
      json(entryPoints = "[1]")
    ).foreach(BuildState.decode(_).isLeft shouldBe true)

  test("rejects non-integral and out-of-range numeric fields"):
    Vector(
      json(schemaVersion = "1.5"),
      json(schemaVersion = "2147483648"),
      json(entryPoints = entry(line = "1.5")),
      json(entryPoints = entry(line = "2147483648")),
      json(entryPoints = entry(line = "0")),
      json(entryPoints = entry(column = "-1"))
    ).foreach(BuildState.decode(_).isLeft shouldBe true)

  test("rejects unknown schema versions and invalid fingerprints"):
    // Any version other than the current one, on either side of it, must be rejected --
    // that is what makes a SchemaVersion bump invalidate stale build state rather than
    // let it be read back with the wrong meaning.
    BuildState.decode(json(schemaVersion = (BuildFingerprint.SchemaVersion - 1).toString)).isLeft shouldBe true
    BuildState.decode(json(schemaVersion = (BuildFingerprint.SchemaVersion + 1).toString)).isLeft shouldBe true
    BuildState.decode(json(fingerprintValue = "\"abc\"")).isLeft shouldBe true
    BuildState.decode(json(fingerprintValue = s""""${"A" * 64}"""")).isLeft shouldBe true

  test("rejects empty, duplicate, unsafe, and unsorted class lists"):
    Vector(
      "[]",
      """["MainMain", "MainMain"]""",
      """["../Main"]""",
      """["demo/Main"]""",
      """["demo.Widget", "MainMain"]"""
    ).foreach(value => BuildState.decode(json(classes = value)).isLeft shouldBe true)

  test("rejects unknown and wrong entrypoint fields"):
    BuildState.decode(json(entryPoints = entry(extra = """, "unknown": true"""))).isLeft shouldBe true
    BuildState.decode(
      json(entryPoints =
        """[{
          |  "className": "MainMain",
          |  "source": "src/main.on",
          |  "line": 1
          |}]""".stripMargin
      )
    ).isLeft shouldBe true
    Vector(
      entry(className = "1"),
      entry(source = "1"),
      entry(line = "\"1\""),
      entry(column = "true")
    ).foreach(value => BuildState.decode(json(entryPoints = value)).isLeft shouldBe true)

  test("rejects non-normalized, absolute, traversal, and platform-specific source paths"):
    Vector(
      "",
      "/absolute.on",
      "../outside.on",
      "src/../outside.on",
      "src\\main.on",
      "C:/absolute.on",
      "C:drive-relative.on",
      "src//main.on",
      "./src/main.on"
    ).foreach { source =>
      withClue(s"source=$source: ") {
        val quoted = "\"" + source.replace("\\", "\\\\") + "\""
        BuildState.decode(json(entryPoints = entry(source = quoted))).isLeft shouldBe true
      }
    }

  test("rejects entrypoints absent from classes"):
    BuildState.decode(json(entryPoints = entry(className = "\"Other\""))).isLeft shouldBe true

  test("rejects duplicate and noncanonically sorted entrypoints"):
    val first =
      """{
        |  "className": "MainMain",
        |  "source": "src/main.on",
        |  "line": 1,
        |  "column": 1
        |}""".stripMargin
    val second =
      """{
        |  "className": "demo.Widget",
        |  "source": "src/widget.on",
        |  "line": 3,
        |  "column": 5
        |}""".stripMargin

    BuildState.decode(json(entryPoints = s"[$first, $first]")).isLeft shouldBe true
    BuildState.decode(json(entryPoints = s"[$second, $first]")).isLeft shouldBe true

  test("writes and loads UTF-8 state while reporting I/O failures"):
    val directory = Files.createTempDirectory("onion-build-state")
    val path = directory.resolve("build-state.json")

    BuildState.write(path, canonicalState).value
    Files.readString(path, UTF_8) shouldBe canonicalJson
    BuildState.load(path).value shouldBe canonicalState
    BuildState.load(directory.resolve("missing.json")).isLeft shouldBe true
    BuildState.write(directory, canonicalState).isLeft shouldBe true

  test("validates every recorded qualified class file below the classes root"):
    val classesRoot = Files.createTempDirectory("onion-build-classes")
    Files.write(classesRoot.resolve("MainMain.class"), Array[Byte](1))
    Files.createDirectories(classesRoot.resolve("demo"))
    Files.write(classesRoot.resolve("demo/Widget.class"), Array[Byte](2))

    BuildState.validatesOutputs(canonicalState, classesRoot) shouldBe true
    Files.delete(classesRoot.resolve("demo/Widget.class"))
    BuildState.validatesOutputs(canonicalState, classesRoot) shouldBe false

  test("rejects symlinked class files and package directories"):
    val classesRoot = Files.createTempDirectory("onion-build-classes")
    val external = Files.createTempDirectory("onion-build-classes-external")
    Files.write(classesRoot.resolve("MainMain.class"), Array[Byte](1))
    Files.write(external.resolve("Widget.class"), Array[Byte](2))
    Files.createSymbolicLink(classesRoot.resolve("demo"), external)

    BuildState.validatesOutputs(canonicalState, classesRoot) shouldBe false

    Files.delete(classesRoot.resolve("demo"))
    Files.createDirectory(classesRoot.resolve("demo"))
    Files.createSymbolicLink(
      classesRoot.resolve("demo/Widget.class"),
      external.resolve("Widget.class")
    )
    BuildState.validatesOutputs(canonicalState, classesRoot) shouldBe false
