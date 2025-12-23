package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Paths

import scala.collection.mutable.Buffer

final class TypingHeaderPass(private val typing: Typing, private val unit: AST.CompilationUnit) {
  import typing.*

  def run(): Unit = {
    unit_ = unit
    val moduleName = if (unit.module != null) unit.module.name else null
    val imports = buildImports(moduleName)
    staticImportedList_ = defaultStaticImports()

    var nonTypeCount = 0
    unit.toplevels.foreach {
      case declaration: AST.ClassDeclaration =>
        registerClass(declaration, moduleName, imports)
      case declaration: AST.InterfaceDeclaration =>
        registerInterface(declaration, moduleName, imports)
      case _ =>
        nonTypeCount += 1
    }

    if (nonTypeCount > 0) {
      registerTopLevelContainer(imports)
    }
  }

  private def buildImports(moduleName: String): Seq[ImportItem] = {
    val imports = Buffer[ImportItem](
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
      ImportItem("*", Seq("onion", "*")),
      ImportItem("*", if (moduleName != null) moduleName.split("\\.").toIndexedSeq.appended("*") else Seq("*"))
    )
    if (unit.imports != null) {
      for ((key, value) <- unit.imports.mapping) {
        imports.append(ImportItem(key, value.split("\\.").toIndexedSeq))
      }
    }
    imports.toSeq
  }

  private def defaultStaticImports(): StaticImportList = {
    val staticList = new StaticImportList
    DefaultStaticImports.classes.foreach { name =>
      staticList.add(new StaticImportItem(name, true))
    }
    staticList
  }

  private def registerClass(declaration: AST.ClassDeclaration, moduleName: String, imports: Seq[ImportItem]): Unit = {
    val node = ClassDefinition.newClass(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null, null)
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    if (table_.lookup(node.name) != null) {
      report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
    } else {
      table_.classes.add(node)
      put(declaration, node)
      add(node.name, new NameMapper(imports))
    }
  }

  private def registerInterface(declaration: AST.InterfaceDeclaration, moduleName: String, imports: Seq[ImportItem]): Unit = {
    val node = ClassDefinition.newInterface(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null)
    node.setSourceFile(Paths.nameOf(unit.sourceFile))
    if (table_.lookup(node.name) != null) {
      report(SemanticError.DUPLICATE_CLASS, declaration, node.name)
    } else {
      table_.classes.add(node)
      put(declaration, node)
      add(node.name, new NameMapper(imports))
    }
  }

  private def registerTopLevelContainer(imports: Seq[ImportItem]): Unit = {
    val node = ClassDefinition.newClass(unit.location, 0, topClass, table_.rootClass, new Array[ClassType](0))
    node.setSourceFile(Paths.nameOf(unit_.sourceFile))
    node.setResolutionComplete(true)
    table_.classes.add(node)
    node.addDefaultConstructor
    put(unit, node)
    add(node.name, new NameMapper(imports))
  }
}
