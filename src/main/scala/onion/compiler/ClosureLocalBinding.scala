package onion.compiler


/**
 * @author Kota Mizushima
 * @param frameIndex Frame level (0 = current, 1 = parent, etc.)
 * @param isBoxed Whether the variable is boxed (for closure capture of mutable vars)
 */
class ClosureLocalBinding(val frameIndex: Int, index: Int, `type`: TypedAST.Type, isMutable: Boolean, isBoxed: Boolean = false)
  extends LocalBinding(index, `type`, isMutable, isBoxed) {

  override def equals(other: Any): Boolean = {
    other match {
      case bind: ClosureLocalBinding =>
        frameIndex == bind.frameIndex &&
        index == bind.index &&
        (tp eq bind.tp) &&
        isMutable == bind.isMutable &&
        isBoxed == bind.isBoxed
      case _ =>
        false
    }
  }

  override def hashCode: Int = frameIndex + index + tp.hashCode + (if (isMutable) 1 else 0) + (if (isBoxed) 2 else 0)
}
