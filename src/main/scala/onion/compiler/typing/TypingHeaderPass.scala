package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Paths
import onion.compiler.typing.session.NameResolutionContext
import onion.compiler.typing.session.TypingUnitContext

import scala.collection.mutable.Buffer

final class TypingHeaderPass(private val typing: Typing, private val unitContext: TypingUnitContext) {
  private val unit = unitContext.unit

  def run(): Unit = {
    val moduleName = if (unit.module != null) unit.module.name else null
    val imports = buildImports(moduleName)
    unitContext.staticImports = collectStaticImports()

    var nonTypeCount = 0
    unit.toplevels.foreach {
      case declaration: AST.ClassDeclaration =>
        registerClass(declaration, moduleName, imports)
      case declaration: AST.InterfaceDeclaration =>
        registerInterface(declaration, moduleName, imports)
      case declaration: AST.RecordDeclaration =>
        registerRecord(declaration, moduleName, imports)
      case declaration: AST.EnumDeclaration =>
        registerEnum(declaration, moduleName, imports)
      case declaration: AST.ExtensionDeclaration =>
        registerExtension(declaration, moduleName, imports)
      case declaration: AST.TypeAliasDeclaration =>
        registerTypeAlias(declaration, moduleName, imports)
      case _ =>
        nonTypeCount += 1
    }

    if (nonTypeCount > 0) {
      registerTopLevelContainer(imports)
    }
  }

  private def buildImports(moduleName: String): Seq[ImportItem] = {
    val imports = Buffer[ImportItem](
      // onion.* must precede java.lang.*: since Java 25 java.lang.IO exists
      // and would otherwise shadow onion.IO
      ImportItem("*", Seq("onion", "*")),
      ImportItem("*", Seq("java", "lang", "*")),
      ImportItem("*", Seq("java", "io", "*")),
      ImportItem("*", Seq("java", "util", "*")),
      ImportItem("*", Seq("javax", "swing", "*")),
      ImportItem("*", Seq("java", "awt", "event", "*")),
      ImportItem("JByte", Seq("java", "lang", "Byte")),
      ImportItem("JShort", Seq("java", "lang", "Short")),
      ImportItem("JCharacter", Seq("java", "lang", "Character")),
      ImportItem("JInteger", Seq("java", "lang", "Integer")),
      ImportItem("JLong", Seq("java", "lang", "Long")),
      ImportItem("JFloat", Seq("java", "lang", "Float")),
      ImportItem("JDouble", Seq("java", "lang", "Double")),
      ImportItem("JBoolean", Seq("java", "lang", "Boolean")),
      ImportItem("*", if (moduleName != null) moduleName.split("\\.").toIndexedSeq.appended("*") else Seq("*"))
    )
    if (unit.imports != null) {
      for ((key, value) <- unit.imports.mapping) {
        val item = ImportItem(key, value.split("\\.").toIndexedSeq)
        if (item.isOnDemand) imports.append(item)
        else {
          // A single-class import may name a nested class with dots only
          // (`onion.Result.Ok`); it is recorded under its binary name
          // (`onion.Result$Ok`) so every use site loads it directly.
          // Single-class imports of unknown classes were accepted silently and
          // only failed (confusingly) at the use site, if at all
          val resolved = typing.table_.loadDottedOrNull(value)
          if (resolved == null) {
            typing.report(SemanticError.CLASS_NOT_FOUND, unit.imports, value, importCandidates(value))
            imports.append(item)
          } else if (resolved.name == value) imports.append(item)
          else imports.append(ImportItem(key, resolved.name.split("\\.").toIndexedSeq))
        }
      }
    }
    imports.toSeq
  }

  def collectStaticImports(): StaticImportList = {
    val staticList = defaultStaticImports()
    if (unit.imports != null) {
      for ((methodName, writtenName) <- unit.imports.staticImports) {
        // `onion.Result.Ok::*` names a nested class with dots, like a type import does.
        val resolved = typing.table_.loadDottedOrNull(writtenName)
        if (resolved == null) {
          typing.report(SemanticError.CLASS_NOT_FOUND, unit.imports, writtenName, importCandidates(writtenName))
        }
        val className = if (resolved == null) writtenName else resolved.name
        if (methodName == "*") staticList.add(new StaticImportItem(className, true))
        else staticList.add(new StaticImportItem(className, true, methodName))
      }
    }
    staticList
  }

  /** "Did you mean" candidates for an import whose nested part did not resolve: the member
    * classes of the longest prefix that is a class (`onion.Result.Okk` -> `onion.Result.Ok`). */
  private def importCandidates(name: String): Array[String] = {
    val parts = name.split('.')
    var i = parts.length - 1
    while (i >= 1) {
      val prefix = parts.take(i).mkString(".")
      val outer = typing.table_.loadDottedOrNull(prefix)
      if (outer != null) return typing.table_.memberClassCandidates(outer, prefix).toArray
      i -= 1
    }
    Array.empty
  }

  private def defaultStaticImports(): StaticImportList = {
    val staticList = new StaticImportList
    DefaultStaticImports.classes.foreach { name =>
      staticList.add(new StaticImportItem(name, true))
    }
    staticList
  }

  private def registerClass(declaration: AST.ClassDeclaration, moduleName: String, imports: Seq[ImportItem]): Unit = {
    val node = ClassDefinition.newClass(declaration.location, declaration.modifiers, typing.createFQCN(moduleName, declaration.name), null, null)
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    if (typing.table_.lookup(node.name) != null) {
      typing.report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
    } else {
      typing.table_.classes.add(node)
      typing.put(declaration, node)
      typing.add(node.name, new NameResolver(NameResolutionContext.fromTyping(typing, imports)))
    }
  }

  private def registerInterface(declaration: AST.InterfaceDeclaration, moduleName: String, imports: Seq[ImportItem]): Unit = {
    val node = ClassDefinition.newInterface(declaration.location, declaration.modifiers, typing.createFQCN(moduleName, declaration.name), null)
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    if (typing.table_.lookup(node.name) != null) {
      typing.report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
    } else {
      typing.table_.classes.add(node)
      typing.put(declaration, node)
      typing.add(node.name, new NameResolver(NameResolutionContext.fromTyping(typing, imports)))
    }
  }

  private def registerRecord(declaration: AST.RecordDeclaration, moduleName: String, imports: Seq[ImportItem]): Unit = {
    // Records are compiled as final classes
    val modifiers = declaration.modifiers | Modifier.FINAL
    val node = ClassDefinition.newClass(declaration.location, modifiers, typing.createFQCN(moduleName, declaration.name), null, null)
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    if (typing.table_.lookup(node.name) != null) {
      typing.report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
    } else {
      typing.table_.classes.add(node)
      typing.put(declaration, node)
      typing.add(node.name, new NameResolver(NameResolutionContext.fromTyping(typing, imports)))
    }
  }

  private def registerEnum(declaration: AST.EnumDeclaration, moduleName: String, imports: Seq[ImportItem]): Unit = {
    // Enums are compiled as final classes extending java.lang.Enum
    val modifiers = declaration.modifiers | Modifier.FINAL | Modifier.ENUM
    val node = ClassDefinition.newClass(declaration.location, modifiers, typing.createFQCN(moduleName, declaration.name), null, null)
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    if (typing.table_.lookup(node.name) != null) {
      typing.report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
    } else {
      typing.table_.classes.add(node)
      typing.put(declaration, node)
      typing.add(node.name, new NameResolver(NameResolutionContext.fromTyping(typing, imports)))
    }
  }

  private def registerExtension(declaration: AST.ExtensionDeclaration, moduleName: String, imports: Seq[ImportItem]): Unit = {
    // Generate container class name from receiver type
    val receiverTypeName = extractTypeName(declaration.receiverType)
    val containerClassName = typing.createFQCN(moduleName, "Extension$" + receiverTypeName.replace(".", "_"))

    // Extension container is a public final class with static methods
    val modifiers = Modifier.PUBLIC | Modifier.FINAL
    val node = ClassDefinition.newClass(declaration.location, modifiers, containerClassName, typing.table_.rootClass, new Array[ClassType](0))
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    node.setResolutionComplete(true) // No inheritance to resolve

    if (typing.table_.lookup(node.name) != null) {
      typing.report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
    } else {
      typing.table_.classes.add(node)
      typing.put(declaration, node)
      typing.add(node.name, new NameResolver(NameResolutionContext.fromTyping(typing, imports)))
      // Register this as an extension declaration for later processing
      typing.registerExtensionDeclaration(declaration, node)
    }
  }

  private def registerTypeAlias(
    declaration: AST.TypeAliasDeclaration,
    moduleName: String,
    imports: Seq[ImportItem]
  ): Unit = {
    val fqcn = typing.createFQCN(moduleName, declaration.name)

    // Check duplicate type alias
    if (typing.typeAliases_.contains(fqcn)) {
      typing.report(SemanticError.DUPLICATE_TYPE_ALIAS, declaration, fqcn)
      return
    }

    // Check conflict with class names
    if (typing.table_.lookup(fqcn) != null) {
      typing.report(SemanticError.DUPLICATE_CLASS, declaration, fqcn)
      return
    }

    // Store with empty type params (will be populated in OutlinePass)
    val entry = TypeAliasEntry(
      fqcn = fqcn,
      typeParameters = Seq.empty,
      targetDescriptor = declaration.targetType.desc,
      node = declaration,
      imports = imports
    )
    typing.typeAliases_(fqcn) = entry
  }

  private def extractTypeName(typeNode: AST.TypeNode): String = {
    typeNode.desc match {
      case AST.ReferenceType(name, _) => name.replace(".", "_")
      case AST.ParameterizedType(component, params) =>
        extractTypeDescName(component) + "_" + params.map(extractTypeDescName).mkString("_")
      case AST.ArrayType(component) => extractTypeDescName(component) + "_Array"
      case AST.PrimitiveType(kind) => kind.toString
      case _ => "Unknown"
    }
  }

  private def extractTypeDescName(desc: AST.TypeDescriptor): String = {
    desc match {
      case AST.ReferenceType(name, _) => name.replace(".", "_")
      case AST.ParameterizedType(component, params) =>
        extractTypeDescName(component) + "_" + params.map(extractTypeDescName).mkString("_")
      case AST.ArrayType(component) => extractTypeDescName(component) + "_Array"
      case AST.PrimitiveType(kind) => kind.toString
      case _ => "Unknown"
    }
  }

  private def registerTopLevelContainer(imports: Seq[ImportItem]): Unit = {
    val node = ClassDefinition.newClass(unit.location, 0, typing.topClass, typing.table_.rootClass, new Array[ClassType](0))
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    node.isTopLevelContainer = true
    node.setResolutionComplete(true)
    typing.table_.classes.add(node)
    node.addDefaultConstructor
    typing.put(unit, node)
    typing.add(node.name, new NameResolver(NameResolutionContext.fromTyping(typing, imports)))
  }
}
