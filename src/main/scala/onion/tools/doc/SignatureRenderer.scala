package onion.tools.doc

import onion.compiler.AST

/**
 * Formats AST declarations back into Onion-like source signatures for display
 * in the generated documentation. Purely presentational — it never affects
 * compilation.
 */
object SignatureRenderer {

  /** Render a type name from an [[AST.TypeNode]]. */
  def renderType(node: AST.TypeNode): String =
    if (node == null) "void" else renderDesc(node.desc)

  private def renderDesc(desc: AST.TypeDescriptor): String = desc match {
    case AST.PrimitiveType(kind)         => kind.name
    case AST.ReferenceType(name, _)      => simpleName(name)
    case AST.ArrayType(component)        => s"${renderDesc(component)}[]"
    case AST.NullableType(inner)         => s"${renderDesc(inner)}?"
    case AST.ParameterizedType(c, params) =>
      s"${renderDesc(c)}[${params.map(renderDesc).mkString(", ")}]"
    case AST.FunctionType(params, result) =>
      s"(${params.map(renderDesc).mkString(", ")}) -> ${renderDesc(result)}"
    case AST.WildcardType(ub, lb) =>
      (ub, lb) match {
        case (Some(u), None) => s"? extends ${renderDesc(u)}"
        case (None, Some(l)) => s"? super ${renderDesc(l)}"
        case _               => "?"
      }
    case other => other.toString
  }

  /** Strip a package qualifier for a compact display name. */
  private def simpleName(name: String): String = {
    val dot = name.lastIndexOf('.')
    if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else name
  }

  private def renderArg(a: AST.Argument): String = {
    val vararg = if (a.isVararg) "..." else ""
    s"${a.name}: ${renderType(a.typeRef)}$vararg"
  }

  private def renderTypeParams(tps: List[AST.TypeParameter]): String =
    if (tps.isEmpty) "" else tps.map(_.name).mkString("[", ", ", "]")

  private def visibility(modifiers: Int): String = {
    if (AST.hasModifier(modifiers, AST.M_PRIVATE)) "private "
    else if (AST.hasModifier(modifiers, AST.M_PROTECTED)) "protected "
    else if (AST.hasModifier(modifiers, AST.M_PUBLIC)) "public "
    else ""
  }

  private def staticKw(modifiers: Int): String =
    if (AST.hasModifier(modifiers, AST.M_STATIC)) "static " else ""

  private def overrideKw(modifiers: Int): String =
    if (AST.hasModifier(modifiers, AST.M_OVERRIDE)) "override " else ""

  // ---- Members --------------------------------------------------------------

  def renderMethod(m: AST.MethodDeclaration): String = {
    val args = m.args.map(renderArg).mkString(", ")
    val tp = renderTypeParams(m.typeParameters)
    s"${visibility(m.modifiers)}${staticKw(m.modifiers)}${overrideKw(m.modifiers)}def ${m.name}$tp($args): ${renderType(m.returnType)}"
  }

  def renderField(f: AST.FieldDeclaration): String = {
    val kw = if (AST.hasModifier(f.modifiers, AST.M_FINAL)) "val" else "var"
    s"${visibility(f.modifiers)}${staticKw(f.modifiers)}$kw ${f.name}: ${renderType(f.typeRef)}"
  }

  def renderDelegatedField(d: AST.DelegatedFieldDeclaration): String = {
    val kw = if (AST.hasModifier(d.modifiers, AST.M_FINAL)) "val" else "var"
    s"${visibility(d.modifiers)}forward $kw ${d.name}: ${renderType(d.typeRef)}"
  }

  def renderConstructor(c: AST.ConstructorDeclaration): String = {
    val args = c.args.map(renderArg).mkString(", ")
    s"${visibility(c.modifiers)}def this($args)"
  }

  // ---- Types ----------------------------------------------------------------

  def renderClass(c: AST.ClassDeclaration): String = {
    val tp = renderTypeParams(c.typeParameters)
    val sup =
      if (c.superClass == null) "" else s" : ${renderType(c.superClass)}"
    val ifaces =
      if (c.superInterfaces.isEmpty) ""
      else s" <: ${c.superInterfaces.map(renderType).mkString(", ")}"
    s"class ${c.name}$tp$sup$ifaces"
  }

  def renderInterface(i: AST.InterfaceDeclaration): String = {
    val tp = renderTypeParams(i.typeParameters)
    val ifaces =
      if (i.superInterfaces.isEmpty) ""
      else s" <: ${i.superInterfaces.map(renderType).mkString(", ")}"
    s"interface ${i.name}$tp$ifaces"
  }

  def renderRecord(r: AST.RecordDeclaration): String = {
    val tp = renderTypeParams(r.typeParameters)
    val args = r.args.map(renderArg).mkString(", ")
    val ifaces =
      if (r.superInterfaces.isEmpty) ""
      else s" <: ${r.superInterfaces.map(renderType).mkString(", ")}"
    s"record ${r.name}$tp($args)$ifaces"
  }

  def renderEnum(e: AST.EnumDeclaration): String = {
    val params =
      if (e.params.isEmpty) ""
      else s"(${e.params.map(renderArg).mkString(", ")})"
    val constants = e.constants.map(_.name).mkString(", ")
    s"enum ${e.name}$params { $constants }"
  }
}
