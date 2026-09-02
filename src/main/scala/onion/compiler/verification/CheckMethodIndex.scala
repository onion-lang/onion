package onion.compiler.verification

import org.objectweb.asm.{ClassReader, ClassVisitor, Label, MethodVisitor, Opcodes}

import scala.collection.mutable

/**
 * Where a synthesized `law` / `example` method came from, read back out of the class file.
 *
 * The clause's `Location` is captured during rewriting and then lost: by the time
 * LawCheckPhase runs, all it holds is a `CompiledClass` (name, path, bytes). Rather than
 * thread a side table through typing, optimization and codegen just to reach this phase,
 * the position is recovered from what the backend already emits — the SourceFile
 * attribute and the line-number table.
 *
 * Reading the bytes also answers "does this class carry checks at all" without loading
 * it, which is what lets a class-loading failure be reported instead of silently taking
 * every law in that class with it.
 */
private[verification] final case class CheckMethodIndex(
  sourceFile: Option[String],
  firstLines: Map[String, Int]
) {
  /** The declared line of a check method, if the backend emitted one for it. */
  def lineOf(methodName: String): Option[Int] = firstLines.get(methodName)

  /** Whether this class declares any `law` / `example` method. */
  def hasChecks: Boolean = firstLines.nonEmpty
}

private[verification] object CheckMethodIndex {

  private val ExamplePrefix = "onion$$example$$"
  private val LawPrefix = "onion$$law$$"

  private def isCheckMethod(name: String): Boolean =
    name.startsWith(ExamplePrefix) || name.startsWith(LawPrefix)

  val empty: CheckMethodIndex = CheckMethodIndex(None, Map.empty)

  /**
   * Never throws: a class file this phase cannot parse must not fail the build on its
   * own, it just yields no position and the diagnostic degrades to what it printed before.
   */
  private val LawNeedle = "onion$$law$$".getBytes("UTF-8")
  private val ExampleNeedle = "onion$$example$$".getBytes("UTF-8")

  /**
   * Whether the class can carry a check at all: a check is a method whose name starts with
   * one of the two prefixes, and a method name is a UTF8 constant-pool entry, so a class
   * without either byte sequence has none. A byte scan is far cheaper than the ClassReader
   * pass `read` makes, and nearly every class fails it.
   */
  def mayHaveChecks(bytes: Array[Byte]): Boolean =
    contains(bytes, LawNeedle) || contains(bytes, ExampleNeedle)

  private def contains(hay: Array[Byte], needle: Array[Byte]): Boolean = {
    val first = needle(0)
    val last = hay.length - needle.length
    var i = 0
    while (i <= last) {
      if (hay(i) == first) {
        var j = 1
        while (j < needle.length && hay(i + j) == needle(j)) j += 1
        if (j == needle.length) return true
      }
      i += 1
    }
    false
  }

  def read(bytes: Array[Byte]): CheckMethodIndex =
    try {
      var source: Option[String] = None
      val lines = mutable.Map.empty[String, Int]

      new ClassReader(bytes).accept(
        new ClassVisitor(Opcodes.ASM9) {
          override def visitSource(file: String, debug: String): Unit =
            source = Option(file).filter(_.nonEmpty)

          override def visitMethod(
            access: Int, name: String, descriptor: String,
            signature: String, exceptions: Array[String]
          ): MethodVisitor =
            if (!isCheckMethod(name)) null
            else
              new MethodVisitor(Opcodes.ASM9) {
                override def visitLineNumber(line: Int, start: Label): Unit =
                  // The first entry is the clause's own line; later ones belong to
                  // sub-expressions of its body.
                  if (!lines.contains(name)) lines(name) = line
              }
        },
        ClassReader.SKIP_FRAMES
      )

      CheckMethodIndex(source, lines.toMap)
    } catch {
      case _: Throwable => empty
    }
}
