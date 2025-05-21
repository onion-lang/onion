package onion.compiler.environment

import java.io.File
import java.net.URLClassLoader

/** Simple class loader utility that returns raw class bytes from a classpath. */
class ClassFileTable(classPathString: String):
  private val loader =
    val paths = classPathString.split(File.pathSeparator).map(p => new File(p))
    new URLClassLoader(paths.map(_.toURI.toURL))

  def loadBytes(className: String): Array[Byte] =
    val resource = className.replace('.', '/') + ".class"
    val in = loader.getResourceAsStream(resource)
    if in == null then null
    else
      try in.readAllBytes()
      finally in.close()

