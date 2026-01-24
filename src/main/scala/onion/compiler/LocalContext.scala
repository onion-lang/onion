/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import toolbox.SymbolGenerator
import scala.collection.mutable

/**
 * Local Variable Context for Type Checking
 *
 * This class manages local variable scopes during type checking, providing:
 *   - Variable lookup and binding
 *   - Scope nesting (frames and scopes)
 *   - Closure variable tracking
 *   - Usage tracking for unused variable warnings
 *   - Return type inference for closures
 *
 * == Scope Management ==
 *
 * LocalContext manages two levels of scoping:
 *   - '''Frames''': Major scope boundaries (methods, closures)
 *   - '''Scopes''': Minor scope boundaries (blocks, loops)
 *
 * == Null-Safe Variants ==
 *
 * Methods that may return null have Option-returning variants:
 *   - `lookup` → `lookupOpt`
 *   - `lookupOnlyCurrentScope` → `lookupOnlyCurrentScopeOpt`
 *
 * New code should prefer the *Opt variants for safer null handling.
 *
 * @author Kota Mizushima
 */
class LocalContext {
  private var contextFrame = new LocalFrame(null)
  private val generator = new SymbolGenerator("symbol#")
  private val boxedVariables = scala.collection.mutable.Set[String]()
  private val usageTracker = new VariableUsageTracker()
  private var returnTypeCollector: mutable.Buffer[TypedAST.Type] = null

  // Smart cast: type narrowing information
  // Maps variable names to their narrowed types (only for immutable variables)
  private var narrowings: Map[String, TypedAST.Type] = Map.empty
  private var returnTypeCollectionDepth: Int = 0
  var isClosure: Boolean                     = false
  var isStatic: Boolean                      = false
  var isGlobal: Boolean                      = false
  var method: TypedAST.Method                     = _
  var constructor: TypedAST.ConstructorDefinition = _
  private var isMethod: Boolean = false
  private var loopDepth: Int = 0

  def setGlobal(isGlobal: Boolean): Unit = {
    this.isGlobal = isGlobal
  }

  def setClosure(isClosure: Boolean): Unit = {
    this.isClosure = isClosure
  }

  def setStatic(isStatic: Boolean): Unit = {
    this.isStatic = isStatic
  }

  def newName: String = {
    generator.generate
  }

  def returnType: TypedAST.Type = {
    if (isMethod) {
      method.returnType
    } else {
      TypedAST.BasicType.VOID
    }
  }

  def setMethod(method: TypedAST.Method): Unit = {
    this.method = method
    this.isMethod = true
  }

  def setConstructor(constructor: TypedAST.ConstructorDefinition): Unit = {
    this.constructor = constructor
    this.isMethod = false
  }

  /** Save current method context for later restoration */
  def saveMethodContext(): (TypedAST.Method, Boolean) = (method, isMethod)

  /** Restore method context from saved state */
  def restoreMethodContext(saved: (TypedAST.Method, Boolean)): Unit = {
    this.method = saved._1
    this.isMethod = saved._2
  }

  def openFrame[A](block: => A): A = try {
    contextFrame = new LocalFrame(contextFrame)
    block
  } finally {
    contextFrame = contextFrame.parent
  }

  def depth: Int = {
    if (contextFrame == null) {
      -1
    } else {
      contextFrame.depth
    }
  }

  def getContextFrame: LocalFrame = {
    contextFrame
  }

  def setContextFrame(frame: LocalFrame): Unit = {
    this.contextFrame = frame
  }

  def openScope[A](body: => A): A = contextFrame.open(body)

  def openLoop[A](body: => A): A = {
    loopDepth += 1
    try body
    finally loopDepth -= 1
  }

  def inLoop: Boolean = loopDepth > 0

  def lookup(name: String): ClosureLocalBinding = {
    contextFrame.lookup(name)
  }

  /** Option-returning version of lookup for safer null handling */
  def lookupOpt(name: String): Option[ClosureLocalBinding] = {
    Option(contextFrame.lookup(name))
  }

  /**
   * Gets all variable names visible from the current scope.
   */
  def allNames: Set[String] = {
    if (contextFrame == null) Set.empty
    else contextFrame.allNames
  }

  def lookupOnlyCurrentScope(name: String): ClosureLocalBinding = {
    contextFrame.lookupOnlyCurrentScope(name)
  }

  /** Option-returning version of lookupOnlyCurrentScope for safer null handling */
  def lookupOnlyCurrentScopeOpt(name: String): Option[ClosureLocalBinding] = {
    Option(contextFrame.lookupOnlyCurrentScope(name))
  }

  /**
   * Checks if a variable with the given name exists in an outer scope (not the current scope).
   * Returns the location of the shadowed variable, or None if not shadowing.
   */
  def checkShadowing(name: String): Option[Location] = {
    contextFrame.lookupInOuterScopes(name)
  }

  def add(name: String, `type` : TypedAST.Type): Int = {
    add(name, `type`, isMutable = true)
  }

  def add(name: String, `type`: TypedAST.Type, isMutable: Boolean): Int = {
    val isBoxed = boxedVariables.contains(name)
    contextFrame.add(name, `type`, isMutable, isBoxed)
  }

  def add(`type` : TypedAST.Type): String = {
    val name = newName
    contextFrame.add(name, `type`, isMutable = true, isBoxed = false)
    name
  }

  /**
   * Mark a variable as needing to be boxed (for closure capture)
   */
  def markAsBoxed(name: String): Unit = {
    boxedVariables += name
  }

  /**
   * Mark multiple variables as needing to be boxed
   */
  def markAsBoxed(names: Set[String]): Unit = {
    boxedVariables ++= names
  }

  /**
   * Check if a variable is boxed
   */
  def isBoxed(name: String): Boolean = {
    boxedVariables.contains(name)
  }

  // Variable usage tracking

  /**
   * Records a variable declaration for unused variable detection.
   */
  def recordDeclaration(name: String, location: Location, isParameter: Boolean = false): Unit = {
    usageTracker.recordDeclaration(name, location, isParameter)
  }

  /**
   * Records that a variable was used.
   */
  def recordUsage(name: String): Unit = {
    usageTracker.recordUsage(name)
  }

  /**
   * Gets all unused local variables (not parameters).
   */
  def unusedLocalVariables: Seq[VariableDecl] = {
    usageTracker.unusedLocalVariables
  }

  /**
   * Gets all unused parameters.
   */
  def unusedParameters: Seq[VariableDecl] = {
    usageTracker.unusedParameters
  }

  /**
   * Clears usage tracking data (call when starting a new method/function).
   */
  def clearUsageTracking(): Unit = {
    usageTracker.clear()
  }

  def startReturnTypeCollection(): mutable.Buffer[TypedAST.Type] = {
    val buf = mutable.ArrayBuffer[TypedAST.Type]()
    returnTypeCollector = buf
    returnTypeCollectionDepth = 0
    buf
  }

  def stopReturnTypeCollection(): Unit = {
    returnTypeCollector = null
    returnTypeCollectionDepth = 0
  }

  def hasReturnTypeCollector: Boolean = returnTypeCollector != null

  def collectingReturnTypes: Boolean =
    returnTypeCollector != null && returnTypeCollectionDepth == 0

  def pushReturnTypeCollectionDepth(): Unit = {
    if (returnTypeCollector != null) returnTypeCollectionDepth += 1
  }

  def popReturnTypeCollectionDepth(): Unit = {
    if (returnTypeCollector != null && returnTypeCollectionDepth > 0) returnTypeCollectionDepth -= 1
  }

  def collectReturnType(tp: TypedAST.Type): Unit = {
    if (collectingReturnTypes && tp != null) returnTypeCollector += tp
  }

  def collectedReturnTypes: Seq[TypedAST.Type] =
    if (returnTypeCollector == null) Seq.empty else returnTypeCollector.toSeq

  // ========== Smart Cast (Type Narrowing) ==========

  /**
   * Gets the effective type for a variable, considering smart casts.
   * Returns the narrowed type if available, otherwise the declared type.
   * Smart casts only apply to immutable (val) variables.
   */
  def getEffectiveType(name: String): TypedAST.Type = {
    val binding = lookup(name)
    if (binding == null) return null
    // Mutable variables are not subject to smart cast (could be reassigned)
    if (binding.isMutable) return binding.tp
    narrowings.getOrElse(name, binding.tp)
  }

  /**
   * Adds a type narrowing for a variable.
   * Used when an is-check or null-check narrows the type.
   */
  def addNarrowing(name: String, tp: TypedAST.Type): Unit =
    narrowings = narrowings + (name -> tp)

  /**
   * Clears the type narrowing for a variable.
   * Used when a variable is reassigned.
   */
  def clearNarrowing(name: String): Unit =
    narrowings = narrowings - name

  /**
   * Saves the current narrowing state.
   * Used before entering a branch to allow restoration after.
   */
  def saveNarrowings(): Map[String, TypedAST.Type] = narrowings

  /**
   * Restores a previously saved narrowing state.
   * Used after exiting a branch to restore the pre-branch state.
   */
  def restoreNarrowings(saved: Map[String, TypedAST.Type]): Unit =
    narrowings = saved

}

