package onion.compiler

import java.util._
import java.util
import scala.jdk.CollectionConverters._

/**


 */
object TypedAST {

  /**
   * This interface represents an internal representation node of onion program.
   * @author Kota Mizushima
   */
  sealed trait Node

  /**
   * @author Kota Mizushima
   */
  class ArrayLength(location: Location, val target: TypedAST.Term) extends Term(location) {
    def this(target: TypedAST.Term) = {
      this(null, target)
    }

    def `type`: TypedAST.Type = BasicType.INT
  }

  /**
   * @author Kota Mizushima
   */
  class RefArray(location: Location, val target: TypedAST.Term, val index: TypedAST.Term) extends Term(location) {
    def this(target: TypedAST.Term, index: TypedAST.Term) = {
      this(null, target, index)
    }

    def `type`: TypedAST.Type = (target.`type`.asInstanceOf[TypedAST.ArrayType]).base
  }

  /**
   * @author Kota Mizushima
   */
  class SetArray(location: Location, val target: TypedAST.Term, val index: TypedAST.Term, val value: TypedAST.Term) extends Term(location) {
    def this(target: TypedAST.Term, index: TypedAST.Term, value: TypedAST.Term) = {
      this(null, target, index, value)
    }

    def `type`: TypedAST.Type = value.`type`

    def `object`: TypedAST.Term = target
  }

  /**
   * @author Kota Mizushima
   */
  class Begin(location: Location, val terms: Array[TypedAST.Term]) extends Term(location) {
    def this(terms: Array[TypedAST.Term]) = {
      this(null, terms)
    }

    def this(expressions: List[_]) = {
      this(expressions.toArray(new Array[TypedAST.Term](0)).asInstanceOf[Array[TypedAST.Term]])
    }

    def this(term: TypedAST.Term) = {
      this(Array[TypedAST.Term](term))
    }

    def this(expression1: TypedAST.Term, expression2: TypedAST.Term) = {
      this(Array[TypedAST.Term](expression1, expression2))
    }

    def this(expression1: TypedAST.Term, expression2: TypedAST.Term, expression3: TypedAST.Term) = {
      this(Array[TypedAST.Term](expression1, expression2, expression3))
    }

    def `type`: TypedAST.Type = terms(terms.length - 1).`type`
  }

  /**
   * @author Kota Mizushima
   */
  object BinaryTerm {

    object Constants {
      final val ADD: Int = 0
      final val SUBTRACT: Int = 1
      final val MULTIPLY: Int = 2
      final val DIVIDE: Int = 3
      final val MOD: Int = 4
      final val LOGICAL_AND: Int = 5
      final val LOGICAL_OR: Int = 6
      final val BIT_AND: Int = 7
      final val BIT_OR: Int = 8
      final val XOR: Int = 9
      final val BIT_SHIFT_L2: Int = 10
      final val BIT_SHIFT_R2: Int = 11
      final val BIT_SHIFT_R3: Int = 12
      final val LESS_THAN: Int = 13
      final val GREATER_THAN: Int = 14
      final val LESS_OR_EQUAL: Int = 15
      final val GREATER_OR_EQUAL: Int = 16
      final val EQUAL: Int = 17
      final val NOT_EQUAL: Int = 18
      final val ELVIS: Int = 19
    }

  }

  class BinaryTerm(location: Location, val kind: Int, val `type`: TypedAST.Type, val lhs: TypedAST.Term, val rhs: TypedAST.Term) extends Term(location) {
    def this(kind: Int, `type`: TypedAST.Type, lhs: TypedAST.Term, rhs: TypedAST.Term) = {
      this(null, kind, `type`, lhs, rhs)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class StatementBlock(location: Location, newStatements : TypedAST.ActionStatement*) extends ActionStatement(location) {
    def this(newStatements: TypedAST.ActionStatement*) = {
      this(null: Location, newStatements : _*)
    }

    def this(newStatements: util.List[TypedAST.ActionStatement]) = {
      this(newStatements.asScala.toIndexedSeq: _*)
    }

    def statements: Array[TypedAST.ActionStatement] = newStatements.toArray
  }

  /**
   * @author Kota Mizushima
   */
  class BoolValue(location: Location, val value: Boolean) extends Term(location) {
    def this(value: Boolean) = {
      this(null, value)
    }

    def `type`: TypedAST.Type = BasicType.BOOLEAN
  }

  /**
   * @author Kota Mizushima
   */
  class Break(location: Location) extends ActionStatement(location) {
    def this() = {
      this(null)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class ByteValue(location: Location, val value: Byte) extends Term(location) {
    def this(value: Byte) = {
      this(null, value)
    }

    def `type`: TypedAST.Type = BasicType.BYTE
  }

  /**
   * @author Kota Mizushima
   */
  class Call(location: Location, val target: TypedAST.Term, val method: TypedAST.Method, val parameters: Array[TypedAST.Term]) extends Term(location) {
    def this(target: TypedAST.Term, method: TypedAST.Method, parameters: Array[TypedAST.Term]) = {
      this(null, target, method, parameters)
    }

    def `type`: TypedAST.Type = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class CallStatic(location: Location, val target: TypedAST.ObjectType, val method: TypedAST.Method, val parameters: Array[TypedAST.Term]) extends Term(location) {
    def this(target: TypedAST.ObjectType, method: TypedAST.Method, parameters: Array[TypedAST.Term]) = {
      this(null, target, method, parameters)
    }
    def `type`: TypedAST.Type = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class CallSuper(location: Location, val target: TypedAST.Term, val method: TypedAST.Method, val params: Array[TypedAST.Term]) extends Term(location) {
    def this(target: TypedAST.Term, method: TypedAST.Method, params: Array[TypedAST.Term]) = {
      this(null, target, method, params)
    }

    def `type`: TypedAST.Type = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class AsInstanceOf(location: Location, val target: TypedAST.Term, val destination: TypedAST.Type) extends Term(location) {
    def this(target: TypedAST.Term, destination: TypedAST.Type) = {
      this(null, target, destination)
    }

    def `type`: TypedAST.Type = destination
  }

  /**
   * @author Kota Mizushima
   */
  class CharacterValue(location: Location, val value: Char) extends Term(location) {
    def this(value: Char) = {
      this(null, value)
    }

    def `type`: TypedAST.Type = BasicType.CHAR
  }

  /**
   * This class represents class or interface definitions of internal language.
   * @author Kota Mizushima
   */
  object ClassDefinition {
    /**
     * This method creates interface definition.
     * @param modifier
     * @param name
     * @param interfaces
     * @return
     */
    def newInterface(location: Location, modifier: Int, name: String, interfaces: Array[TypedAST.ClassType]): TypedAST.ClassDefinition = {
      new TypedAST.ClassDefinition(location, true, modifier, name, null, if(interfaces != null) interfaces.toIndexedSeq else Seq())
    }

    /**
     * This method creates class definition.
     * @param modifier
     * @param name
     * @param superClass
     * @param interfaces
     * @return
     */
    def newClass(location: Location, modifier: Int, name: String, superClass: TypedAST.ClassType, interfaces: Array[TypedAST.ClassType]): TypedAST.ClassDefinition = {
      new TypedAST.ClassDefinition(location, false, modifier, name, superClass, if(interfaces != null) interfaces.toIndexedSeq else Seq())
    }

    /**
     * This method creates class definition.
     * @param modifier
     * @param name
     * @return
     */
    def newClass(modifier: Int, name: String): TypedAST.ClassDefinition =  new TypedAST.ClassDefinition(null, false, modifier, name, null, null)
  }

  class ClassDefinition(val location: Location, val isInterface: Boolean, val modifier: Int, val name: String, var superClass: TypedAST.ClassType, var interfaces: Seq[TypedAST.ClassType], val typeParameters: Array[TypedAST.TypeParameter] = Array())
    extends TypedAST.AbstractClassType() with Node with Named {

    def constructors: Array[TypedAST.ConstructorRef] = constructors_.toArray(new Array[TypedAST.ConstructorRef](0))
    def methods: Seq[TypedAST.Method] = methods_.values.toSeq
    def fields: Array[TypedAST.FieldRef] = fields_.values.toArray

    val fields_ : OrderedTable[TypedAST.FieldRef]     = new OrderedTable[TypedAST.FieldRef]
    val methods_ : MultiTable[TypedAST.Method]        = new MultiTable[TypedAST.Method]
    val constructors_ : List[TypedAST.ConstructorRef] = new ArrayList[TypedAST.ConstructorRef]
    var isResolutionComplete: Boolean            = false
    private var sourceFile: String = null

    def this() = {
      this(null: Location, false, 0, null: String, null: TypedAST.ClassType, null)
    }

    def setSuperClass(superClass: TypedAST.ClassType):Unit = {
      this.superClass = superClass
    }

    def setInterfaces(interfaces: Array[TypedAST.ClassType]):Unit = {
      this.interfaces = interfaces.toIndexedSeq
    }

    def setResolutionComplete(isInResolution: Boolean):Unit = {
      this.isResolutionComplete = isInResolution
    }

    def add(method: TypedAST.Method): Unit = {
      methods_.add(method)
    }

    def add(field: TypedAST.FieldRef):Unit = {
      fields_.add(field)
    }

    def add(constructor: TypedAST.ConstructorRef): Unit = {
      constructors_.add(constructor)
    }

    def addDefaultConstructor: Unit = {
      constructors_.add(ConstructorDefinition.newDefaultConstructor(this))
    }

    def methods(name: String): Array[TypedAST.Method] = methods_.get(name).toArray

    def field(name: String): TypedAST.FieldRef = fields_.get(name).orNull

    def setSourceFile(sourceFile: String): Unit = this.sourceFile = sourceFile

    def getSourceFile: String = sourceFile
  }

  /**
   * @author Kota Mizushima
   */
  object ConstructorDefinition {
    def newDefaultConstructor(`type`: TypedAST.ClassType): TypedAST.ConstructorDefinition = {
      val block: TypedAST.StatementBlock = new TypedAST.StatementBlock(new TypedAST.Return(null))
      val init: TypedAST.Super = new TypedAST.Super(`type`.superClass, new Array[TypedAST.Type](0), new Array[TypedAST.Term](0))
      val node: TypedAST.ConstructorDefinition = new TypedAST.ConstructorDefinition(Modifier.PUBLIC, `type`, new Array[TypedAST.Type](0), block, init)
      node.frame = new LocalFrame(null)
      node
    }
  }

  class ConstructorDefinition(val location: Location, val modifier: Int, val classType: TypedAST.ClassType, val arguments: Array[TypedAST.Type], var block: TypedAST.StatementBlock, var superInitializer: TypedAST.Super) extends
  Node with TypedAST.ConstructorRef {
    def this(modifier: Int, classType: TypedAST.ClassType, arguments: Array[TypedAST.Type], block: TypedAST.StatementBlock, superInitializer: TypedAST.Super) = {
      this(null, modifier, classType, arguments, block, superInitializer)
    }

    def name: String =  "new"

    def getArgs: Array[TypedAST.Type] = arguments

    def affiliation: TypedAST.ClassType = classType

    var frame: LocalFrame = _
  }

  /**
   * @author Kota Mizushima
   */
  class Continue(location: Location) extends TypedAST.ActionStatement(location) {
    def this() = {
      this(null)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class DoubleValue(location: Location, val value: Double) extends Term(location) {
    def this(value: Double) = {
      this(null, value)
    }

    def `type`: TypedAST.Type = BasicType.DOUBLE
  }

  /**
   * @author Kota Mizushima
   */
  object Term {
    def defaultValue(`type`: TypedAST.Type): TypedAST.Term = {
      if (`type` eq BasicType.CHAR) return new TypedAST.CharacterValue(0.asInstanceOf[Char])
      if (`type` eq BasicType.BYTE) return new TypedAST.ByteValue(0.asInstanceOf[Byte])
      if (`type` eq BasicType.SHORT) return new TypedAST.ShortValue(0.asInstanceOf[Short])
      if (`type` eq BasicType.INT) return new TypedAST.IntValue(0)
      if (`type` eq BasicType.LONG) return new TypedAST.LongValue(0)
      if (`type` eq BasicType.FLOAT) return new TypedAST.FloatValue(0.0f)
      if (`type` eq BasicType.DOUBLE) return new TypedAST.DoubleValue(0.0)
      if (`type` eq BasicType.BOOLEAN) return new TypedAST.BoolValue(false)
      if (`type`.isObjectType) return new TypedAST.NullValue
      null
    }
  }

  abstract class Term(val location: Location) extends Node {
    def `type`: TypedAST.Type

    def isBasicType: Boolean = `type`.isBasicType

    def isArrayType: Boolean = `type`.isArrayType

    def isClassType: Boolean = `type`.isClassType

    def isNullType: Boolean = `type`.isNullType

    def isReferenceType: Boolean = `type`.isObjectType
  }

  class ExpressionActionStatement(location: Location, val term: TypedAST.Term) extends ActionStatement(location) {
    def this(term: TypedAST.Term) = {
      this(null, term)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class FieldDefinition(val location: Location, val modifier: Int, val affiliation: TypedAST.ClassType, val name: String, val `type`: TypedAST.Type)
    extends Node with FieldRef {
  }

  /**
   * @author Kota Mizushima
   */
  class RefField(location: Location, val target: TypedAST.Term, val field: TypedAST.FieldRef)  extends Term(location) {
    def this(target: TypedAST.Term, field: TypedAST.FieldRef) = {
      this(null, target, field)
    }

    def `type`: TypedAST.Type = field.`type`
  }

  class SetField(location: Location, val target: TypedAST.Term, val field: TypedAST.FieldRef, val value: TypedAST.Term) extends Term(location) {
    def this(target: TypedAST.Term, field: TypedAST.FieldRef, value: TypedAST.Term) = {
      this(null, target, field, value)
    }

    def `type`: TypedAST.Type = field.`type`
  }

  class FloatValue (location: Location, val value: Float)  extends Term(location) {
    def this(value: Float) = {
      this(null, value)
    }

    def `type`: TypedAST.Type = BasicType.FLOAT
  }

  class IfStatement(location: Location, val condition: TypedAST.Term, val thenStatement: TypedAST.ActionStatement, val elseStatement: TypedAST.ActionStatement)
    extends ActionStatement(location) {

    def this(condition: TypedAST.Term, thenStatement: TypedAST.ActionStatement, elseStatement: TypedAST.ActionStatement) = {
      this(null, condition, thenStatement, elseStatement)
    }

    def getCondition: TypedAST.Term = condition

    def getThenStatement: TypedAST.ActionStatement = thenStatement

    def getElseStatement: TypedAST.ActionStatement = elseStatement
  }

  class InstanceOf(location: Location, val target: TypedAST.Term, val checked: TypedAST.Type)  extends Term(location) {
    def this(target: TypedAST.Term, checked: TypedAST.Type) = {
      this(null, target, checked)
    }

    def `type`: TypedAST.Type = BasicType.BOOLEAN
  }

  class IntValue(location: Location, val value: Int)  extends Term(location) {
    def this(value: Int) = {
      this(null, value)
    }

    def `type`: TypedAST.Type =  BasicType.INT
  }

  class ListLiteral(location: Location, val elements: Array[TypedAST.Term], val `type`: TypedAST.Type)  extends Term(location) {
    def this(elements: Array[TypedAST.Term], `type`: TypedAST.Type) = {
      this(null, elements, `type`)
    }

    def getElements: Array[TypedAST.Term] = elements
  }

  class RefLocal(location: Location, val frame: Int, val index: Int, val `type`: TypedAST.Type)  extends Term(location) {
    def this(bind: ClosureLocalBinding) = {
      this(null, bind.frameIndex, bind.index, bind.tp)
    }

    def this(frame: Int, index: Int, `type`: TypedAST.Type) = {
      this(null, frame, index, `type`)
    }
  }

  class SetLocal(location: Location, val frame: Int, val index: Int, val `type`: TypedAST.Type, val value: TypedAST.Term) extends Term(location) {
    def this(bind: ClosureLocalBinding, value: TypedAST.Term) = {
      this(null, bind.frameIndex, bind.index, bind.tp, value)
    }

    def this(frame: Int, index: Int, `type`: TypedAST.Type, value: TypedAST.Term) = {
      this(null, frame, index, `type`, value)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class NewClosure(location: Location, val `type`: TypedAST.ClassType, val method: TypedAST.Method, val block: TypedAST.ActionStatement) extends Term(location) {
    var frame: LocalFrame = _

    def this(`type`: TypedAST.ClassType, method: TypedAST.Method, block: TypedAST.ActionStatement) = {
      this(null, `type`, method, block)
    }

    def modifier: Int = method.modifier

    def classType: TypedAST.ClassType = `type`

    def name: String = method.name

    def arguments: Array[TypedAST.Type] = method.arguments

    def returnType: TypedAST.Type =  method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class LongValue (location: Location, val value: Long) extends Term(location) {
    def this(value: Long) = {
      this(null, value)
    }

    def `type`: TypedAST.Type = BasicType.LONG
  }

  /**
   * @author Kota Mizushima
   */
  class ConditionalLoop(location: Location, val condition: TypedAST.Term, val stmt: TypedAST.ActionStatement) extends ActionStatement(location) {
    def this(condition: TypedAST.Term, stmt: TypedAST.ActionStatement) = {
      this(null, condition, stmt)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class Member(modifier: Int, classType: TypedAST.Type) extends Node {
    def getClassType: TypedAST.Type = classType

    def getModifier: Int = modifier
  }

  /**
   * @author Kota Mizushima
   */
  class MethodDefinition(
    val location: Location,
    val modifier: Int,
    val classType: TypedAST.ClassType,
    val name: String,
    val arguments: Array[TypedAST.Type],
    val returnType: TypedAST.Type,
    var block: TypedAST.StatementBlock,
    override val typeParameters: Array[TypedAST.TypeParameter] = Array()
  ) extends Node with Method {
    private var closure: Boolean = false
    private var frame: LocalFrame = _

    def affiliation: TypedAST.ClassType = classType

    def getBlock: TypedAST.StatementBlock = block

    def setBlock(block: TypedAST.StatementBlock): Unit =  this.block = block

    def setClosure(closure: Boolean): Unit =  this.closure = closure

    def hasClosure: Boolean = closure

    def setFrame(frame: LocalFrame): Unit =  this.frame = frame

    def getFrame: LocalFrame = frame

  }

  /**
   * @author Kota Mizushima
   */
  class NewObject(location: Location, val constructor: TypedAST.ConstructorRef, val parameters: Array[TypedAST.Term]) extends Term(location) {
    def this(constructor: TypedAST.ConstructorRef, parameters: Array[TypedAST.Term]) = {
      this(null, constructor, parameters)
    }

    def `type`: TypedAST.Type = constructor.affiliation
  }

  class NewArray(location: Location, val arrayType: TypedAST.ArrayType, val parameters: Array[TypedAST.Term]) extends Term(location) {
    def this(arrayType: TypedAST.ArrayType, parameters: Array[TypedAST.Term]) = {
      this(null, arrayType, parameters)
    }

    def `type`: TypedAST.Type = arrayType
  }

  class NOP(location: Location) extends ActionStatement(location) {
    def this() = {
      this(null)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class NullValue(location: Location) extends Term(location) {
    def this() = {
      this(null)
    }

    def `type`: TypedAST.Type = NullType.NULL
  }

  /**
   * @author Kota Mizushima
   */
  class Return(location: Location, val term: TypedAST.Term) extends ActionStatement(location) {
    def this(term: TypedAST.Term) = {
      this(null, term)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class ShortValue(location: Location, val value: Short) extends Term(location) {
    def this(value: Short) = {
      this(null, value)
    }

    def `type`: TypedAST.Type = BasicType.SHORT
  }

  abstract class ActionStatement(val location: Location)

  class RefStaticField(location: Location, val target: TypedAST.ClassType, val field: TypedAST.FieldRef)  extends Term(location) {

    def this(target: TypedAST.ClassType, field: TypedAST.FieldRef) = {
      this(null, target, field)
    }

    def `type`: TypedAST.Type = field.`type`
  }

  /**
   * @author Kota Mizushima
   */
  class SetStaticField(location: Location, val target: TypedAST.ObjectType, val field: TypedAST.FieldRef, val value: TypedAST.Term) extends Term(location) {
    def this(target: TypedAST.ObjectType, field: TypedAST.FieldRef, value: TypedAST.Term) = {
      this(null, target, field, value)
    }

    def `type`: TypedAST.Type = field.`type`
  }

  /**
   * @author Kota Mizushima
   */
  class StringValue(location: Location, val value: String, val `type`: TypedAST.Type) extends Term(location) {
    def this(value: String, `type`: TypedAST.Type) = {
      this(null, value, `type`)
    }
  }

  class Super(val classType: TypedAST.ClassType, val arguments: Array[TypedAST.Type], val terms: Array[TypedAST.Term]) extends Node

  class Synchronized(location: Location, val term: TypedAST.Term, val statement: TypedAST.ActionStatement)  extends ActionStatement(location) {
    def this(term: TypedAST.Term, statement: TypedAST.ActionStatement) = {
      this(null, term, statement)
    }
  }

  class OuterThis(location: Location, val `type`: TypedAST.ClassType)  extends Term(location) {
    def this(classType: TypedAST.ClassType) = {
      this(null, classType)
    }
  }

  class This(location: Location, val `type`: TypedAST.ClassType) extends Term(location) {
    def this(classType: TypedAST.ClassType) = {
      this(null, classType)
    }
  }

  class Throw(location: Location, val term: TypedAST.Term)  extends ActionStatement(location) {
    def this(term: TypedAST.Term) = {
      this(null, term)
    }
  }

  class Try(location: Location, var tryStatement: TypedAST.ActionStatement, var catchTypes: Array[ClosureLocalBinding], var catchStatements: Array[TypedAST.ActionStatement]) extends ActionStatement(location) {
    def this(tryStatement: TypedAST.ActionStatement, catchTypes: Array[ClosureLocalBinding], catchStatements: Array[TypedAST.ActionStatement]) = {
      this(null, tryStatement, catchTypes, catchStatements)
    }
  }

  object UnaryTerm {

    object Constants {
      final val PLUS: Int = 0
      final val MINUS: Int = 1
      final val NOT: Int = 2
      final val BIT_NOT: Int = 3
    }

  }

  class UnaryTerm(location: Location, val kind: Int, val `type`: TypedAST.Type, val operand: TypedAST.Term)  extends Term(location) {
    def this(kind: Int, `type`: TypedAST.Type, operand: TypedAST.Term) = {
      this(null, kind, `type`, operand)
    }
  }

  case class TypeParameter(name: String, upperBound: Option[TypedAST.Type])

  abstract class AbstractClassType extends AbstractObjectType with ClassType {
    private val constructorRefFinder: TypedAST.ConstructorFinder = new TypedAST.ConstructorFinder()

    def findConstructor(params: Array[TypedAST.Term]): Array[TypedAST.ConstructorRef] = constructorRefFinder.find(this, params)

    def isClassType: Boolean = true

    def isArrayType: Boolean = false
  }

  /**
   * @author Kota Mizushima
   */
  abstract class AbstractObjectType extends ObjectType {
    private var methodRefFinder: TypedAST.MethodFinder = new TypedAST.MethodFinder
    private var fieldRefFinder: TypedAST.FieldFinder   = new TypedAST.FieldFinder

    def findField(name: String): TypedAST.FieldRef = fieldRefFinder.find(this, name)

    def findMethod(name: String, params: Array[TypedAST.Term]): Array[TypedAST.Method] = {
      methodRefFinder.find(this, name, params)
    }

    def isBasicType: Boolean =  false

    def isNullType: Boolean =  false

    def isObjectType: Boolean =  true
  }

  /**
   * @author Kota Mizushima
   */
  class ArrayType(val component: TypedAST.Type, val dimension: Int, table: ClassTable) extends AbstractObjectType {
    val superClass: TypedAST.ClassType        = table.load("java.lang.Object")
    val interfaces: Seq[TypedAST.ClassType] = Seq(table.load("java.io.Serializable"), table.load("java.lang.Cloneable"))
    var name: String                     = "[" * dimension + component.name

    def base: TypedAST.Type =  if (dimension == 1) component else table.loadArray(component, dimension - 1)

    def isInterface: Boolean = false

    def modifier: Int = 0

    def methods: Seq[TypedAST.Method] = superClass.methods

    def methods(name: String): Array[TypedAST.Method] =  superClass.methods(name)

    def fields: Array[TypedAST.FieldRef] = superClass.fields

    def field(name: String): TypedAST.FieldRef = superClass.field(name)

    def isArrayType: Boolean =  true

    def isClassType: Boolean =  false
  }

  object BasicType {
    final val BYTE: TypedAST.BasicType    = new TypedAST.BasicType("byte")
    final val SHORT: TypedAST.BasicType   = new TypedAST.BasicType("short")
    final val CHAR: TypedAST.BasicType    = new TypedAST.BasicType("char")
    final val INT: TypedAST.BasicType     = new TypedAST.BasicType("int")
    final val LONG: TypedAST.BasicType    = new TypedAST.BasicType("long")
    final val FLOAT: TypedAST.BasicType   = new TypedAST.BasicType("float")
    final val DOUBLE: TypedAST.BasicType  = new TypedAST.BasicType("double")
    final val BOOLEAN: TypedAST.BasicType = new TypedAST.BasicType("boolean")
    final val VOID: TypedAST.BasicType    = new TypedAST.BasicType("void")
  }

  class BasicType private(name_ : String) extends Type {
    import BasicType._
    def name: String = name_

    def isNumeric: Boolean = isInteger && isReal

    def isInteger: Boolean = (this eq BYTE) || (this eq SHORT) || (this eq INT) || (this eq LONG)

    def isReal: Boolean = (this eq FLOAT) || (this eq DOUBLE)

    def isBoolean: Boolean = this eq BOOLEAN

    def isArrayType: Boolean = false

    def isBasicType: Boolean = true

    def isClassType: Boolean = false

    def isNullType: Boolean =  false

    def isObjectType: Boolean = false
  }

  final val BASIC_TYPE_BYTE = BasicType.BYTE
  final val BASIC_TYPE_SHORT = BasicType.SHORT
  final val BASIC_TYPE_CHAR = BasicType.CHAR
  final val BASIC_TYPE_INT = BasicType.INT
  final val BASIC_TYPE_LONG = BasicType.LONG
  final val BASIC_TYPE_FLOAT = BasicType.FLOAT
  final val BASIC_TYPE_DOUBLE = BasicType.DOUBLE
  final val BASIC_TYPE_BOOLEAN = BasicType.BOOLEAN
  final val BASIC_TYPE_VOID = BasicType.VOID

  abstract sealed trait ClassType extends ObjectType {
    def constructors: Array[TypedAST.ConstructorRef]

    def findConstructor(params: Array[TypedAST.Term]): Array[TypedAST.ConstructorRef]
  }

  class ConstructorFinder {
    def find(target: TypedAST.ClassType, args: Array[TypedAST.Term]): Array[TypedAST.ConstructorRef] = {
      val constructors: Set[TypedAST.ConstructorRef] = new TreeSet[TypedAST.ConstructorRef](new TypedAST.ConstructorComparator)
      if (target == null) return new Array[TypedAST.ConstructorRef](0)
      val cs: Array[TypedAST.ConstructorRef] = target.constructors
      var i: Int = 0
      while (i < cs.length) {
        val c: TypedAST.ConstructorRef = cs(i)
        if (matcher.matches(c.getArgs, args)) constructors.add(c)
        i += 1;
      }
      val selected: List[TypedAST.ConstructorRef] = new ArrayList[TypedAST.ConstructorRef]
      selected.addAll(constructors)
      Collections.sort(selected, sorter)
      if (selected.size < 2) {
        return selected.toArray(new Array[TypedAST.ConstructorRef](0))
      }
      val constructor1: TypedAST.ConstructorRef = selected.get(0)
      val constructor2: TypedAST.ConstructorRef = selected.get(1)
      if (isAmbiguous(constructor1, constructor2)) {
        return selected.toArray(new Array[TypedAST.ConstructorRef](0))
      }
      Array[TypedAST.ConstructorRef](constructor1)
    }

    private def isAmbiguous(constructor1: TypedAST.ConstructorRef, constructor2: TypedAST.ConstructorRef): Boolean = {
      sorter.compare(constructor1, constructor2) >= 0
    }

    private def isAllSuperType(arg1: Array[TypedAST.Type], arg2: Array[TypedAST.Type]): Boolean = {
      var i: Int = 0
      while (i < arg1.length) {
        if (!TypeRules.isSuperType(arg1(i), arg2(i))) return false
        i += 1
      }
      true
    }

    private final val sorter: Comparator[TypedAST.ConstructorRef] = new Comparator[TypedAST.ConstructorRef] {
      def compare(c1: TypedAST.ConstructorRef, c2: TypedAST.ConstructorRef): Int = {
        val arg1: Array[TypedAST.Type] = c1.getArgs
        val arg2: Array[TypedAST.Type] = c2.getArgs
        if (isAllSuperType(arg2, arg1)) return -1
        if (isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: TypedAST.ParameterMatcher = new TypedAST.StandardParameterMatcher
  }

  trait ConstructorRef extends MemberRef {
    def affiliation: TypedAST.ClassType

    def getArgs: Array[TypedAST.Type]
  }

  /**
   * @author Kota Mizushima
   */
  class ConstructorComparator extends Comparator[TypedAST.ConstructorRef] {
    def compare(c1: TypedAST.ConstructorRef, c2: TypedAST.ConstructorRef): Int = {
      val args1: Array[TypedAST.Type] = c1.getArgs
      val args2: Array[TypedAST.Type] = c2.getArgs
      val result: Int = args1.length - args2.length
      if (result != 0) {
        return result
      }
      var i: Int = 0
      while (i < args1.length) {
        if (args1(i) ne args2(i)) return args1(i).name.compareTo(args2(i).name)
        i += 1;
      }
      0
    }
  }

  class FieldFinder {
    def find(target: TypedAST.ObjectType, name: String): TypedAST.FieldRef = {
      if (target == null) return null
      var field: TypedAST.FieldRef = target.field(name)
      if (field != null) return field
      field = find(target.superClass, name)
      if (field != null) return field
      val interfaces = target.interfaces
      for (anInterface <- target.interfaces) {
        field = find(anInterface, name)
        if (field != null) return field
      }
      null
    }
  }

  trait FieldRef extends MemberRef {
    def modifier: Int

    def affiliation: TypedAST.ClassType

    def `type`: TypedAST.Type
  }

  class FieldComparator extends Comparator[TypedAST.FieldRef] {
    def compare(o1: TypedAST.FieldRef, o2: TypedAST.FieldRef): Int =  o1.name.compareTo(o2.name)
  }

  trait MemberRef extends Named {
    def modifier: Int

    def affiliation: TypedAST.ClassType

    def name: String
  }

  /**
   * @author Kota Mizushima
   */
  object MethodFinder {
    private def isAllSuperType(arg1: Array[TypedAST.Type], arg2: Array[TypedAST.Type]): Boolean = {
      var i: Int = 0
      while (i < arg1.length) {
        if (!TypeRules.isSuperType(arg1(i), arg2(i))) return false
        i += 1
      }
      true
    }
  }

  class MethodFinder {
    import MethodFinder._
    def find(target: TypedAST.ObjectType, name: String, arguments: Array[TypedAST.Term]): Array[TypedAST.Method] = {
      val methods: Set[TypedAST.Method] = new TreeSet[TypedAST.Method](new TypedAST.MethodComparator)
      find(methods, target, name, arguments)
      val selectedMethods: List[TypedAST.Method] = new ArrayList[TypedAST.Method]
      selectedMethods.addAll(methods)
      Collections.sort(selectedMethods, sorter)
      if (selectedMethods.size < 2) {
        return selectedMethods.toArray(new Array[TypedAST.Method](0)).asInstanceOf[Array[TypedAST.Method]]
      }
      val method1: TypedAST.Method = selectedMethods.get(0)
      val method2: TypedAST.Method = selectedMethods.get(1)
      if (isAmbiguous(method1, method2)) {
        return selectedMethods.toArray(new Array[TypedAST.Method](0)).asInstanceOf[Array[TypedAST.Method]]
      }
      Array[TypedAST.Method](method1)
    }

    def isAmbiguous(method1: TypedAST.Method, method2: TypedAST.Method): Boolean =  sorter.compare(method1, method2) >= 0

    private def find(methods: Set[TypedAST.Method], target: TypedAST.ObjectType, name: String, params: Array[TypedAST.Term]): Unit = {
      if (target == null) return
      val ms: Array[TypedAST.Method] = target.methods(name)
      for (m <- target.methods(name)) {
        if (matcher.matches(m.arguments, params)) methods.add(m)
      }
      val superClass: TypedAST.ClassType = target.superClass
      find(methods, superClass, name, params)
      val interfaces = target.interfaces
      for (anInterface <- interfaces) {
        find(methods, anInterface, name, params)
      }
    }

    private final val sorter: Comparator[TypedAST.Method] = new Comparator[TypedAST.Method] {
      def compare(m1: TypedAST.Method, m2: TypedAST.Method): Int = {
        val arg1: Array[TypedAST.Type] = m1.arguments
        val arg2: Array[TypedAST.Type] = m2.arguments
        if (isAllSuperType(arg2, arg1)) return -1
        if (isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: TypedAST.ParameterMatcher  = new TypedAST.StandardParameterMatcher
  }

  trait Method extends MemberRef {
    def affiliation: TypedAST.ClassType

    def arguments: Array[TypedAST.Type]

    def returnType: TypedAST.Type

    def typeParameters: Array[TypedAST.TypeParameter] = Array()
  }

  class MethodComparator extends Comparator[TypedAST.Method] {
    def compare(m1: TypedAST.Method, m2: TypedAST.Method): Int = {
      var result: Int = m1.name.compareTo(m2.name)
      if (result != 0) return result
      val args1: Array[TypedAST.Type] = m1.arguments
      val args2: Array[TypedAST.Type] = m2.arguments
      result = args1.length - args2.length
      if (result != 0) return result
      var i: Int = 0
      while (i < args1.length) {
        if (args1(i) ne args2(i)) return args1(i).name.compareTo(args2(i).name)
        i += 1
      }
      0
    }
  }

  object NullType {
    val NULL: TypedAST.NullType = new TypedAST.NullType("null")
  }

  class NullType (val name: String) extends Type {
    def isArrayType: Boolean = false

    def isBasicType: Boolean = false

    def isClassType: Boolean = false

    def isNullType: Boolean = true

    def isObjectType: Boolean = false
  }

  /**
   * @author Kota Mizushima
   */
  sealed trait ObjectType extends Type {
    def isInterface: Boolean

    def modifier: Int

    def superClass: TypedAST.ClassType

    def interfaces: Seq[TypedAST.ClassType]

    def methods: Seq[TypedAST.Method]

    def methods(name: String): Array[TypedAST.Method]

    def fields: Array[TypedAST.FieldRef]

    def field(name: String): TypedAST.FieldRef

    def findMethod(name: String, params: Array[TypedAST.Term]): Array[TypedAST.Method]
  }

  /**
   * @author Kota Mizushima
   */
  trait ParameterMatcher {
    def matches(arguments: Array[TypedAST.Type], parameters: Array[TypedAST.Term]): Boolean
  }

  /**
   * @author Kota Mizushima
   */
  class StandardParameterMatcher extends ParameterMatcher {
    def matches(arguments: Array[TypedAST.Type], parameters: Array[TypedAST.Term]): Boolean = {
      if (arguments.length != parameters.length) return false
      val parameterTypes: Array[TypedAST.Type] = new Array[TypedAST.Type](parameters.length)
      var i: Int = 0
      while (i < parameters.length) {
        parameterTypes(i) = parameters(i).`type`
        i += 1
      }
      i = 0
      while (i < arguments.length) {
        if (!TypeRules.isSuperType(arguments(i), parameterTypes(i))) return false
        i += 1;
      }
      true
    }
  }

  abstract sealed class Type {
    def name: String

    def isBasicType: Boolean

    def isClassType: Boolean

    def isNullType: Boolean

    def isArrayType: Boolean

    def isObjectType: Boolean
  }

  object TypeRules {
    def isSuperType(left: TypedAST.Type, right: TypedAST.Type): Boolean = {
      if (left.isBasicType) {
        if (right.isBasicType) {
          return isSuperTypeForBasic(left.asInstanceOf[TypedAST.BasicType], right.asInstanceOf[TypedAST.BasicType])
        }
        return false
      }
      if (left.isClassType) {
        if (right.isClassType) {
          return isSuperTypeForClass(left.asInstanceOf[TypedAST.ClassType], right.asInstanceOf[TypedAST.ClassType])
        }
        if (right.isArrayType) {
          return left eq right.asInstanceOf[TypedAST.ArrayType].superClass
        }
        if (right.isNullType) {
          return true
        }
        return false
      }
      if (left.isArrayType) {
        if (right.isArrayType) {
          return isSuperTypeForArray(left.asInstanceOf[TypedAST.ArrayType], right.asInstanceOf[TypedAST.ArrayType])
        }
        if (right.isNullType) {
          return true
        }
        return false
      }
      false
    }

    def isAssignable(left: TypedAST.Type, right: TypedAST.Type): Boolean = isSuperType(left, right)

    private def isSuperTypeForArray(left: TypedAST.ArrayType, right: TypedAST.ArrayType): Boolean = isSuperType(left.base, right.base)

    private def isSuperTypeForClass(left: TypedAST.ClassType, right: TypedAST.ClassType): Boolean = {
      if (right == null) return false
      if (left eq right) return true
      if (isSuperTypeForClass(left, right.superClass)) return true
      var i: Int = 0
      while (i < right.interfaces.length) {
        if (isSuperTypeForClass(left, right.interfaces(i))) return true
        i += 1
      }
      false
    }

    private def isSuperTypeForBasic(left: TypedAST.BasicType, right: TypedAST.BasicType): Boolean = {
      if (left eq BasicType.DOUBLE) {
        return ((right eq BasicType.CHAR) || (right eq BasicType.BYTE) || (right eq BasicType.SHORT) || (right eq BasicType.INT) || (right eq BasicType.LONG) || (right eq BasicType.FLOAT) || (right eq BasicType.DOUBLE))
      }
      if (left eq BasicType.FLOAT) {
        return ((right eq BasicType.CHAR) || (right eq BasicType.BYTE) || (right eq BasicType.SHORT) || (right eq BasicType.INT) || (right eq BasicType.LONG) || (right eq BasicType.FLOAT))
      }
      if (left eq BasicType.LONG) {
        return ((right eq BasicType.CHAR) || (right eq BasicType.BYTE) || (right eq BasicType.SHORT) || (right eq BasicType.INT) || (right eq BasicType.LONG))
      }
      if (left eq BasicType.INT) {
        return ((right eq BasicType.CHAR) || (right eq BasicType.BYTE) || (right eq BasicType.SHORT) || (right eq BasicType.INT))
      }
      if (left eq BasicType.SHORT) {
        return  ((right eq BasicType.BYTE) || (right eq BasicType.SHORT));
      }
      if ((left eq BasicType.BOOLEAN) && (right eq BasicType.BOOLEAN)) return true
      if ((left eq BasicType.BYTE) && (right eq BasicType.BYTE)) return true
      if ((left eq BasicType.CHAR) && (right eq BasicType.CHAR)) return true
      false
    }
  }

  class TypeRules
}

@deprecated("Use TypedAST", "0.1")
object IRT:
  export TypedAST.*
