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
  abstract trait Node

  /**
   * @author Kota Mizushima
   */
  class ArrayLength(location: Location, val target: IRT.Term) extends Term(location) {
    def this(target: IRT.Term) {
      this(null, target)
    }

    def `type`: IRT.TypeRef = BasicTypeRef.INT
  }

  /**
   * @author Kota Mizushima
   */
  class RefArray(location: Location, val target: IRT.Term, val index: IRT.Term) extends Term(location) {
    def this(target: IRT.Term, index: IRT.Term) {
      this(null, target, index)
    }

    def `type`: IRT.TypeRef = (target.`type`.asInstanceOf[IRT.ArrayTypeRef]).base
  }

  /**
   * @author Kota Mizushima
   */
  class SetArray(location: Location, val target: IRT.Term, val index: IRT.Term, val value: IRT.Term) extends Term(location) {
    def this(target: IRT.Term, index: IRT.Term, value: IRT.Term) {
      this(null, target, index, value)
    }

    def `type`: IRT.TypeRef = value.`type`

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

    def `type`: IRT.TypeRef = terms(terms.length - 1).`type`
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

  class BinaryTerm(location: Location, val kind: Int, val `type`: IRT.TypeRef, val lhs: IRT.Term, val rhs: IRT.Term) extends Term(location) {
    def this(kind: Int, `type`: IRT.TypeRef, lhs: IRT.Term, rhs: IRT.Term) {
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

    def `type`: IRT.TypeRef = BasicTypeRef.BOOLEAN
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

    def `type`: IRT.TypeRef = BasicTypeRef.BYTE
  }

  /**
   * @author Kota Mizushima
   */
  class Call(location: Location, val target: IRT.Term, val method: IRT.MethodRef, val parameters: Array[IRT.Term]) extends Term(location) {
    def this(target: IRT.Term, method: IRT.MethodRef, parameters: Array[IRT.Term]) {
      this(null, target, method, parameters)
    }

    def `type`: IRT.TypeRef = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class CallStatic(location: Location, val target: IRT.ObjectTypeRef, val method: IRT.MethodRef, val parameters: Array[IRT.Term]) extends Term(location) {
    def this(target: IRT.ObjectTypeRef, method: IRT.MethodRef, parameters: Array[IRT.Term]) {
      this(null, target, method, parameters)
    }
    def `type`: IRT.TypeRef = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class CallSuper(location: Location, val target: IRT.Term, val method: IRT.MethodRef, val params: Array[IRT.Term]) extends Term(location) {
    def this(target: IRT.Term, method: IRT.MethodRef, params: Array[IRT.Term]) {
      this(null, target, method, params)
    }

    def `type`: IRT.TypeRef = method.returnType
  }

  /**
   * @author Kota Mizushima
   */
  class AsInstanceOf(location: Location, val target: IRT.Term, val destination: IRT.TypeRef) extends Term(location) {
    def this(target: IRT.Term, destination: IRT.TypeRef) {
      this(null, target, destination)
    }

    def `type`: IRT.TypeRef = destination
  }

  /**
   * @author Kota Mizushima
   */
  class CharacterValue(location: Location, val value: Char) extends Term(location) {
    def this(value: Char) {
      this(null, value)
    }

    def `type`: IRT.TypeRef = BasicTypeRef.CHAR
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
    def newInterface(location: Location, modifier: Int, name: String, interfaces: Array[IRT.ClassTypeRef]): IRT.ClassDefinition = {
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
    def newClass(location: Location, modifier: Int, name: String, superClass: IRT.ClassTypeRef, interfaces: Array[IRT.ClassTypeRef]): IRT.ClassDefinition = {
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

  class ClassDefinition(val location: Location, val isInterface: Boolean, val modifier: Int, val name: String, var superClass: IRT.ClassTypeRef, var interfaces: Array[IRT.ClassTypeRef])
    extends IRT.AbstractClassTypeRef() with Node with Named {

    def constructors: Array[IRT.ConstructorRef] = constructors_.toArray(new Array[IRT.ConstructorRef](0))
    def methods: Array[IRT.MethodRef] = methods_.values.toArray(new Array[IRT.MethodRef](0))
    def fields: Array[IRT.FieldRef] = fields_.values.toArray(new Array[IRT.FieldRef](0))

    var fields_ : OrderedTable[IRT.FieldRef] = new OrderedTable[IRT.FieldRef]
    var methods_ : MultiTable[IRT.MethodRef] = new MultiTable[IRT.MethodRef]
    var constructors_ : List[IRT.ConstructorRef] = new ArrayList[IRT.ConstructorRef]
    var isResolutionComplete: Boolean = false
    private var sourceFile: String = null

    def this() {
      this(null: Location, false, 0, null: String, null: IRT.ClassTypeRef, null)
    }

    def setSuperClass(superClass: IRT.ClassTypeRef) {
      this.superClass = superClass
    }

    def setInterfaces(interfaces: Array[IRT.ClassTypeRef]) {
      this.interfaces = interfaces
    }

    def setResolutionComplete(isInResolution: Boolean) {
      this.isResolutionComplete = isInResolution
    }

    def add(method: IRT.MethodRef) {
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

    def methods(name: String): Array[IRT.MethodRef] = (methods_.get(name).toArray(new Array[IRT.MethodRef](0))).asInstanceOf[Array[IRT.MethodRef]]

    def field(name: String): IRT.FieldRef = fields_.get(name)

    def setSourceFile(sourceFile: String): Unit = this.sourceFile = sourceFile

    def getSourceFile: String = sourceFile
  }

  /**
   * @author Kota Mizushima
   */
  object ConstructorDefinition {
    def newDefaultConstructor(`type`: IRT.ClassTypeRef): IRT.ConstructorDefinition = {
      val block: IRT.StatementBlock = new IRT.StatementBlock(new IRT.Return(null))
      val init: IRT.Super = new IRT.Super(`type`.superClass, new Array[IRT.TypeRef](0), new Array[IRT.Term](0))
      val node: IRT.ConstructorDefinition = new IRT.ConstructorDefinition(Modifier.PUBLIC, `type`, new Array[IRT.TypeRef](0), block, init)
      node.frame = new LocalFrame(null)
      node
    }
  }

  class ConstructorDefinition(val location: Location, val modifier: Int, val classType: IRT.ClassTypeRef, val arguments: Array[IRT.TypeRef], var block: IRT.StatementBlock, var superInitializer: IRT.Super) extends
  Node with IRT.ConstructorRef {
    def this(modifier: Int, classType: IRT.ClassTypeRef, arguments: Array[IRT.TypeRef], block: IRT.StatementBlock, superInitializer: IRT.Super) {
      this(null, modifier, classType, arguments, block, superInitializer)
    }

    def name: String =  "new"

    def getArgs: Array[IRT.TypeRef] = arguments

    def affiliation: IRT.ClassTypeRef = classType

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

    def `type`: IRT.TypeRef = BasicTypeRef.DOUBLE
  }

  /**
   * @author Kota Mizushima
   */
  object Term {
    def defaultValue(`type`: IRT.TypeRef): IRT.Term = {
      if (`type` eq BasicTypeRef.CHAR) return new IRT.CharacterValue(0.asInstanceOf[Char])
      if (`type` eq BasicTypeRef.BYTE) return new IRT.ByteValue(0.asInstanceOf[Byte])
      if (`type` eq BasicTypeRef.SHORT) return new IRT.ShortValue(0.asInstanceOf[Short])
      if (`type` eq BasicTypeRef.INT) return new IRT.IntValue(0)
      if (`type` eq BasicTypeRef.LONG) return new IRT.LongValue(0)
      if (`type` eq BasicTypeRef.FLOAT) return new IRT.FloatValue(0.0f)
      if (`type` eq BasicTypeRef.DOUBLE) return new IRT.DoubleValue(0.0)
      if (`type` eq BasicTypeRef.BOOLEAN) return new IRT.BoolValue(false)
      if (`type`.isObjectType) return new IRT.NullValue
      null
    }
  }

  abstract class Term(val location: Location) extends Node {
    def `type`: IRT.TypeRef

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
  class FieldDefinition(val location: Location, val modifier: Int, val affiliation: IRT.ClassTypeRef, val name: String, val `type`: IRT.TypeRef)
    extends Node with FieldRef {
  }

  /**
   * @author Kota Mizushima
   */
  class RefField(location: Location, val target: IRT.Term, val field: IRT.FieldRef)  extends Term(location) {
    def this(target: IRT.Term, field: IRT.FieldRef) {
      this(null, target, field)
    }

    def `type`: IRT.TypeRef = field.`type`
  }

  class SetField(location: Location, val target: IRT.Term, val field: IRT.FieldRef, val value: IRT.Term) extends Term(location) {
    def this(target: IRT.Term, field: IRT.FieldRef, value: IRT.Term) {
      this(null, target, field, value)
    }

    def `type`: IRT.TypeRef = field.`type`
  }

  class FloatValue (location: Location, val value: Float)  extends Term(location) {
    def this(value: Float) {
      this(null, value)
    }

    def `type`: IRT.TypeRef = BasicTypeRef.FLOAT
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

  class InstanceOf(location: Location, val target: IRT.Term, val checked: IRT.TypeRef)  extends Term(location) {
    def this(target: IRT.Term, checked: IRT.TypeRef) {
      this(null, target, checked)
    }

    def `type`: IRT.TypeRef = BasicTypeRef.BOOLEAN
  }

  class IntValue(location: Location, val value: Int)  extends Term(location) {
    def this(value: Int) {
      this(null, value)
    }

    def `type`: IRT.TypeRef =  BasicTypeRef.INT
  }

  class ListLiteral(location: Location, val elements: Array[IRT.Term], val `type`: IRT.TypeRef)  extends Term(location) {
    def this(elements: Array[IRT.Term], `type`: IRT.TypeRef) {
      this(null, elements, `type`)
    }

    def getElements: Array[IRT.Term] = elements
  }

  class RefLocal(location: Location, val frame: Int, val index: Int, val `type`: IRT.TypeRef)  extends Term(location) {
    def this(bind: ClosureLocalBinding) {
      this(null, bind.frameIndex, bind.index, bind.tp)
    }

    def this(frame: Int, index: Int, `type`: IRT.TypeRef) {
      this(null, frame, index, `type`)
    }
  }

  class SetLocal(location: Location, val frame: Int, val index: Int, val `type`: IRT.TypeRef, val value: IRT.Term) extends Term(location) {
    def this(bind: ClosureLocalBinding, value: IRT.Term) {
      this(null, bind.frameIndex, bind.index, bind.tp, value)
    }

    def this(frame: Int, index: Int, `type`: IRT.TypeRef, value: IRT.Term) {
      this(null, frame, index, `type`, value)
    }
  }

  /**
   * @author Kota Mizushima
   */
  class NewClosure(location: Location, val `type`: IRT.ClassTypeRef, method: IRT.MethodRef, block: IRT.ActionStatement) extends Term(location) {
    private var frame: LocalFrame = null

    def this(`type`: IRT.ClassTypeRef, method: IRT.MethodRef, block: IRT.ActionStatement) {
      this(null, `type`, method, block)
    }

    def getModifier: Int = method.modifier

    def getClassType: IRT.ClassTypeRef = `type`

    def getMethod: IRT.MethodRef = method

    def getName: String = method.name

    def getArguments: Array[IRT.TypeRef] = method.arguments

    def getReturnType: IRT.TypeRef =  method.returnType

    def getBlock: IRT.ActionStatement = block

    def setFrame(frame: LocalFrame): Unit = this.frame = frame

    def getFrame: LocalFrame = frame

  }

  /**
   * @author Kota Mizushima
   */
  class LongValue (location: Location, val value: Long) extends Term(location) {
    def this(value: Long) {
      this(null, value)
    }

    def `type`: IRT.TypeRef = BasicTypeRef.LONG
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
  class Member(modifier: Int, classType: IRT.TypeRef) extends Node {
    def getClassType: IRT.TypeRef = classType

    def getModifier: Int = modifier
  }

  /**
   * @author Kota Mizushima
   */
  class MethodDefinition(val location: Location, val modifier: Int, val classType: IRT.ClassTypeRef, val name: String, val arguments: Array[IRT.TypeRef], val returnType: IRT.TypeRef, var block: IRT.StatementBlock)
    extends Node with MethodRef {
    private var closure: Boolean = false
    private var frame: LocalFrame = null

    def affiliation: IRT.ClassTypeRef = classType

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

    def `type`: IRT.TypeRef = constructor.affiliation
  }

  class NewArray(location: Location, val arrayType: IRT.ArrayTypeRef, val parameters: Array[IRT.Term]) extends Term(location) {
    def this(arrayType: IRT.ArrayTypeRef, parameters: Array[IRT.Term]) {
      this(null, arrayType, parameters)
    }

    def `type`: IRT.TypeRef = arrayType
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

    def `type`: IRT.TypeRef = NullTypeRef.NULL
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

    def `type`: IRT.TypeRef = BasicTypeRef.SHORT
  }

  abstract class ActionStatement(val location: Location)

  class RefStaticField(location: Location, val target: IRT.ClassTypeRef, val field: IRT.FieldRef)  extends Term(location) {

    def this(target: IRT.ClassTypeRef, field: IRT.FieldRef) {
      this(null, target, field)
    }

    def `type`: IRT.TypeRef = field.`type`
  }

  /**
   * @author Kota Mizushima
   */
  class SetStaticField(location: Location, val target: IRT.ObjectTypeRef, val field: IRT.FieldRef, val value: IRT.Term) extends Term(location) {
    def this(target: IRT.ObjectTypeRef, field: IRT.FieldRef, value: IRT.Term) {
      this(null, target, field, value)
    }

    def `type`: IRT.TypeRef = field.`type`
  }

  /**
   * @author Kota Mizushima
   */
  class StringValue(location: Location, val value: String, val `type`: IRT.TypeRef) extends Term(location) {
    def this(value: String, `type`: IRT.TypeRef) {
      this(null, value, `type`)
    }
  }

  class Super(val classType: IRT.ClassTypeRef, val arguments: Array[IRT.TypeRef], val terms: Array[IRT.Term]) extends Node

  class Synchronized(location: Location, val term: IRT.Term, val statement: IRT.ActionStatement)  extends ActionStatement(location) {
    def this(term: IRT.Term, statement: IRT.ActionStatement) {
      this(null, term, statement)
    }
  }

  class OuterThis(location: Location, val `type`: IRT.ClassTypeRef)  extends Term(location) {
    def this(classType: IRT.ClassTypeRef) {
      this(null, classType)
    }
  }

  class This(location: Location, val `type`: IRT.ClassTypeRef) extends Term(location) {
    def this(classType: IRT.ClassTypeRef) {
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

  class UnaryTerm(location: Location, val kind: Int, val `type`: IRT.TypeRef, val operand: IRT.Term)  extends Term(location) {
    def this(kind: Int, `type`: IRT.TypeRef, operand: IRT.Term) {
      this(null, kind, `type`, operand)
    }
  }

  abstract class AbstractClassTypeRef() extends AbstractObjectTypeRef with ClassTypeRef {
    private val constructorRefFinder: IRT.ConstructorRefFinder = new IRT.ConstructorRefFinder()

    def findConstructor(params: Array[IRT.Term]): Array[IRT.ConstructorRef] = constructorRefFinder.find(this, params)

    def isClassType: Boolean = true

    def isArrayType: Boolean = false
  }

  /**
   * @author Kota Mizushima
   */
  abstract class AbstractObjectTypeRef() extends ObjectTypeRef {
    private var methodRefFinder: IRT.MethodRefFinder = new IRT.MethodRefFinder
    private var fieldRefFinder: IRT.FieldRefFinder = new IRT.FieldRefFinder

    def findField(name: String): IRT.FieldRef = fieldRefFinder.find(this, name)

    def findMethod(name: String, params: Array[IRT.Term]): Array[IRT.MethodRef] = {
      methodRefFinder.find(this, name, params)
    }

    def isBasicType: Boolean =  false

    def isNullType: Boolean =  false

    def isObjectType: Boolean =  true
  }

  /**
   * @author Kota Mizushima
   */
  class ArrayTypeRef(val component: IRT.TypeRef, val dimension: Int, table: ClassTable) extends AbstractObjectTypeRef {
    val superClass: IRT.ClassTypeRef = table.load("java.lang.Object")
    val interfaces: Array[IRT.ClassTypeRef] = Array[IRT.ClassTypeRef](table.load("java.io.Serializable"), table.load("java.lang.Cloneable"))
    var name: String = "[" * dimension + component.name

    def base: IRT.TypeRef =  if (dimension == 1) component else table.loadArray(component, dimension - 1)

    def isInterface: Boolean = false

    def modifier: Int = 0

    def methods: Array[IRT.MethodRef] = superClass.methods

    def methods(name: String): Array[IRT.MethodRef] =  superClass.methods(name)

    def fields: Array[IRT.FieldRef] = superClass.fields

    def field(name: String): IRT.FieldRef = superClass.field(name)

    def isArrayType: Boolean =  true

    def isClassType: Boolean =  false
  }

  object BasicTypeRef {
    final val BYTE: IRT.BasicTypeRef = new IRT.BasicTypeRef("byte")
    final val SHORT: IRT.BasicTypeRef = new IRT.BasicTypeRef("short")
    final val CHAR: IRT.BasicTypeRef = new IRT.BasicTypeRef("char")
    final val INT: IRT.BasicTypeRef = new IRT.BasicTypeRef("int")
    final val LONG: IRT.BasicTypeRef = new IRT.BasicTypeRef("long")
    final val FLOAT: IRT.BasicTypeRef = new IRT.BasicTypeRef("float")
    final val DOUBLE: IRT.BasicTypeRef = new IRT.BasicTypeRef("double")
    final val BOOLEAN: IRT.BasicTypeRef = new IRT.BasicTypeRef("boolean")
    final val VOID: IRT.BasicTypeRef = new IRT.BasicTypeRef("void")
  }

  final val BASIC_TYPE_REF_BYTE = BasicTypeRef.BYTE
  final val BASIC_TYPE_REF_SHORT = BasicTypeRef.SHORT
  final val BASIC_TYPE_REF_CHAR = BasicTypeRef.CHAR
  final val BASIC_TYPE_REF_INT = BasicTypeRef.INT
  final val BASIC_TYPE_REF_LONG = BasicTypeRef.LONG
  final val BASIC_TYPE_REF_FLOAT = BasicTypeRef.FLOAT
  final val BASIC_TYPE_REF_DOUBLE = BasicTypeRef.DOUBLE
  final val BASIC_TYPE_REF_BOOLEAN = BasicTypeRef.BOOLEAN
  final val BASIC_TYPE_REF_VOID = BasicTypeRef.VOID

  class BasicTypeRef private(name_ : String) extends TypeRef {
    import BasicTypeRef._
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

  abstract trait ClassTypeRef extends ObjectTypeRef {
    def constructors: Array[IRT.ConstructorRef]

    def findConstructor(params: Array[IRT.Term]): Array[IRT.ConstructorRef]
  }

  class ConstructorRefFinder {
    def find(target: IRT.ClassTypeRef, args: Array[IRT.Term]): Array[IRT.ConstructorRef] = {
      val constructors: Set[IRT.ConstructorRef] = new TreeSet[IRT.ConstructorRef](new IRT.ConstructorRefComparator)
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

    private def isAllSuperType(arg1: Array[IRT.TypeRef], arg2: Array[IRT.TypeRef]): Boolean = {
      var i: Int = 0
      while (i < arg1.length) {
        if (!TypeRules.isSuperType(arg1(i), arg2(i))) return false
        i += 1
      }
      true
    }

    private final val sorter: Comparator[IRT.ConstructorRef] = new Comparator[IRT.ConstructorRef] {
      def compare(c1: IRT.ConstructorRef, c2: IRT.ConstructorRef): Int = {
        val arg1: Array[IRT.TypeRef] = c1.getArgs
        val arg2: Array[IRT.TypeRef] = c2.getArgs
        if (isAllSuperType(arg2, arg1)) return -1
        if (isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: IRT.ParameterMatcher = new IRT.StandardParameterMatcher
  }

  abstract trait ConstructorRef extends MemberRef {
    def affiliation: IRT.ClassTypeRef

    def getArgs: Array[IRT.TypeRef]
  }

  /**
   * @author Kota Mizushima
   */
  class ConstructorRefComparator extends Comparator[IRT.ConstructorRef] {
    def compare(c1: IRT.ConstructorRef, c2: IRT.ConstructorRef): Int = {
      val args1: Array[IRT.TypeRef] = c1.getArgs
      val args2: Array[IRT.TypeRef] = c2.getArgs
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

  class FieldRefFinder {
    def find(target: IRT.ObjectTypeRef, name: String): IRT.FieldRef = {
      if (target == null) return null
      var field: IRT.FieldRef = target.field(name)
      if (field != null) return field
      field = find(target.superClass, name)
      if (field != null) return field
      val interfaces: Array[IRT.ClassTypeRef] = target.interfaces
      for (anInterface <- target.interfaces) {
        field = find(anInterface, name)
        if (field != null) return field
      }
      null
    }
  }

  abstract trait FieldRef extends MemberRef {
    def modifier: Int

    def affiliation: IRT.ClassTypeRef

    def `type`: IRT.TypeRef
  }

  class FieldRefComparator extends Comparator[IRT.FieldRef] {
    def compare(o1: IRT.FieldRef, o2: IRT.FieldRef): Int =  o1.name.compareTo(o2.name)
  }

  abstract trait MemberRef extends Named {
    def modifier: Int

    def affiliation: IRT.ClassTypeRef

    def name: String
  }

  /**
   * @author Kota Mizushima
   */
  object MethodRefFinder {
    private def isAllSuperType(arg1: Array[IRT.TypeRef], arg2: Array[IRT.TypeRef]): Boolean = {
      var i: Int = 0
      while (i < arg1.length) {
        if (!TypeRules.isSuperType(arg1(i), arg2(i))) return false
        i += 1
      }
      true
    }
  }

  class MethodRefFinder {
    import MethodRefFinder._
    def find(target: IRT.ObjectTypeRef, name: String, arguments: Array[IRT.Term]): Array[IRT.MethodRef] = {
      val methods: Set[IRT.MethodRef] = new TreeSet[IRT.MethodRef](new IRT.MethodRefComparator)
      find(methods, target, name, arguments)
      val selectedMethods: List[IRT.MethodRef] = new ArrayList[IRT.MethodRef]
      selectedMethods.addAll(methods)
      Collections.sort(selectedMethods, sorter)
      if (selectedMethods.size < 2) {
        return selectedMethods.toArray(new Array[IRT.MethodRef](0)).asInstanceOf[Array[IRT.MethodRef]]
      }
      val method1: IRT.MethodRef = selectedMethods.get(0)
      val method2: IRT.MethodRef = selectedMethods.get(1)
      if (isAmbiguous(method1, method2)) {
        return selectedMethods.toArray(new Array[IRT.MethodRef](0)).asInstanceOf[Array[IRT.MethodRef]]
      }
      Array[IRT.MethodRef](method1)
    }

    def isAmbiguous(method1: IRT.MethodRef, method2: IRT.MethodRef): Boolean =  sorter.compare(method1, method2) >= 0

    private def find(methods: Set[IRT.MethodRef], target: IRT.ObjectTypeRef, name: String, params: Array[IRT.Term]) {
      if (target == null) return
      val ms: Array[IRT.MethodRef] = target.methods(name)
      for (m <- target.methods(name)) {
        if (matcher.matches(m.arguments, params)) methods.add(m)
      }
      val superClass: IRT.ClassTypeRef = target.superClass
      find(methods, superClass, name, params)
      val interfaces: Array[IRT.ClassTypeRef] = target.interfaces
      for (anInterface <- interfaces) {
        find(methods, anInterface, name, params)
      }
    }

    private final val sorter: Comparator[IRT.MethodRef] = new Comparator[IRT.MethodRef] {
      def compare(m1: IRT.MethodRef, m2: IRT.MethodRef): Int = {
        val arg1: Array[IRT.TypeRef] = m1.arguments
        val arg2: Array[IRT.TypeRef] = m2.arguments
        if (isAllSuperType(arg2, arg1)) return -1
        if (isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: IRT.ParameterMatcher = new IRT.StandardParameterMatcher
  }

  abstract trait MethodRef extends MemberRef {
    def affiliation: IRT.ClassTypeRef

    def arguments: Array[IRT.TypeRef]

    def returnType: IRT.TypeRef
  }

  class MethodRefComparator extends Comparator[IRT.MethodRef] {
    def compare(m1: IRT.MethodRef, m2: IRT.MethodRef): Int = {
      var result: Int = m1.name.compareTo(m2.name)
      if (result != 0) return result
      val args1: Array[IRT.TypeRef] = m1.arguments
      val args2: Array[IRT.TypeRef] = m2.arguments
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

  object NullTypeRef {
    var NULL: IRT.NullTypeRef = new IRT.NullTypeRef("null")
  }

  class NullTypeRef private(name_ :String) extends TypeRef {
    def name: String = name_

    def isArrayType: Boolean = false

    def isBasicType: Boolean = false

    def isClassType: Boolean = false

    def isNullType: Boolean = true

    def isObjectType: Boolean = false
  }

  /**
   * @author Kota Mizushima
   */
  abstract trait ObjectTypeRef extends TypeRef {
    def isInterface: Boolean

    def modifier: Int

    def superClass: IRT.ClassTypeRef

    def interfaces: Array[IRT.ClassTypeRef]

    def methods: Array[IRT.MethodRef]

    def methods(name: String): Array[IRT.MethodRef]

    def fields: Array[IRT.FieldRef]

    def field(name: String): IRT.FieldRef

    def findMethod(name: String, params: Array[IRT.Term]): Array[IRT.MethodRef]
  }

  /**
   * @author Kota Mizushima
   */
  abstract trait ParameterMatcher {
    def matches(arguments: Array[IRT.TypeRef], parameters: Array[IRT.Term]): Boolean
  }

  /**
   * @author Kota Mizushima
   */
  class StandardParameterMatcher extends ParameterMatcher {
    def matches(arguments: Array[IRT.TypeRef], parameters: Array[IRT.Term]): Boolean = {
      if (arguments.length != parameters.length) return false
      val parameterTypes: Array[IRT.TypeRef] = new Array[IRT.TypeRef](parameters.length)
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

  abstract trait TypeRef {
    def name: String

    def isBasicType: Boolean

    def isClassType: Boolean

    def isNullType: Boolean

    def isArrayType: Boolean

    def isObjectType: Boolean
  }

  object TypeRules {
    def isSuperType(left: IRT.TypeRef, right: IRT.TypeRef): Boolean = {
      if (left.isBasicType) {
        if (right.isBasicType) {
          return isSuperTypeForBasic(left.asInstanceOf[IRT.BasicTypeRef], right.asInstanceOf[IRT.BasicTypeRef])
        }
        return false
      }
      if (left.isClassType) {
        if (right.isClassType) {
          return isSuperTypeForClass(left.asInstanceOf[IRT.ClassTypeRef], right.asInstanceOf[IRT.ClassTypeRef])
        }
        if (right.isArrayType) {
          return left eq (right.asInstanceOf[IRT.ArrayTypeRef]).superClass
        }
        if (right.isNullType) {
          return true
        }
        return false
      }
      if (left.isArrayType) {
        if (right.isArrayType) {
          return isSuperTypeForArray(left.asInstanceOf[IRT.ArrayTypeRef], right.asInstanceOf[IRT.ArrayTypeRef])
        }
        if (right.isNullType) {
          return true
        }
        return false
      }
      false
    }

    def isAssignable(left: IRT.TypeRef, right: IRT.TypeRef): Boolean = isSuperType(left, right)

    private def isSuperTypeForArray(left: IRT.ArrayTypeRef, right: IRT.ArrayTypeRef): Boolean = isSuperType(left.base, right.base)

    private def isSuperTypeForClass(left: IRT.ClassTypeRef, right: IRT.ClassTypeRef): Boolean = {
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

    private def isSuperTypeForBasic(left: IRT.BasicTypeRef, right: IRT.BasicTypeRef): Boolean = {
      if (left eq BasicTypeRef.DOUBLE) {
        return ((right eq BasicTypeRef.CHAR) || (right eq BasicTypeRef.BYTE) || (right eq BasicTypeRef.SHORT) || (right eq BasicTypeRef.INT) || (right eq BasicTypeRef.LONG) || (right eq BasicTypeRef.FLOAT) || (right eq BasicTypeRef.DOUBLE))
      }
      if (left eq BasicTypeRef.FLOAT) {
        return ((right eq BasicTypeRef.CHAR) || (right eq BasicTypeRef.BYTE) || (right eq BasicTypeRef.SHORT) || (right eq BasicTypeRef.INT) || (right eq BasicTypeRef.LONG) || (right eq BasicTypeRef.FLOAT))
      }
      if (left eq BasicTypeRef.LONG) {
        return ((right eq BasicTypeRef.CHAR) || (right eq BasicTypeRef.BYTE) || (right eq BasicTypeRef.SHORT) || (right eq BasicTypeRef.INT) || (right eq BasicTypeRef.LONG))
      }
      if (left eq BasicTypeRef.INT) {
        return ((right eq BasicTypeRef.CHAR) || (right eq BasicTypeRef.BYTE) || (right eq BasicTypeRef.SHORT) || (right eq BasicTypeRef.INT))
      }
      if (left eq BasicTypeRef.SHORT) {
        return  ((right eq BasicTypeRef.BYTE) || (right eq BasicTypeRef.SHORT));
      }
      if ((left eq BasicTypeRef.BOOLEAN) && (right eq BasicTypeRef.BOOLEAN)) return true
      if ((left eq BasicTypeRef.BYTE) && (right eq BasicTypeRef.BYTE)) return true
      if ((left eq BasicTypeRef.CHAR) && (right eq BasicTypeRef.CHAR)) return true
      false
    }
  }

  class TypeRules
}
