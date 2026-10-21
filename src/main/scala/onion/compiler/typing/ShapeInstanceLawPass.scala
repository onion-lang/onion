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
 * Laws are gathered file-wide (the lowering puts a top-level `example` on the file class,
 * and a record's own `law`/`example` lives on the record, not on the shape it exercises),
 * but a law only satisfies the gate for shape `S` when its body actually touches `S` —
 * constructs it, calls/reads a member declared on it, or type-tests against it — not merely
 * by being present somewhere in the same file (#2228). E0080.
 */
final class ShapeInstanceLawPass(typing: Typing) {

  private val ShapeInterface = "onion.Shape"
  private val LawPrefix = "onion$$law$$"
  private val ExamplePrefix = "onion$$example$$"

  def run(classes: Seq[ClassDefinition]): Unit = {
    val lawsByFile: Map[String, Seq[MethodDefinition]] = classes.iterator
      .flatMap(cd => cd.methods.collect {
        case m: MethodDefinition if m.name.startsWith(LawPrefix) || m.name.startsWith(ExamplePrefix) => m
      })
      .toSeq
      .groupBy(m => Option(m.classType).flatMap(ct => Option(fileOf(ct, classes))).getOrElse(""))

    for (cd <- classes) {
      val concrete = !cd.isInterface && !Modifier.isAbstract(cd.modifier)
      if (concrete && implementsShape(cd)) {
        val file = Option(cd.getSourceFile).getOrElse("")
        val lawsInFile = lawsByFile.getOrElse(file, Seq.empty)
        if (!lawsInFile.exists(m => referencesShape(m, cd))) {
          typing.report(SemanticError.SHAPE_INSTANCE_WITHOUT_LAW, cd.location, cd.name)
        }
      }
    }
  }

  /** The source file a law/example method's own class was declared in. */
  private def fileOf(classType: ClassType, classes: Seq[ClassDefinition]): String =
    classes.find(_.name == classType.name).flatMap(cd => Option(cd.getSourceFile)).getOrElse("")

  /**
   * Whether `law`/`example` method `m` exercises `shape`: its body constructs, calls,
   * reads, or type-tests something whose static type is `shape` — not merely sits in the
   * same file. A law for an unrelated type (or no law at all) must not satisfy E0080 for
   * `shape` (issue #2228).
   */
  private def referencesShape(m: MethodDefinition, shape: ClassDefinition): Boolean = {
    val name = shape.name

    def typeIsShape(t: Type): Boolean = t match {
      case null => false
      case n: NullableType => typeIsShape(n.unwrap)
      case ct: ClassType => ct.name == name
      case _ => false
    }

    def term(t: Term): Boolean = t != null && (typeIsShape(t.`type`) || termChildren(t))

    def termChildren(t: Term): Boolean = t match {
      case x: ArrayLength => term(x.target)
      case x: RefArray => term(x.target) || term(x.index)
      case x: NonNullAssert => term(x.target)
      case x: SafeRefArray => term(x.target) || term(x.index)
      case x: SetArray => term(x.target) || term(x.index) || term(x.value)
      case x: Begin => x.terms.exists(term)
      case x: BinaryTerm => term(x.lhs) || term(x.rhs)
      case x: Call => term(x.target) || x.parameters.exists(term) || affiliationIsShape(x.method)
      case x: SafeCall => term(x.target) || x.parameters.exists(term) || affiliationIsShape(x.method)
      case x: SafeStaticExtensionCall => term(x.target) || x.parameters.exists(term)
      case x: CallStatic => x.target.name == name || x.parameters.exists(term) || affiliationIsShape(x.method)
      case x: CallSuper => term(x.target) || x.params.exists(term)
      case x: AsInstanceOf => typeIsShape(x.destination) || term(x.target)
      case x: StatementTerm => stmt(x.statement)
      case x: SynchronizedTerm => term(x.lock) || term(x.body)
      case x: RefField => term(x.target) || affiliationIsShape(x.field)
      case x: SafeFieldAccess => term(x.target) || affiliationIsShape(x.field)
      case x: SetField => term(x.target) || term(x.value) || affiliationIsShape(x.field)
      case x: InstanceOf => typeIsShape(x.checked) || term(x.target)
      case x: ListLiteral => x.elements.exists(term)
      case x: MapLiteral => x.keys.exists(term) || x.values.exists(term)
      case x: SetLocal => term(x.value)
      case x: NewClosure => stmt(x.block)
      case x: NewObject => affiliationIsShape(x.constructor) || x.parameters.exists(term)
      case x: NewArray => x.parameters.exists(term)
      case x: NewArrayWithValues => x.values.exists(term)
      case x: RefStaticField => x.target.name == name || affiliationIsShape(x.field)
      case x: SetStaticField => x.target.name == name || term(x.value)
      case x: UnaryTerm => term(x.operand)
      case _ => false
    }

    def affiliationIsShape(member: MemberRef): Boolean =
      member != null && member.affiliation != null && member.affiliation.name == name

    def stmt(s: ActionStatement): Boolean = s != null && (s match {
      case x: StatementBlock => x.statements.exists(stmt)
      case x: IfStatement => term(x.getCondition) || stmt(x.getThenStatement) || stmt(x.getElseStatement)
      case x: ConditionalLoop => term(x.condition) || stmt(x.stmt) || stmt(x.update)
      case x: ExpressionActionStatement => term(x.term)
      case x: Return => term(x.term)
      case x: Throw => term(x.term)
      case x: Synchronized => term(x.term) || stmt(x.statement)
      case x: Try =>
        x.resources.exists { case (_, t) => term(t) } ||
          stmt(x.tryStatement) || x.catchStatements.exists(stmt) || stmt(x.finallyStatement)
      case _ => false
    })

    Option(m.getBlock).exists(stmt)
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
