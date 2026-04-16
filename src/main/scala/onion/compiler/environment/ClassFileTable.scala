/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import java.io.{File, IOException}
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * @author Kota Mizushima
 *
 */
class ClassFileTable(classPathString: String) {
  private val classLoader: ClassLoader = createClassLoader(classPathString)
  private val bytesCache = new ConcurrentHashMap[String, Option[Array[Byte]]]()

  private def createClassLoader(classPath: String): ClassLoader = {
    val urls = classPath.split(File.pathSeparator).map { path =>
      val file = new File(path)
      file.toURI.toURL
    }
    new URLClassLoader(urls, Thread.currentThread().getContextClassLoader)
  }

  /**
   * Load class bytes for the given class name
   * @param className fully qualified class name
   * @return byte array of the class file, or null if not found
   */
  def loadBytes(className: String): Array[Byte] = {
    val cached = bytesCache.get(className)
    if (cached != null) return cached.orNull

    val resourcePath = className.replace('.', '/') + ".class"
    val inputStream = classLoader.getResourceAsStream(resourcePath)
    val loaded =
      if (inputStream == null) None
      else
        try {
          Option(inputStream.readAllBytes())
        } catch {
          case _: IOException => None
        } finally {
          inputStream.close()
        }

    bytesCache.putIfAbsent(className, loaded)
    loaded.orNull
  }
}
