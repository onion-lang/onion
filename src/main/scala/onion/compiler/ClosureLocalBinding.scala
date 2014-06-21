package onion.compiler


/**
 * @author Kota Mizushima
 *
 */
class ClosureLocalBinding(val frameIndex: Int, index: Int, `type`: IRT.TypeRef) extends LocalBinding(index, `type`) {
  override def equals(other: Any): Boolean = {
    other match {
      case bind: ClosureLocalBinding =>
        if (frameIndex != bind.frameIndex) false
        if (index != bind.index) false
        if (tp ne bind.tp) false
        else true
      case _ =>
        false
    }
  }

  override def hashCode: Int = frameIndex + index + tp.hashCode
}
