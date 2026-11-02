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
  suppressedWarnings: Set[WarningCategory] = Set.empty,
  dumpAst: Boolean = false,
  dumpTypedAst: Boolean = false,
  compileProfile: onion.compiler.pipeline.CompileProfileSettings = onion.compiler.pipeline.CompileProfileSettings(),
  // Sample count and RNG seed for `law` checking. Settings rather than constants so a
  // reported counterexample can be reproduced, and so a run can be widened to look for
  // others (issue #346).
  /** Whether the build runs contract predicates and reproduces history counterexamples. The editor turns it off: it validates on every keystroke and must not execute half-typed user code (issue #346). */
  runContracts: Boolean = true,
  contractSamples: Int = onion.compiler.verification.ArgGenerator.DefaultSamples,
  contractSeed: Long = onion.compiler.verification.ArgGenerator.DefaultSeed,
  // Whether to emit a LocalVariableTable. On by default, because a debugger that can step
  // through `.on` source but shows nothing for any variable is most of the way to useless,
  // and the cost is a few bytes per method. `-g:none` turns it off.
  emitDebugInfo: Boolean = true,
  // Contract clauses are a preview feature (RFC #2557): off unless asked for.
  contracts: ContractMode = ContractMode.Check
)
