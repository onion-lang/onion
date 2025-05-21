/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.environment

import java.io._
import java.net.{URL, URLClassLoader}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

/**
 * @author Kota Mizushima
 *
 */
class ClassFileTable(classPathString: String) {
  private val classLoader: ClassLoader = createClassLoader(classPathString)

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
    val resourcePath = className.replace('.', '/') + ".class"
    val inputStream = classLoader.getResourceAsStream(resourcePath)
    if (inputStream == null) return null
    
    try {
      val out = new ByteArrayOutputStream()
      val buf = new Array[Byte](8192)
      var len = inputStream.read(buf)
      while (len != -1) {
        out.write(buf, 0, len)
        len = inputStream.read(buf)
      }
      out.toByteArray
    } catch {
      case _: IOException => null
    } finally {
      inputStream.close()
    }
  }
}