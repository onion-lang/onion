package onion.compiler.environment

import onion.compiler.{IRT, Modifier, OnionTypeConversion, MultiTable, OrderedTable, ClassTable}
import org.objectweb.asm.{ClassReader, Opcodes, Type}
import org.objectweb.asm.tree.{ClassNode, MethodNode, FieldNode}

object AsmRefs:
  private def toOnionModifier(access: Int): Int =
    var mod = 0
    if (access & Opcodes.ACC_PRIVATE) != 0 then mod |= Modifier.PRIVATE
    if (access & Opcodes.ACC_PROTECTED) != 0 then mod |= Modifier.PROTECTED
    if (access & Opcodes.ACC_PUBLIC) != 0 then mod |= Modifier.PUBLIC
    if (access & Opcodes.ACC_STATIC) != 0 then mod |= Modifier.STATIC
    if (access & Opcodes.ACC_SYNCHRONIZED) != 0 then mod |= Modifier.SYNCHRONIZED
    if (access & Opcodes.ACC_ABSTRACT) != 0 then mod |= Modifier.ABSTRACT
    if (access & Opcodes.ACC_FINAL) != 0 then mod |= Modifier.FINAL
    mod

  final val CONSTRUCTOR_NAME = "<init>"

  class AsmMethodRef(method: MethodNode, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.Method:
    override val modifier: Int = toOnionModifier(method.access)
    override val name: String = method.name
    private val argTypes = Type.getArgumentTypes(method.desc).map(t => bridge.toOnionType(t))
    override def arguments: Array[IRT.Type] = argTypes.clone()
    override val returnType: IRT.Type = bridge.toOnionType(Type.getReturnType(method.desc))
    val underlying: MethodNode = method

  class AsmFieldRef(field: FieldNode, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.FieldRef:
    override val modifier: Int = toOnionModifier(field.access)
    override val name: String = field.name
    override val `type`: IRT.Type = bridge.toOnionType(Type.getType(field.desc))
    val underlying: FieldNode = field

  class AsmConstructorRef(method: MethodNode, override val affiliation: IRT.ClassType, bridge: OnionTypeConversion) extends IRT.ConstructorRef:
    override val modifier: Int = toOnionModifier(method.access)
    override val name: String = CONSTRUCTOR_NAME
    private val args0 = Type.getArgumentTypes(method.desc).map(t => bridge.toOnionType(t))
    override def getArgs: Array[IRT.Type] = args0.clone()
    val underlying: MethodNode = method

  class AsmClassType(classBytes: Array[Byte], table: ClassTable) extends IRT.AbstractClassType:
    private val node =
      val cr = new ClassReader(classBytes)
      val n = new ClassNode()
      cr.accept(n, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
      n

    private val bridge = new OnionTypeConversion(table)
    private val modifier_ = toOnionModifier(node.access)

    private lazy val methods_ : MultiTable[IRT.Method] =
      val m = new MultiTable[IRT.Method]
      import scala.jdk.CollectionConverters.*
      for method <- node.methods.asInstanceOf[java.util.List[MethodNode]].asScala if method.name != CONSTRUCTOR_NAME do
        m.add(new AsmMethodRef(method, this, bridge))
      m

    private lazy val fields_ : OrderedTable[IRT.FieldRef] =
      val f = new OrderedTable[IRT.FieldRef]
      import scala.jdk.CollectionConverters.*
      for field <- node.fields.asInstanceOf[java.util.List[FieldNode]].asScala do
        f.add(new AsmFieldRef(field, this, bridge))
      f

    private lazy val constructors_ : Seq[IRT.ConstructorRef] =
      import scala.jdk.CollectionConverters.*
      node.methods.asInstanceOf[java.util.List[MethodNode]].asScala.collect {
        case m if m.name == CONSTRUCTOR_NAME => new AsmConstructorRef(m, this, bridge)
      }.toSeq

    def isInterface: Boolean = (node.access & Opcodes.ACC_INTERFACE) != 0
    def modifier: Int = modifier_
    def name: String = node.name.replace('/', '.')
    def superClass: IRT.ClassType =
      if node.superName == null then null else table.load(node.superName.replace('/', '.'))
    def interfaces: Seq[IRT.ClassType] =
      import scala.jdk.CollectionConverters.*
      node.interfaces.asInstanceOf[java.util.List[String]].asScala.map(n => table.load(n.replace('/', '.'))).toIndexedSeq

    def methods: Seq[IRT.Method] = methods_.values
    def methods(name: String): Array[IRT.Method] = methods_.get(name).toArray
    def fields: Array[IRT.FieldRef] = fields_.values.toArray
    def field(name: String): IRT.FieldRef = fields_.get(name).orNull
    def constructors: Array[IRT.ConstructorRef] = constructors_.toArray

