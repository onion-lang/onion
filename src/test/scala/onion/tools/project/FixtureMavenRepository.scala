package onion.tools.project

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.jar.{JarEntry, JarOutputStream}

import org.objectweb.asm.{ClassWriter, Opcodes}

/**
 * Builds a `file://` Maven repository containing two artifacts, one depending on the other.
 *
 * Tests that need a real resolvable coordinate publish one here rather than naming
 * something on Maven Central, so they are deterministic, run with the network down, and do
 * not rot when a public artifact is deleted or re-released.
 *
 *   - `core:1.0.0` — `Core.message()` returns `"greeting from core"`
 *   - `greeter:1.0.0` — `Greeter.greet()` calls `Core.message()`, and its POM declares the
 *     dependency. Anything that resolves `greeter` without its transitive will compile and
 *     then fail to link, which is what makes the pair worth having.
 */
object FixtureMavenRepository:

  val Group = "com.example.oniontest"
  val Artifact = "greeter"
  val Version = "1.0.0"

  /** `group:artifact` of the artifact a test should depend on. */
  val Coordinate: Dependency = Dependency(Group, Artifact, Version)

  val Greeting = "greeting from core"

  /** Publishes the fixture artifacts into a fresh temporary repository and returns its root. */
  def publish(): Path =
    val repository = Files.createTempDirectory("onion-maven-repository")
    val prefix = Group.replace('.', '/')

    publishArtifact(repository, "core", s"$prefix/Core", classBytes(
      internalName = s"$prefix/Core",
      method = "message",
      body = _.visitLdcInsn(Greeting)
    ), dependsOn = None)

    publishArtifact(repository, Artifact, s"$prefix/Greeter", classBytes(
      internalName = s"$prefix/Greeter",
      method = "greet",
      body = _.visitMethodInsn(
        Opcodes.INVOKESTATIC, s"$prefix/Core", "message", "()Ljava/lang/String;", false)
    ), dependsOn = Some("core"))

    repository

  /**
   * A graph whose answer depends on ''where'' a dependency sits in it — the shape of
   * anthropic-java, whose `<dependencyManagement>` holds kotlin-reflect at 1.9.0 while
   * jackson-module-kotlin, one level down, asks for 1.9.25.
   *
   *   - `app:1.0.0` manages `lib` at 1.0.0, and depends on `mid:1.0.0` and on `lib` (no
   *     version; the management supplies it)
   *   - `mid:1.0.0` depends on `lib:2.0.0`
   *   - `lib:1.0.0` and `lib:2.0.0` both exist, with `Lib.version()` saying which
   *
   * Resolving `app` from the root lets app's management reach mid's request: `lib:1.0.0`.
   * Resolving the same three coordinates as a flat list of roots takes mid out from under
   * app, and the highest request wins: `lib:2.0.0`.
   */
  object Managed:
    val App: Dependency = Dependency(Group, "app", "1.0.0")
    val Mid: Dependency = Dependency(Group, "mid", "1.0.0")
    val Lib: Dependency = Dependency(Group, "lib", "1.0.0")
    val NewerLib: Dependency = Dependency(Group, "lib", "2.0.0")

    def publish(): Path =
      val repository = Files.createTempDirectory("onion-maven-managed")
      val prefix = Group.replace('.', '/')
      for version <- Seq(Lib.version, NewerLib.version) do
        write(repository, Dependency(Group, "lib", version), s"$prefix/Lib", classBytes(
          internalName = s"$prefix/Lib",
          method = "version",
          body = _.visitLdcInsn(version)
        ), extra = "")
      write(repository, Mid, s"$prefix/Mid", classBytes(
        internalName = s"$prefix/Mid", method = "name", body = _.visitLdcInsn("mid")),
        extra = dependencies(s"""    <dependency>
           |      <groupId>$Group</groupId>
           |      <artifactId>lib</artifactId>
           |      <version>${NewerLib.version}</version>
           |    </dependency>
           |""".stripMargin))
      write(repository, App, s"$prefix/App", classBytes(
        internalName = s"$prefix/App", method = "name", body = _.visitLdcInsn("app")),
        extra = s"""  <dependencyManagement>
           |    <dependencies>
           |      <dependency>
           |        <groupId>$Group</groupId>
           |        <artifactId>lib</artifactId>
           |        <version>${Lib.version}</version>
           |      </dependency>
           |    </dependencies>
           |  </dependencyManagement>
           |""".stripMargin + dependencies(s"""    <dependency>
           |      <groupId>$Group</groupId>
           |      <artifactId>mid</artifactId>
           |      <version>${Mid.version}</version>
           |    </dependency>
           |    <dependency>
           |      <groupId>$Group</groupId>
           |      <artifactId>lib</artifactId>
           |    </dependency>
           |""".stripMargin))
      repository

    def manifestStanzas(repository: Path): String =
      s"""[[repositories]]
         |url = "${repository.toUri.toString}"
         |
         |[dependencies]
         |"$Group:app" = "${App.version}"
         |""".stripMargin

    private def dependencies(body: String): String =
      s"  <dependencies>\n$body  </dependencies>\n"

    private def write(
      repository: Path,
      coordinate: Dependency,
      internalName: String,
      classFile: Array[Byte],
      extra: String
    ): Unit =
      val directory = repository.resolve(coordinate.group.replace('.', '/'))
        .resolve(coordinate.artifact).resolve(coordinate.version)
      Files.createDirectories(directory)
      val base = s"${coordinate.artifact}-${coordinate.version}"
      val stream = JarOutputStream(Files.newOutputStream(directory.resolve(s"$base.jar")))
      try
        stream.putNextEntry(JarEntry(s"$internalName.class"))
        stream.write(classFile)
        stream.closeEntry()
      finally stream.close()
      Files.writeString(
        directory.resolve(s"$base.pom"),
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<project xmlns="http://maven.apache.org/POM/4.0.0">
           |  <modelVersion>4.0.0</modelVersion>
           |  <groupId>${coordinate.group}</groupId>
           |  <artifactId>${coordinate.artifact}</artifactId>
           |  <version>${coordinate.version}</version>
           |  <packaging>jar</packaging>
           |$extra</project>
           |""".stripMargin,
        UTF_8
      )

  /** The `[[repositories]]` / `[dependencies]` stanzas naming this repository. */
  def manifestStanzas(repository: Path): String =
    s"""[[repositories]]
       |url = "${repository.toUri.toString}"
       |
       |[dependencies]
       |"$Group:$Artifact" = "$Version"
       |""".stripMargin

  /** `public final class <name> { public static String <method>() { … } }` */
  private def classBytes(
    internalName: String,
    method: String,
    body: org.objectweb.asm.MethodVisitor => Unit
  ): Array[Byte] =
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
      internalName, null, "java/lang/Object", null)
    val visitor = writer.visitMethod(
      Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, "()Ljava/lang/String;", null, null)
    visitor.visitCode()
    body(visitor)
    visitor.visitInsn(Opcodes.ARETURN)
    visitor.visitMaxs(0, 0)
    visitor.visitEnd()
    writer.visitEnd()
    writer.toByteArray

  private def publishArtifact(
    repository: Path,
    artifact: String,
    internalName: String,
    classFile: Array[Byte],
    dependsOn: Option[String]
  ): Unit =
    val directory = repository
      .resolve(Group.replace('.', '/')).resolve(artifact).resolve(Version)
    Files.createDirectories(directory)

    val stream = JarOutputStream(
      Files.newOutputStream(directory.resolve(s"$artifact-$Version.jar")))
    try
      stream.putNextEntry(JarEntry(s"$internalName.class"))
      stream.write(classFile)
      stream.closeEntry()
    finally stream.close()

    val dependencyBlock = dependsOn.map { name =>
      s"""  <dependencies>
         |    <dependency>
         |      <groupId>$Group</groupId>
         |      <artifactId>$name</artifactId>
         |      <version>$Version</version>
         |    </dependency>
         |  </dependencies>
         |""".stripMargin
    }.getOrElse("")

    Files.writeString(
      directory.resolve(s"$artifact-$Version.pom"),
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<project xmlns="http://maven.apache.org/POM/4.0.0">
         |  <modelVersion>4.0.0</modelVersion>
         |  <groupId>$Group</groupId>
         |  <artifactId>$artifact</artifactId>
         |  <version>$Version</version>
         |  <packaging>jar</packaging>
         |$dependencyBlock</project>
         |""".stripMargin,
      UTF_8
    )
