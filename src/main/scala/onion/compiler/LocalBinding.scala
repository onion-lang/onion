/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

/**
 * @author Kota Mizushima
 * @param index Variable index
 * @param tp Variable type
 * @param isMutable Whether the variable is mutable
 * @param isBoxed Whether the variable is boxed (for closure capture of mutable vars)
 */
case class LocalBinding(index: Int, tp: TypedAST.Type, isMutable: Boolean, isBoxed: Boolean = false)
