/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import scala.collection.mutable.Buffer

/**
 * Collects and manages compiler warnings.
 *
 * @param level The warning level (Off, On, Error)
 * @param suppressedCategories Categories to suppress
 */
class WarningReporter(
  level: WarningLevel = WarningLevel.On,
  suppressedCategories: Set[WarningCategory] = Set.empty
) {
  private val warnings = Buffer[CompileWarning]()
  private var sourceFile: String = ""

  def setSourceFile(file: String): Unit = {
    sourceFile = file
  }

  def report(category: WarningCategory, location: Location, message: String): Unit = {
    if (level == WarningLevel.Off) return
    if (suppressedCategories.contains(category)) return

    warnings += CompileWarning(sourceFile, location, category, message)
  }

  // Convenience methods for each warning category

  def unusedVariable(location: Location, name: String): Unit = {
    report(WarningCategory.UnusedVariable, location, s"unused variable '$name'")
  }

  def unusedImport(location: Location, importName: String): Unit = {
    report(WarningCategory.UnusedImport, location, s"unused import '$importName'")
  }

  def unreachableCode(location: Location): Unit = {
    report(WarningCategory.UnreachableCode, location, "unreachable code")
  }

  def deprecatedFeature(location: Location, feature: String, alternative: Option[String] = None): Unit = {
    val msg = alternative match {
      case Some(alt) => s"'$feature' is deprecated, use '$alt' instead"
      case None => s"'$feature' is deprecated"
    }
    report(WarningCategory.DeprecatedFeature, location, msg)
  }

  def shadowedVariable(location: Location, name: String, originalLocation: Location): Unit = {
    val origLoc = if (originalLocation != null) s" (originally at line ${originalLocation.line})" else ""
    report(WarningCategory.ShadowedVariable, location, s"variable '$name' shadows outer variable$origLoc")
  }

  def unusedParameter(location: Location, name: String): Unit = {
    report(WarningCategory.UnusedParameter, location, s"unused parameter '$name'")
  }

  def emptyBlock(location: Location, blockType: String): Unit = {
    report(WarningCategory.EmptyBlock, location, s"empty $blockType block")
  }

  def redundantCast(location: Location, fromType: String, toType: String): Unit = {
    report(WarningCategory.RedundantCast, location, s"redundant cast from '$fromType' to '$toType'")
  }

  def possibleNullDereference(location: Location, expression: String): Unit = {
    report(WarningCategory.PossibleNullDeref, location, s"possible null dereference: $expression")
  }

  def unnecessaryConversion(location: Location, fromType: String, toType: String): Unit = {
    report(WarningCategory.UnnecessaryConversion, location, s"unnecessary conversion from '$fromType' to '$toType'")
  }

  def uncheckedCast(location: Location, fromType: String, toType: String): Unit = {
    report(WarningCategory.UncheckedCast, location, s"unchecked cast from '$fromType' to '$toType'")
  }

  def getWarnings: Seq[CompileWarning] = warnings.toSeq

  def hasWarnings: Boolean = warnings.nonEmpty

  def warningCount: Int = warnings.size

  /**
   * Returns true if warnings should be treated as errors.
   */
  def treatAsErrors: Boolean = level == WarningLevel.Error

  /**
   * Prints all warnings to stderr.
   */
  def printWarnings(): Unit = {
    warnings.foreach { w =>
      System.err.println(w.formatted)
    }
    if (warnings.nonEmpty) {
      System.err.println(s"${warnings.size} warning(s)")
    }
  }

  def clear(): Unit = warnings.clear()
}

object WarningReporter {
  /**
   * Creates a WarningReporter that suppresses all warnings.
   */
  def silent: WarningReporter = new WarningReporter(WarningLevel.Off)

  /**
   * Creates a WarningReporter with default settings.
   */
  def default: WarningReporter = new WarningReporter(WarningLevel.On)

  /**
   * Creates a WarningReporter that treats warnings as errors.
   */
  def strict: WarningReporter = new WarningReporter(WarningLevel.Error)
}
