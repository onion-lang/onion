package onion.compiler.backend.asm

import org.objectweb.asm.ClassWriter

/**
 * A `ClassWriter` for `COMPUTE_FRAMES` mode that tolerates merging a stack-map-frame
 * type against a class still being compiled in the same unit (or otherwise absent
 * from the classpath). ASM's default `getCommonSuperClass` resolves both sides via
 * `Class.forName` on the *thread's* classloader to walk their supertypes; a class this
 * compilation is producing was never loaded that way; the reflective load fails as an
 * `ClassNotFoundException` wrapped in `TypeNotPresentException`, escaping as an
 * uncaught internal compiler error (I0000) instead of a diagnostic (crash reproducer:
 * MutationFuzzSpec, a `select`/`if` used as a statement whose branches yield unrelated
 * reference types -- the merge point's stack-map frame needs a common ancestor of
 * both even though the value itself is discarded right after).
 *
 * `java.lang.Object` is always a legal common ancestor for two reference types, so
 * falling back to it on failure keeps the emitted frame verifier-safe -- it can only
 * widen the inferred merge type, never narrow it, and any narrower type actually
 * needed at a use site is already made concrete there via an explicit checkcast
 * inserted by the type checker, independent of this frame computation.
 */
private[asm] class RobustClassWriter(flags: Int) extends ClassWriter(flags) {
  override def getCommonSuperClass(type1: String, type2: String): String =
    try super.getCommonSuperClass(type1, type2)
    catch case _: TypeNotPresentException => "java/lang/Object"
}
