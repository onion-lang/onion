package onion.compiler

/**
 * Onion's primitive type keywords, capitalized (`void` is the one exception --
 * it stays lowercase in the grammar, so it is not a candidate for either use below).
 *
 * One list, shared by two independent "did you mean a primitive?" diagnostics that
 * used to enumerate the same eight names separately:
 *   - `typing.NameResolver` -- class-not-found suggestions, so a Java/Scala-style
 *     lowercase spelling (`int`, `boolean`, ...), which parses as an ordinary
 *     unresolved reference type, gets "did you mean `Int`?" instead of a bare
 *     "check spelling or add import".
 *   - `parser.SyntaxHintClassifier` -- the `hint.primitive_dot_static` syntax hint
 *     for `Int.parseInt(...)`-style mistakes (a primitive's static members are
 *     accessed with `::`, not `.`).
 */
private[compiler] object PrimitiveTypeNames {

  val all: Seq[String] = Seq("Int", "Long", "Short", "Byte", "Char", "Float", "Double", "Boolean")
}
