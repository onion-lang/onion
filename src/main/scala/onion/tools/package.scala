package onion
import java.io.Closeable

package object tools {
  def using[T <: Closeable, U](resource:  T)(block: T => U): U = try {
    block(resource)
  } finally {
    resource.close()
  }
}
