package onion.compiler.typing

import onion.compiler.{Modifier, SemanticError, Typing}
import onion.compiler.TypedAST._

/**
 * The open-Shape law gate (issue #364).
 *
 * `onion.Shape` was deliberately closed through v0.6/v0.7: the shipped derivations
 * (regex, json, yaml, config) carry their laws with them. Opening the interface to
 * user-written instances must not open a hole in the story, so this pass enforces the
 * deal: a concrete user class implementing `onion.Shape` compiles only when its source
 * file asserts at least one machine-checked `law` or `example` — the L1 round-trip
 * being the canonical one. A shape whose laws nobody states is exactly the ad-hoc
 * parser the boundary vocabulary exists to replace.
 *
 * The check is per source file rather than per class because the lowering puts a
 * top-level `example` on the file class, and because the law for shape `S` naturally
 * mentions `S` from the outside. E0080.
 */
final class ShapeInstanceLawPass(typing: Typing) {

  private val ShapeInterface = "onion.Shape"
  private val LawPrefix = "onion$$law$$"
  private val ExamplePrefix = "onion$$example$$"

  def run(classes: Seq[ClassDefinition]): Unit = {
    val checkedFiles: Set[String] = classes.iterator
      .filter(cd => cd.methods.exists(m =>
        m.name.startsWith(LawPrefix) || m.name.startsWith(ExamplePrefix)))
      .map(cd => Option(cd.getSourceFile).getOrElse(""))
      .toSet

    for (cd <- classes) {
      val concrete = !cd.isInterface && !Modifier.isAbstract(cd.modifier)
      if (concrete && implementsShape(cd)) {
        val file = Option(cd.getSourceFile).getOrElse("")
        if (!checkedFiles.contains(file)) {
          typing.report(SemanticError.SHAPE_INSTANCE_WITHOUT_LAW, cd.location, cd.name)
        }
      }
    }
  }

  /** Whether `cd` implements onion.Shape anywhere in its supertype graph. */
  private def implementsShape(cd: ClassDefinition): Boolean = {
    val seen = scala.collection.mutable.Set[String]()
    def walk(t: ClassType): Boolean = {
      if (t == null || !seen.add(t.name)) return false
      if (t.name == ShapeInterface) return true
      val ifaces = try t.interfaces catch { case _: Throwable => Seq.empty }
      val sup = try t.superClass catch { case _: Throwable => null }
      ifaces.exists(walk) || walk(sup)
    }
    cd.interfaces.exists(walk) || walk(cd.superClass)
  }
}
