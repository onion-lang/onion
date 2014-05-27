package onion.compiler


/**
 * @author Kota Mizushima
 *
 */
class ClosureLocalBinding(val frameIndex: Int, index: Int, `type`: IRT.TypeRef) extends LocalBinding(index, `type`) {
  override def equals(other: Any): Boolean = {
    other match {
      case bind: ClosureLocalBinding =>
        if (frame != bind.frame) return false
        if (index != bind.index) return false
        if (tp ne bind.tp) return false
        true
      case _ =>
        false
    }
  }

  override def hashCode: Int = frame + index + tp.hashCode

  private var frame: Int = 0
}
