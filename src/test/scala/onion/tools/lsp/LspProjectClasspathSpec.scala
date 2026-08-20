package onion.tools.lsp

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import onion.tools.project.FixtureMavenRepository
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * The editor has to validate against the same classpath the build compiles against.
 *
 * It did not: `validationConfig` was a constant with `Seq(".")`, so a project's own
 * `target/classes` was invisible (a symbol defined in a sibling file read as undefined) and
 * — once `[dependencies]` existed — every type coming from a jar was underlined in an
 * editor while `onion build` succeeded. Diagnostics that disagree with the build are worse
 * than no diagnostics, because they train people to ignore them.
 */
class LspProjectClasspathSpec extends AnyFunSuite with Matchers:

  test("a standalone file, outside any project, keeps the classpath it always had"):
    val loose = Files.createTempDirectory("onion-lsp-loose").resolve("scratch.on")
    Files.writeString(loose, "IO::println(\"hi\")\n", UTF_8)

    LspProjectClasspath.invalidate()
    LspProjectClasspath.forDocument(Some(loose)) shouldBe Seq(".")

  test("a URI that is not a file has no project to find"):
    LspProjectClasspath.invalidate()
    LspProjectClasspath.forDocument(None) shouldBe Seq(".")

  test("a project file validates against the project's own build output"):
    val project = fixture(dependencies = None)

    LspProjectClasspath.invalidate()
    val classpath = LspProjectClasspath.forDocument(Some(project.resolve("src/main.on")))

    classpath shouldBe Seq(project.resolve("target").resolve("classes").toString)

  test("a project file validates against its resolved dependencies too"):
    val project = fixture(dependencies = Some(FixtureMavenRepository.publish()))

    LspProjectClasspath.invalidate()
    val classpath = LspProjectClasspath.forDocument(Some(project.resolve("src/main.on")))

    classpath.head shouldBe project.resolve("target").resolve("classes").toString
    // greeter and its transitive core, the same two the build sees.
    classpath.tail should have size 2
    classpath.tail.foreach(_ should endWith(".jar"))

  test("a malformed manifest degrades to the project's own classes rather than failing"):
    // A half-typed manifest is a normal editing state. Refusing to validate the file the
    // author is looking at because of it would be the wrong trade; the build still reports it.
    val project = fixture(dependencies = None)
    Files.writeString(project.resolve("onion.toml"), "[package]\nname = \n", UTF_8)

    LspProjectClasspath.invalidate()
    LspProjectClasspath.forDocument(Some(project.resolve("src/main.on"))) shouldBe
      Seq(project.resolve("target").resolve("classes").toString)

  test("an edited manifest is picked up rather than served from the cache"):
    val project = fixture(dependencies = None)
    LspProjectClasspath.invalidate()
    LspProjectClasspath.forDocument(Some(project.resolve("src/main.on"))) should have size 1

    val manifest = project.resolve("onion.toml")
    Files.writeString(
      manifest,
      Files.readString(manifest, UTF_8) +
        "\n" + FixtureMavenRepository.manifestStanzas(FixtureMavenRepository.publish()),
      UTF_8
    )
    // The stamp is size plus modification time, and the size definitely changed here.
    LspProjectClasspath.forDocument(Some(project.resolve("src/main.on"))) should have size 3

  private def fixture(dependencies: Option[Path]): Path =
    val root = Files.createTempDirectory("onion-lsp-project").toRealPath()
    Files.writeString(
      root.resolve("onion.toml"),
      s"""[package]
         |name = "demo"
         |version = "1.0.0"
         |""".stripMargin +
        dependencies.map("\n" + FixtureMavenRepository.manifestStanzas(_)).getOrElse(""),
      UTF_8
    )
    val main = root.resolve("src").resolve("main.on")
    Files.createDirectories(main.getParent)
    Files.writeString(main, "IO::println(\"hi\")\n", UTF_8)
    root
