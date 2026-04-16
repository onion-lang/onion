package onion.compiler.diagnostics

import onion.compiler.{AST, CompileError, CompileWarning, Location, Modifier, TypedAST}
import onion.compiler.toolbox.{Inputs, Message, Systems}

import java.io.{FileNotFoundException, IOException, PrintStream}
import scala.math.max
import scala.util.Using

object DiagnosticRenderer {
  def printErrors(errors: Seq[CompileError], out: PrintStream = Console.err): Unit = {
    if (errors.isEmpty) return
    formatErrors(errors).foreach(out.println)
    out.println(Message("error.count", errors.size))
  }

  def printWarnings(warnings: Seq[CompileWarning], out: PrintStream = Console.err): Unit = {
    if (warnings.isEmpty) return
    warnings.foreach(w => out.println(formatWarning(w)))
    out.println(s"${warnings.size} warning(s)")
  }

  def printDiagnostics(diagnostics: DiagnosticBag, out: PrintStream = Console.err): Unit = {
    printWarnings(diagnostics.warnings, out)
    printErrors(diagnostics.allErrors, out)
  }

  def formatErrors(errors: Seq[CompileError]): Seq[String] =
    errors.map(formatError)

  def formatError(error: CompileError): String = {
    val locationOpt = Option(error.location)
    val sourceFileOpt = Option(error.sourceFile).filter(_.nonEmpty)
    val errorCodePrefix = error.code.map(code => s"[$code] ").getOrElse("")
    val builder = new StringBuilder
    sourceFileOpt match {
      case None =>
        builder.append(s"$errorCodePrefix${error.message}")
      case Some(sourceFile) =>
        val lineNumber = locationOpt.map(_.line).getOrElse(0)
        val columnNumber = locationOpt.map(_.column).getOrElse(0)
        val locationText = (lineNumber, columnNumber) match {
          case (l, c) if l > 0 && c > 0 => s"$l:$c"
          case (l, _) if l > 0 => l.toString
          case _ => ""
        }
        val lineText = locationOpt.flatMap(loc => readSourceLine(sourceFile, loc.line)).getOrElse("")
        builder.append(s"$sourceFile:$locationText: $errorCodePrefix${error.message}")
        builder.append(Systems.lineSeparator)
        if (lineText.nonEmpty && lineNumber > 0) {
          val lineNumWidth = lineNumber.toString.length
          val prefix = s"  $lineNumber | "
          builder.append(prefix)
          builder.append(lineText)
          builder.append(Systems.lineSeparator)
          locationOpt.foreach { loc =>
            val indentPrefix = " " * (lineNumWidth + 2) + " | "
            builder.append(indentPrefix)
            builder.append(underlineAt(loc))
          }
        }
    }
    builder.toString
  }

  def formatWarning(warning: CompileWarning): String = {
    val loc = Option(warning.location).map(loc => s"${loc.line}:${loc.column}").getOrElse("?")
    val code = warning.code.map(code => s"[$code] ").getOrElse("")
    s"${code}${warning.sourceFile}:$loc: warning: ${warning.message}"
  }

  def dumpAst(units: Seq[AST.CompilationUnit], out: PrintStream = System.err): Unit =
    out.println(renderAst(units))

  def dumpTyped(classes: Seq[TypedAST.ClassDefinition], out: PrintStream = System.err): Unit =
    out.println(renderTyped(classes))

  def renderAst(units: Seq[AST.CompilationUnit]): String = {
    val lines = Vector.newBuilder[String]
    lines += "[AST]"
    units.foreach { unit =>
      val source = Option(unit.sourceFile).filter(_.nonEmpty).getOrElse("<memory>")
      lines += s"-- $source"
      lines += unit.toString
    }
    lines.result().mkString(System.lineSeparator())
  }

  def renderTyped(classes: Seq[TypedAST.ClassDefinition]): String = {
    val lines = Vector.newBuilder[String]
    lines += "[Typed AST]"
    classes.foreach { cls =>
      lines += formatClass(cls)
    }
    lines.result().mkString(System.lineSeparator())
  }

  private def underlineAt(loc: Location): String = {
    val safeColumn = max(loc.column, 1)
    val underlineLength = loc.spanLength
    val spaces = " " * (safeColumn - 1)
    if (underlineLength > 1) {
      spaces + "~" * underlineLength
    } else {
      spaces + "^"
    }
  }

  private def readSourceLine(sourceFile: String, lineNumber: Int): Option[String] = {
    if (lineNumber <= 0) return None
    Using(Inputs.newReader(sourceFile)) { reader =>
      Iterator
        .continually(reader.readLine())
        .takeWhile(_ != null)
        .zipWithIndex
        .collectFirst { case (line, index) if index + 1 == lineNumber => line }
    }.recover {
      case _: FileNotFoundException | _: IOException => None
    }.getOrElse(None)
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
