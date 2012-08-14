package onion.compiler


/**
 * Created with IntelliJ IDEA.
 * User: Mizushima
 * Date: 12/05/20
 * Time: 9:34
 * To change this template use File | Settings | File Templates.
 */
/**
 * @author Kota Mizushima
 *         Date: 2005/06/28
 */
class ClosureLocalBinding(var frameIndex: Int, index: Int, `type`: IRT.TypeRef) extends LocalBinding(index, `type`) {

  def setFrameIndex(frameIndex: Int) {
    this.frameIndex = frameIndex
  }

  def getFrameIndex: Int = {
    return frameIndex
  }

  override def equals(other: Any): Boolean = {
    other match {
      case bind: ClosureLocalBinding =>
        if (frame != bind.frame) return false
        if (getIndex != bind.getIndex) return false
        if (getType ne bind.getType) return false
        return true
      case _ => false
    }
  }

  override def hashCode: Int = {
    return frame + getIndex + getType.hashCode
  }

  private var frame: Int = 0
}