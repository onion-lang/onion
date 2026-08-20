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
