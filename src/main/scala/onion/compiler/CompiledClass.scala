/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;
import scala.reflect.BeanProperty

/**
 * @author Kota Mizushima
 * Date: 2005/04/09
 */
case class CompiledClass(@BeanProperty className: String, @BeanProperty outputPath: String, @BeanProperty content: Array[Byte])
