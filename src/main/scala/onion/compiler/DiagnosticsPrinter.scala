package onion.compiler

import java.io.PrintStream

object DiagnosticsPrinter {
  def dumpAst(units: Seq[AST.CompilationUnit], out: PrintStream = System.err): Unit = {
    out.println("[AST]")
    units.foreach { unit =>
      val source = Option(unit.sourceFile).filter(_.nonEmpty).getOrElse("<memory>")
      out.println(s"-- $source")
      out.println(unit)
    }
  }

  def dumpTyped(classes: Seq[TypedAST.ClassDefinition], out: PrintStream = System.err): Unit = {
    out.println("[Typed AST]")
    classes.foreach { cls =>
      out.println(formatClass(cls))
    }
  }

  private def formatClass(cls: TypedAST.ClassDefinition): String = {
    val builder = new StringBuilder
    val kind =
      if (cls.isInterface) "interface"
      else if (Modifier.isEnum(cls.modifier)) "enum"
      else "class"
    val typeParams = formatTypeParams(cls.typeParameters)
    val header = new StringBuilder()
    header.append(formatClassModifiers(cls.modifier))
    header.append(kind)
    header.append(" ")
    header.append(cls.name)
    header.append(typeParams)

    val superClass = Option(cls.superClass).filter(_.name != "java.lang.Object")
    superClass.foreach(sc => header.append(" extends ").append(sc.name))
    if (cls.interfaces != null && cls.interfaces.nonEmpty) {
      header.append(" implements ").append(cls.interfaces.map(_.name).mkString(", "))
    }

    builder.append(header.toString).append(System.lineSeparator())

    val fields = cls.fields.sortBy(_.name)
    if (fields.nonEmpty) {
      builder.append("  fields:").append(System.lineSeparator())
      fields.foreach { field =>
        val mutability = if (Modifier.isFinal(field.modifier)) "val" else "var"
        builder
          .append("    ")
          .append(formatMemberModifiers(field.modifier))
          .append(mutability)
          .append(" ")
          .append(field.name)
          .append(": ")
          .append(field.`type`.name)
          .append(System.lineSeparator())
      }
    }

    val methods = cls.methods.toSeq.sortBy(_.name)
    if (methods.nonEmpty) {
      builder.append("  methods:").append(System.lineSeparator())
      methods.foreach { method =>
        builder
          .append("    ")
          .append(formatMemberModifiers(method.modifier))
          .append("def ")
          .append(method.name)
          .append(formatTypeParams(method.typeParameters))
          .append(formatArgs(method))
          .append(": ")
          .append(method.returnType.name)
          .append(System.lineSeparator())
      }
    }

    builder.toString
  }

  private def formatTypeParams(params: Array[TypedAST.TypeParameter]): String = {
    if (params.isEmpty) return ""
    params
      .map { param =>
        param.upperBound match {
          case Some(bound) if bound.name != "java.lang.Object" => s"${param.name} extends ${bound.name}"
          case _ => param.name
        }
      }
      .mkString("[", ", ", "]")
  }

  private def formatArgs(method: TypedAST.Method): String = {
    val args = method.argumentsWithDefaults.zipWithIndex.map { case (arg, index) =>
      val varargSuffix = if (method.isVararg && index == method.argumentsWithDefaults.length - 1) "..." else ""
      s"${arg.name}: ${arg.argType.name}$varargSuffix"
    }
    args.mkString("(", ", ", ")")
  }

  private def formatClassModifiers(modifier: Int): String = {
    val parts = Seq(
      "public" -> Modifier.isPublic(modifier),
      "protected" -> Modifier.isProtected(modifier),
      "private" -> Modifier.isPrivate(modifier),
      "abstract" -> Modifier.isAbstract(modifier),
      "final" -> Modifier.isFinal(modifier),
      "sealed" -> Modifier.isSealed(modifier)
    ).collect { case (label, true) => label }
    if (parts.isEmpty) "" else parts.mkString("", " ", " ") 
  }

  private def formatMemberModifiers(modifier: Int): String = {
    val parts = Seq(
      "public" -> Modifier.isPublic(modifier),
      "protected" -> Modifier.isProtected(modifier),
      "private" -> Modifier.isPrivate(modifier),
      "static" -> Modifier.isStatic(modifier),
      "abstract" -> Modifier.isAbstract(modifier),
      "final" -> Modifier.isFinal(modifier),
      "synchronized" -> Modifier.isSynchronized(modifier),
      "volatile" -> Modifier.isVolatile(modifier)
    ).collect { case (label, true) => label }
    if (parts.isEmpty) "" else parts.mkString("", " ", " ")
  }
}
