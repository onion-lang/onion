package onion.compiler.environment

import onion.compiler.{TypedAST, Modifier, OnionTypeConversion, MultiTable, OrderedTable, ClassTable}
import org.objectweb.asm.{ClassReader, Opcodes, Type}
import org.objectweb.asm.signature.{SignatureReader, SignatureVisitor}
import org.objectweb.asm.tree.{ClassNode, MethodNode, FieldNode}
import scala.collection.mutable

object AsmRefs {
  private val asmModifierMappings = Seq(
    Opcodes.ACC_PRIVATE -> Modifier.PRIVATE, Opcodes.ACC_PROTECTED -> Modifier.PROTECTED,
    Opcodes.ACC_PUBLIC -> Modifier.PUBLIC, Opcodes.ACC_STATIC -> Modifier.STATIC,
    Opcodes.ACC_SYNCHRONIZED -> Modifier.SYNCHRONIZED, Opcodes.ACC_ABSTRACT -> Modifier.ABSTRACT,
    Opcodes.ACC_FINAL -> Modifier.FINAL
  )
  private def toOnionModifier(access: Int): Int =
    asmModifierMappings.foldLeft(0) { case (acc, (srcFlag, dstFlag)) =>
      if ((access & srcFlag) != 0) acc | dstFlag else acc
    }

  final val CONSTRUCTOR_NAME = "<init>"

  private final class SignatureTypeMapper(table: ClassTable, baseEnv: Map[String, TypedAST.TypeVariableType], root0: () => TypedAST.ClassType) {
    private def root: TypedAST.ClassType = root0()

    private def typeParamEnv(typeParams: Array[TypedAST.TypeParameter]): Map[String, TypedAST.TypeVariableType] =
      baseEnv ++ typeParams.map { tp =>
        val upper = tp.upperBound match {
          case Some(ap: TypedAST.AppliedClassType) => ap.raw
          case Some(ct: TypedAST.ClassType) => ct
          case _ => root
        }
        tp.name -> new TypedAST.TypeVariableType(tp.name, upper)
      }.toMap

    final class TypeRefVisitor(onComplete: TypedAST.Type => Unit, env: Map[String, TypedAST.TypeVariableType])
      extends SignatureVisitor(Opcodes.ASM9) {

      private var arrayDim = 0
      private var internalName: String = null
      private val innerNames = mutable.ArrayBuffer[String]()
      private val typeArgs = mutable.ArrayBuffer[TypedAST.Type]()
      private var done = false

      private def finish(t: TypedAST.Type): Unit = {
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
        val tpe: TypedAST.Type =
          descriptor match {
            case 'V' => TypedAST.BasicType.VOID
            case 'Z' => TypedAST.BasicType.BOOLEAN
            case 'B' => TypedAST.BasicType.BYTE
            case 'S' => TypedAST.BasicType.SHORT
            case 'C' => TypedAST.BasicType.CHAR
            case 'I' => TypedAST.BasicType.INT
            case 'J' => TypedAST.BasicType.LONG
            case 'F' => TypedAST.BasicType.FLOAT
            case 'D' => TypedAST.BasicType.DOUBLE
            case _ => null
          }
        finish(tpe)
      }

      override def visitTypeVariable(name: String): Unit = {
        val tv = env.getOrElse(name, new TypedAST.TypeVariableType(name, root))
        finish(tv)
      }

      override def visitClassType(name: String): Unit = {
        internalName = name
      }

      override def visitInnerClassType(name: String): Unit = {
        innerNames += name
      }

      override def visitTypeArgument(): Unit = {
        typeArgs += new TypedAST.WildcardType(root, None)
      }

      override def visitTypeArgument(wildcard: Char): SignatureVisitor = {
        new TypeRefVisitor(
          t =>
            wildcard match {
              case '+' =>
                typeArgs += new TypedAST.WildcardType(if (t == null) root else t, None)
              case '-' =>
                typeArgs += new TypedAST.WildcardType(root, Some(if (t == null) root else t))
              case _ =>
                typeArgs += (if (t == null) root else t)
            }
          ,
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
        else finish(TypedAST.AppliedClassType(raw, typeArgs.toList))
      }
    }

    final case class ClassInfo(
      typeParameters: Array[TypedAST.TypeParameter],
      superClass: TypedAST.ClassType,
      interfaces: Seq[TypedAST.ClassType],
      env: Map[String, TypedAST.TypeVariableType]
    )

    def parseClass(signature: String, fallbackSuper: String, fallbackIfaces: java.util.List[String]): ClassInfo = {
      val typeParamsBuf = mutable.ArrayBuffer[TypedAST.TypeParameter]()
      var currentName: String = null
      var currentUpper: TypedAST.ClassType = null
      var parsedSuper: TypedAST.ClassType = null
      val parsedIfaces = mutable.ArrayBuffer[TypedAST.ClassType]()

      def finishTypeParam(): Unit = {
        if (currentName == null) return
        val upper = if (currentUpper == null) root else currentUpper
        typeParamsBuf += TypedAST.TypeParameter(currentName, Some(upper))
        currentName = null
        currentUpper = null
      }

      val tmpEnv = mutable.HashMap[String, TypedAST.TypeVariableType]() ++ baseEnv

      val reader = new SignatureReader(signature)
      reader.accept(new SignatureVisitor(Opcodes.ASM9) {
        override def visitFormalTypeParameter(name: String): Unit = {
          finishTypeParam()
          currentName = name
          tmpEnv += name -> new TypedAST.TypeVariableType(name, root)
        }

        override def visitClassBound(): SignatureVisitor =
          new TypeRefVisitor(
            t =>
              if (currentUpper == null) {
                currentUpper = t match {
                  case ap: TypedAST.AppliedClassType => ap.raw
                  case ct: TypedAST.ClassType => ct
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
                  case ap: TypedAST.AppliedClassType => ap.raw
                  case ct: TypedAST.ClassType => ct
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
                case ct: TypedAST.ClassType => ct
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
                case ct: TypedAST.ClassType => parsedIfaces += ct
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
      typeParameters: Array[TypedAST.TypeParameter],
      arguments: Array[TypedAST.Type],
      returnType: TypedAST.Type,
      env: Map[String, TypedAST.TypeVariableType]
    )

    def parseMethod(signature: String, desc: String): MethodInfo = {
      val typeParamsBuf = mutable.ArrayBuffer[TypedAST.TypeParameter]()
      var currentName: String = null
      var currentUpper: TypedAST.ClassType = null
      val argsBuf = mutable.ArrayBuffer[TypedAST.Type]()
      var return0: TypedAST.Type = null

      def finishTypeParam(): Unit = {
        if (currentName == null) return
        val upper = if (currentUpper == null) root else currentUpper
        typeParamsBuf += TypedAST.TypeParameter(currentName, Some(upper))
        currentName = null
        currentUpper = null
      }

      val tmpEnv = mutable.HashMap[String, TypedAST.TypeVariableType]() ++ baseEnv
      val reader = new SignatureReader(signature)
      reader.accept(new SignatureVisitor(Opcodes.ASM9) {
        override def visitFormalTypeParameter(name: String): Unit = {
          finishTypeParam()
          currentName = name
          tmpEnv += name -> new TypedAST.TypeVariableType(name, root)
        }

        override def visitClassBound(): SignatureVisitor =
          new TypeRefVisitor(
            t =>
              if (currentUpper == null) {
                currentUpper = t match {
                  case ap: TypedAST.AppliedClassType => ap.raw
                  case ct: TypedAST.ClassType => ct
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
                  case ap: TypedAST.AppliedClassType => ap.raw
                  case ct: TypedAST.ClassType => ct
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
        val argTypes = Type.getArgumentTypes(desc).map(bridge.toOnionType)
        MethodInfo(Array.empty, argTypes, bridge.toOnionType(Type.getReturnType(desc)), env)
      } else {
        MethodInfo(typeParams, argsBuf.toArray, return0, env)
      }
    }

    def parseField(signature: String, desc: String): TypedAST.Type = {
      var result: TypedAST.Type = null
      val reader = new SignatureReader(signature)
      reader.acceptType(new TypeRefVisitor(t => result = t, baseEnv))
      if (result == null) {
        val bridge = new OnionTypeConversion(table)
        bridge.toOnionType(Type.getType(desc))
      } else result
    }
  }

  class AsmMethodRef(method: MethodNode, override val affiliation: TypedAST.ClassType, table: ClassTable, classEnv: Map[String, TypedAST.TypeVariableType]) extends TypedAST.Method {
    override val modifier: Int = toOnionModifier(method.access)
    override val name: String = method.name
    private val bridge = new OnionTypeConversion(table)
    private val mapper = new SignatureTypeMapper(table, classEnv, () => table.load("java.lang.Object"))
    private val parsed =
      if (method.signature != null) mapper.parseMethod(method.signature, method.desc)
      else null
    override val typeParameters: Array[TypedAST.TypeParameter] =
      if (parsed == null) Array()
      else parsed.typeParameters.clone()
    private val argTypes: Array[TypedAST.Type] =
      if (parsed == null) Type.getArgumentTypes(method.desc).map(bridge.toOnionType)
      else parsed.arguments
    override def arguments: Array[TypedAST.Type] = argTypes.clone()
    override val returnType: TypedAST.Type =
      if (parsed == null) bridge.toOnionType(Type.getReturnType(method.desc))
      else parsed.returnType
    val underlying: MethodNode = method
  }

  class AsmFieldRef(field: FieldNode, override val affiliation: TypedAST.ClassType, table: ClassTable, classEnv: Map[String, TypedAST.TypeVariableType]) extends TypedAST.FieldRef {
    override val modifier: Int = toOnionModifier(field.access)
    override val name: String = field.name
    private val mapper = new SignatureTypeMapper(table, classEnv, () => table.load("java.lang.Object"))
    override val `type`: TypedAST.Type =
      if (field.signature != null) mapper.parseField(field.signature, field.desc)
      else new OnionTypeConversion(table).toOnionType(Type.getType(field.desc))
    val underlying: FieldNode = field
  }

  class AsmConstructorRef(method: MethodNode, override val affiliation: TypedAST.ClassType, table: ClassTable, classEnv: Map[String, TypedAST.TypeVariableType]) extends TypedAST.ConstructorRef {
    override val modifier: Int = toOnionModifier(method.access)
    override val name: String = CONSTRUCTOR_NAME
    private val bridge = new OnionTypeConversion(table)
    private val mapper = new SignatureTypeMapper(table, classEnv, () => table.load("java.lang.Object"))
    private val parsed =
      if (method.signature != null) mapper.parseMethod(method.signature, method.desc)
      else null
    override val typeParameters: Array[TypedAST.TypeParameter] =
      if (parsed == null) Array()
      else parsed.typeParameters.clone()
    private val args0 =
      if (parsed == null) Type.getArgumentTypes(method.desc).map(bridge.toOnionType)
      else parsed.arguments
    override def getArgs: Array[TypedAST.Type] = args0.clone()
    val underlying: MethodNode = method
  }

  class AsmClassType(classBytes: Array[Byte], table: ClassTable) extends TypedAST.AbstractClassType {
    private val node = {
      val cr = new ClassReader(classBytes)
      val n = new ClassNode()
      cr.accept(n, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
      n
    }

    private val modifier_ = toOnionModifier(node.access)
    private def root: TypedAST.ClassType = if (node.name == "java/lang/Object") this else table.load("java.lang.Object")
    private val genericMapper = new SignatureTypeMapper(table, Map.empty, () => root)
    private lazy val classInfo =
      if (node.signature != null) genericMapper.parseClass(node.signature, node.superName, node.interfaces.asInstanceOf[java.util.List[String]])
      else {
        import scala.jdk.CollectionConverters._
        val super0 = if (node.superName == null) null else table.load(node.superName.replace('/', '.'))
        val ifaces0 = node.interfaces.asInstanceOf[java.util.List[String]].asScala.map(n => table.load(n.replace('/', '.'))).toIndexedSeq
        genericMapper.ClassInfo(Array.empty, super0, ifaces0, Map.empty)
      }

    override def typeParameters: Array[TypedAST.TypeParameter] = classInfo.typeParameters.clone()
    private lazy val classEnv: Map[String, TypedAST.TypeVariableType] = classInfo.env

    private lazy val methods_ : MultiTable[TypedAST.Method] = {
      val m = new MultiTable[TypedAST.Method]
      import scala.jdk.CollectionConverters._
      for (method <- node.methods.asInstanceOf[java.util.List[MethodNode]].asScala if method.name != CONSTRUCTOR_NAME) {
        m.add(new AsmMethodRef(method, this, table, classEnv))
      }
      m
    }

    private lazy val fields_ : OrderedTable[TypedAST.FieldRef] = {
      val f = new OrderedTable[TypedAST.FieldRef]
      import scala.jdk.CollectionConverters._
      for (field <- node.fields.asInstanceOf[java.util.List[FieldNode]].asScala) {
        f.add(new AsmFieldRef(field, this, table, classEnv))
      }
      f
    }

    private lazy val constructors_ : Seq[TypedAST.ConstructorRef] = {
      import scala.jdk.CollectionConverters._
      node.methods.asInstanceOf[java.util.List[MethodNode]].asScala.collect {
        case m if m.name == CONSTRUCTOR_NAME => new AsmConstructorRef(m, this, table, classEnv)
      }.toSeq
    }

    def isInterface: Boolean = (node.access & Opcodes.ACC_INTERFACE) != 0
    def modifier: Int = modifier_
    def name: String = node.name.replace('/', '.')
    def superClass: TypedAST.ClassType = {
      classInfo.superClass
    }
    def interfaces: Seq[TypedAST.ClassType] = {
      classInfo.interfaces
    }

    def methods: Seq[TypedAST.Method] = methods_.values
    def methods(name: String): Array[TypedAST.Method] = methods_.get(name).toArray
    def fields: Array[TypedAST.FieldRef] = fields_.values.toArray
    def field(name: String): TypedAST.FieldRef = fields_.get(name).orNull
    def constructors: Array[TypedAST.ConstructorRef] = constructors_.toArray
  }
}
