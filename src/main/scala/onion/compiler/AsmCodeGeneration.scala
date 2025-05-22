package onion.compiler

import org.objectweb.asm.{ClassWriter, Opcodes, Type}
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

/**
 * Experimental ASM-based bytecode generator. Only supports a very small
 * subset of language features.  Unsupported constructs are ignored or
 * generate empty stubs.
 */
class AsmCodeGeneration(config: CompilerConfig) extends BytecodeGenerator:
  private def toAsmModifier(mod: Int): Int =
    var access = 0
    if Modifier.isPublic(mod) then access |= Opcodes.ACC_PUBLIC
    if Modifier.isProtected(mod) then access |= Opcodes.ACC_PROTECTED
    if Modifier.isPrivate(mod) then access |= Opcodes.ACC_PRIVATE
    if Modifier.isStatic(mod) then access |= Opcodes.ACC_STATIC
    if Modifier.isFinal(mod) then access |= Opcodes.ACC_FINAL
    if Modifier.isAbstract(mod) then access |= Opcodes.ACC_ABSTRACT
    access

  private def asmType(tp: TypedAST.Type): Type = tp match
    case TypedAST.BasicType.VOID    => Type.VOID_TYPE
    case TypedAST.BasicType.BOOLEAN => Type.BOOLEAN_TYPE
    case TypedAST.BasicType.BYTE    => Type.BYTE_TYPE
    case TypedAST.BasicType.SHORT   => Type.SHORT_TYPE
    case TypedAST.BasicType.CHAR    => Type.CHAR_TYPE
    case TypedAST.BasicType.INT     => Type.INT_TYPE
    case TypedAST.BasicType.LONG    => Type.LONG_TYPE
    case TypedAST.BasicType.FLOAT   => Type.FLOAT_TYPE
    case TypedAST.BasicType.DOUBLE  => Type.DOUBLE_TYPE
    case c: TypedAST.ClassType      => Type.getObjectType(c.name.replace('.', '/'))
    case a: TypedAST.ArrayType      => Type.getType("[" * a.dimension + asmType(a.component).getDescriptor)
    case _                          => Type.VOID_TYPE

  def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass] =
    classes.map(codeClass)

  private def codeClass(node: TypedAST.ClassDefinition): CompiledClass =
    val cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
    val superName =
      if node.superClass != null then node.superClass.name.replace('.', '/')
      else "java/lang/Object"
    val interfaces =
      if node.interfaces != null then node.interfaces.map(_.name.replace('.', '/')).toArray
      else Array.empty[String]
    val classAccess =
      if node.isInterface then
        toAsmModifier(node.modifier) | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT
      else
        toAsmModifier(node.modifier)
    cw.visit(Opcodes.V21, classAccess,
      node.name.replace('.', '/'), null, superName, interfaces)

    for m <- node.methods do m match
      case md: TypedAST.MethodDefinition => codeMethod(cw, md)
      case _ =>
    cw.visitEnd()
    val bytes = cw.toByteArray
    val dir = if config.outputDirectory != null then config.outputDirectory else ""
    CompiledClass(node.name, dir, bytes)

  private def codeMethod(cw: ClassWriter, node: TypedAST.MethodDefinition): Unit =
    val access = toAsmModifier(node.modifier)
    val desc = Method(node.name, asmType(node.returnType), node.arguments.map(asmType)).getDescriptor
    val isAbstract = Modifier.isAbstract(node.modifier) || node.block == null
    if isAbstract then
      cw.visitMethod(access | Opcodes.ACC_ABSTRACT, node.name, desc, null, null).visitEnd()
    else
      val mv = cw.visitMethod(access, node.name, desc, null, null)
      val gen = new GeneratorAdapter(mv, access, node.name, desc)
      mv.visitCode()
      findReturn(node.block) match
        case Some(ret) =>
          emitReturn(gen, ret.term, node.returnType)
        case None =>
          emitDefaultReturn(gen, node.returnType)
      gen.endMethod()

  private def findReturn(stmt: TypedAST.ActionStatement): Option[TypedAST.Return] = stmt match
    case r: TypedAST.Return => Some(r)
    case sb: TypedAST.StatementBlock =>
      sb.statements.iterator.map(findReturn).collectFirst { case Some(r) => r }
    case _ => None

  private def emitReturn(gen: GeneratorAdapter, term: TypedAST.Term, tp: TypedAST.Type): Unit = term match
    case v: TypedAST.IntValue =>
      gen.push(v.value)
      gen.returnValue()
    case v: TypedAST.StringValue =>
      gen.push(v.value)
      gen.returnValue()
    case _ =>
      emitDefaultReturn(gen, tp)

  private def emitDefaultReturn(gen: GeneratorAdapter, tp: TypedAST.Type): Unit =
    tp match
      case TypedAST.BasicType.VOID =>
        gen.visitInsn(Opcodes.RETURN)
      case TypedAST.BasicType.BOOLEAN | TypedAST.BasicType.BYTE |
           TypedAST.BasicType.SHORT | TypedAST.BasicType.CHAR |
           TypedAST.BasicType.INT =>
        gen.push(0)
        gen.returnValue()
      case TypedAST.BasicType.LONG =>
        gen.push(0L)
        gen.returnValue()
      case TypedAST.BasicType.FLOAT =>
        gen.push(0f)
        gen.returnValue()
      case TypedAST.BasicType.DOUBLE =>
        gen.push(0d)
        gen.returnValue()
      case _ =>
        gen.visitInsn(Opcodes.ACONST_NULL)
        gen.returnValue()

