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

  classes.map(k => (k.className, k.content)).foreach{ case (className, content) =>
    defineClass(className, content, 0, content.length)
  }

  @throws(classOf[ClassNotFoundException])
  protected override def findClass(name: String): Class[_] = super.findClass(name)
}
