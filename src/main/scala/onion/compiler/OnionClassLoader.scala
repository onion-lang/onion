/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.io.File
import java.net._
import java.net.URL
import java.net.URLClassLoader

/**
 * @author Kota Mizushima
 *
 */
class OnionClassLoader @throws(classOf[MalformedURLException]) (parent: ClassLoader, classPath: Seq[String], classes: Seq[CompiledClass]) extends
  URLClassLoader(classPath.map(cp => new File(cp).toURI.toURL).toArray, parent) {

  // Define classes lazily: eager defineClass in declaration order fails when
  // a subclass is declared before its superclass in the same script (the JVM
  // resolves the super while defining and it isn't there yet)
  private val pendingClasses = scala.collection.mutable.Map(classes.map(k => k.className -> k.content)*)

  classes.foreach(k => loadClass(k.className))

  @throws(classOf[ClassNotFoundException])
  protected override def findClass(name: String): Class[?] =
    pendingClasses.remove(name) match {
      case Some(content) => defineClass(name, content, 0, content.length)
      case None => super.findClass(name)
    }
}
