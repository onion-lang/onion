package onion.compiler

/**
 * The scalar component types a boundary derivation can read out of text, and everything
 * each of them needs in order to be read.
 *
 * Onion has four independent mechanisms for turning external text into typed values --
 * `record ... from re"..."`, `derive!(Json, Yaml)`, auto-CLI, and the resource literals --
 * and each of them used to enumerate the same eight types separately:
 *
 *   - `Rewriting.cliKindOf`            AST type   -> tag
 *   - `Rewriting.convertCliValue`      tag        -> onion.Cli.parseX
 *   - `Rewriting.convertCapturedValue` tag        -> java.lang.X.parseX
 *   - `Rewriting.jsonGetterOf`         tag        -> Json.getX
 *   - `TypingOutlinePass.isFromDerivableType`     Type -> Boolean
 *
 * Adding `java.time.LocalDate` therefore meant five edits that had to agree, in two
 * languages, with nothing checking that they did. That is not a missing feature; it is a
 * missing abstraction (issue #349).
 *
 * One entry per type, here. The four derivations read the columns they need.
 */
private[compiler] final case class ScalarConversion(
  /** The name used by the derivations to refer to this kind. */
  tag: String,
  /** Fully-qualified wrapper class whose static parse method reads the text. */
  wrapper: String,
  /** That wrapper's parse method. Throws IllegalArgumentException on bad input. */
  parseMethod: String,
  /** The onion.Cli helper, which reports and exits rather than throwing. */
  cliParseMethod: String,
  /** The onion.Json static getter for this component type. */
  jsonGetter: String
)

private[compiler] object ScalarConversions {

  /**
   * `Boolean` is the one type whose JDK parser cannot fail: `Boolean.parseBoolean` maps
   * everything that is not "true" to `false`, so a malformed field silently became a
   * valid-looking value rather than a parse failure. It routes through `onion.Scalars`
   * instead, which rejects anything that is not a boolean spelling.
   */
  val all: List[ScalarConversion] = List(
    ScalarConversion("String",  "",                    "",             "",             "getString"),
    ScalarConversion("Int",     "java.lang.Integer",   "parseInt",     "parseInt",     "getInt"),
    ScalarConversion("Long",    "java.lang.Long",      "parseLong",    "parseLong",    "getLong"),
    ScalarConversion("Double",  "java.lang.Double",    "parseDouble",  "parseDouble",  "getDouble"),
    ScalarConversion("Float",   "java.lang.Float",     "parseFloat",   "parseFloat",   "getFloat"),
    ScalarConversion("Boolean", "onion.Scalars",       "toBoolean",    "parseBoolean", "getBoolean"),
    ScalarConversion("Short",   "java.lang.Short",     "parseShort",   "parseShort",   "getShort"),
    ScalarConversion("Byte",    "java.lang.Byte",      "parseByte",    "parseByte",    "getByte")
  )

  private val byTagIndex: Map[String, ScalarConversion] = all.map(k => k.tag -> k).toMap

  def byTag(tag: String): Option[ScalarConversion] = byTagIndex.get(tag)

  /** A String needs no conversion: a captured group and a CLI argument are already one. */
  def isIdentity(kind: ScalarConversion): Boolean = kind.wrapper.isEmpty

  /** The kind for a source-level type, or None when the type is not a supported scalar. */
  def ofAst(typeRef: AST.TypeNode): Option[ScalarConversion] = {
    if (typeRef == null) return None
    val tag = typeRef.desc match {
      case AST.PrimitiveType(AST.KInt)     => "Int"
      case AST.PrimitiveType(AST.KLong)    => "Long"
      case AST.PrimitiveType(AST.KDouble)  => "Double"
      case AST.PrimitiveType(AST.KFloat)   => "Float"
      case AST.PrimitiveType(AST.KBoolean) => "Boolean"
      case AST.PrimitiveType(AST.KShort)   => "Short"
      case AST.PrimitiveType(AST.KByte)    => "Byte"
      case AST.ReferenceType("String", _)           => "String"
      case AST.ReferenceType("java.lang.String", _) => "String"
      case _ => return None
    }
    byTag(tag)
  }

  /** The same question asked of a checked type, for the typing pass. */
  def ofType(tp: TypedAST.Type): Option[ScalarConversion] = tp match {
    case TypedAST.BasicType.INT     => byTag("Int")
    case TypedAST.BasicType.LONG    => byTag("Long")
    case TypedAST.BasicType.DOUBLE  => byTag("Double")
    case TypedAST.BasicType.FLOAT   => byTag("Float")
    case TypedAST.BasicType.BOOLEAN => byTag("Boolean")
    case TypedAST.BasicType.SHORT   => byTag("Short")
    case TypedAST.BasicType.BYTE    => byTag("Byte")
    case ct: TypedAST.ClassType if ct.name == "java.lang.String" => byTag("String")
    case _ => None
  }

  /** Whether a checked type can be read out of text at all. */
  def isDerivable(tp: TypedAST.Type): Boolean = ofType(tp).isDefined

  /** The supported spellings, for a diagnostic that has to list them. */
  def supportedNames: String = all.map(_.tag).mkString(", ")
}
