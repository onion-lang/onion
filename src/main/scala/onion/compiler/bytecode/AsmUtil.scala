package onion.compiler.bytecode

import org.objectweb.asm.{Opcodes, Type as AsmType}
import org.objectweb.asm.commons.GeneratorAdapter

object AsmUtil {
  val JavaLangObject: String = "java.lang.Object"
  val JavaUtilArrayList: String = "java.util.ArrayList"

  def internalName(fqcn: String): String = fqcn.replace('.', '/')

  def objectType(fqcn: String): AsmType = AsmType.getObjectType(internalName(fqcn))

  def getField(gen: GeneratorAdapter, ownerFqcn: String, name: String, fieldType: AsmType): Unit =
    gen.getField(objectType(ownerFqcn), name, fieldType)

  def putField(gen: GeneratorAdapter, ownerFqcn: String, name: String, fieldType: AsmType): Unit =
    gen.putField(objectType(ownerFqcn), name, fieldType)

  /** Emit a default return for the given ASM type. */
  def emitDefaultReturn(gen: GeneratorAdapter, returnType: AsmType): Unit = {
    returnType.getSort match {
      case AsmType.VOID =>
        gen.returnValue()
      case AsmType.BOOLEAN =>
        gen.push(false)
        gen.returnValue()
      case AsmType.BYTE | AsmType.SHORT | AsmType.CHAR | AsmType.INT =>
        gen.push(0)
        gen.returnValue()
      case AsmType.LONG =>
        gen.push(0L)
        gen.returnValue()
      case AsmType.FLOAT =>
        gen.push(0.0f)
        gen.returnValue()
      case AsmType.DOUBLE =>
        gen.push(0.0d)
        gen.returnValue()
      case _ =>
        gen.visitInsn(Opcodes.ACONST_NULL)
        gen.returnValue()
    }
  }
}
