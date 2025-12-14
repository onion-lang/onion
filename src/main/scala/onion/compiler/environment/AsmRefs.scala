package onion.compiler.environment

import onion.compiler.{IRT, Modifier, OnionTypeConversion, MultiTable, OrderedTable, ClassTable}
import org.objectweb.asm.{ClassReader, Opcodes, Type}
import org.objectweb.asm.signature.{SignatureReader, SignatureVisitor}
import org.objectweb.asm.tree.{ClassNode, MethodNode, FieldNode}
import scala.collection.mutable

object AsmRefs {
  private def toOnionModifier(access: Int): Int = {
    var mod = 0
    if ((access & Opcodes.ACC_PRIVATE) != 0) mod |= Modifier.PRIVATE
    if ((access & Opcodes.ACC_PROTECTED) != 0) mod |= Modifier.PROTECTED
    if ((access & Opcodes.ACC_PUBLIC) != 0) mod |= Modifier.PUBLIC
    if ((access & Opcodes.ACC_STATIC) != 0) mod |= Modifier.STATIC
    if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) mod |= Modifier.SYNCHRONIZED
    if ((access & Opcodes.ACC_ABSTRACT) != 0) mod |= Modifier.ABSTRACT
    if ((access & Opcodes.ACC_FINAL) != 0) mod |= Modifier.FINAL
    mod
  }

  final val CONSTRUCTOR_NAME = "<init>"

  private final class SignatureTypeMapper(table: ClassTable, baseEnv: Map[String, IRT.TypeVariableType], root0: () => IRT.ClassType) {
    private def root: IRT.ClassType = root0()

    private def typeParamEnv(typeParams: Array[IRT.TypeParameter]): Map[String, IRT.TypeVariableType] = {
      val env = mutable.HashMap[String, IRT.TypeVariableType]() ++ baseEnv
      var i = 0
      while (i < typeParams.length) {
        val tp = typeParams(i)
        val upper = tp.upperBound match {
          case Some(ap: IRT.AppliedClassType) => ap.raw
          case Some(ct: IRT.ClassType) => ct
          case _ => root
        }
        env += tp.name -> new IRT.TypeVariableType(tp.name, upper)
        i += 1
      }
      env.toMap
    }

    final class TypeRefVisitor(onComplete: IRT.Type => Unit, env: Map[String, IRT.TypeVariableType])
      extends SignatureVisitor(Opcodes.ASM9) {

      private var arrayDim = 0
      private var internalName: String = null
      private val innerNames = mutable.ArrayBuffer[String]()
      private val typeArgs = mutable.ArrayBuffer[IRT.Type]()
      private var done = false

      private def finish(t: IRT.Type): Unit = {
        if (done) return
        done = true
        if (t == null) {
          onComplete(null)
          return
        }
        val wrapped =
          if (arrayDim == 0) t
          else table.loadArray(t, arrayDim)
        onComplete(wrapped)
      }

      override def visitArrayType(): SignatureVisitor = {
        arrayDim += 1
        this
      }

      override def visitBaseType(descriptor: Char): Unit = {
        val tpe: IRT.Type =
          descriptor match {
            case 'V' => IRT.BasicType.VOID
            case 'Z' => IRT.BasicType.BOOLEAN
            case 'B' => IRT.BasicType.BYTE
            case 'S' => IRT.BasicType.SHORT
            case 'C' => IRT.BasicType.CHAR
            case 'I' => IRT.BasicType.INT
            case 'J' => IRT.BasicType.LONG
            case 'F' => IRT.BasicType.FLOAT
            case 'D' => IRT.BasicType.DOUBLE
            case _ => null
          }
        finish(tpe)
      }

      override def visitTypeVariable(name: String): Unit = {
        val tv = env.getOrElse(name, new IRT.TypeVariableType(name, root))
        finish(tv)
      }

      override def visitClassType(name: String): Unit = {
        internalName = name
      }

      override def visitInnerClassType(name: String): Unit = {
        innerNames += name
      }

      override def visitTypeArgument(): Unit = {
        typeArgs += root
      }

      override def visitTypeArgument(wildcard: Char): SignatureVisitor = {
        new TypeRefVisitor(
          t =>
            wildcard match {
              case '-' | '*' => typeArgs += root
              case _ => typeArgs += (if (t == null) root else t)
            },
          env
        )
      }

      override def visitEnd(): Unit = {
        if (internalName == null) {
          finish(null)
          return
        }
        val fullInternal =
          if (innerNames.isEmpty) internalName
          else internalName + innerNames.mkString("$", "$", "")
        val fqcn = fullInternal.replace('/', '.')
        val raw = table.load(fqcn)
        if (raw == null) {
          finish(null)
          return
        }
        if (typeArgs.isEmpty) finish(raw)
        else finish(IRT.AppliedClassType(raw, typeArgs.toList))
      }
    }

    final case class ClassInfo(
      typeParameters: Array[IRT.TypeParameter],
      superClass: IRT.ClassType,
      interfaces: Seq[IRT.ClassType],
      env: Map[String, IRT.TypeVariableType]
    )

    def parseClass(signature: String, fallbackSuper: String, fallbackIfaces: java.util.List[String]): ClassInfo = {
      val typeParamsBuf = mutable.ArrayBuffer[IRT.TypeParameter]()
      var currentName: String = null
      var currentUpper: IRT.ClassType = null
      var parsedSuper: IRT.ClassType = null
      val parsedIfaces = mutable.ArrayBuffer[IRT.ClassType]()

      def finishTypeParam(): Unit = {
        if (currentName == null) return
        val upper = if (currentUpper == null) root else currentUpper
        typeParamsBuf += IRT.TypeParameter(currentName, Some(upper))
        currentName = null
        currentUpper = null
      }

      val tmpEnv = mutable.HashMap[String, IRT.TypeVariableType]() ++ baseEnv

      val reader = new SignatureReader(signature)
      reader.accept(new SignatureVisitor(Opcodes.ASM9) {
        override def visitFormalTypeParameter(name: String): Unit = {
          finishTypeParam()
          currentName = name
          tmpEnv += name -> new IRT.TypeVariableType(name, root)
        }

        override def visitClassBound(): SignatureVisitor =
          new TypeRefVisitor(
            t =>
              if (currentUpper == null) {
                currentUpper = t match {
                  case ap: IRT.AppliedClassType => ap.raw
                  case ct: IRT.ClassType => ct
                  case _ => root
                }
              },
            tmpEnv.toMap
          )

        override def visitInterfaceBound(): SignatureVisitor =
          new TypeRefVisitor(
            t =>
              if (currentUpper == null) {
                currentUpper = t match {
                  case ap: IRT.AppliedClassType => ap.raw
                  case ct: IRT.ClassType => ct
                  case _ => root
                }
              },
            tmpEnv.toMap
          )

        override def visitSuperclass(): SignatureVisitor = {
          finishTypeParam()
          val (params, nextEnv) = {
            val arr = typeParamsBuf.toArray
            (arr, typeParamEnv(arr))
          }
          new TypeRefVisitor(
            t =>
              parsedSuper = t match {
                case ct: IRT.ClassType => ct
                case _ => null
              },
            nextEnv
          )
        }

        override def visitInterface(): SignatureVisitor = {
          val arr = typeParamsBuf.toArray
          val nextEnv = typeParamEnv(arr)
          new TypeRefVisitor(
            t =>
              t match {
                case ct: IRT.ClassType => parsedIfaces += ct
                case _ =>
              },
            nextEnv
          )
        }
      })

      finishTypeParam()
      val typeParams = typeParamsBuf.toArray
      val finalEnv = typeParamEnv(typeParams)

      val superClass0 =
        if (parsedSuper != null) parsedSuper
        else if (fallbackSuper == null) null
        else table.load(fallbackSuper.replace('/', '.'))

      import scala.jdk.CollectionConverters._
      val interfaces0 =
        if (parsedIfaces.nonEmpty) parsedIfaces.toIndexedSeq
        else fallbackIfaces.asScala.map(n => table.load(n.replace('/', '.'))).toIndexedSeq

      ClassInfo(typeParams, superClass0, interfaces0, finalEnv)
    }

    final case class MethodInfo(
      typeParameters: Array[IRT.TypeParameter],
      arguments: Array[IRT.Type],
      returnType: IRT.Type,
      env: Map[String, IRT.TypeVariableType]
    )

    def parseMethod(signature: String, desc: String): MethodInfo = {
      val typeParamsBuf = mutable.ArrayBuffer[IRT.TypeParameter]()
      var currentName: String = null
      var currentUpper: IRT.ClassType = null
      val argsBuf = mutable.ArrayBuffer[IRT.Type]()
      var return0: IRT.Type = null

      def finishTypeParam(): Unit = {
        if (currentName == null) return
        val upper = if (currentUpper == null) root else currentUpper
        typeParamsBuf += IRT.TypeParameter(currentName, Some(upper))
        currentName = null
        currentUpper = null
      }

      val tmpEnv = mutable.HashMap[String, IRT.TypeVariableType]() ++ baseEnv
      val reader = new SignatureReader(signature)
      reader.accept(new SignatureVisitor(Opcodes.ASM9) {
        override def visitFormalTypeParameter(name: String): Unit = {
          finishTypeParam()
          currentName = name
          tmpEnv += name -> new IRT.TypeVariableType(name, root)
        }

        override def visitClassBound(): SignatureVisitor =
          new TypeRefVisitor(
            t =>
              if (currentUpper == null) {
                currentUpper = t match {
                  case ap: IRT.AppliedClassType => ap.raw
                  case ct: IRT.ClassType => ct
                  case _ => root
                }
              },
            tmpEnv.toMap
          )

        override def visitInterfaceBound(): SignatureVisitor =
          new TypeRefVisitor(
            t =>
              if (currentUpper == null) {
                currentUpper = t match {
                  case ap: IRT.AppliedClassType => ap.raw
                  case ct: IRT.ClassType => ct
                  case _ => root
                }
              },
            tmpEnv.toMap
          )

        override def visitParameterType(): SignatureVisitor = {
          finishTypeParam()
          val typeParams = typeParamsBuf.toArray
          val env = typeParamEnv(typeParams)
          new TypeRefVisitor(t => argsBuf += t, env)
        }

        override def visitReturnType(): SignatureVisitor = {
          finishTypeParam()
          val typeParams = typeParamsBuf.toArray
          val env = typeParamEnv(typeParams)
          new TypeRefVisitor(t => return0 = t, env)
        }
      })

      val typeParams = typeParamsBuf.toArray
      val env = typeParamEnv(typeParams)

      if (argsBuf.isEmpty && return0 == null) {
        val bridge = new OnionTypeConversion(table)
        val asmArgs = Type.getArgumentTypes(desc)
        val argTypes = new Array[IRT.Type](asmArgs.length)
        var i = 0
        while (i < asmArgs.length) {
          argTypes(i) = bridge.toOnionType(asmArgs(i))
          i += 1
        }
        MethodInfo(Array.empty, argTypes, bridge.toOnionType(Type.getReturnType(desc)), env)
      } else {
        MethodInfo(typeParams, argsBuf.toArray, return0, env)
      }
    }

    def parseField(signature: String, desc: String): IRT.Type = {
      var result: IRT.Type = null
      val reader = new SignatureReader(signature)
      reader.acceptType(new TypeRefVisitor(t => result = t, baseEnv))
      if (result == null) {
        val bridge = new OnionTypeConversion(table)
        bridge.toOnionType(Type.getType(desc))
      } else result
    }
  }

  class AsmMethodRef(method: MethodNode, override val affiliation: IRT.ClassType, table: ClassTable, classEnv: Map[String, IRT.TypeVariableType]) extends IRT.Method {
    override val modifier: Int = toOnionModifier(method.access)
    override val name: String = method.name
    private val bridge = new OnionTypeConversion(table)
    private val mapper = new SignatureTypeMapper(table, classEnv, () => table.load("java.lang.Object"))
    private val parsed =
      if (method.signature != null) mapper.parseMethod(method.signature, method.desc)
      else null
    override val typeParameters: Array[IRT.TypeParameter] =
      if (parsed == null) Array()
      else parsed.typeParameters.clone()
    private val argTypes: Array[IRT.Type] =
      if (parsed == null) {
        val asmTypes = Type.getArgumentTypes(method.desc)
        val result = new Array[IRT.Type](asmTypes.length)
        var i = 0
        while (i < asmTypes.length) {
          result(i) = bridge.toOnionType(asmTypes(i))
          i += 1
        }
        result
      } else {
        parsed.arguments
      }
    override def arguments: Array[IRT.Type] = argTypes.clone()
    override val returnType: IRT.Type =
      if (parsed == null) bridge.toOnionType(Type.getReturnType(method.desc))
      else parsed.returnType
    val underlying: MethodNode = method
  }

  class AsmFieldRef(field: FieldNode, override val affiliation: IRT.ClassType, table: ClassTable, classEnv: Map[String, IRT.TypeVariableType]) extends IRT.FieldRef {
    override val modifier: Int = toOnionModifier(field.access)
    override val name: String = field.name
    private val mapper = new SignatureTypeMapper(table, classEnv, () => table.load("java.lang.Object"))
    override val `type`: IRT.Type =
      if (field.signature != null) mapper.parseField(field.signature, field.desc)
      else new OnionTypeConversion(table).toOnionType(Type.getType(field.desc))
    val underlying: FieldNode = field
  }

  class AsmConstructorRef(method: MethodNode, override val affiliation: IRT.ClassType, table: ClassTable, classEnv: Map[String, IRT.TypeVariableType]) extends IRT.ConstructorRef {
    override val modifier: Int = toOnionModifier(method.access)
    override val name: String = CONSTRUCTOR_NAME
    private val bridge = new OnionTypeConversion(table)
    private val mapper = new SignatureTypeMapper(table, classEnv, () => table.load("java.lang.Object"))
    private val parsed =
      if (method.signature != null) mapper.parseMethod(method.signature, method.desc)
      else null
    override val typeParameters: Array[IRT.TypeParameter] =
      if (parsed == null) Array()
      else parsed.typeParameters.clone()
    private val args0 =
      if (parsed == null) {
        val asmTypes = Type.getArgumentTypes(method.desc)
        val result = new Array[IRT.Type](asmTypes.length)
        var i = 0
        while (i < asmTypes.length) {
          result(i) = bridge.toOnionType(asmTypes(i))
          i += 1
        }
        result
      } else {
        parsed.arguments
      }
    override def getArgs: Array[IRT.Type] = args0.clone()
    val underlying: MethodNode = method
  }

  class AsmClassType(classBytes: Array[Byte], table: ClassTable) extends IRT.AbstractClassType {
    private val node = {
      val cr = new ClassReader(classBytes)
      val n = new ClassNode()
      cr.accept(n, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
      n
    }

    private val modifier_ = toOnionModifier(node.access)
    private def root: IRT.ClassType = if (node.name == "java/lang/Object") this else table.load("java.lang.Object")
    private val genericMapper = new SignatureTypeMapper(table, Map.empty, () => root)
    private lazy val classInfo =
      if (node.signature != null) genericMapper.parseClass(node.signature, node.superName, node.interfaces.asInstanceOf[java.util.List[String]])
      else {
        import scala.jdk.CollectionConverters._
        val super0 = if (node.superName == null) null else table.load(node.superName.replace('/', '.'))
        val ifaces0 = node.interfaces.asInstanceOf[java.util.List[String]].asScala.map(n => table.load(n.replace('/', '.'))).toIndexedSeq
        genericMapper.ClassInfo(Array.empty, super0, ifaces0, Map.empty)
      }

    override def typeParameters: Array[IRT.TypeParameter] = classInfo.typeParameters.clone()
    private lazy val classEnv: Map[String, IRT.TypeVariableType] = classInfo.env

    private lazy val methods_ : MultiTable[IRT.Method] = {
      val m = new MultiTable[IRT.Method]
      import scala.jdk.CollectionConverters._
      for (method <- node.methods.asInstanceOf[java.util.List[MethodNode]].asScala if method.name != CONSTRUCTOR_NAME) {
        m.add(new AsmMethodRef(method, this, table, classEnv))
      }
      m
    }

    private lazy val fields_ : OrderedTable[IRT.FieldRef] = {
      val f = new OrderedTable[IRT.FieldRef]
      import scala.jdk.CollectionConverters._
      for (field <- node.fields.asInstanceOf[java.util.List[FieldNode]].asScala) {
        f.add(new AsmFieldRef(field, this, table, classEnv))
      }
      f
    }

    private lazy val constructors_ : Seq[IRT.ConstructorRef] = {
      import scala.jdk.CollectionConverters._
      node.methods.asInstanceOf[java.util.List[MethodNode]].asScala.collect {
        case m if m.name == CONSTRUCTOR_NAME => new AsmConstructorRef(m, this, table, classEnv)
      }.toSeq
    }

    def isInterface: Boolean = (node.access & Opcodes.ACC_INTERFACE) != 0
    def modifier: Int = modifier_
    def name: String = node.name.replace('/', '.')
    def superClass: IRT.ClassType = {
      classInfo.superClass
    }
    def interfaces: Seq[IRT.ClassType] = {
      classInfo.interfaces
    }

    def methods: Seq[IRT.Method] = methods_.values
    def methods(name: String): Array[IRT.Method] = methods_.get(name).toArray
    def fields: Array[IRT.FieldRef] = fields_.values.toArray
    def field(name: String): IRT.FieldRef = fields_.get(name).orNull
    def constructors: Array[IRT.ConstructorRef] = constructors_.toArray
  }
}
