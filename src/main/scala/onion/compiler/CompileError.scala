/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

/**
 * Compilation context information for error reporting.
 * Provides context about where in the code the error occurred.
 *
 * @param currentClass  The class being compiled (if any)
 * @param currentMethod The method being compiled (if any)
 * @param currentPhase  The compilation phase (e.g., "typing", "codegen")
 */
case class CompilationContext(
  currentClass: Option[String] = None,
  currentMethod: Option[String] = None,
  currentPhase: String = "typing"
) {
  /**
   * Returns a formatted context string for error messages.
   * Example: "in class Foo, method bar"
   */
  def formatContext: Option[String] = {
    (currentClass, currentMethod) match {
      case (Some(cls), Some(mtd)) => Some(s"in class $cls, method $mtd")
      case (Some(cls), None) => Some(s"in class $cls")
      case (None, Some(mtd)) => Some(s"in method $mtd")
      case (None, None) => None
    }
  }

  /** Creates a new context with the given class */
  def withClass(className: String): CompilationContext =
    copy(currentClass = Some(className))

  /** Creates a new context with the given method */
  def withMethod(methodName: String): CompilationContext =
    copy(currentMethod = Some(methodName))

  /** Creates a new context with the given phase */
  def withPhase(phase: String): CompilationContext =
    copy(currentPhase = phase)

  /** Clears the method context (e.g., when leaving a method) */
  def clearMethod: CompilationContext =
    copy(currentMethod = None)

  /** Clears the class context (e.g., when leaving a class) */
  def clearClass: CompilationContext =
    copy(currentClass = None, currentMethod = None)
}

object CompilationContext {
  /** Default empty context */
  val empty: CompilationContext = CompilationContext()
}

/**
 * Represents a compilation error with location and context information.
 *
 * @param sourceFile The source file where the error occurred
 * @param location   The location (line/column) in the source file
 * @param message    The error message
 * @param errorCode  Optional error code for categorization
 * @param context    Optional compilation context (class, method, phase)
 */
case class CompileError(
  sourceFile: String,
  location: Location,
  message: String,
  errorCode: Option[String] = None,
  context: Option[CompilationContext] = None
) {
  /**
   * Returns the full error message with context information.
   */
  def fullMessage: String = {
    context.flatMap(_.formatContext) match {
      case Some(ctx) => s"$message ($ctx)"
      case None => message
    }
  }
}
