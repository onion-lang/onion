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

    /** Binary operator kinds */
    enum Kind:
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, MOD
      case LOGICAL_AND, LOGICAL_OR
      case BIT_AND, BIT_OR, XOR
      case BIT_SHIFT_L2, BIT_SHIFT_R2, BIT_SHIFT_R3
      case LESS_THAN, GREATER_THAN, LESS_OR_EQUAL, GREATER_OR_EQUAL
      case EQUAL, NOT_EQUAL
      case ELVIS

  }

  class BinaryTerm(location: Location, val kind: BinaryTerm.Kind, val `type`: TypedAST.Type, val lhs: TypedAST.Term, val rhs: TypedAST.Term) extends Term(location) {
    def this(kind: BinaryTerm.Kind, `type`: TypedAST.Type, lhs: TypedAST.Term, rhs: TypedAST.Term) = {
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

  class ClassDefinition(
    val location: Location,
    val isInterface: Boolean,
    val modifier: Int,
    val name: String,
    var superClass: TypedAST.ClassType,
    var interfaces: Seq[TypedAST.ClassType],
    typeParameters0: Array[TypedAST.TypeParameter] = Array()
  )
    extends TypedAST.AbstractClassType() with Node with Named {

    private var typeParameters_ = typeParameters0
    private var staticInitializers_ = Array[TypedAST.ActionStatement]()

    override def typeParameters: Array[TypedAST.TypeParameter] = typeParameters_

    def setTypeParameters(typeParameters: Array[TypedAST.TypeParameter]): Unit =
      typeParameters_ = typeParameters

    def staticInitializers: Array[TypedAST.ActionStatement] = staticInitializers_

    def setStaticInitializers(initializers: Array[TypedAST.ActionStatement]): Unit =
      staticInitializers_ = initializers

    def constructors: Array[TypedAST.ConstructorRef] = constructors_.toArray
    def methods: Seq[TypedAST.Method] = methods_.values.toSeq
    def fields: Array[TypedAST.FieldRef] = fields_.values.toArray

    val fields_ : OrderedTable[TypedAST.FieldRef]     = new OrderedTable[TypedAST.FieldRef]
    val methods_ : MultiTable[TypedAST.Method]        = new MultiTable[TypedAST.Method]
    val constructors_ : scala.collection.mutable.ArrayBuffer[TypedAST.ConstructorRef] = scala.collection.mutable.ArrayBuffer.empty
    var isResolutionComplete: Boolean            = false
    private var sourceFile: String = null
    private val sealedSubtypes_ : scala.collection.mutable.ArrayBuffer[TypedAST.ClassType] = scala.collection.mutable.ArrayBuffer.empty

    /** Returns all direct subtypes of this sealed type */
    def sealedSubtypes: Array[TypedAST.ClassType] = sealedSubtypes_.toArray

    /** Register a subtype of this sealed type */
    def addSealedSubtype(subtype: TypedAST.ClassType): Unit = sealedSubtypes_ += subtype

    /** Check if this is a sealed type */
    def isSealed: Boolean = Modifier.isSealed(modifier)

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

    def add(constructor: TypedAST.ConstructorRef): Unit =
      constructors_ += constructor

    def addDefaultConstructor: Unit =
      constructors_ += ConstructorDefinition.newDefaultConstructor(this)

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

  class ConstructorDefinition(val location: Location, val modifier: Int, val classType: TypedAST.ClassType, val arguments: Array[TypedAST.Type], var block: TypedAST.StatementBlock, var superInitializer: TypedAST.Super, override val typeParameters: Array[TypedAST.TypeParameter] = Array()) extends
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

  class StatementTerm(location: Location, val statement: TypedAST.ActionStatement, val termType: TypedAST.Type) extends Term(location) {
    def this(statement: TypedAST.ActionStatement, termType: TypedAST.Type) = {
      this(null, statement, termType)
    }

    def `type`: TypedAST.Type = termType
  }

  /**
   * SynchronizedTerm - synchronized block that returns a value
   */
  class SynchronizedTerm(location: Location, val lock: TypedAST.Term, val body: TypedAST.Term) extends Term(location) {
    def `type`: TypedAST.Type = body.`type`
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
    override val typeParameters: Array[TypedAST.TypeParameter] = Array(),
    val throwsTypes: Array[TypedAST.ClassType] = Array(),
    private val vararg: Boolean = false
  ) extends Node with Method {
    private var closure: Boolean = false
    private var frame: LocalFrame = _
    private var argsWithDefaults_ : Array[MethodArgument] = null

    def affiliation: TypedAST.ClassType = classType

    override def isVararg: Boolean = vararg

    def getBlock: TypedAST.StatementBlock = block

    def setBlock(block: TypedAST.StatementBlock): Unit =  this.block = block

    def setClosure(closure: Boolean): Unit =  this.closure = closure

    def hasClosure: Boolean = closure

    def setFrame(frame: LocalFrame): Unit =  this.frame = frame

    def getFrame: LocalFrame = frame

    /** Set the arguments with default values */
    def setArgumentsWithDefaults(args: Array[MethodArgument]): Unit = argsWithDefaults_ = args

    /** Get arguments with names and optional default values */
    override def argumentsWithDefaults: Array[MethodArgument] =
      if (argsWithDefaults_ != null) argsWithDefaults_
      else super.argumentsWithDefaults

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

  class NewArrayWithValues(location: Location, val arrayType: TypedAST.ArrayType, val values: Array[TypedAST.Term]) extends Term(location) {
    def this(arrayType: TypedAST.ArrayType, values: Array[TypedAST.Term]) = {
      this(null, arrayType, values)
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

  class Try(
    location: Location,
    var resources: Array[(ClosureLocalBinding, Term)],
    var tryStatement: TypedAST.ActionStatement,
    var catchTypes: Array[ClosureLocalBinding],
    var catchStatements: Array[TypedAST.ActionStatement],
    var finallyStatement: TypedAST.ActionStatement = null
  ) extends ActionStatement(location) {
    def this(tryStatement: TypedAST.ActionStatement, catchTypes: Array[ClosureLocalBinding], catchStatements: Array[TypedAST.ActionStatement]) = {
      this(null, Array(), tryStatement, catchTypes, catchStatements, null)
    }

    def this(tryStatement: TypedAST.ActionStatement, catchTypes: Array[ClosureLocalBinding], catchStatements: Array[TypedAST.ActionStatement], finallyStatement: TypedAST.ActionStatement) = {
      this(null, Array(), tryStatement, catchTypes, catchStatements, finallyStatement)
    }

    def this(resources: Array[(ClosureLocalBinding, Term)], tryStatement: TypedAST.ActionStatement, catchTypes: Array[ClosureLocalBinding], catchStatements: Array[TypedAST.ActionStatement], finallyStatement: TypedAST.ActionStatement) = {
      this(null, resources, tryStatement, catchTypes, catchStatements, finallyStatement)
    }
  }

  object UnaryTerm {

    /** Unary operator kinds */
    enum Kind:
      case PLUS, MINUS, NOT, BIT_NOT

  }

  class UnaryTerm(location: Location, val kind: UnaryTerm.Kind, val `type`: TypedAST.Type, val operand: TypedAST.Term)  extends Term(location) {
    def this(kind: UnaryTerm.Kind, `type`: TypedAST.Type, operand: TypedAST.Term) = {
      this(null, kind, `type`, operand)
    }
  }

  case class TypeParameter(name: String, upperBound: Option[TypedAST.Type])

  object AppliedClassType {
    private val cache = scala.collection.mutable.HashMap[(TypedAST.ClassType, scala.collection.immutable.List[TypedAST.Type]), AppliedClassType]()

    def apply(raw: TypedAST.ClassType, typeArguments: scala.collection.immutable.List[TypedAST.Type]): AppliedClassType =
      cache.getOrElseUpdate((raw, typeArguments), new AppliedClassType(raw, typeArguments.toArray[TypedAST.Type]))
  }

  final class AppliedClassType private(val raw: TypedAST.ClassType, val typeArguments: Array[TypedAST.Type])
    extends AbstractClassType {
    def name: String = raw.name
    def isInterface: Boolean = raw.isInterface
    def modifier: Int = raw.modifier

    private lazy val subst: scala.collection.immutable.Map[String, TypedAST.Type] =
      raw.typeParameters.map(_.name).zip(typeArguments).toMap

    private def substitute(tp: TypedAST.Type): TypedAST.Type = tp match
      case tv: TypedAST.TypeVariableType =>
        subst.getOrElse(tv.name, tv)
      case ap: TypedAST.AppliedClassType =>
        val newArgs = ap.typeArguments.map(substitute)
        if newArgs.sameElements(ap.typeArguments) then ap
        else TypedAST.AppliedClassType(ap.raw, newArgs.toList)
      case at: TypedAST.ArrayType =>
        val newComponent = substitute(at.component)
        if newComponent eq at.component then at
        else at.table.loadArray(newComponent, at.dimension)
      case w: TypedAST.WildcardType =>
        val newUpper = substitute(w.upperBound)
        val newLower = w.lowerBound.map(substitute)
        if ((newUpper eq w.upperBound) && newLower == w.lowerBound) then w
        else new TypedAST.WildcardType(newUpper, newLower)
      case other =>
        other

    private def specializeClass(tp: TypedAST.ClassType): TypedAST.ClassType =
      substitute(tp).asInstanceOf[TypedAST.ClassType]

    private lazy val superClass0: TypedAST.ClassType =
      if raw.superClass == null then null else specializeClass(raw.superClass)

    private lazy val interfaces0: Seq[TypedAST.ClassType] =
      raw.interfaces.map(specializeClass)

    def superClass: TypedAST.ClassType = superClass0
    def interfaces: Seq[TypedAST.ClassType] = interfaces0
    def methods: Seq[TypedAST.Method] = raw.methods
    def methods(name: String): Array[TypedAST.Method] = raw.methods(name)
    def fields: Array[TypedAST.FieldRef] = raw.fields
    def field(name: String): TypedAST.FieldRef = raw.field(name)
    def constructors: Array[TypedAST.ConstructorRef] = raw.constructors
    override def typeParameters: Array[TypedAST.TypeParameter] = Array()
  }

  final class TypeVariableType(val name: String, val upperBound: TypedAST.ClassType) extends AbstractClassType {
    def isInterface: Boolean = upperBound.isInterface
    def modifier: Int = upperBound.modifier
    def superClass: TypedAST.ClassType = upperBound.superClass
    def interfaces: Seq[TypedAST.ClassType] = upperBound.interfaces
    def methods: Seq[TypedAST.Method] = upperBound.methods
    def methods(name: String): Array[TypedAST.Method] = upperBound.methods(name)
    def fields: Array[TypedAST.FieldRef] = upperBound.fields
    def field(name: String): TypedAST.FieldRef = upperBound.field(name)
    def constructors: Array[TypedAST.ConstructorRef] = upperBound.constructors
    override def typeParameters: Array[TypedAST.TypeParameter] = Array()
  }

  final class WildcardType(val upperBound: TypedAST.Type, val lowerBound: Option[TypedAST.Type]) extends Type {
    def name: String =
      lowerBound match
        case Some(lb) => s"? super ${lb.name}"
        case None =>
          upperBound match
            case ct: TypedAST.ClassType if ct.name == "java.lang.Object" => "?"
            case _ => s"? extends ${upperBound.name}"

    def isArrayType: Boolean = false
    def isBasicType: Boolean = false
    def isClassType: Boolean = false
    def isNullType: Boolean = false
    def isObjectType: Boolean = false
  }

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
  class ArrayType(val component: TypedAST.Type, val dimension: Int, val table: ClassTable) extends AbstractObjectType {
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

    def typeParameters: Array[TypedAST.TypeParameter] = Array()
  }

  class ConstructorFinder {
    def find(target: TypedAST.ClassType, args: Array[TypedAST.Term]): Array[TypedAST.ConstructorRef] = {
      if (target == null) return Array.empty
      val matching = target.constructors.filter(c => matcher.matches(c.getArgs, args))
      if (matching.isEmpty) return Array.empty
      val sorted = matching.sortWith((c1, c2) => sorter.compare(c1, c2) < 0)
      if (sorted.length < 2 || isAmbiguous(sorted(0), sorted(1))) sorted
      else Array(sorted(0))
    }

    private def isAmbiguous(constructor1: TypedAST.ConstructorRef, constructor2: TypedAST.ConstructorRef): Boolean = {
      sorter.compare(constructor1, constructor2) >= 0
    }

    private final val sorter: Comparator[TypedAST.ConstructorRef] = new Comparator[TypedAST.ConstructorRef] {
      def compare(c1: TypedAST.ConstructorRef, c2: TypedAST.ConstructorRef): Int = {
        val arg1: Array[TypedAST.Type] = c1.getArgs
        val arg2: Array[TypedAST.Type] = c2.getArgs
        if (TypeRules.isAllSuperType(arg2, arg1)) return -1
        if (TypeRules.isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: TypedAST.ParameterMatcher = new TypedAST.StandardParameterMatcher
  }

  trait ConstructorRef extends MemberRef {
    def affiliation: TypedAST.ClassType

    def getArgs: Array[TypedAST.Type]

    def typeParameters: Array[TypedAST.TypeParameter] = Array()
  }

  /**
   * @author Kota Mizushima
   */
  class ConstructorComparator extends Comparator[TypedAST.ConstructorRef] {
    def compare(c1: TypedAST.ConstructorRef, c2: TypedAST.ConstructorRef): Int = {
      val args1: Array[TypedAST.Type] = c1.getArgs
      val args2: Array[TypedAST.Type] = c2.getArgs
      val result: Int = args1.length - args2.length
      if (result != 0) return result
      var i = 0
      while (i < args1.length) {
        if (args1(i) ne args2(i)) return args1(i).name.compareTo(args2(i).name)
        i += 1
      }
      0
    }
  }

  class FieldFinder {
    def find(target: TypedAST.ObjectType, name: String): TypedAST.FieldRef = {
      if (target == null) null
      else Option(target.field(name))
        .orElse(Option(find(target.superClass, name)))
        .orElse(target.interfaces.view.map(i => find(i, name)).find(_ != null))
        .orNull
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
  class MethodFinder {
    def find(target: TypedAST.ObjectType, name: String, arguments: Array[TypedAST.Term]): Array[TypedAST.Method] = {
      val methods: Set[TypedAST.Method] = new TreeSet[TypedAST.Method](new TypedAST.MethodComparator)
      find(methods, target, name, arguments)
      val sorted = methods.asScala.toArray.sortWith((m1, m2) => sorter.compare(m1, m2) < 0)
      if (sorted.length < 2 || isAmbiguous(sorted(0), sorted(1))) sorted
      else Array(sorted(0))
    }

    def isAmbiguous(method1: TypedAST.Method, method2: TypedAST.Method): Boolean =  sorter.compare(method1, method2) >= 0

    private def find(methods: Set[TypedAST.Method], target: TypedAST.ObjectType, name: String, params: Array[TypedAST.Term]): Unit = {
      if (target == null) return
      val ms: Array[TypedAST.Method] = target.methods(name)
      for (m <- ms) {
        if (matcher.matches(m.arguments, params)) methods.add(m)
        else if (m.isVararg && matcher.matchesVararg(m.arguments, params)) methods.add(m)
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
        if (TypeRules.isAllSuperType(arg2, arg1)) return -1
        if (TypeRules.isAllSuperType(arg1, arg2)) return 1
        0
      }
    }
    private final val matcher: TypedAST.ParameterMatcher  = new TypedAST.StandardParameterMatcher
  }

  /**
   * Represents a method argument with optional default value.
   */
  case class MethodArgument(name: String, argType: Type, defaultValue: Option[Term] = None)

  trait Method extends MemberRef {
    def affiliation: TypedAST.ClassType

    def arguments: Array[TypedAST.Type]

    def returnType: TypedAST.Type

    def typeParameters: Array[TypedAST.TypeParameter] = Array()

    /** Whether this method accepts variable-length arguments (last parameter is vararg) */
    def isVararg: Boolean = false

    /** Arguments with names and optional default values. Override in subclasses that support defaults. */
    def argumentsWithDefaults: Array[MethodArgument] = arguments.zipWithIndex.map { case (t, i) =>
      MethodArgument(s"arg$i", t, None)
    }

    /** Minimum number of required arguments (without defaults) */
    def minArguments: Int = argumentsWithDefaults.count(_.defaultValue.isEmpty)
  }

  class MethodComparator extends Comparator[TypedAST.Method] {
    def compare(m1: TypedAST.Method, m2: TypedAST.Method): Int = {
      var result: Int = m1.name.compareTo(m2.name)
      if (result != 0) return result
      val args1: Array[TypedAST.Type] = m1.arguments
      val args2: Array[TypedAST.Type] = m2.arguments
      result = args1.length - args2.length
      if (result != 0) return result
      var i = 0
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

  object BottomType {
    val BOTTOM: TypedAST.BottomType = new TypedAST.BottomType("Nothing")
  }

  class BottomType(val name: String) extends Type {
    def isArrayType: Boolean = false

    def isBasicType: Boolean = false

    def isClassType: Boolean = false

    def isNullType: Boolean = false

    override def isBottomType: Boolean = true

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
    def matchesVararg(arguments: Array[TypedAST.Type], parameters: Array[TypedAST.Term]): Boolean
  }

  /**
   * @author Kota Mizushima
   */
  class StandardParameterMatcher extends ParameterMatcher {
    def matches(arguments: Array[TypedAST.Type], parameters: Array[TypedAST.Term]): Boolean =
      if (arguments.length != parameters.length) false
      else {
        var i = 0
        while (i < arguments.length) {
          if (!TypeRules.isSuperType(arguments(i), parameters(i).`type`)) return false
          i += 1
        }
        true
      }

    def matchesVararg(arguments: Array[TypedAST.Type], parameters: Array[TypedAST.Term]): Boolean = {
      if (arguments.isEmpty) return false
      val lastArgType = arguments.last
      // Vararg parameter must be an array type
      if (!lastArgType.isArrayType) return false
      val arrayType = lastArgType.asInstanceOf[ArrayType]
      val componentType = arrayType.base

      val fixedArgCount = arguments.length - 1

      // Must have at least the fixed arguments
      if (parameters.length < fixedArgCount) return false

      // Check fixed arguments match
      var i = 0
      while (i < fixedArgCount) {
        if (!TypeRules.isSuperType(arguments(i), parameters(i).`type`)) return false
        i += 1
      }

      // Check vararg portion
      if (parameters.length == arguments.length) {
        // Could be either: passing an array directly, or passing one element
        val lastParamType = parameters.last.`type`
        // Check if it's a direct array pass
        if (lastParamType.isArrayType && TypeRules.isSuperType(lastArgType, lastParamType)) {
          return true
        }
        // Check if it's a single element that matches component type
        TypeRules.isSuperType(componentType, lastParamType)
      } else if (parameters.length > arguments.length) {
        // Multiple vararg elements - all must match component type
        i = fixedArgCount
        while (i < parameters.length) {
          if (!TypeRules.isSuperType(componentType, parameters(i).`type`)) return false
          i += 1
        }
        true
      } else if (parameters.length == fixedArgCount) {
        // No vararg elements - empty array will be passed
        true
      } else {
        false
      }
    }
  }

  abstract sealed class Type {
    def name: String

    def isBasicType: Boolean

    def isClassType: Boolean

    def isNullType: Boolean

    def isArrayType: Boolean

    def isBottomType: Boolean = false

    def isObjectType: Boolean
  }

  object TypeRules {
    def isSuperType(left: TypedAST.Type, right: TypedAST.Type): Boolean = {
      if (right.isBottomType) return true
      if (left.isBottomType) return right.isBottomType
      val l = left match {
        case tv: TypedAST.TypeVariableType => tv.upperBound
        case _ => left
      }
      val r = right match {
        case tv: TypedAST.TypeVariableType => tv.upperBound
        case _ => right
      }
      if (l.isBasicType) {
        if (r.isBasicType) {
          return isSuperTypeForBasic(l.asInstanceOf[TypedAST.BasicType], r.asInstanceOf[TypedAST.BasicType])
        }
        return false
      }
      if (l.isClassType) {
        if (r.isClassType) {
          return isSuperTypeForClass(l.asInstanceOf[TypedAST.ClassType], r.asInstanceOf[TypedAST.ClassType])
        }
        if (r.isArrayType) {
          return l eq r.asInstanceOf[TypedAST.ArrayType].superClass
        }
        if (r.isNullType) {
          return true
        }
        return false
      }
      if (l.isArrayType) {
        if (r.isArrayType) {
          return isSuperTypeForArray(l.asInstanceOf[TypedAST.ArrayType], r.asInstanceOf[TypedAST.ArrayType])
        }
        if (r.isNullType) {
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

      (left, right) match
        case (lraw, rapp: TypedAST.AppliedClassType) if lraw eq rapp.raw =>
          return true
        case (lapp: TypedAST.AppliedClassType, rapp: TypedAST.AppliedClassType)
          if (lapp.raw eq rapp.raw) && lapp.typeArguments.length == rapp.typeArguments.length =>
          return lapp.typeArguments.zip(rapp.typeArguments).forall { (expectedArg, actualArg) =>
            expectedArg match
              case w: TypedAST.WildcardType =>
                w.lowerBound match
                  case Some(lb) => isSuperType(actualArg, lb)
                  case None => isSuperType(w.upperBound, actualArg)
              case tv: TypedAST.TypeVariableType =>
                isSuperType(tv.upperBound, actualArg)
              case _ =>
                expectedArg eq actualArg
          }
        case _ =>
      isSuperTypeForClass(left, right.superClass) ||
        right.interfaces.exists(iface => isSuperTypeForClass(left, iface))
    }

    // Map from a BasicType to the set of types that can be assigned to it
    private val basicTypeAssignableFrom: scala.collection.immutable.Map[BasicType, scala.collection.immutable.Set[BasicType]] = {
      import scala.collection.immutable.{Map => SMap, Set => SSet}
      SMap(
        BasicType.DOUBLE  -> SSet(BasicType.CHAR, BasicType.BYTE, BasicType.SHORT, BasicType.INT, BasicType.LONG, BasicType.FLOAT, BasicType.DOUBLE),
        BasicType.FLOAT   -> SSet(BasicType.CHAR, BasicType.BYTE, BasicType.SHORT, BasicType.INT, BasicType.LONG, BasicType.FLOAT),
        BasicType.LONG    -> SSet(BasicType.CHAR, BasicType.BYTE, BasicType.SHORT, BasicType.INT, BasicType.LONG),
        BasicType.INT     -> SSet(BasicType.CHAR, BasicType.BYTE, BasicType.SHORT, BasicType.INT),
        BasicType.SHORT   -> SSet(BasicType.BYTE, BasicType.SHORT),
        BasicType.BOOLEAN -> SSet(BasicType.BOOLEAN),
        BasicType.BYTE    -> SSet(BasicType.BYTE),
        BasicType.CHAR    -> SSet(BasicType.CHAR),
        BasicType.VOID    -> SSet(BasicType.VOID)
      )
    }

    private def isSuperTypeForBasic(left: TypedAST.BasicType, right: TypedAST.BasicType): Boolean =
      basicTypeAssignableFrom.get(left).exists(_.contains(right))

    /** Check if all types in arg1 are supertypes of corresponding types in arg2 */
    def isAllSuperType(arg1: Array[TypedAST.Type], arg2: Array[TypedAST.Type]): Boolean =
      arg1.zip(arg2).forall((a1, a2) => isSuperType(a1, a2))
  }

  class TypeRules
}
