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
      case AST.ArrayType(component)           =>  val (base, dimension) = split(descriptor); table.loadArray(map(base), dimension)
    }
    private def forName(name: String, qualified: Boolean): IxCode.ClassTypeRef = {
      if(qualified) {
        return table.load(name);
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
  private var table: ClassTable = _
  private var ast2ixt: Map[AST.Node, IxCode.Node] = _
  private var ixt2ast: Map[IxCode.Node, AST.Node] = _
  private val reporter: SemanticErrorReporter = new SemanticErrorReporter(config.getMaxErrorReports)
  def newEnvironment(source: Array[AST.CompilationUnit]) = new TypingEnvironment
  def doProcess(source: Array[AST.CompilationUnit], environment: TypingEnvironment): Array[IxCode.ClassDefinition] = {
    null
  }
}
