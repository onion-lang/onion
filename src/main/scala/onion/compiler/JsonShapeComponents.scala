package onion.compiler

/**
 * The component types a `shape name = json` clause reads beyond the scalars every other
 * boundary shares (`ScalarConversions`): lists, nested records and absent keys.
 *
 * A component is described to the runtime (`onion.Shapes.json`, see `MappedShape`) by a
 * tag in a small grammar:
 *
 * {{{
 *   tag     ::= element | "List[" element "]"   followed by an optional "?"
 *   element ::= <scalar tag> | "Nested"
 * }}}
 *
 * `Nested` is a record that itself declares a json shape; its shape is reached through the
 * hidden `onion$$jsonShape()` every such record gets (see `Rewriting`), so the outer record
 * needs neither the inner record's shape name nor its declaration order.
 *
 * Rewriting classifies the component's *written* type (it runs before names resolve), and
 * typing classifies the *resolved* one; a component is accepted only when the two agree,
 * so a type alias or a shadowing class named `List` can never make the runtime read
 * something other than what the declaration says (it is E0061 instead).
 */
private[compiler] object JsonShapeComponents {

  /** The hidden static method a record with a json shape carries: its first json shape. */
  val HiddenShapeMethod = "onion$$jsonShape"

  val NestedTag = "Nested"

  /** Whether `format` names the json document format (the only one that reads structure). */
  def isJsonFormat(format: String): Boolean = format.equalsIgnoreCase("json")

  /** The first `shape name = json` clause of a record, the one a nesting record reads it by. */
  def firstJsonClause(declaration: AST.RecordDeclaration): Option[AST.ShapeClause] =
    declaration.shapes.find(_.source match {
      case AST.FormatSource(f) => isJsonFormat(f)
      case _                   => false
    })

  /** Whether every shape clause of the record is a json one: only then may components go beyond scalars. */
  def allJson(declaration: AST.RecordDeclaration): Boolean =
    declaration.shapes.nonEmpty && declaration.shapes.forall(_.source match {
      case AST.FormatSource(f) => isJsonFormat(f)
      case _                   => false
    })

  // ------------------------------------------------------------------ written types

  /** The tag for a component's written type, or None when a json shape cannot read it. */
  def tagOfAst(typeRef: AST.TypeNode): Option[String] =
    if (typeRef == null) None else tagOfDesc(typeRef.desc)

  private def tagOfDesc(desc: AST.TypeDescriptor): Option[String] = desc match {
    case AST.NullableType(inner) => nonNullTagOfDesc(inner).map(_ + "?")
    case d                       => nonNullTagOfDesc(d)
  }

  private def nonNullTagOfDesc(desc: AST.TypeDescriptor): Option[String] = desc match {
    case AST.ParameterizedType(AST.ReferenceType(n, _), List(elem)) if isListName(n) =>
      elementTagOfDesc(elem).map(e => s"List[$e]")
    case d => elementTagOfDesc(d)
  }

  private def elementTagOfDesc(desc: AST.TypeDescriptor): Option[String] = desc match {
    case AST.NullableType(_) => None // List[String?] is not supported: absence is per key
    case d =>
      ScalarConversions.ofAst(AST.TypeNode(null, d, false)).map(_.tag).orElse(d match {
        case AST.ReferenceType(n, _) if !isListName(n) && !isScalarBoxName(n) => Some(NestedTag)
        case _ => None
      })
  }

  /** The written type of the record a `Nested` component reads, when there is one. */
  def nestedRecordOfAst(typeRef: AST.TypeNode): Option[AST.TypeDescriptor] = {
    def strip(d: AST.TypeDescriptor): AST.TypeDescriptor = d match {
      case AST.NullableType(inner) => strip(inner)
      case AST.ParameterizedType(AST.ReferenceType(n, _), List(elem)) if isListName(n) => strip(elem)
      case other => other
    }
    tagOfAst(typeRef).filter(_.contains(NestedTag)).map(_ => strip(typeRef.desc))
  }

  private def isListName(n: String): Boolean = n == "List" || n == "java.util.List"

  // Boxed spellings are not scalars to ScalarConversions and are not records either.
  private def isScalarBoxName(n: String): Boolean = n.startsWith("java.lang.")

  // ------------------------------------------------------------------ resolved types

  /**
   * The tag for a component's resolved type, or None when a json shape cannot read it.
   * `isJsonRecord` answers whether a class is a record that declares a json shape.
   */
  def tagOfType(tp: TypedAST.Type, isJsonRecord: TypedAST.ClassType => Boolean): Option[String] = tp match {
    case n: TypedAST.NullableType => nonNullTagOfType(n.unwrap, isJsonRecord).map(_ + "?")
    case t                        => nonNullTagOfType(t, isJsonRecord)
  }

  private def nonNullTagOfType(tp: TypedAST.Type, isJsonRecord: TypedAST.ClassType => Boolean): Option[String] = tp match {
    case ap: TypedAST.AppliedClassType if ap.raw.name == "java.util.List" && ap.typeArguments.length == 1 =>
      elementTagOfType(ap.typeArguments(0), isJsonRecord).map(e => s"List[$e]")
    case t => elementTagOfType(t, isJsonRecord)
  }

  private def elementTagOfType(tp: TypedAST.Type, isJsonRecord: TypedAST.ClassType => Boolean): Option[String] =
    ScalarConversions.ofType(tp).map(_.tag).orElse(boxedTag(tp)).orElse(tp match {
      case _: TypedAST.AppliedClassType => None
      case ct: TypedAST.ClassType if isJsonRecord(ct) => Some(NestedTag)
      case _ => None
    })

  /** `List[Int]`'s element resolves to a boxed Integer; read it as the scalar it boxes. */
  private def boxedTag(tp: TypedAST.Type): Option[String] = tp match {
    case ct: TypedAST.ClassType => ct.name match {
      case "java.lang.Integer" => Some("Int")
      case "java.lang.Long"    => Some("Long")
      case "java.lang.Double"  => Some("Double")
      case "java.lang.Float"   => Some("Float")
      case "java.lang.Boolean" => Some("Boolean")
      case "java.lang.Short"   => Some("Short")
      case "java.lang.Byte"    => Some("Byte")
      case _                   => None
    }
    case _ => None
  }

  /** Whether a tag is an ordinary non-null scalar, which every shape format can read. */
  def isPlainScalar(tag: String): Boolean = ScalarConversions.byTag(tag).isDefined

  /** The component types a json shape reads, for E0061's "Supported component types". */
  def supportedDescription: String =
    onion.compiler.toolbox.Message("error.semantic.jsonShapeComponentTypes", ScalarConversions.supportedNames)
}
