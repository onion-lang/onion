package onion.compiler


/**
 * @author Kota Mizushima
 *
 */
class ClosureLocalBinding(val frameIndex: Int, index: Int, `type`: IRT.Type, isMutable: Boolean) extends LocalBinding(index, `type`, isMutable) {
  override def equals(other: Any): Boolean = {
    other match {
      case bind: ClosureLocalBinding =>
        frameIndex == bind.frameIndex &&
        index == bind.index &&
        (tp eq bind.tp) &&
        isMutable == bind.isMutable
      case _ =>
        false
    }
  }

  override def hashCode: Int = frameIndex + index + tp.hashCode + (if (isMutable) 1 else 0)
}
