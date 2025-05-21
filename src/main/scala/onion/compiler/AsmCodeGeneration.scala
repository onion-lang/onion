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
    cw.visit(Opcodes.V21, toAsmModifier(node.modifier) | (if node.isInterface then Opcodes.ACC_INTERFACE else 0),
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
    val mv = cw.visitMethod(access, node.name, desc, null, null)
    val gen = new GeneratorAdapter(mv, access, node.name, desc)
    mv.visitCode()
    node.block.statements match
      case Array(ret: TypedAST.Return) =>
        emitReturn(gen, ret.term, node.returnType)
      case _ =>
        gen.visitInsn(Opcodes.RETURN)
    gen.endMethod()

  private def emitReturn(gen: GeneratorAdapter, term: TypedAST.Term, tp: TypedAST.Type): Unit = term match
    case v: TypedAST.IntValue =>
      gen.push(v.value)
      gen.returnValue()
    case v: TypedAST.StringValue =>
      gen.push(v.value)
      gen.returnValue()
    case _ =>
      gen.visitInsn(Opcodes.RETURN)

