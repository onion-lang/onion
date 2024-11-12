/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler
import java.lang.{Byte => JByte, Short => JShort, Character => JCharacter, Integer => JInteger, Long => JLong, Double => JDouble, Float => JFloat, Boolean => JBoolean }
import java.util.{Map => JMap, HashMap => JHashMap, List, ArrayList, Set, Iterator}
import scala.jdk.CollectionConverters._
import onion.compiler.toolbox._
import org.apache.bcel.Constants
import org.apache.bcel.classfile.JavaClass
import org.apache.bcel.generic._
import onion.compiler.IRT.BinaryTerm.Constants._
import onion.compiler.IRT.UnaryTerm.Constants._

/**
 * @author Kota Mizushima
 */
object CodeGeneration {
  def translateIxTypeToVmType(`type`: IRT.Type): Type = {
    if (`type`.isBasicType) {
      BASIC_TYPE_MAPPING(`type`.asInstanceOf[IRT.BasicType])
    } else if (`type`.isArrayType) {
      val arrayType: IRT.ArrayType = `type`.asInstanceOf[IRT.ArrayType]
      new ArrayType(translateIxTypeToVmType(arrayType.component), arrayType.dimension)
    } else if (`type`.isClassType) {
      new ObjectType(`type`.name)
    } else {
      Type.NULL
    }
  }

  private def toJavaModifier(src: Int): Int = {
    var modifier: Int = 0
    modifier |= (if (Modifier.isPrivate(src)) Constants.ACC_PRIVATE else modifier)
    modifier |= (if (Modifier.isProtected(src)) Constants.ACC_PROTECTED else modifier)
    modifier |= (if (Modifier.isPublic(src)) Constants.ACC_PUBLIC else modifier)
    modifier |= (if (Modifier.isStatic(src)) Constants.ACC_STATIC else modifier)
    modifier |= (if (Modifier.isSynchronized(src)) Constants.ACC_SYNCHRONIZED else modifier)
    modifier |= (if (Modifier.isAbstract(src)) Constants.ACC_ABSTRACT else modifier)
    modifier |= (if (Modifier.isFinal(src)) Constants.ACC_FINAL else modifier)
    modifier
  }

  private val unboxingMethods                                    = Map(
    "java.lang.Byte" -> "byteValue",
    "java.lang.Short" -> "shortValue",
    "java.lang.Character" -> "charValue",
    "java.lang.Integer" -> "intValue",
    "java.lang.Long" -> "longValue",
    "java.lang.Float" -> "floatValue",
    "java.lang.Double" -> "doubleValue",
    "java.lang.Boolean" -> "booleanValue"
  )
  private final val FRAME_PREFIX: String                         = "frame"
  private final val OUTER_THIS: String                           = "outer$"
  private final val CLOSURE_CLASS_SUFFIX: String                 = "Closure"
  private final val BASIC_TYPE_MAPPING: Map[IRT.BasicType, Type] = Map(
    IRT.BasicType.BYTE -> Type.BYTE,
    IRT.BasicType.SHORT ->	Type.SHORT,
    IRT.BasicType.CHAR -> Type.CHAR,
    IRT.BasicType.INT ->	Type.INT,
    IRT.BasicType.LONG -> 	Type.LONG,
    IRT.BasicType.FLOAT ->	Type.FLOAT,
    IRT.BasicType.DOUBLE -> Type.DOUBLE,
    IRT.BasicType.BOOLEAN ->	Type.BOOLEAN,
    IRT.BasicType.VOID -> Type.VOID
  )

  class Proxy(pool: ConstantPoolGen) {
    private val code: InstructionList = new InstructionList()
    private val factory: InstructionFactory = new InstructionFactory(pool)
    private var frame: LocalFrame = _
    private var frameObjectIndex: Int = 0
    private var indexTable: Array[Int] = _
    private var method: MethodGen = _

    def setFrame(frame: LocalFrame): Unit = {
      this.frame = frame
    }
    def getFrame: LocalFrame = frame
    def getFrameObjectIndex: Int = frameObjectIndex
    def setFrameObjectIndex(frameObjectIndex: Int): Unit = {
      this.frameObjectIndex = frameObjectIndex
    }
    def setIndexTable(indexTable: Array[Int]): Unit = {
      this.indexTable = indexTable.clone.asInstanceOf[Array[Int]]
    }
    def index(index: Int): Int = indexTable(index)
    def getIndexTable: Array[Int] = indexTable.clone.asInstanceOf[Array[Int]]
    def setMethod(method: MethodGen): Unit = {
      this.method = method
    }
    def getMethod: MethodGen = {
      method
    }

    def getCode: InstructionList = code

    def addExceptionHandler(start_pc: InstructionHandle, end_pc: InstructionHandle, handler_pc: InstructionHandle, catch_type: ObjectType): CodeExceptionGen = {
      method.addExceptionHandler(start_pc, end_pc, handler_pc, catch_type)
    }

    def addLineNumber(ih: InstructionHandle, src_line: Int): LineNumberGen = method.addLineNumber(ih, src_line)

    def appendCallConstructor(`type`: ObjectType, params: Array[Type]): InstructionHandle = {
      appendInvoke(`type`.getClassName, "<init>", Type.VOID, params, Constants.INVOKESPECIAL)
    }

    def appendDefaultValue(tp: Type): InstructionHandle = {
      if (tp.isInstanceOf[BasicType]) {
        if (tp eq Type.BOOLEAN) {
          appendConstant(JBoolean.valueOf(false))
        } else if (tp eq Type.BYTE) {
          appendConstant(JByte.valueOf(0.asInstanceOf[Byte]))
        } else if (tp eq Type.SHORT) {
          appendConstant(JShort.valueOf(0.asInstanceOf[Short]))
        } else if (tp eq Type.CHAR) {
          appendConstant(JCharacter.valueOf(0.asInstanceOf[Char]))
        } else if (tp eq Type.INT) {
          appendConstant(JInteger.valueOf(0))
        } else if (tp eq Type.LONG) {
          appendConstant(JLong.valueOf(0L))
        } else if (tp eq Type.FLOAT) {
          appendConstant(JFloat.valueOf(0.0f))
        } else if (tp eq Type.DOUBLE) {
          appendConstant(JDouble.valueOf(0.0))
        } else {
          append(InstructionConst.NOP)
        }
      } else {
        appendNull(tp)
      }
    }

    def boxing(`type`: Type): ObjectType = {
      val boxedType: ObjectType = BOXING_TABLE(`type`.asInstanceOf[BasicType])
      if (boxedType == null) throw new RuntimeException("type " + `type` + "cannot be boxed")
      boxedType
    }

    def appendArrayLoad(`type`: Type): InstructionHandle = code.append(InstructionFactory.createArrayLoad(`type`))

    def appendArrayStore(`type`: Type): InstructionHandle =  code.append(InstructionFactory.createArrayStore(`type`))

    def appendBinaryOperation(op: String, `type`: Type): InstructionHandle = {
      code.append(InstructionFactory.createBinaryOperation(op, `type`))
    }

    def appendBranchInstruction(opcode: Short, target: InstructionHandle): BranchHandle = {
      code.append(InstructionFactory.createBranchInstruction(opcode, target))
    }

    def appendDup(size: Int): InstructionHandle =  code.append(InstructionFactory.createDup(size))

    def appendDup_1(size: Int): InstructionHandle =  code.append(InstructionFactory.createDup_1(size))

    def appendDup_2(size: Int): InstructionHandle = code.append(InstructionFactory.createDup_2(size))

    def appendLoad(`type`: Type, index: Int): InstructionHandle = code.append(InstructionFactory.createLoad(`type`, index))

    def appendNull(`type`: Type): InstructionHandle = code.append(InstructionFactory.createNull(`type`))

    def appendPop(size: Int): InstructionHandle = code.append(InstructionFactory.createPop(size))

    def appendReturn(`type`: Type): InstructionHandle = code.append(InstructionFactory.createReturn(`type`))

    def appendStore(`type`: Type, index: Int): InstructionHandle = code.append(InstructionFactory.createStore(`type`, index))

    def appendThis: InstructionHandle = code.append(InstructionFactory.createThis)

    def appendAppend(`type`: Type): InstructionHandle = code.append(factory.createAppend(`type`))

    def appendCast(src_type: Type, dest_type: Type): InstructionHandle = code.append(factory.createCast(src_type, dest_type))

    def appendCheckCast(t: ReferenceType): InstructionHandle = code.append(factory.createCheckCast(t))

    def appendConstant(value: AnyRef): InstructionHandle = code.append(factory.createConstant(value))

    def appendFieldAccess(class_name: String, name: String, `type`: Type, kind: Short): InstructionHandle = {
      code.append(factory.createFieldAccess(class_name, name, `type`, kind))
    }

    def appendGetField(class_name: String, name: String, t: Type): InstructionHandle = {
      code.append(factory.createGetField(class_name, name, t))
    }

    def appendGetStatic(class_name: String, name: String, t: Type): InstructionHandle = {
      code.append(factory.createGetStatic(class_name, name, t))
    }

    def appendInstanceOf(t: ReferenceType): InstructionHandle = {
      code.append(factory.createInstanceOf(t))
    }

    def appendInvoke(class_name: String, name: String, ret_type: Type, arg_types: Array[Type], kind: Short): InstructionHandle = {
      code.append(factory.createInvoke(class_name, name, ret_type, arg_types, kind))
    }

    def appendNew(s: String): InstructionHandle = {
      code.append(factory.createNew(s))
    }

    def appendNew(t: ObjectType): InstructionHandle = {
      code.append(factory.createNew(t))
    }

    def appendNewArray(t: Type, dim: Short): InstructionHandle = {
      code.append(factory.createNewArray(t, dim))
    }

    def appendPutField(class_name: String, name: String, t: Type): InstructionHandle = {
      code.append(factory.createPutField(class_name, name, t))
    }

    def appendPutStatic(class_name: String, name: String, t: Type): InstructionHandle = {
      code.append(factory.createPutStatic(class_name, name, t))
    }

    def append(i: BranchInstruction): BranchHandle = {
      code.append(i)
    }

    def append(c: CompoundInstruction): InstructionHandle = code.append(c)

    def append(i: Instruction): InstructionHandle = code.append(i)

    def append(i: Instruction, c: CompoundInstruction): InstructionHandle = code.append(i, c)

    def append(i: Instruction, j: Instruction): InstructionHandle = code.append(i, j)

    def append(i: Instruction, il: InstructionList): InstructionHandle = code.append(i, il)

    def append(ih: InstructionHandle, i: BranchInstruction): BranchHandle = code.append(ih, i)

    def append(ih: InstructionHandle, c: CompoundInstruction): InstructionHandle = code.append(ih, c)

    def append(ih: InstructionHandle, i: Instruction): InstructionHandle = code.append(ih, i)

    def append(ih: InstructionHandle, il: InstructionList): InstructionHandle = code.append(ih, il)

    def append(il: InstructionList): InstructionHandle = code.append(il)

    def getEnd: InstructionHandle = code.getEnd

    def getInstructionHandles: Array[InstructionHandle] = code.getInstructionHandles

    def getInstructionPositions: Array[Int] =  code.getInstructionPositions

    def getInstructions: Array[Instruction] =  code.getInstructions

    def getLength: Int = code.getLength

    def getStart: InstructionHandle = code.getStart

    def insert(i: BranchInstruction): BranchHandle = code.insert(i)

    def insert(c: CompoundInstruction): InstructionHandle = code.insert(c)

    def insert(i: Instruction): InstructionHandle =  code.insert(i)

    def insert(i: Instruction, c: CompoundInstruction): InstructionHandle = code.insert(i, c)

    def insert(i: Instruction, j: Instruction): InstructionHandle = code.insert(i, j)

    def insert(i: Instruction, il: InstructionList): InstructionHandle = code.insert(i, il)

    def insert(ih: InstructionHandle, i: BranchInstruction): BranchHandle = code.insert(ih, i)

    def insert(ih: InstructionHandle, c: CompoundInstruction): InstructionHandle = code.insert(ih, c)

    def insert(ih: InstructionHandle, i: Instruction): InstructionHandle = code.insert(ih, i)

    def insert(ih: InstructionHandle, il: InstructionList): InstructionHandle =  code.insert(ih, il)

    def insert(il: InstructionList): InstructionHandle = code.insert(il)

    def isEmpty: Boolean = code.isEmpty

    def iterator: Iterator[_] = code.iterator

    def move(ih: InstructionHandle, target: InstructionHandle): Unit = code.move(ih, target)

    def move(start: InstructionHandle, end: InstructionHandle, target: InstructionHandle): Unit =  code.move(start, end, target)

    def redirectBranches(old_target: InstructionHandle, new_target: InstructionHandle): Unit =  code.redirectBranches(old_target, new_target)

    def redirectExceptionHandlers(exceptions: Array[CodeExceptionGen], old_target: InstructionHandle, new_target: InstructionHandle): Unit = code.redirectExceptionHandlers(exceptions, old_target, new_target)

    def redirectLocalVariables(lg: Array[LocalVariableGen], old_target: InstructionHandle, new_target: InstructionHandle): Unit =  code.redirectLocalVariables(lg, old_target, new_target)

    def update: Unit =  code.update

    private final val BOXING_TABLE = Map(
      Type.BOOLEAN -> new ObjectType("java.lang.Boolean"),
      Type.BYTE -> new ObjectType("java.lang.Byte"),
      Type.SHORT -> new ObjectType("java.lang.Short"),
      Type.CHAR -> new ObjectType("java.lang.Character"),
      Type.INT -> new ObjectType("java.lang.Integer"),
      Type.LONG -> new ObjectType("java.lang.Long"),
      Type.FLOAT -> new ObjectType("java.lang.Float"),
      Type.DOUBLE -> new ObjectType("java.lang.Double")
    )
  }
}

class CodeGeneration(config: CompilerConfig) {
  import CodeGeneration._

  def process(classes: Seq[IRT.ClassDefinition]): Seq[CompiledClass] = {
    compiledClasses.clear
    val base =  (if (config.outputDirectory != null) config.outputDirectory else ".") + Systems.fileSeparator
    for (klass <- classes) codeClass(klass)
    val classFiles: List[CompiledClass] = new ArrayList[CompiledClass]
    for (o <- compiledClasses.asScala) {
      val clazz: JavaClass = o
      val outDir: String = getOutputDir(base, clazz.getClassName)
      classFiles.add(new CompiledClass(clazz.getClassName, outDir, clazz.getBytes))
    }
    classFiles.asScala.toSeq
  }

  private def getOutputDir(base: String, fqcn: String): String = {
    val packageName: String = getPackageName(fqcn)
    base + packageName.replaceAll(".", Systems.fileSeparator)
  }

  private def getPackageName(fqcn: String): String = {
    val index: Int = fqcn.lastIndexOf("\\.")
    if (index < 0) "" else fqcn.substring(0, index)
  }

  private def classModifier(node: IRT.ClassDefinition): Int = {
    var modifier: Int = toJavaModifier(node.modifier)
    modifier |= (if (node.isInterface) Constants.ACC_INTERFACE else modifier)
    modifier |= (if ((!Modifier.isInternal(modifier))) Constants.ACC_PUBLIC else modifier)
    modifier
  }

  def codeClass(node: IRT.ClassDefinition): Unit = {
    val modifier: Int = classModifier(node)
    val className: String = node.name
    generator = new SymbolGenerator(className + CLOSURE_CLASS_SUFFIX)
    val superClass: String = node.superClass.name
    val interfaces = namesOf(node.interfaces)
    val file: String = node.getSourceFile
    val gen: ClassGen = new ClassGen(className, superClass, file, modifier, interfaces.toArray)
    val constructors: Array[IRT.ConstructorRef] = node.constructors
    for (ref <- constructors) {
      codeConstructor(gen, (ref.asInstanceOf[IRT.ConstructorDefinition]))
    }
    val methods = node.methods
    for (ref <- methods) {
      codeMethod(gen, (ref.asInstanceOf[IRT.MethodDefinition]))
    }
    val fields: Array[IRT.FieldRef] = node.fields
    for (ref <- fields) {
      codeField(gen, (ref.asInstanceOf[IRT.FieldDefinition]))
    }
    compiledClasses.add(gen.getJavaClass)
  }

  def codeExpressions(nodes: Array[IRT.Term], code: CodeGeneration.Proxy): InstructionHandle = {
    if (nodes.length > 0) {
      val start = codeExpression(nodes(0), code)
      for(i <- 1 until nodes.length) {
        codeExpression(nodes(i), code)
      }
      start
    } else {
      code.append(InstructionConst.NOP)
    }
  }

  def codeConstructor(gen: ClassGen, node: IRT.ConstructorDefinition): Unit = {
    val isStaticOld: Boolean = isStatic
    isStatic = false
    val code: CodeGeneration.Proxy = new CodeGeneration.Proxy(gen.getConstantPool)
    val frame: LocalFrame = node.frame
    code.setFrame(frame)
    val args: Array[String] = new Array[String](node.getArgs.length)

    for(i <- 0 until args.length) {
      args(i) = "arg" + i
    }
    var classType: ObjectType = typeOf(node.affiliation).asInstanceOf[ObjectType]
    val modifier: Int = toJavaModifier(node.modifier)
    var arguments: Array[Type] = typesOf(node.getArgs)
    val method: MethodGen = new MethodGen(modifier, Type.VOID, arguments, args, "<init>", classType.getClassName, code.getCode, gen.getConstantPool)
    if (frame.closed) {
      val frameObjectIndexLocal = frameObjectIndex(1, node.getArgs)
      code.setFrameObjectIndex(frameObjectIndexLocal)
      code.setIndexTable(makeIndexTableForClosureFrame(frame))
      appendInitialCode(code, frame, arguments, 1)
    }
    else {
      code.setIndexTable(makeIndexTableFor(1, frame))
    }
    code.setMethod(method)
    val init: IRT.Super = node.superInitializer
    classType = typeOf(init.classType).asInstanceOf[ObjectType]
    arguments = typesOf(init.arguments)
    code.append(InstructionConst.ALOAD_0)
    codeExpressions(init.terms, code)
    code.appendCallConstructor(classType, arguments)
    codeBlock(node.block, code)
    method.setMaxLocals
    method.setMaxStack
    code.appendReturn(typeOf(IRT.BasicType.VOID))
    gen.addMethod(method.getMethod)
    isStatic = isStaticOld
  }

  def codeMethod(gen: ClassGen, node: IRT.MethodDefinition): Unit = {
    val isStaticOld: Boolean = isStatic
    isStatic = Modifier.isStatic(node.modifier)
    val code: CodeGeneration.Proxy = new CodeGeneration.Proxy(gen.getConstantPool)
    val frame: LocalFrame = node.getFrame
    code.setFrame(frame)
    val modifier: Int = toJavaModifier(node.modifier)
    val returned: Type = typeOf(node.returnType)
    val arguments: Array[Type] = typesOf(node.arguments)
    val argNames: Array[String] = names(arguments.length)
    val name: String = node.name
    val className: String = node.affiliation.name
    val method: MethodGen = new MethodGen(modifier, returned, arguments, argNames, name, className, code.getCode, gen.getConstantPool)
    code.setMethod(method)
    if (!Modifier.isAbstract(node.modifier)) {
      if (frame.closed) {
        var origin: Int = 0
        if (Modifier.isStatic(node.modifier)) {
          code.setFrameObjectIndex(frameObjectIndex(0, node.arguments))
          origin = 0
        }
        else {
          code.setFrameObjectIndex(frameObjectIndex(1, node.arguments))
          origin = 1
        }
        code.setIndexTable(makeIndexTableForClosureFrame(frame))
        appendInitialCode(code, frame, arguments, origin)
      }
      else {
        if (Modifier.isStatic(node.modifier)) {
          code.setIndexTable(makeIndexTableFor(0, frame))
        }
        else {
          code.setIndexTable(makeIndexTableFor(1, frame))
        }
      }
      codeBlock(node.getBlock, code)
      method.setMaxLocals
      method.setMaxStack
    }
    gen.addMethod(method.getMethod)
    isStatic = isStaticOld
  }

  private def appendInitialCode(code: CodeGeneration.Proxy, frame: LocalFrame, arguments: Array[Type], origin: Int): Unit = {
    val frameObjectIndex: Int = code.getFrameObjectIndex
    code.appendConstant(JInteger.valueOf(frame.entries.length))
    code.appendNewArray(Type.OBJECT, 1.asInstanceOf[Short])
    code.appendDup(1)
    code.appendStore(new ArrayType(Type.OBJECT, 1), frameObjectIndex)
    val index: Int = origin
    var i: Int = 0
    while (i < arguments.length) {
      val arg: Type = arguments(i)
      code.appendDup(1)
      code.appendConstant(JInteger.valueOf(i))
      if (arguments(i).isInstanceOf[BasicType]) {
        val boxed: ObjectType = code.boxing(arg)
        code.appendNew(boxed)
        code.appendDup(1)
        code.appendLoad(arg, index + i)
        code.appendCallConstructor(boxed, Array[Type](arg))
      } else {
        code.appendLoad(arg, index + i)
      }
      code.appendArrayStore(Type.OBJECT)
      if ((arg eq Type.DOUBLE) || (arg eq Type.LONG)) {
        i += 2
      }
      else {
        i += 1
      }
    }
  }

  private def implementsMethods(gen: ClassGen, methods: Array[IRT.Method]): Unit = {
    {
      var i: Int = 0
      while (i < methods.length) {
        {
          val method: IRT.Method = methods(i)
          val returnType: Type = typeOf(method.returnType)
          val name: String = method.name
          val args: Array[Type] = typesOf(method.arguments)
          val argNames: Array[String] = names(args.length)
          val code: CodeGeneration.Proxy = new CodeGeneration.Proxy(gen.getConstantPool)
          val mgen: MethodGen = new MethodGen(Constants.ACC_PUBLIC, returnType, args, argNames, name, gen.getClassName, code.getCode, gen.getConstantPool)
          code.appendDefaultValue(returnType)
          code.appendReturn(returnType)
          mgen.setMaxLocals
          mgen.setMaxStack
          gen.addMethod(mgen.getMethod)
        }
        ({
          i += 1;
          i
        })
      }
    }
  }

  def codeClosure(node: IRT.NewClosure, code: CodeGeneration.Proxy): InstructionHandle = {
    val classType: IRT.ClassType = node.classType
    val closureName: String = generator.generate
    val arguments: Array[Type] = typesOf(node.arguments)
    val gen: ClassGen = new ClassGen(closureName, "java.lang.Object", "<generated>", Constants.ACC_PUBLIC, Array[String](classType.name))
    val methods: Set[_] = Classes.getInterfaceMethods(classType)
    methods.remove(node.method)
    implementsMethods(gen, methods.toArray(new Array[IRT.Method](0)).asInstanceOf[Array[IRT.Method]])
    val frame: LocalFrame = node.frame
    val depth: Int = frame.depth
    var i: Int = 1
    while (i <= depth) {
      val field: FieldGen = new FieldGen(Constants.ACC_PRIVATE, new ArrayType("java.lang.Object", 1), FRAME_PREFIX + i, gen.getConstantPool)
      gen.addField(field.getField)
      i += 1
    }
    gen.addField(new FieldGen(Constants.ACC_PUBLIC, new ObjectType("java.lang.Object"), OUTER_THIS, gen.getConstantPool).getField)
    val types: Array[Type] = closureArguments(depth)
    var method: MethodGen = createClosureConstructor(closureName, types, gen.getConstantPool)
    gen.addMethod(method.getMethod)
    val closureCode: CodeGeneration.Proxy = new CodeGeneration.Proxy(gen.getConstantPool)
    method = new MethodGen(Constants.ACC_PUBLIC, typeOf(node.returnType), arguments, names(arguments.length), node.name, closureName, closureCode.getCode, gen.getConstantPool)
    closureCode.setMethod(method)
    closureCode.setFrame(frame)
    if (frame.closed) {
      val frameObjectIndexLocal = frameObjectIndex(1, node.arguments)
      closureCode.setFrameObjectIndex(frameObjectIndexLocal)
      closureCode.setIndexTable(makeIndexTableForClosureFrame(frame))
      appendInitialCode(closureCode, frame, arguments, 1)
    }
    else {
      closureCode.setIndexTable(makeIndexTableFor(1, frame))
    }
    val isClosureOld: Boolean = isClosure
    val currentClosureNameOld: String = currentClosureName
    isClosure = true
    currentClosureName = closureName
    codeStatement(node.block, closureCode)
    isClosure = isClosureOld
    currentClosureName = currentClosureNameOld
    method.setMaxLocals
    method.setMaxStack
    gen.addMethod(method.getMethod)
    compiledClasses.add(gen.getJavaClass)
    val start: InstructionHandle = code.appendNew(new ObjectType(closureName))
    code.appendDup(1)
    val name: String = code.getMethod.getClassName
    val index: Int = code.getFrameObjectIndex
    if (!isStatic) {
      if (isClosure) {
        code.appendThis
        code.appendGetField(currentClosureName, OUTER_THIS, new ObjectType("java.lang.Object"))
      }
      else {
        code.appendThis
      }
    }
    code.appendLoad(new ArrayType("java.lang.Object", 1), index)

    i = 1
    while (i < depth) {
      code.appendThis
      code.appendGetField(name, FRAME_PREFIX + i, new ArrayType("java.lang.Object", 1))
      i += 1;
    }
    code.appendCallConstructor(new ObjectType(closureName), complementOuterThis(closureArguments(depth)))
    start
  }

  private def complementOuterThis(types: Array[Type]): Array[Type] = {
    if (!isStatic) {
      val newTypes: Array[Type] = new Array[Type](types.length + 1)
      newTypes(0) = new ObjectType("java.lang.Object") {
        var i: Int = 0
        while (i < types.length) {
          {
            newTypes(i + 1) = types(i)
          }
          ({
            i += 1;
            i
          })
        }
      }
      newTypes
    } else {
      types
    }
  }

  private def closureArguments(size: Int): Array[Type] = {
    val arguments: Array[Type] = new Array[Type](size)

    {
      var i: Int = 0
      while (i < arguments.length) {
        {
          arguments(i) = new ArrayType("java.lang.Object", 1)
        }
        ({
          i += 1;
          i
        })
      }
    }
    arguments
  }

  private def codeList(node: IRT.ListLiteral, code: CodeGeneration.Proxy): InstructionHandle = {
    val listType: ObjectType = typeOf(node.`type`).asInstanceOf[ObjectType]
    val start: InstructionHandle = code.appendNew("java.util.ArrayList")
    code.appendDup(1)
    code.appendCallConstructor(new ObjectType("java.util.ArrayList"), new Array[Type](0))
    val elements: Array[IRT.Term] = node.getElements
    var i: Int = 0
    while (i < elements.length) {
      code.appendDup(1)
      codeExpression(elements(i), code)
      code.appendInvoke(listType.getClassName, "add", Type.BOOLEAN, Array[Type](Type.OBJECT), Constants.INVOKEINTERFACE)
      code.appendPop(1)
      i += 1
    }
    start
  }

  def codeSuperCall(node: IRT.CallSuper, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.target, code)
    codeExpressions(node.params, code)
    val method: IRT.Method = node.method
    code.appendInvoke(method.affiliation.name, method.name, typeOf(method.returnType), typesOf(method.arguments), Constants.INVOKESPECIAL)
    start
  }

  private def names(size: Int): Array[String] = {
    val names: Array[String] = new Array[String](size)
    var i: Int = 0
    while (i < names.length) {
      names(i) = "args" + size
      i += 1;
    }
    names
  }

  private def createClosureConstructor(className: String, types: Array[Type], pool: ConstantPoolGen): MethodGen = {
    var argNames: Array[String] = null
    if (isStatic) {
      argNames = new Array[String](types.length)

      var i: Int = 0
      while (i < types.length) {
        argNames(i) = FRAME_PREFIX + (i + 1)
        i += 1;
      }
    } else {
      argNames = new Array[String](types.length + 1)
      argNames(0) = OUTER_THIS
      var i: Int = 0
      while (i < types.length) {
        argNames(i + 1) = FRAME_PREFIX + (i + 1)
        i += 1;
      }
    }
    val code: CodeGeneration.Proxy = new CodeGeneration.Proxy(pool)
    val constructor: MethodGen = new MethodGen(Constants.ACC_PUBLIC, Type.VOID, complementOuterThis(types), argNames, "<init>", className, code.getCode, pool)
    code.appendThis
    code.appendCallConstructor(Type.OBJECT, new Array[Type](0))
    if (!isStatic) {
      code.appendThis
      code.appendLoad(Type.OBJECT, 1)
      code.appendPutField(className, OUTER_THIS, Type.OBJECT)
    }
    val origin: Int = if (isStatic) 1 else 2
    var i: Int = 0
    while (i < types.length) {
      code.appendThis
      code.appendLoad(types(i), i + origin)
      code.appendPutField(className, FRAME_PREFIX + (i + 1), types(i))
      i += 1;
    }
    code.append(InstructionConst.RETURN)
    constructor.setMaxLocals
    constructor.setMaxStack
    constructor
  }

  def codeField(gen: ClassGen, node: IRT.FieldDefinition): Unit = {
    val field = new FieldGen(toJavaModifier(node.modifier), typeOf(node.`type`), node.name, gen.getConstantPool)
    gen.addField(field.getField)
  }

  def codeBlock(node: IRT.StatementBlock, code: CodeGeneration.Proxy): InstructionHandle = {
    var start: InstructionHandle = null
    if (node.statements.length > 0) {
      start = codeStatement(node.statements(0), code)
      var i: Int = 1
      while (i < node.statements.length) {
        codeStatement(node.statements(i), code)
        i += 1;
      }
    } else {
      start = code.append(InstructionConst.NOP)
    }
    start
  }

  def codeExpressionStatement(node: IRT.ExpressionActionStatement, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.term, code)
    val `type`: IRT.Type = node.term.`type`
    if (`type` ne IRT.BasicType.VOID) {
      if (isWideType(`type`)) {
        code.append(InstructionConst.POP2)
      }
      else {
        code.append(InstructionConst.POP)
      }
    }
    start
  }

  def codeStatement(node: IRT.ActionStatement, code: CodeGeneration.Proxy): InstructionHandle = {
    node match {
      case node1: IRT.StatementBlock =>
        codeBlock(node1, code)
      case node1: IRT.ExpressionActionStatement =>
        codeExpressionStatement(node1, code)
      case node1: IRT.IfStatement =>
        codeIf(node1, code)
      case node1: IRT.ConditionalLoop =>
        codeLoop(node1, code)
      case node1: IRT.NOP =>
        codeEmpty(node1, code)
      case node1: IRT.Return =>
        codeReturn(node1, code)
      case node1: IRT.Synchronized =>
        codeSynchronized(node1, code)
      case node1: IRT.Throw =>
        codeThrowNode(node1, code)
      case node1: IRT.Try =>
        codeTry(node1, code)
      case _ =>
        code.append(InstructionConst.NOP)
    }
  }

  def codeReturn(node: IRT.Return, code: CodeGeneration.Proxy): InstructionHandle = {
    var start: InstructionHandle = null
    if (node.term != null) {
      start = codeExpression(node.term, code)
      val `type`: Type = typeOf(node.term.`type`)
      code.appendReturn(`type`)
    }
    else {
      start = code.append(InstructionConst.RETURN)
    }
    start
  }

  def codeSynchronized(node: IRT.Synchronized, code: CodeGeneration.Proxy): InstructionHandle = {
    null
  }

  def codeThrowNode(node: IRT.Throw, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.term, code)
    code.append(InstructionConst.ATHROW)
    start
  }

  def codeTry(node: IRT.Try, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeStatement(node.tryStatement, code)
    val to: BranchHandle = code.append(new GOTO(null))
    val length: Int = node.catchTypes.length
    val catchEnds: Array[BranchHandle] = new Array[BranchHandle](length)

    {
      var i: Int = 0
      while (i < length) {
        {
          val bind: ClosureLocalBinding = node.catchTypes(i)
          val index: Int = code.getIndexTable(bind.index)
          val `type`: ObjectType = typeOf(bind.tp).asInstanceOf[ObjectType]
          val target: InstructionHandle = code.appendStore(`type`, index)
          code.addExceptionHandler(start, to, target, `type`)
          codeStatement(node.catchStatements(i), code)
          catchEnds(i) = code.append(new GOTO(null))
        }
        ({
          i += 1;
          i
        })
      }
    }
    val end: InstructionHandle = code.append(InstructionConst.NOP)
    to.setTarget(end)
    var i: Int = 0
    while (i < catchEnds.length) {
      catchEnds(i).setTarget(end)
      i += 1;
    }
    start
  }

  def codeEmpty(node: IRT.NOP, code: CodeGeneration.Proxy): InstructionHandle = {
    code.append(InstructionConst.NOP)
  }

  def codeIf(node: IRT.IfStatement, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.getCondition, code)
    val toThen: BranchHandle = code.append(new IFNE(null))
    if (node.getElseStatement != null) {
      codeStatement(node.getElseStatement, code)
    }
    val toEnd: BranchHandle = code.append(new GOTO(null))
    toThen.setTarget(codeStatement(node.getThenStatement, code))
    toEnd.setTarget(code.append(new NOP))
    start
  }

  def codeLoop(node: IRT.ConditionalLoop, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.condition, code)
    val branch: BranchHandle = code.append(new IFEQ(null))
    codeStatement(node.stmt, code)
    code.append(new GOTO(start))
    val end: InstructionHandle = code.append(InstructionConst.NOP)
    branch.setTarget(end)
    start
  }

  private def nameOf(symbol: IRT.ClassType): String = symbol.name

  private def namesOf(symbols: Seq[IRT.ClassType]): Seq[String] = symbols.map{ s => nameOf(s)}

  def codeExpression(node: IRT.Term, code: CodeGeneration.Proxy): InstructionHandle = {
    var start: InstructionHandle = null
    if (node.isInstanceOf[IRT.BinaryTerm]) {
      start = codeBinaryExpression(node.asInstanceOf[IRT.BinaryTerm], code)
    }
    else if (node.isInstanceOf[IRT.UnaryTerm]) {
      start = codeUnaryExpression(node.asInstanceOf[IRT.UnaryTerm], code)
    }
    else if (node.isInstanceOf[IRT.Begin]) {
      start = codeBegin(node.asInstanceOf[IRT.Begin], code)
    }
    else if (node.isInstanceOf[IRT.SetLocal]) {
      start = codeLocalAssign(node.asInstanceOf[IRT.SetLocal], code)
    }
    else if (node.isInstanceOf[IRT.RefLocal]) {
      start = codeLocalRef(node.asInstanceOf[IRT.RefLocal], code)
    }
    else if (node.isInstanceOf[IRT.RefStaticField]) {
      start = codeStaticFieldRef(node.asInstanceOf[IRT.RefStaticField], code)
    }
    else if (node.isInstanceOf[IRT.RefField]) {
      start = codeFieldRef(node.asInstanceOf[IRT.RefField], code)
    }
    else if (node.isInstanceOf[IRT.SetField]) {
      start = codeFieldAssign(node.asInstanceOf[IRT.SetField], code)
    }
    else if (node.isInstanceOf[IRT.Call]) {
      start = codeMethodCall(node.asInstanceOf[IRT.Call], code)
    }
    else if (node.isInstanceOf[IRT.RefArray]) {
      start = codeArrayRef(node.asInstanceOf[IRT.RefArray], code)
    }
    else if (node.isInstanceOf[IRT.ArrayLength]) {
      start = codeArrayLengthNode(node.asInstanceOf[IRT.ArrayLength], code)
    }
    else if (node.isInstanceOf[IRT.SetArray]) {
      start = codeArrayAssignment(node.asInstanceOf[IRT.SetArray], code)
    }
    else if (node.isInstanceOf[IRT.NewObject]) {
      start = codeNew(node.asInstanceOf[IRT.NewObject], code)
    }
    else if (node.isInstanceOf[IRT.NewArray]) {
      start = codeNewArray(node.asInstanceOf[IRT.NewArray], code)
    }
    else if (node.isInstanceOf[IRT.RefArray]) {
      start = codeArrayRef(node.asInstanceOf[IRT.RefArray], code)
    }
    else if (node.isInstanceOf[IRT.CallStatic]) {
      start = codeStaticMethodCall(node.asInstanceOf[IRT.CallStatic], code)
    }
    else if (node.isInstanceOf[IRT.CharacterValue]) {
      start = codeChar(node.asInstanceOf[IRT.CharacterValue], code)
    }
    else if (node.isInstanceOf[IRT.StringValue]) {
      start = codeString(node.asInstanceOf[IRT.StringValue], code)
    }
    else if (node.isInstanceOf[IRT.IntValue]) {
      start = codeInteger(node.asInstanceOf[IRT.IntValue], code)
    }
    else if (node.isInstanceOf[IRT.LongValue]) {
      start = codeLong(node.asInstanceOf[IRT.LongValue], code)
    }
    else if (node.isInstanceOf[IRT.FloatValue]) {
      start = codeFloat(node.asInstanceOf[IRT.FloatValue], code)
    }
    else if (node.isInstanceOf[IRT.DoubleValue]) {
      start = codeDouble(node.asInstanceOf[IRT.DoubleValue], code)
    }
    else if (node.isInstanceOf[IRT.BoolValue]) {
      start = codeBoolean(node.asInstanceOf[IRT.BoolValue], code)
    }
    else if (node.isInstanceOf[IRT.NullValue]) {
      start = codeNull(node.asInstanceOf[IRT.NullValue], code)
    }
    else if (node.isInstanceOf[IRT.AsInstanceOf]) {
      start = codeCast(node.asInstanceOf[IRT.AsInstanceOf], code)
    }
    else if (node.isInstanceOf[IRT.This]) {
      start = codeSelf(node.asInstanceOf[IRT.This], code)
    }
    else if (node.isInstanceOf[IRT.OuterThis]) {
      start = codeOuterThis(node.asInstanceOf[IRT.OuterThis], code)
    }
    else if (node.isInstanceOf[IRT.InstanceOf]) {
      start = codeIsInstance(node.asInstanceOf[IRT.InstanceOf], code)
    }
    else if (node.isInstanceOf[IRT.NewClosure]) {
      start = codeClosure(node.asInstanceOf[IRT.NewClosure], code)
    }
    else if (node.isInstanceOf[IRT.ListLiteral]) {
      start = codeList(node.asInstanceOf[IRT.ListLiteral], code)
    }
    else if (node.isInstanceOf[IRT.CallSuper]) {
      start = codeSuperCall(node.asInstanceOf[IRT.CallSuper], code)
    }
    else {
      throw new RuntimeException
    }
    start
  }

  def codeBegin(node: IRT.Begin, code: CodeGeneration.Proxy): InstructionHandle = {
    var start: InstructionHandle = null
    val terms: Array[IRT.Term] = node.terms
    if (terms.length > 0) {
      start = codeExpression(terms(0), code)
      var i: Int = 1
      while (i < terms.length) {
        val `type`: IRT.Type = terms(i - 1).`type`
        if (`type` ne IRT.BasicType.VOID) {
          if (isWideType(`type`)) {
            code.append(InstructionConst.POP2)
          } else {
            code.append(InstructionConst.POP)
          }
          codeExpression(terms(i), code)
        }
        i += 1
      }
    } else {
      start = code.append(InstructionConst.NOP)
    }
    start
  }

  def codeLocalAssign(node: IRT.SetLocal, code: CodeGeneration.Proxy): InstructionHandle = {
    var start: InstructionHandle = null
    val `type`: Type = typeOf(node.`type`)
    if (node.frame == 0 && !code.getFrame.closed) {
      start = codeExpression(node.value, code)
      if (isWideType(node.`type`)) {
        code.append(InstructionConst.DUP2)
      }
      else {
        code.append(InstructionConst.DUP)
      }
      code.appendStore(`type`, code.getIndexTable(node.index))
    } else {
      if (node.frame == 0 && code.getFrame.closed) {
        val index: Int = code.getFrameObjectIndex
        start = code.appendLoad(new ArrayType("java.lang.Object", 1), index)
        code.appendConstant(JInteger.valueOf(code.index(node.index)))
      }
      else {
        start = code.appendThis
        code.appendGetField(code.getMethod.getClassName, FRAME_PREFIX + node.frame, new ArrayType("java.lang.Object", 1))
        code.appendConstant(JInteger.valueOf(node.index))
      }
      if (node.isBasicType) {
        val boxed: ObjectType = code.boxing(`type`)
        code.appendNew(boxed)
        code.appendDup(1)
        codeExpression(node.value, code)
        code.appendInvoke(boxed.getClassName, "<init>", Type.VOID, Array[Type](`type`), Constants.INVOKESPECIAL)
        code.appendDup_2(1)
        code.appendArrayStore(Type.OBJECT)
        val method: String = unboxingMethods(boxed.getClassName).asInstanceOf[String]
        code.appendInvoke(boxed.getClassName, method, `type`, new Array[Type](0), Constants.INVOKEVIRTUAL)
      }
      else {
        codeExpression(node.value, code)
        code.appendDup_2(1)
        code.appendArrayStore(Type.OBJECT)
      }
    }
    start
  }

  def codeLocalRef(node: IRT.RefLocal, code: CodeGeneration.Proxy): InstructionHandle = {
    var start: InstructionHandle = null
    val `type`: Type = typeOf(node.`type`)
    if (node.frame == 0 && !code.getFrame.closed) {
      start = code.appendLoad(`type`, code.index(node.index))
    }
    else {
      if (node.frame == 0 && code.getFrame.closed) {
        val index: Int = code.getFrameObjectIndex
        start = code.appendLoad(new ArrayType("java.lang.Object", 1), index)
        code.appendConstant(JInteger.valueOf(code.index(node.index)))
      }
      else {
        start = code.appendThis
        code.appendGetField(code.getMethod.getClassName, FRAME_PREFIX + node.frame, new ArrayType("java.lang.Object", 1))
        code.appendConstant(JInteger.valueOf(node.index))
      }
      code.appendArrayLoad(Type.OBJECT)
      if (node.isBasicType) {
        val boxed: ObjectType = code.boxing(`type`)
        val method: String = unboxingMethods(boxed.getClassName).asInstanceOf[String]
        code.appendCast(Type.OBJECT, boxed)
        code.appendInvoke(boxed.getClassName, method, `type`, new Array[Type](0), Constants.INVOKEVIRTUAL)
      }
      else {
        code.appendCast(Type.OBJECT, `type`)
      }
    }
    start
  }

  def codeStaticFieldRef(node: IRT.RefStaticField, code: CodeGeneration.Proxy): InstructionHandle = {
    val classType: String = node.field.affiliation.name
    val name: String = node.field.name
    val `type`: Type = typeOf(node.`type`)
    code.appendGetStatic(classType, name, `type`)
  }

  def codeMethodCall(node: IRT.Call, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.target, code)
    var i: Int = 0
    while (i < node.parameters.length) {
      codeExpression(node.parameters(i), code)
      i += 1;
    }
    val classType: IRT.ObjectType = node.target.`type`.asInstanceOf[IRT.ObjectType]
    var kind: Short = 0
    if (classType.isInterface) {
      kind = Constants.INVOKEINTERFACE
    }
    else {
      kind = Constants.INVOKEVIRTUAL
    }
    val className: String = classType.name
    val name: String = node.method.name
    val ret: Type = typeOf(node.`type`)
    val args: Array[Type] = typesOf(node.method.arguments)
    code.appendInvoke(className, name, ret, args, kind)
    start
  }

  def codeArrayRef(node: IRT.RefArray, code: CodeGeneration.Proxy): InstructionHandle = {
    val targetType: IRT.ArrayType = node.target.`type`.asInstanceOf[IRT.ArrayType]
    val start: InstructionHandle = codeExpression(node.target, code)
    codeExpression(node.index, code)
    code.appendArrayLoad(typeOf(targetType.base))
    start
  }

  def codeArrayLengthNode(node: IRT.ArrayLength, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.target, code)
    code.append(InstructionConst.ARRAYLENGTH)
    start
  }

  def codeArrayAssignment(node: IRT.SetArray, code: CodeGeneration.Proxy): InstructionHandle = {
    val targetType: IRT.ArrayType = node.`object`.`type`.asInstanceOf[IRT.ArrayType]
    val start: InstructionHandle = codeExpression(node.`object`, code)
    code.appendDup(1)
    codeExpression(node.index, code)
    codeExpression(node.value, code)
    code.appendArrayStore(typeOf(targetType.base))
    start
  }

  def codeNew(node: IRT.NewObject, code: CodeGeneration.Proxy): InstructionHandle = {
    val `type`: IRT.ClassType = node.constructor.affiliation
    val start: InstructionHandle = code.appendNew(typeOf(`type`).asInstanceOf[ObjectType])
    code.append(InstructionConst.DUP)
    var i: Int = 0
    while (i < node.parameters.length) {
      codeExpression(node.parameters(i), code)
      i += 1
    }

    val className: String = `type`.name
    val arguments: Array[Type] = typesOf(node.constructor.getArgs)
    val kind: Short = Constants.INVOKESPECIAL
    code.appendInvoke(className, "<init>", Type.VOID, arguments, kind)
    start
  }

  def codeNewArray(node: IRT.NewArray, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpressions(node.parameters, code)
    val `type`: IRT.ArrayType = node.arrayType
    code.appendNewArray(typeOf(`type`.component), node.parameters.length.asInstanceOf[Short])
    start
  }

  def codeStaticMethodCall(node: IRT.CallStatic, code: CodeGeneration.Proxy): InstructionHandle = {
    var start: InstructionHandle = null
    if (node.parameters.length > 0) {
      start = codeExpression(node.parameters(0), code)
      var i: Int = 1
      while (i < node.parameters.length) {
        codeExpression(node.parameters(i), code)
        i += 1;
      }
    } else {
      start = code.append(InstructionConst.NOP)
    }
    val className: String = node.target.name
    val name: String = node.method.name
    val returnType: Type = typeOf(node.`type`)
    val arguments: Array[Type] = typesOf(node.method.arguments)
    val kind: Short = Constants.INVOKESTATIC
    code.appendInvoke(className, name, returnType, arguments, kind)
    start
  }

  def codeBinaryExpression(node: IRT.BinaryTerm, code: CodeGeneration.Proxy): InstructionHandle = {
    if (node.kind == LOGICAL_AND) {
      return codeLogicalAnd(node, code)
    }
    else if (node.kind == LOGICAL_OR) {
      return codeLogicalOr(node, code)
    }
    else if (node.kind == ELVIS) {
      return codeElvis(node, code)
    }
    val left: IRT.Term = node.lhs
    val right: IRT.Term = node.rhs
    val start: InstructionHandle = codeExpression(left, code)
    codeExpression(right, code)
    node.kind match {
      case ADD =>
        add(code, left.`type`)
      case SUBTRACT =>
        sub(code, left.`type`)
      case MULTIPLY =>
        mul(code, left.`type`)
      case DIVIDE =>
        div(code, left.`type`)
      case MOD =>
        mod(code, left.`type`)
      case EQUAL =>
        eq(code, left.`type`)
      case NOT_EQUAL =>
        noteq(code, left.`type`)
      case LESS_OR_EQUAL =>
        lte(code, left.`type`)
      case GREATER_OR_EQUAL =>
        gte(code, left.`type`)
      case LESS_THAN =>
        lt(code, left.`type`)
      case GREATER_THAN =>
        gt(code, left.`type`)
      case BIT_AND =>
        bitAnd(code, left.`type`)
      case BIT_OR =>
        bitOr(code, right.`type`)
      case XOR =>
        xor(code, right.`type`)
      case BIT_SHIFT_L2 =>
        bitShiftL2(code, left.`type`)
      case BIT_SHIFT_R2 =>
        bitShiftR2(code, left.`type`)
      case BIT_SHIFT_R3 =>
        bitShiftR3(code, left.`type`)
      case _ =>
    }
    start
  }

  def bitShiftR2(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(InstructionConst.ISHR)
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(InstructionConst.LSHR)
    }
    else {
      throw new RuntimeException
    }
  }

  def bitShiftL2(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(InstructionConst.ISHL)
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(InstructionConst.LSHL)
    }
    else {
      throw new RuntimeException
    }
  }

  def bitShiftR3(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(InstructionConst.IUSHR)
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(InstructionConst.LUSHR)
    }
    else {
      throw new RuntimeException
    }
  }

  def codeLogicalAnd(node: IRT.BinaryTerm, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.lhs, code)
    var b1: BranchHandle = null
    var b2: BranchHandle = null
    var b3: BranchHandle = null
    b1 = code.append(new IFEQ(null))
    codeExpression(node.rhs, code)
    b2 = code.append(new IFEQ(null))
    code.append(InstructionConst.ICONST_1)
    b3 = code.append(new GOTO(null))
    val failure: InstructionHandle = code.append(InstructionConst.ICONST_0)
    b1.setTarget(failure)
    b2.setTarget(failure)
    b3.setTarget(code.append(InstructionConst.NOP))
    start
  }

  def codeLogicalOr(node: IRT.BinaryTerm, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.lhs, code)
    var b1: BranchHandle = null
    var b2: BranchHandle = null
    var b3: BranchHandle = null
    b1 = code.append(new IFNE(null))
    codeExpression(node.rhs, code)
    b2 = code.append(new IFNE(null))
    code.append(InstructionConst.ICONST_0)
    b3 = code.append(new GOTO(null))
    val success: InstructionHandle = code.append(InstructionConst.ICONST_1)
    b1.setTarget(success)
    b2.setTarget(success)
    b3.setTarget(code.append(new NOP))
    start
  }

  def codeElvis(node: IRT.BinaryTerm, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.lhs, code)
    code.appendDup(1)
    code.appendNull(typeOf(node.`type`))
    val b1: BranchHandle = code.append(new IF_ACMPEQ(null))
    val b2: BranchHandle = code.append(new GOTO(null))
    b1.setTarget(code.appendPop(1))
    codeExpression(node.rhs, code)
    b2.setTarget(code.append(new NOP))
    start
  }

  def bitAnd(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if ((`type` eq IRT.BasicType.INT) || (`type` eq IRT.BasicType.BOOLEAN)) {
      code.append(new IAND)
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LAND)
    }
    else {
      throw new RuntimeException
    }
  }

  def bitOr(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if ((`type` eq IRT.BasicType.INT) || (`type` eq IRT.BasicType.BOOLEAN)) {
      code.append(new IOR)
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LOR)
    }
    else {
      throw new RuntimeException
    }
  }

  def xor(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if ((`type` eq IRT.BasicType.INT) || (`type` eq IRT.BasicType.BOOLEAN)) {
      code.append(new IXOR)
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LXOR)
    }
    else {
      throw new RuntimeException
    }
  }

  def eq(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    var b1: BranchHandle = null
    if ((`type` eq IRT.BasicType.INT) || (`type` eq IRT.BasicType.CHAR) || (`type` eq IRT.BasicType.BOOLEAN)) {
      b1 = code.append(new IF_ICMPEQ(null))
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LCMP)
      b1 = code.append(new IFEQ(null))
    }
    else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FCMPL)
      b1 = code.append(new IFEQ(null))
    }
    else if (`type` eq IRT.BasicType.DOUBLE) {
      code.append(new DCMPL)
      b1 = code.append(new IFEQ(null))
    }
    else {
      b1 = code.append(new IF_ACMPEQ(null))
    }
    processBranch(code, b1)
  }

  def noteq(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    var b1: BranchHandle = null
    if ((`type` eq IRT.BasicType.INT) || (`type` eq IRT.BasicType.CHAR) || (`type` eq IRT.BasicType.BOOLEAN)) {
      b1 = code.append(new IF_ICMPNE(null))
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LCMP)
      b1 = code.append(new IFNE(null))
    }
    else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FCMPL)
      b1 = code.append(new IFNE(null))
    }
    else if (`type` eq IRT.BasicType.DOUBLE) {
      code.append(new DCMPL)
      b1 = code.append(new IFNE(null))
    }
    else {
      b1 = code.append(new IF_ACMPNE(null))
    }
    processBranch(code, b1)
  }

  def gt(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    var b1: BranchHandle = null
    if (`type` eq IRT.BasicType.INT) {
      b1 = code.append(new IF_ICMPGT(null))
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LCMP)
      b1 = code.append(new IFGT(null))
    }
    else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FCMPL)
      b1 = code.append(new IFGT(null))
    }
    else if (`type` eq IRT.BasicType.DOUBLE) {
      code.append(new DCMPL)
      b1 = code.append(new IFGT(null))
    }
    else {
      throw new RuntimeException("")
    }
    processBranch(code, b1)
  }

  def gte(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    var comparation: BranchHandle = null
    if (`type` eq IRT.BasicType.INT) {
      comparation = code.append(new IF_ICMPGE(null))
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LCMP)
      comparation = code.append(new IFGE(null))
    }
    else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FCMPL)
      comparation = code.append(new IFGE(null))
    }
    else if (`type` eq IRT.BasicType.DOUBLE) {
      code.append(new DCMPL)
      comparation = code.append(new IFGE(null))
    }
    else {
      throw new RuntimeException("")
    }
    processBranch(code, comparation)
  }

  def lte(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    var b1: BranchHandle = null
    if (`type` eq IRT.BasicType.INT) {
      b1 = code.append(new IF_ICMPLE(null))
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LCMP)
      b1 = code.append(new IFLT(null))
    }
    else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FCMPL)
      b1 = code.append(new IFLE(null))
    }
    else if (`type` eq IRT.BasicType.DOUBLE) {
      code.append(new DCMPL)
      b1 = code.append(new IFLE(null))
    }
    else {
      throw new RuntimeException("")
    }
    processBranch(code, b1)
  }

  def lt(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    var comparation: BranchHandle = null
    if (`type` eq IRT.BasicType.INT) {
      comparation = code.append(new IF_ICMPLT(null))
    }
    else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LCMP)
      comparation = code.append(new IFLT(null))
    }
    else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FCMPL)
      comparation = code.append(new IFLT(null))
    }
    else if (`type` eq IRT.BasicType.DOUBLE) {
      code.append(new DCMPL)
      comparation = code.append(new IFLT(null))
    }
    else {
      throw new RuntimeException("")
    }
    processBranch(code, comparation)
  }

  private def processBranch(code: CodeGeneration.Proxy, b1: BranchHandle): Unit = {
    code.append(InstructionConst.ICONST_0)
    val b2: BranchHandle = code.append(new GOTO(null))
    b1.setTarget(code.append(InstructionConst.ICONST_1))
    b2.setTarget(code.append(InstructionConst.NOP))
  }

  def codeChar(node: IRT.CharacterValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendConstant(JCharacter.valueOf(node.value))
  }

  def codeString(node: IRT.StringValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendConstant(node.value)
  }

  def codeInteger(node: IRT.IntValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendConstant(JInteger.valueOf(node.value))
  }

  def codeLong(node: IRT.LongValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendConstant(JLong.valueOf(node.value))
  }

  def codeFloat(node: IRT.FloatValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendConstant(JFloat.valueOf(node.value))
  }

  def codeDouble(node: IRT.DoubleValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendConstant(JDouble.valueOf(node.value))
  }

  def codeBoolean(node: IRT.BoolValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendConstant(JBoolean.valueOf(node.value))
  }

  def codeNull(node: IRT.NullValue, code: CodeGeneration.Proxy): InstructionHandle = {
    code.append(InstructionConst.ACONST_NULL)
  }

  def codeUnaryExpression(node: IRT.UnaryTerm, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.operand, code)
    val `type`: IRT.Type = node.operand.`type`
    node.kind match {
      case PLUS =>
        plus(code, `type`)
      case MINUS =>
        minus(code, `type`)
      case NOT =>
        not(code, `type`)
      case BIT_NOT =>
        bitNot(code, `type`)
      case _ =>
        throw new RuntimeException
    }
    start
  }

  private def plus(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if ((`type` ne IRT.BasicType.INT) && (`type` ne IRT.BasicType.LONG) && (`type` ne IRT.BasicType.FLOAT) && (`type` ne IRT.BasicType.DOUBLE)) {
      throw new RuntimeException
    }
  }

  private def minus(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(InstructionConst.INEG)
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(InstructionConst.LNEG)
    } else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(InstructionConst.FNEG)
    } else if (`type` eq IRT.BasicType.DOUBLE) {
      code.append(InstructionConst.DNEG)
    } else {
      throw new RuntimeException
    }
  }

  private def not(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.BOOLEAN) {
      val b1: BranchHandle = code.append(new IFNE(null))
      var b2: BranchHandle = null
      code.append(new ICONST(1))
      b2 = code.append(new GOTO(null))
      b1.setTarget(code.append(new ICONST(0)))
      b2.setTarget(code.append(new NOP))
    } else {
      throw new RuntimeException
    }
  }

  private def bitNot(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(new ICONST(-1))
      code.append(new IXOR)
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LCONST(-1))
      code.append(new LXOR)
    } else {
      throw new RuntimeException
    }
  }

  def codeCast(node: IRT.AsInstanceOf, code: CodeGeneration.Proxy): InstructionHandle = {
    val target: IRT.Term = node.target
    val start: InstructionHandle = codeExpression(target, code)
    val fromType = target.`type`
    val toType: IRT.Type = node.destination
    fromType match {
      case IRT.BasicType.INT | IRT.BasicType.SHORT | IRT.BasicType.BYTE =>
        if(toType != IRT.BasicType.INT) {
          code.appendCast(typeOf(IRT.BasicType.INT), typeOf(toType));
        }
      case _ =>
        if(fromType != toType) {
          code.appendCast(typeOf(fromType), typeOf(toType))
        }
    }
    start
  }

  def codeIsInstance(node: IRT.InstanceOf, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.target, code)
    code.appendInstanceOf(typeOf(node.checked).asInstanceOf[ReferenceType])
    start
  }

  def codeSelf(node: IRT.This, code: CodeGeneration.Proxy): InstructionHandle = {
    code.append(InstructionConst.ALOAD_0)
  }

  def codeOuterThis(node: IRT.OuterThis, code: CodeGeneration.Proxy): InstructionHandle = {
    code.appendThis
    code.appendGetField(currentClosureName, OUTER_THIS, Type.OBJECT)
    code.appendCast(Type.OBJECT, typeOf(node.`type`))
  }

  def codeFieldRef(node: IRT.RefField, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.target, code)
    val symbol: IRT.ClassType = node.target.`type`.asInstanceOf[IRT.ClassType]
    code.appendGetField(symbol.name, node.field.name, typeOf(node.`type`))
    start
  }

  def codeFieldAssign(node: IRT.SetField, code: CodeGeneration.Proxy): InstructionHandle = {
    val start: InstructionHandle = codeExpression(node.target, code)
    codeExpression(node.value, code)
    if (isWideType(node.value.`type`)) {
      code.append(InstructionConst.DUP2_X1)
    } else {
      code.append(InstructionConst.DUP_X1)
    }
    val symbol: IRT.ClassType = node.target.`type`.asInstanceOf[IRT.ClassType]
    code.appendPutField(symbol.name, node.field.name, typeOf(node.`type`))
    start
  }

  private def add(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(new IADD)
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LADD)
    } else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FADD)
    } else {
      code.append(new DADD)
    }
  }

  private def sub(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(new ISUB)
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LSUB)
    } else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FSUB)
    } else {
      code.append(new DSUB)
    }
  }

  private def mul(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(new IMUL)
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LMUL)
    } else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FMUL)
    } else {
      code.append(new DMUL)
    }
  }

  private def div(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(new IDIV)
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LDIV)
    } else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FDIV)
    } else {
      code.append(new DDIV)
    }
  }

  private def mod(code: CodeGeneration.Proxy, `type`: IRT.Type): Unit = {
    if (`type` eq IRT.BasicType.INT) {
      code.append(new IREM)
    } else if (`type` eq IRT.BasicType.LONG) {
      code.append(new LREM)
    } else if (`type` eq IRT.BasicType.FLOAT) {
      code.append(new FREM)
    } else {
      code.append(new DREM)
    }
  }

  private def frameObjectIndex(origin: Int, arguments: Array[IRT.Type]): Int = {
    var maxIndex: Int = origin
    var i: Int = 0
    while (i < arguments.length) {
      if (isWideType(arguments(i))) {
        maxIndex += 2
      } else {
        maxIndex += 1
      }
      i += 1;
    }
    maxIndex
  }

  private def makeIndexTableFor(origin: Int, frame: LocalFrame): Array[Int] = {
    val bindings: Seq[LocalBinding] = frame.entries
    val indexTable: Array[Int] = new Array[Int](bindings.length)
    var maxIndex: Int = origin
    var i: Int = 0
    while (i < bindings.length) {
      indexTable(i) = maxIndex
      if (isWideType(bindings(i).tp)) {
        maxIndex += 2
      } else {
        maxIndex += 1
      }
      i += 1;
    }
    indexTable
  }

  private def makeIndexTableForClosureFrame(frame: LocalFrame): Array[Int] = {
    val bindings: Seq[LocalBinding] = frame.entries
    val indexTable: Array[Int] = new Array[Int](bindings.length)
    var maxIndex: Int = 0
    var i: Int = 0
    while (i < bindings.length) {
      indexTable(i) = maxIndex
      maxIndex += 1
      i += 1
    }
    indexTable
  }

  private def isWideType(symbol: IRT.Type): Boolean = {
    ((symbol eq IRT.BasicType.DOUBLE) || (symbol eq IRT.BasicType.LONG))
  }

  private def typeOf(`type`: IRT.Type): Type = {
    translateIxTypeToVmType(`type`)
  }

  private def typesOf(types: Array[IRT.Type]): Array[Type] = {
    val destinationTypes: Array[Type] = new Array[Type](types.length)

    var i: Int = 0
    while (i < destinationTypes.length) {
      destinationTypes(i) = translateIxTypeToVmType(types(i))
      i += 1;
    }
    destinationTypes
  }

  private final val compiledClasses: List[JavaClass] = new ArrayList[JavaClass]
  private var generator: SymbolGenerator = null
  private var isStatic: Boolean = false
  private var isClosure: Boolean = false
  private var currentClosureName: String = null
}
