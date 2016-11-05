package onion.compiler

import java.util._
import java.util

/**


 */
object IRT {

  /**
   * This interface represents an internal representation node of onion program.
   * @author Kota Mizushima
   */
  abstract sealed trait Node

  /**
   * @author Kota Mizushima
   */
  class ArrayLength(location: Location, val target: IRT.Term) extends Term(location) {
    def this(target: IRT.Term) {
      this(null, target)
    }

    def `type`: IRT.Type = BasicType.INT
  }

  /**
   * @author Kota Mizushima
   */
  class RefArray(location: Location, val target: IRT.Term, val index: IRT.Term) extends Term(location) {
    def this(target: IRT.Term, index: IRT.Term) {
      this(null, target, index)
    }

    def `type`: IRT.Type = (target.`type`.asInstanceOf[IRT.ArrayType]).base
  }

  /**
   * @author Kota Mizushima
   */
  class SetArray(location: Location, val target: IRT.Term, val index: IRT.Term, val value: IRT.Term) extends Term(location) {
    def this(target: IRT.Term, index: IRT.Term, value: IRT.Term) {
      this(null, target, index, value)
    }

    def `type`: IRT.Type = value.`type`

    def `object`: IRT.Term = target
  }

  /**
   * @author Kota Mizushima
   */
  class Begin(location: Location, val terms: Array[IRT.Term]) extends Term(location) {
    def this(terms: Array[IRT.Term]) {
      this(null, terms)
    }

    def this(expressions: List[_]) {
      this(expressions.toArray(new Array[IRT.Term](0)).asInstanceOf[Array[IRT.Term]])
    }

    def this(term: IRT.Term) {
      this(Array[IRT.Term](term))
    }

    def this(expression1: IRT.Term, expression2: IRT.Term) {
      this(Array[IRT.Term](expression1, expression2))
    }

    def this(expression1: IRT.Term, expression2: IRT.Term, expression3: IRT.Term) {
      this(Array[IRT.Term](expression1, expression2, expression3))
    }

    def `type`: IRT.Type = terms(terms.length - 1).`type`
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

  class BinaryTerm(location: Location, val kind: Int, val `type`: IRT.Type, val lhs: IRT.Term, val rhs: IRT.Term) extends Term(location) {
    def this(kind: Int, `type`: IRT.Type, lhs: IRT.Term, rhs: IRT.Term) {
      this(null, kind, `type`, lhs, rhs)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class StatementBlock(location: Location, newStatements : IRT.ActionStatement*) extends ActionStatement(location) {
    def this(newStatements: IRT.ActionStatement*) {
      this(null: Location, newStatements : _*)
    }

    def this(newStatements: util.List[IRT.ActionStatement]) {
      this(newStatements.toArray(new Array[IRT.ActionStatement](0)): _*)
    }

    def statements: Array[IRT.ActionStatement] = newStatements.toArray
  }

  /**
   * @author Kota Mizushima
   */
  class BoolValue(location: Location, val value: Boolean) extends Term(location) {
    def this(value: Boolean) {
      this(null, value)
    }

    def `type`: IRT.Type = BasicType.BOOLEAN
  }

  /**
   * @author Kota Mizushima
   */
  class Break(location: Location) extends ActionStatement(location) {
    def this() {
      this(null)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class ByteValue(location: Location, val value: Byte) extends Term(location) {
    def this(value: Byte) {
      this(null, value)
    }

    def `type`: IRT.Type = BasicType.BYTE
  }

  /**
   * @author Kota Mizushima
   */
  class Call(location: Location, val target: IRT.Term, val method: IRT.Method, val parameters: Array[IRT.Term]) extends Term(location) {
    def this(target: IRT.Term, method: IRT.Method, parameters: Array[IRT.Term]) {
      this(null, target, method, parameters)
    }

    def `type`: IRT.Type = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class CallStatic(location: Location, val target: IRT.ObjectType, val method: IRT.Method, val parameters: Array[IRT.Term]) extends Term(location) {
    def this(target: IRT.ObjectType, method: IRT.Method, parameters: Array[IRT.Term]) {
      this(null, target, method, parameters)
    }
    def `type`: IRT.Type = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class CallSuper(location: Location, val target: IRT.Term, val method: IRT.Method, val params: Array[IRT.Term]) extends Term(location) {
    def this(target: IRT.Term, method: IRT.Method, params: Array[IRT.Term]) {
      this(null, target, method, params)
    }

    def `type`: IRT.Type = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class AsInstanceOf(location: Location, val target: IRT.Term, val destination: IRT.Type) extends Term(location) {
    def this(target: IRT.Term, destination: IRT.Type) {
      this(null, target, destination)
    }

    def `type`: IRT.Type = destination
  }

  /**
   * @author Kota Mizushima
   */
  class CharacterValue(location: Location, val value: Char) extends Term(location) {
    def this(value: Char) {
      this(null, value)
    }

    def `type`: IRT.Type = BasicType.CHAR
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
    def newInterface(location: Location, modifier: Int, name: String, interfaces: Array[IRT.ClassType]): IRT.ClassDefinition = {
      new IRT.ClassDefinition(location, true, modifier, name, null, interfaces)
    }

    /**
     * This method creates class definition.
     * @param modifier
     * @param name
     * @param superClass
     * @param interfaces
     * @return
     */
    def newClass(location: Location, modifier: Int, name: String, superClass: IRT.ClassType, interfaces: Array[IRT.ClassType]): IRT.ClassDefinition = {
      new IRT.ClassDefinition(location, false, modifier, name, superClass, interfaces)
    }

    /**
     * This method creates class definition.
     * @param modifier
     * @param name
     * @return
     */
    def newClass(modifier: Int, name: String): IRT.ClassDefinition =  new IRT.ClassDefinition(null, false, modifier, name, null, null)
  }

  class ClassDefinition(val location: Location, val isInterface: Boolean, val modifier: Int, val name: String, var superClass: IRT.ClassType, var interfaces: Seq[IRT.ClassType])
    extends IRT.AbstractClassType() with Node with Named {

    def constructors: Array[IRT.ConstructorRef] = constructors_.toArray(new Array[IRT.ConstructorRef](0))
    def methods: Seq[IRT.Method] = methods_.values.toSeq
    def fields: Array[IRT.FieldRef] = fields_.values.toArray

    val fields_ : OrderedTable[IRT.FieldRef]     = new OrderedTable[IRT.FieldRef]
    val methods_ : MultiTable[IRT.Method]        = new MultiTable[IRT.Method]
    val constructors_ : List[IRT.ConstructorRef] = new ArrayList[IRT.ConstructorRef]
    var isResolutionComplete: Boolean            = false
    private var sourceFile: String = null

    def this() {
      this(null: Location, false, 0, null: String, null: IRT.ClassType, null)
    }

    def setSuperClass(superClass: IRT.ClassType) {
      this.superClass = superClass
    }

    def setInterfaces(interfaces: Array[IRT.ClassType]) {
      this.interfaces = interfaces
    }

    def setResolutionComplete(isInResolution: Boolean) {
      this.isResolutionComplete = isInResolution
    }

    def add(method: IRT.Method) {
      methods_.add(method)
    }

    def add(field: IRT.FieldRef) {
      fields_.add(field)
    }

    def add(constructor: IRT.ConstructorRef) {
      constructors_.add(constructor)
    }

    def addDefaultConstructor {
      constructors_.add(ConstructorDefinition.newDefaultConstructor(this))
    }

    def methods(name: String): Array[IRT.Method] = methods_.get(name).toArray

    def field(name: String): IRT.FieldRef = fields_.get(name).getOrElse(null)

    def setSourceFile(sourceFile: String): Unit = this.sourceFile = sourceFile

    def getSourceFile: String = sourceFile
  }

  /**
   * @author Kota Mizushima
   */
  object ConstructorDefinition {
    def newDefaultConstructor(`type`: IRT.ClassType): IRT.ConstructorDefinition = {
      val block: IRT.StatementBlock = new IRT.StatementBlock(new IRT.Return(null))
      val init: IRT.Super = new IRT.Super(`type`.superClass, new Array[IRT.Type](0), new Array[IRT.Term](0))
      val node: IRT.ConstructorDefinition = new IRT.ConstructorDefinition(Modifier.PUBLIC, `type`, new Array[IRT.Type](0), block, init)
      node.frame = new LocalFrame(null)
      node
    }
  }

  class ConstructorDefinition(val location: Location, val modifier: Int, val classType: IRT.ClassType, val arguments: Array[IRT.Type], var block: IRT.StatementBlock, var superInitializer: IRT.Super) extends
  Node with IRT.ConstructorRef {
    def this(modifier: Int, classType: IRT.ClassType, arguments: Array[IRT.Type], block: IRT.StatementBlock, superInitializer: IRT.Super) {
      this(null, modifier, classType, arguments, block, superInitializer)
    }

    def name: String =  "new"

    def getArgs: Array[IRT.Type] = arguments

    def affiliation: IRT.ClassType = classType

    var frame: LocalFrame = null
  }

  /**
   * @author Kota Mizushima
   */
  class Continue(location: Location) extends IRT.ActionStatement(location) {
    def this() {
      this(null)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class DoubleValue(location: Location, val value: Double) extends Term(location) {
    def this(value: Double) {
      this(null, value)
    }

    def `type`: IRT.Type = BasicType.DOUBLE
  }

  /**
   * @author Kota Mizushima
   */
  object Term {
    def defaultValue(`type`: IRT.Type): IRT.Term = {
      if (`type` eq BasicType.CHAR) return new IRT.CharacterValue(0.asInstanceOf[Char])
      if (`type` eq BasicType.BYTE) return new IRT.ByteValue(0.asInstanceOf[Byte])
      if (`type` eq BasicType.SHORT) return new IRT.ShortValue(0.asInstanceOf[Short])
      if (`type` eq BasicType.INT) return new IRT.IntValue(0)
      if (`type` eq BasicType.LONG) return new IRT.LongValue(0)
      if (`type` eq BasicType.FLOAT) return new IRT.FloatValue(0.0f)
      if (`type` eq BasicType.DOUBLE) return new IRT.DoubleValue(0.0)
      if (`type` eq BasicType.BOOLEAN) return new IRT.BoolValue(false)
      if (`type`.isObjectType) return new IRT.NullValue
      null
    }
  }

  abstract class Term(val location: Location) extends Node {
    def `type`: IRT.Type

    def isBasicType: Boolean = `type`.isBasicType

    def isArrayType: Boolean = `type`.isArrayType

    def isClassType: Boolean = `type`.isClassType

    def isNullType: Boolean = `type`.isNullType

    def isReferenceType: Boolean = `type`.isObjectType
  }

  class ExpressionActionStatement(location: Location, val term: IRT.Term) extends ActionStatement(location) {
    def this(term: IRT.Term) {
      this(null, term)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class FieldDefinition(val location: Location, val modifier: Int, val affiliation: IRT.ClassType, val name: String, val `type`: IRT.Type)
    extends Node with FieldRef {
  }

  /**
   * @author Kota Mizushima
   */
  class RefField(location: Location, val target: IRT.Term, val field: IRT.FieldRef)  extends Term(location) {
    def this(target: IRT.Term, field: IRT.FieldRef) {
      this(null, target, field)
    }

    def `type`: IRT.Type = field.`type`
  }

  class SetField(location: Location, val target: IRT.Term, val field: IRT.FieldRef, val value: IRT.Term) extends Term(location) {
    def this(target: IRT.Term, field: IRT.FieldRef, value: IRT.Term) {
      this(null, target, field, value)
    }

    def `type`: IRT.Type = field.`type`
  }

  class FloatValue (location: Location, val value: Float)  extends Term(location) {
    def this(value: Float) {
      this(null, value)
    }

    def `type`: IRT.Type = BasicType.FLOAT
  }

  class IfStatement(location: Location, val condition: IRT.Term, val thenStatement: IRT.ActionStatement, val elseStatement: IRT.ActionStatement)
    extends ActionStatement(location) {

    def this(condition: IRT.Term, thenStatement: IRT.ActionStatement, elseStatement: IRT.ActionStatement) {
      this(null, condition, thenStatement, elseStatement)
    }

    def getCondition: IRT.Term = condition

    def getThenStatement: IRT.ActionStatement = thenStatement

    def getElseStatement: IRT.ActionStatement = elseStatement
  }

  class InstanceOf(location: Location, val target: IRT.Term, val checked: IRT.Type)  extends Term(location) {
    def this(target: IRT.Term, checked: IRT.Type) {
      this(null, target, checked)
    }

    def `type`: IRT.Type = BasicType.BOOLEAN
  }

  class IntValue(location: Location, val value: Int)  extends Term(location) {
    def this(value: Int) {
      this(null, value)
    }

    def `type`: IRT.Type =  BasicType.INT
  }

  class ListLiteral(location: Location, val elements: Array[IRT.Term], val `type`: IRT.Type)  extends Term(location) {
    def this(elements: Array[IRT.Term], `type`: IRT.Type) {
      this(null, elements, `type`)
    }

    def getElements: Array[IRT.Term] = elements
  }

  class RefLocal(location: Location, val frame: Int, val index: Int, val `type`: IRT.Type)  extends Term(location) {
    def this(bind: ClosureLocalBinding) {
      this(null, bind.frameIndex, bind.index, bind.tp)
    }

    def this(frame: Int, index: Int, `type`: IRT.Type) {
      this(null, frame, index, `type`)
    }
  }

  class SetLocal(location: Location, val frame: Int, val index: Int, val `type`: IRT.Type, val value: IRT.Term) extends Term(location) {
    def this(bind: ClosureLocalBinding, value: IRT.Term) {
      this(null, bind.frameIndex, bind.index, bind.tp, value)
    }

    def this(frame: Int, index: Int, `type`: IRT.Type, value: IRT.Term) {
      this(null, frame, index, `type`, value)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class NewClosure(location: Location, val `type`: IRT.ClassType, val method: IRT.Method, val block: IRT.ActionStatement) extends Term(location) {
    var frame: LocalFrame = null

    def this(`type`: IRT.ClassType, method: IRT.Method, block: IRT.ActionStatement) {
      this(null, `type`, method, block)
    }

    def modifier: Int = method.modifier

    def classType: IRT.ClassType = `type`

    def name: String = method.name

    def arguments: Array[IRT.Type] = method.arguments

    def returnType: IRT.Type =  method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class LongValue (location: Location, val value: Long) extends Term(location) {
    def this(value: Long) {
      this(null, value)
    }

    def `type`: IRT.Type = BasicType.LONG
  }

  /**
   * @author Kota Mizushima
   */
  class ConditionalLoop(location: Location, val condition: IRT.Term, val stmt: IRT.ActionStatement) extends ActionStatement(location) {
    def this(condition: IRT.Term, stmt: IRT.ActionStatement) {
      this(null, condition, stmt)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class Member(modifier: Int, classType: IRT.Type) extends Node {
    def getClassType: IRT.Type = classType

    def getModifier: Int = modifier
  }

  /**
   * @author Kota Mizushima
   */
  class MethodDefinition(val location: Location, val modifier: Int, val classType: IRT.ClassType, val name: String, val arguments: Array[IRT.Type], val returnType: IRT.Type, var block: IRT.StatementBlock)
    extends Node with Method {
    private var closure: Boolean = false
    private var frame: LocalFrame = null

    def affiliation: IRT.ClassType = classType

    def getBlock: IRT.StatementBlock = block

    def setBlock(block: IRT.StatementBlock): Unit =  this.block = block

    def setClosure(closure: Boolean): Unit =  this.closure = closure

    def hasClosure: Boolean = closure

    def setFrame(frame: LocalFrame): Unit =  this.frame = frame

    def getFrame: LocalFrame = frame

  }

  /**
   * @author Kota Mizushima
   */
  class NewObject(location: Location, val constructor: IRT.ConstructorRef, val parameters: Array[IRT.Term]) extends Term(location) {
    def this(constructor: IRT.ConstructorRef, parameters: Array[IRT.Term]) {
      this(null, constructor, parameters)
    }

    def `type`: IRT.Type = constructor.affiliation
  }

  class NewArray(location: Location, val arrayType: IRT.ArrayType, val parameters: Array[IRT.Term]) extends Term(location) {
    def this(arrayType: IRT.ArrayType, parameters: Array[IRT.Term]) {
      this(null, arrayType, parameters)
    }

    def `type`: IRT.Type = arrayType
  }

  class NOP(location: Location) extends ActionStatement(location) {
    def this() {
      this(null)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class NullValue(location: Location) extends Term(location) {
    def this() {
      this(null)
    }

    def `type`: IRT.Type = NullType.NULL
  }

  /**
   * @author Kota Mizushima
   */
  class Return(location: Location, val term: IRT.Term) extends ActionStatement(location) {
    def this(term: IRT.Term) {
      this(null, term)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class ShortValue(location: Location, val value: Short) extends Term(location) {
    def this(value: Short) {
      this(null, value)
    }

    def `type`: IRT.Type = BasicType.SHORT
  }

  abstract class ActionStatement(val location: Location)

  class RefStaticField(location: Location, val target: IRT.ClassType, val field: IRT.FieldRef)  extends Term(location) {

    def this(target: IRT.ClassType, field: IRT.FieldRef) {
      this(null, target, field)
    }

    def `type`: IRT.Type = field.`type`
  }

  /**
   * @author Kota Mizushima
   */
  class SetStaticField(location: Location, val target: IRT.ObjectType, val field: IRT.FieldRef, val value: IRT.Term) extends Term(location) {
    def this(target: IRT.ObjectType, field: IRT.FieldRef, value: IRT.Term) {
      this(null, target, field, value)
    }

    def `type`: IRT.Type = field.`type`
  }

  /**
   * @author Kota Mizushima
   */
  class StringValue(location: Location, val value: String, val `type`: IRT.Type) extends Term(location) {
    def this(value: String, `type`: IRT.Type) {
      this(null, value, `type`)
    }
  }

  class Super(val classType: IRT.ClassType, val arguments: Array[IRT.Type], val terms: Array[IRT.Term]) extends Node

  class Synchronized(location: Location, val term: IRT.Term, val statement: IRT.ActionStatement)  extends ActionStatement(location) {
    def this(term: IRT.Term, statement: IRT.ActionStatement) {
      this(null, term, statement)
    }
  }

  class OuterThis(location: Location, val `type`: IRT.ClassType)  extends Term(location) {
    def this(classType: IRT.ClassType) {
      this(null, classType)
    }
  }

  class This(location: Location, val `type`: IRT.ClassType) extends Term(location) {
    def this(classType: IRT.ClassType) {
      this(null, classType)
    }
  }

  class Throw(location: Location, val term: IRT.Term)  extends ActionStatement(location) {
    def this(term: IRT.Term) {
      this(null, term)
    }
  }

  class Try(location: Location, var tryStatement: IRT.ActionStatement, var catchTypes: Array[ClosureLocalBinding], var catchStatements: Array[IRT.ActionStatement]) extends ActionStatement(location) {
    def this(tryStatement: IRT.ActionStatement, catchTypes: Array[ClosureLocalBinding], catchStatements: Array[IRT.ActionStatement]) {
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

  class UnaryTerm(location: Location, val kind: Int, val `type`: IRT.Type, val operand: IRT.Term)  extends Term(location) {
    def this(kind: Int, `type`: IRT.Type, operand: IRT.Term) {
      this(null, kind, `type`, operand)
    }
  }

  abstract class AbstractClassType extends AbstractObjectType with ClassType {
    private val constructorRefFinder: IRT.ConstructorFinder = new IRT.ConstructorFinder()

    def findConstructor(params: Array[IRT.Term]): Array[IRT.ConstructorRef] = constructorRefFinder.find(this, params)

    def isClassType: Boolean = true

    def isArrayType: Boolean = false
  }

  /**
   * @author Kota Mizushima
   */
  abstract class AbstractObjectType extends ObjectType {
    private var methodRefFinder: IRT.MethodFinder = new IRT.MethodFinder
    private var fieldRefFinder: IRT.FieldFinder   = new IRT.FieldFinder

    def findField(name: String): IRT.FieldRef = fieldRefFinder.find(this, name)

    def findMethod(name: String, params: Array[IRT.Term]): Array[IRT.Method] = {
      methodRefFinder.find(this, name, params)
    }

    def isBasicType: Boolean =  false

    def isNullType: Boolean =  false

    def isObjectType: Boolean =  true
  }

  /**
   * @author Kota Mizushima
   */
  class ArrayType(val component: IRT.Type, val dimension: Int, table: ClassTable) extends AbstractObjectType {
    val superClass: IRT.ClassType        = table.load("java.lang.Object")
    val interfaces: Seq[IRT.ClassType] = Seq(table.load("java.io.Serializable"), table.load("java.lang.Cloneable"))
    var name: String                     = "[" * dimension + component.name

    def base: IRT.Type =  if (dimension == 1) component else table.loadArray(component, dimension - 1)

    def isInterface: Boolean = false

    def modifier: Int = 0

    def methods: Seq[IRT.Method] = superClass.methods

    def methods(name: String): Array[IRT.Method] =  superClass.methods(name)

    def fields: Array[IRT.FieldRef] = superClass.fields

    def field(name: String): IRT.FieldRef = superClass.field(name)

    def isArrayType: Boolean =  true

    def isClassType: Boolean =  false
  }

  object BasicType {
    final val BYTE: IRT.BasicType    = new IRT.BasicType("byte")
    final val SHORT: IRT.BasicType   = new IRT.BasicType("short")
    final val CHAR: IRT.BasicType    = new IRT.BasicType("char")
    final val INT: IRT.BasicType     = new IRT.BasicType("int")
    final val LONG: IRT.BasicType    = new IRT.BasicType("long")
    final val FLOAT: IRT.BasicType   = new IRT.BasicType("float")
    final val DOUBLE: IRT.BasicType  = new IRT.BasicType("double")
    final val BOOLEAN: IRT.BasicType = new IRT.BasicType("boolean")
    final val VOID: IRT.BasicType    = new IRT.BasicType("void")
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
    def constructors: Array[IRT.ConstructorRef]

    def findConstructor(params: Array[IRT.Term]): Array[IRT.ConstructorRef]
  }

  class ConstructorFinder {
    def find(target: IRT.ClassType, args: Array[IRT.Term]): Array[IRT.ConstructorRef] = {
      val constructors: Set[IRT.ConstructorRef] = new TreeSet[IRT.ConstructorRef](new IRT.ConstructorComparator)
      if (target == null) return new Array[IRT.ConstructorRef](0)
      val cs: Array[IRT.ConstructorRef] = target.constructors
      var i: Int = 0
      while (i < cs.length) {
        val c: IRT.ConstructorRef = cs(i)
        if (matcher.matches(c.getArgs, args)) constructors.add(c)
        i += 1;
      }
      val selected: List[IRT.ConstructorRef] = new ArrayList[IRT.ConstructorRef]
      selected.addAll(constructors)
      Collections.sort(selected, sorter)
      if (selected.size < 2) {
        return selected.toArray(new Array[IRT.ConstructorRef](0))
      }
      val constructor1: IRT.ConstructorRef = selected.get(0)
      val constructor2: IRT.ConstructorRef = selected.get(1)
      if (isAmbiguous(constructor1, constructor2)) {
        return selected.toArray(new Array[IRT.ConstructorRef](0))
      }
      Array[IRT.ConstructorRef](constructor1)
    }

    private def isAmbiguous(constructor1: IRT.ConstructorRef, constructor2: IRT.ConstructorRef): Boolean = {
      sorter.compare(constructor1, constructor2) >= 0
    }

    private def isAllSuperType(arg1: Array[IRT.Type], arg2: Array[IRT.Type]): Boolean = {
      var i: Int = 0
      while (i < arg1.length) {
        if (!TypeRules.isSuperType(arg1(i), arg2(i))) return false
        i += 1
      }
      true
    }

    private final val sorter: Comparator[IRT.ConstructorRef] = new Comparator[IRT.ConstructorRef] {
      def compare(c1: IRT.ConstructorRef, c2: IRT.ConstructorRef): Int = {
        val arg1: Array[IRT.Type] = c1.getArgs
        val arg2: Array[IRT.Type] = c2.getArgs
        if (isAllSuperType(arg2, arg1)) return -1
        if (isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: IRT.ParameterMatcher = new IRT.StandardParameterMatcher
  }

  abstract trait ConstructorRef extends MemberRef {
    def affiliation: IRT.ClassType

    def getArgs: Array[IRT.Type]
  }

  /**
   * @author Kota Mizushima
   */
  class ConstructorComparator extends Comparator[IRT.ConstructorRef] {
    def compare(c1: IRT.ConstructorRef, c2: IRT.ConstructorRef): Int = {
      val args1: Array[IRT.Type] = c1.getArgs
      val args2: Array[IRT.Type] = c2.getArgs
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
    def find(target: IRT.ObjectType, name: String): IRT.FieldRef = {
      if (target == null) return null
      var field: IRT.FieldRef = target.field(name)
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

  abstract trait FieldRef extends MemberRef {
    def modifier: Int

    def affiliation: IRT.ClassType

    def `type`: IRT.Type
  }

  class FieldComparator extends Comparator[IRT.FieldRef] {
    def compare(o1: IRT.FieldRef, o2: IRT.FieldRef): Int =  o1.name.compareTo(o2.name)
  }

  abstract trait MemberRef extends Named {
    def modifier: Int

    def affiliation: IRT.ClassType

    def name: String
  }

  /**
   * @author Kota Mizushima
   */
  object MethodFinder {
    private def isAllSuperType(arg1: Array[IRT.Type], arg2: Array[IRT.Type]): Boolean = {
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
    def find(target: IRT.ObjectType, name: String, arguments: Array[IRT.Term]): Array[IRT.Method] = {
      val methods: Set[IRT.Method] = new TreeSet[IRT.Method](new IRT.MethodComparator)
      find(methods, target, name, arguments)
      val selectedMethods: List[IRT.Method] = new ArrayList[IRT.Method]
      selectedMethods.addAll(methods)
      Collections.sort(selectedMethods, sorter)
      if (selectedMethods.size < 2) {
        return selectedMethods.toArray(new Array[IRT.Method](0)).asInstanceOf[Array[IRT.Method]]
      }
      val method1: IRT.Method = selectedMethods.get(0)
      val method2: IRT.Method = selectedMethods.get(1)
      if (isAmbiguous(method1, method2)) {
        return selectedMethods.toArray(new Array[IRT.Method](0)).asInstanceOf[Array[IRT.Method]]
      }
      Array[IRT.Method](method1)
    }

    def isAmbiguous(method1: IRT.Method, method2: IRT.Method): Boolean =  sorter.compare(method1, method2) >= 0

    private def find(methods: Set[IRT.Method], target: IRT.ObjectType, name: String, params: Array[IRT.Term]) {
      if (target == null) return
      val ms: Array[IRT.Method] = target.methods(name)
      for (m <- target.methods(name)) {
        if (matcher.matches(m.arguments, params)) methods.add(m)
      }
      val superClass: IRT.ClassType = target.superClass
      find(methods, superClass, name, params)
      val interfaces = target.interfaces
      for (anInterface <- interfaces) {
        find(methods, anInterface, name, params)
      }
    }

    private final val sorter: Comparator[IRT.Method] = new Comparator[IRT.Method] {
      def compare(m1: IRT.Method, m2: IRT.Method): Int = {
        val arg1: Array[IRT.Type] = m1.arguments
        val arg2: Array[IRT.Type] = m2.arguments
        if (isAllSuperType(arg2, arg1)) return -1
        if (isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: IRT.ParameterMatcher  = new IRT.StandardParameterMatcher
  }

  abstract trait Method extends MemberRef {
    def affiliation: IRT.ClassType

    def arguments: Array[IRT.Type]

    def returnType: IRT.Type
  }

  class MethodComparator extends Comparator[IRT.Method] {
    def compare(m1: IRT.Method, m2: IRT.Method): Int = {
      var result: Int = m1.name.compareTo(m2.name)
      if (result != 0) return result
      val args1: Array[IRT.Type] = m1.arguments
      val args2: Array[IRT.Type] = m2.arguments
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
    val NULL: IRT.NullType = new IRT.NullType("null")
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
  abstract sealed trait ObjectType extends Type {
    def isInterface: Boolean

    def modifier: Int

    def superClass: IRT.ClassType

    def interfaces: Seq[IRT.ClassType]

    def methods: Seq[IRT.Method]

    def methods(name: String): Array[IRT.Method]

    def fields: Array[IRT.FieldRef]

    def field(name: String): IRT.FieldRef

    def findMethod(name: String, params: Array[IRT.Term]): Array[IRT.Method]
  }

  /**
   * @author Kota Mizushima
   */
  abstract trait ParameterMatcher {
    def matches(arguments: Array[IRT.Type], parameters: Array[IRT.Term]): Boolean
  }

  /**
   * @author Kota Mizushima
   */
  class StandardParameterMatcher extends ParameterMatcher {
    def matches(arguments: Array[IRT.Type], parameters: Array[IRT.Term]): Boolean = {
      if (arguments.length != parameters.length) return false
      val parameterTypes: Array[IRT.Type] = new Array[IRT.Type](parameters.length)
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
    def isSuperType(left: IRT.Type, right: IRT.Type): Boolean = {
      if (left.isBasicType) {
        if (right.isBasicType) {
          return isSuperTypeForBasic(left.asInstanceOf[IRT.BasicType], right.asInstanceOf[IRT.BasicType])
        }
        return false
      }
      if (left.isClassType) {
        if (right.isClassType) {
          return isSuperTypeForClass(left.asInstanceOf[IRT.ClassType], right.asInstanceOf[IRT.ClassType])
        }
        if (right.isArrayType) {
          return left eq (right.asInstanceOf[IRT.ArrayType]).superClass
        }
        if (right.isNullType) {
          return true
        }
        return false
      }
      if (left.isArrayType) {
        if (right.isArrayType) {
          return isSuperTypeForArray(left.asInstanceOf[IRT.ArrayType], right.asInstanceOf[IRT.ArrayType])
        }
        if (right.isNullType) {
          return true
        }
        return false
      }
      false
    }

    def isAssignable(left: IRT.Type, right: IRT.Type): Boolean = isSuperType(left, right)

    private def isSuperTypeForArray(left: IRT.ArrayType, right: IRT.ArrayType): Boolean = isSuperType(left.base, right.base)

    private def isSuperTypeForClass(left: IRT.ClassType, right: IRT.ClassType): Boolean = {
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

    private def isSuperTypeForBasic(left: IRT.BasicType, right: IRT.BasicType): Boolean = {
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
