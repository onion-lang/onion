/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

/**
 * Warning levels for compiler diagnostics.
 */
enum WarningLevel:
  case Off     // No warnings
  case On      // Warnings displayed but don't fail compilation
  case Error   // Warnings treated as errors

/**
 * Warning categories that can be individually suppressed.
 */
enum WarningCategory(val code: String, val description: String):
  case UnusedVariable    extends WarningCategory("W0001", "Unused variable")
  case UnusedImport      extends WarningCategory("W0002", "Unused import")
  case UnreachableCode   extends WarningCategory("W0003", "Unreachable code")
  case DeprecatedFeature extends WarningCategory("W0004", "Deprecated feature")
  case ShadowedVariable  extends WarningCategory("W0005", "Shadowed variable")
  case UnusedParameter   extends WarningCategory("W0006", "Unused parameter")
  case EmptyBlock        extends WarningCategory("W0007", "Empty block")
  case RedundantCast     extends WarningCategory("W0008", "Redundant cast")

/**
 * A compiler warning with location and message.
 */
case class CompileWarning(
  sourceFile: String,
  location: Location,
  category: WarningCategory,
  message: String
):
  def code: String = category.code

  def formatted: String =
    val loc = if location != null then s"${location.line}:${location.column}" else "?"
    s"[$code] $sourceFile:$loc: warning: $message"
