package onion.compiler

import org.objectweb.asm.Type
import scala.collection.mutable

object OnionTypeConversion:
  private val basicTypeTable = Map[
    Type, TypedAST.BasicType](
      Type.BOOLEAN_TYPE -> TypedAST.BasicType.BOOLEAN,
      Type.BYTE_TYPE    -> TypedAST.BasicType.BYTE,
      Type.SHORT_TYPE   -> TypedAST.BasicType.SHORT,
      Type.CHAR_TYPE    -> TypedAST.BasicType.CHAR,
      Type.INT_TYPE     -> TypedAST.BasicType.INT,
      Type.LONG_TYPE    -> TypedAST.BasicType.LONG,
      Type.FLOAT_TYPE   -> TypedAST.BasicType.FLOAT,
      Type.DOUBLE_TYPE  -> TypedAST.BasicType.DOUBLE,
      Type.VOID_TYPE    -> TypedAST.BasicType.VOID)

  private val c2t = Map[Class[_], TypedAST.BasicType](
      classOf[Boolean] -> TypedAST.BasicType.BOOLEAN,
      classOf[Byte]    -> TypedAST.BasicType.BYTE,
      classOf[Short]   -> TypedAST.BasicType.SHORT,
      classOf[Char]    -> TypedAST.BasicType.CHAR,
      classOf[Int]     -> TypedAST.BasicType.INT,
      classOf[Long]    -> TypedAST.BasicType.LONG,
      classOf[Float]   -> TypedAST.BasicType.FLOAT,
      classOf[Double]  -> TypedAST.BasicType.DOUBLE,
      classOf[Unit]    -> TypedAST.BasicType.VOID)

class OnionTypeConversion(table: ClassTable):
  import OnionTypeConversion.*

  def toOnionType(klass: Class[_]): TypedAST.Type =
    c2t.get(klass) match
      case Some(t) => t
      case None =>
        if klass.isArray then
          val dim = klass.getName.takeWhile(_ == '[').length
          val comp = toOnionType(klass.getComponentType)
          if comp != null then table.loadArray(comp, dim) else null
        else
          table.load(klass.getName)

  def toOnionType(tpe: Type): TypedAST.Type =
    basicTypeTable.get(tpe) match
      case Some(t) => t
      case None =>
        if tpe.getSort == Type.ARRAY then
          val comp = toOnionType(tpe.getElementType)
          if comp != null then table.loadArray(comp, tpe.getDimensions) else null
        else
          table.load(tpe.getClassName)

