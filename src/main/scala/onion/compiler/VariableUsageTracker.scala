/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import scala.collection.mutable

/**
 * Tracks variable declarations and usages for unused variable detection.
 */
class VariableUsageTracker {
  private val declarations = mutable.Map[String, VariableDecl]()
  private val usages = mutable.Set[String]()

  /**
   * Records a variable declaration.
   *
   * @param name Variable name
   * @param location Source location of declaration
   * @param isParameter Whether this is a method/function parameter
   */
  def recordDeclaration(name: String, location: Location, isParameter: Boolean = false): Unit = {
    // Don't track synthetic/generated names
    if (!name.startsWith("symbol#") && !name.startsWith("$")) {
      declarations(name) = VariableDecl(name, location, isParameter)
    }
  }

  /**
   * Records a variable usage.
   */
  def recordUsage(name: String): Unit = {
    usages += name
  }

  /**
   * Returns all unused variables (declared but never used).
   */
  def unusedVariables: Seq[VariableDecl] = {
    declarations.values.filter(d => !usages.contains(d.name)).toSeq
  }

  /**
   * Returns unused regular variables (not parameters).
   */
  def unusedLocalVariables: Seq[VariableDecl] = {
    unusedVariables.filter(!_.isParameter)
  }

  /**
   * Returns unused parameters.
   */
  def unusedParameters: Seq[VariableDecl] = {
    unusedVariables.filter(_.isParameter)
  }

  /**
   * Clears all tracking data (call when starting a new method/function).
   */
  def clear(): Unit = {
    declarations.clear()
    usages.clear()
  }
}

/**
 * Represents a variable declaration.
 */
case class VariableDecl(name: String, location: Location, isParameter: Boolean)
