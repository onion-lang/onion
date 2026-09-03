package onion.compiler.backend.asm

import org.objectweb.asm.{Opcodes, Type as AsmType}
import org.objectweb.asm.commons.GeneratorAdapter

object AsmUtil {
  /** Precomputed, so array maps over ASM types do not synthesize a ClassTag (a ClassValue lookup) per call. */
  val asmTypeTag: scala.reflect.ClassTag[AsmType] = scala.reflect.ClassTag(classOf[AsmType])
  /** The shared empty array (a default argument built with `Array.empty` synthesized a ClassTag per call). */
  val noAsmTypes: Array[AsmType] = new Array[AsmType](0)
  val JavaLangObject: String = "java.lang.Object"
  val JavaUtilArrayList: String = "java.util.ArrayList"
  val JavaUtilLinkedHashMap: String = "java.util.LinkedHashMap"

  def internalName(fqcn: String): String = fqcn.replace('.', '/')

  // Built per call site before; a name maps to one Type, so it is kept.
  private val objectTypes = new java.util.concurrent.ConcurrentHashMap[String, AsmType]()
  def objectType(fqcn: String): AsmType =
    val cached = objectTypes.get(fqcn)
    if cached != null then cached
    else
      val fresh = AsmType.getObjectType(internalName(fqcn))
      val winner = objectTypes.putIfAbsent(fqcn, fresh)
      if winner == null then fresh else winner

  def getField(gen: GeneratorAdapter, ownerFqcn: String, name: String, fieldType: AsmType): Unit =
    gen.getField(objectType(ownerFqcn), name, fieldType)

  def putField(gen: GeneratorAdapter, ownerFqcn: String, name: String, fieldType: AsmType): Unit =
    gen.putField(objectType(ownerFqcn), name, fieldType)

  def newArray(gen: GeneratorAdapter, component: AsmType): Unit =
    gen.newArray(component)

  def checkCast(gen: GeneratorAdapter, tp: AsmType): Unit =
    gen.checkCast(tp)

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
