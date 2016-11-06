/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler
import scala.beans.BeanProperty

/**
 * @author Kota Mizushima
 *
 */
case class CompiledClass(@BeanProperty className: String, @BeanProperty outputPath: String, @BeanProperty content: Array[Byte])
