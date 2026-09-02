/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import java.io.{File, IOException}
import java.util.concurrent.ConcurrentHashMap

import onion.compiler.ExplicitClasspathClassLoader

/**
 * @author Kota Mizushima
 *
 */
class ClassFileTable(classPathString: String) {
  private val classLoader: ClassLoader = createClassLoader(classPathString)
  private val bytesCache = new ConcurrentHashMap[String, Option[Array[Byte]]]()

  private def createClassLoader(classPath: String): ClassLoader = {
    // `File.toURI` stats the path to decide on a trailing slash (directory vs jar), on
    // every entry of every compilation. The URL depends only on the path and that one
    // bit, so it is cached by both; the bit is still checked each time, so a directory
    // created after the first compilation is picked up.
    val urls =
      classPath.split(File.pathSeparator, -1).iterator
        .filter(_.nonEmpty)
        .map { path =>
          val file = new File(path)
          val key = if (file.isDirectory) path + File.separator else path
          ClassFileTable.urls.computeIfAbsent(key, _ => file.toURI.toURL)
        }
        .toArray
    ExplicitClasspathClassLoader(
      urls,
      Thread.currentThread().getContextClassLoader
    )
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

object ClassFileTable {
  private val urls = new ConcurrentHashMap[String, java.net.URL]()
}
