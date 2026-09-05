package onion.compiler.rewrite

import onion.compiler.{AST, CompileError}
import onion.compiler.exceptions.CompilationException

/** Declaration construction only; the caller owns selection and body rewriting. */
private[compiler] object AdtEnumLowering {
  /**
   * Desugars a Scala-3-style ADT (`case`) enum into already-working constructs —
   * NO new codegen. Given
   *
   *   enum Shape {
   *     case Circle(radius: Double)
   *     case Square(side: Double)
   *     case Origin
   *   public:
   *     def area(): Double = select this { ... }
   *   }
   *
   * we generate:
   *   - `sealed interface Shape { <the enum body-section methods, verbatim, as
   *     default methods> }`
   *   - `record Circle(radius: Double) conforms Shape`  (one per product case)
   *   - `record Square(side: Double) conforms Shape`
   *   - `record Origin() conforms Shape`               (zero-field record per singleton)
   *
   * A `sealed interface` + `record X conforms Shape` + a `select` over `case x is X:`
   * gives exhaustiveness (E0042) for free. A singleton case is constructed as
   * `new Origin()` (first-cut form; see the singleton note below).
   */
  def lower(enumDecl: AST.EnumDeclaration): List[AST.Toplevel] = {
    val loc = enumDecl.location
    val enumName = enumDecl.name
    // Scope guard (first version): ADT enums have PER-CASE fields only; enum-level
    // shared params mixed with `case` cases is a separate feature.
    if (enumDecl.params.nonEmpty) {
      throw new CompilationException(Seq(CompileError("", loc,
        s"enum $enumName mixes shared parameters with `case` cases; ADT enums carry per-case fields only")))
    }

    // A generic ADT enum (`enum Opt[T] { case Some(value: T); case Nothing }`)
    // carries its parameters onto every generated declaration: the sealed
    // interface becomes `Opt[T]`, each case record becomes `Some[T]`, and each
    // case's implemented supertype becomes `Opt[T]` rather than a raw `Opt`
    // (#311). Without the applied supertype the record would implement the raw
    // type, which E0066 forbids.
    val typeParams = enumDecl.typeParameters
    val enumTypeNode =
      if (typeParams.isEmpty) AST.TypeNode(loc, AST.ReferenceType(enumName, false), false)
      else
        AST.TypeNode(
          loc,
          AST.ParameterizedType(
            AST.ReferenceType(enumName, false),
            typeParams.map(tp => AST.ReferenceType(tp.name, false))
          ),
          false
        )

    // Interface methods: the enum body sections' methods become interface members
    // verbatim (bodies preserved -> default methods; bodiless -> abstract).
    val bodyMethods: List[AST.MethodDeclaration] = enumDecl.sections.flatMap(_.members).collect {
      case m: AST.MethodDeclaration => m
    }

    // Singleton cases (`case Origin`) desugar to a ZERO-FIELD record `record Origin()`,
    // constructed as `new Origin()` and matched via `case o is Origin:`. A dedicated
    // `Name::Origin` static VALUE accessor is intentionally not synthesized: the
    // singleton record type and any same-named accessor would collide under `::`
    // resolution (the name `Origin` already denotes the record type), so `new Origin()`
    // is the first-cut construction form (see the class notes / final report).
    val interfaceDecl = AST.InterfaceDeclaration(
      loc, enumDecl.modifiers | AST.M_SEALED, enumName, Nil, bodyMethods, typeParams
    )

    // One record per case; singleton -> zero-field record. All implement the enum.
    val recordDecls: List[AST.RecordDeclaration] = enumDecl.constants.filter(_.isCase).map { c =>
      val fields = c.caseFields.getOrElse(Nil)
      AST.recordDeclaration(
        loc, AST.M_PUBLIC, c.name, typeParams, fields, List(enumTypeNode),
        null, Nil, Nil, Nil, Nil, Nil
      )
    }

    interfaceDecl :: recordDecls
  }
}
