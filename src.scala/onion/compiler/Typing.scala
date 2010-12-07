package onion.compiler
import _root_.scala.collection.mutable.{Map, HashMap}
import _root_.scala.collection.JavaConversions._
import _root_.onion.compiler.util.{Boxing, Classes, Paths, Systems}
import _root_.onion.compiler.SemanticErrorReporter.Constants._
import _root_.onion.compiler.IxCode.BinaryExpression.Constants._
import _root_.onion.compiler.IxCode.UnaryExpression.Constants._

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 10:46:42
 * To change this template use File | Settings | File Templates.
 */
class Typing(config: CompilerConfig) extends AnyRef with ProcessingUnit[Array[AST.CompilationUnit], Array[IxCode.ClassDefinition]] {
  class TypingEnvironment
  type Environment = TypingEnvironment
  type Dimension = Int
  private def split(descriptor: AST.TypeDescriptor): (AST.TypeDescriptor, Dimension) = {
    def loop(target: AST.TypeDescriptor, dimension: Int): (AST.TypeDescriptor, Int) = target match {
      case AST.ArrayType(component) => loop(component, dimension + 1)
      case otherwise => (otherwise, dimension)
    }
    loop(descriptor, 0)
  }
  private class NameMapper(imports: ImportList) {
    def map(typeNode: AST.TypeNode): IxCode.TypeRef = map(typeNode.desc)
    def map(descriptor : AST.TypeDescriptor): IxCode.TypeRef = descriptor match {
      case AST.PrimitiveType(AST.KChar)       => IxCode.BasicTypeRef.CHAR
      case AST.PrimitiveType(AST.KByte)       => IxCode.BasicTypeRef.BYTE
      case AST.PrimitiveType(AST.KShort)      => IxCode.BasicTypeRef.SHORT
      case AST.PrimitiveType(AST.KInt)        => IxCode.BasicTypeRef.INT
      case AST.PrimitiveType(AST.KLong)       => IxCode.BasicTypeRef.LONG
      case AST.PrimitiveType(AST.KFloat)      => IxCode.BasicTypeRef.FLOAT
      case AST.PrimitiveType(AST.KDouble)     => IxCode.BasicTypeRef.DOUBLE
      case AST.PrimitiveType(AST.KBoolean)    => IxCode.BasicTypeRef.BOOLEAN
      case AST.PrimitiveType(AST.KVoid)       => IxCode.BasicTypeRef.VOID
      case AST.ReferenceType(name, qualified) => forName(name, qualified)
      case AST.ParameterizedType(base, _)     => map(base)
      case AST.ArrayType(component)           =>  val (base, dimension) = split(descriptor); table_.loadArray(map(base), dimension)
    }
    private def forName(name: String, qualified: Boolean): IxCode.ClassTypeRef = {
      if(qualified) {
        return table_.load(name);
      }else {
        for(item <- imports) {
          val qname = item `match` name
          if(qname != null) {
            val mappedType = forName(qname, true)
            if(mappedType != null) return mappedType
          }
        }
        return null
      }
    }
  }
  private val table_  = new ClassTable(classpath(config.getClassPath))
  private val ast2ixt_ = Map[AST.Node, IxCode.Node]()
  private var ixt2ast_ = Map[IxCode.Node, AST.Node]()
  private var mappers_  = Map[String, NameMapper]()
  private var mapper_ : NameMapper = _
  private var importedList_ : ImportList = _
  private var staticImportedList_ : StaticImportList = _
  private var unit_ : AST.CompilationUnit = _
  private val reporter_ : SemanticErrorReporter = new SemanticErrorReporter(config.getMaxErrorReports)
  def newEnvironment(source: Array[AST.CompilationUnit]) = new TypingEnvironment
  def doProcess(source: Array[AST.CompilationUnit], environment: TypingEnvironment): Array[IxCode.ClassDefinition] = {
    null
  }

  def buildClassTable(unit: AST.CompilationUnit) {
    unit_ = unit
    val module = unit.module
    val imports = unit.imports
    val moduleName = if (module != null) module.name else null
    val list = new ImportList
    list.add(new ImportItem("*", "java.lang.*"))
    list.add(new ImportItem("*", "java.io.*"))
    list.add(new ImportItem("*", "java.util.*"))
    list.add(new ImportItem("*", "javax.swing.*"))
    list.add(new ImportItem("*", "java.awt.event.*"))
    list.add(new ImportItem("*", "onion.*"))
    list.add(new ImportItem("*", if (moduleName != null) moduleName + ".*" else "*"))
    for((key, value) <- imports.mapping) {
      list.add(new ImportItem(key, value))
    }
    val staticList = new StaticImportList
    staticList.add(new StaticImportItem("java.lang.System", true))
    staticList.add(new StaticImportItem("java.lang.Runtime", true))
    staticList.add(new StaticImportItem("java.lang.Math", true))
    importedList_ = list
    staticImportedList_ = staticList
    var count = 0
    for(top <- unit.toplevels) top match {
      case declaration: AST.ClassDeclaration =>
        val node = IxCode.ClassDefinition.newClass(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null, null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(DUPLICATE_CLASS, declaration, node.name)
        }else {
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(importedList_))
        }
      case declaration: AST.InterfaceDeclaration =>
        val node = IxCode.ClassDefinition.newInterface(declaration.location, declaration.modifiers, createFQCN(moduleName, declaration.name), null)
        node.setSourceFile(Paths.nameOf(unit.sourceFile))
        if (table_.lookup(node.name) != null) {
          report(DUPLICATE_CLASS, declaration, node.name)
        }else{
          table_.classes.add(node)
          put(declaration, node)
          add(node.name, new NameMapper(importedList_))
        }
      case otherwise =>
        count += 1
    }
    if (count > 0) {
      val node = IxCode.ClassDefinition.newClass(unit.location, 0, topClass, table_.rootClass, new Array[IxCode.ClassTypeRef](0))
      node.setSourceFile(Paths.nameOf(unit_.sourceFile))
      node.setResolutionComplete(true)
      table_.classes.add(node)
      node.addDefaultConstructor()
      put(unit, node)
      add(node.name, new NameMapper(list))
    }
  }
  def report(error: Int, node: AST.Node, items: AnyRef*) {
    report(error, node.location, items)
  }
  def report(error: Int, location: Location, items: AnyRef*) {
    reporter_.setSourceFile(unit_.sourceFile)
    reporter_.report(error, location, items.toArray)
  }
  def createFQCN(moduleName: String, simpleName: String): String =  (if (moduleName != null) moduleName + "." else "") + simpleName
  def load(name: String): IxCode.ClassTypeRef = table_.load(name)
  def loadTopClass: IxCode.ClassTypeRef = table_.load(topClass)
  def loadArray(base: IxCode.TypeRef, dimension: Int): IxCode.ArrayTypeRef = table_.loadArray(base, dimension)
  def rootClass: IxCode.ClassTypeRef = table_.rootClass
  def problems: Array[CompileError] = reporter_.getProblems
  def sourceClasses: Array[IxCode.ClassDefinition] = table_.classes.values.toArray(new Array[IxCode.ClassDefinition](0))
  def topClass: String = {
    var module = unit_.module
    var moduleName: String = if (module != null) module.name else null
    return createName(moduleName, Paths.cutExtension(unit_.sourceFile) + "Main")
  }
  private def put(astNode: AST.Node, kernelNode: IxCode.Node) {
    ast2ixt_(astNode) = kernelNode
    ixt2ast_(kernelNode) = astNode
  }
  private def lookupAST(kernelNode: IxCode.Node): Option[AST.Node] =  ixt2ast_.get(kernelNode)
  private def lookupKernelNode(astNode: AST.Node): Option[IxCode.Node] = ast2ixt_.get(astNode)
  private def add(className: String, mapper: NameMapper): Unit = mappers_(className) = mapper
  private def find(className: String): Option[NameMapper] = mappers_.get(className)
  private def createName(moduleName: String, simpleName: String): String = (if (moduleName != null) moduleName + "." else "") + simpleName
  private def classpath(paths: Array[String]): String = paths.foldLeft(new StringBuilder){(builder, path) => builder.append(Systems.getPathSeparator).append(path)}.toString
  private def mapFrom(typeNode: AST.TypeNode): IxCode.TypeRef = mapFrom(typeNode, mapper_)
  private def mapFrom(typeNode: AST.TypeNode, mapper: NameMapper): IxCode.TypeRef = {
    val mappedType = mapper.map(typeNode)
    if (mappedType == null) report(CLASS_NOT_FOUND, typeNode, AST.toString(typeNode.desc))
    return mappedType
  }
}
