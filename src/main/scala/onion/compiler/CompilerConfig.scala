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
 *
 */
case class CompilerConfig(
  classPath: Seq[String],
  superClass: String,
  encoding: String,
  outputDirectory: String,
  maxErrorReports: Int,
  verbose: Boolean = false,
  warningLevel: WarningLevel = WarningLevel.On,
  suppressedWarnings: Set[WarningCategory] = Set.empty
)
