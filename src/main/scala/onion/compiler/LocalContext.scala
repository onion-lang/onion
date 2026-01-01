/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import toolbox.SymbolGenerator

/**
 * @author Kota Mizushima
 *
 */
class LocalContext {
  private var contextFrame = new LocalFrame(null)
  private val generator = new SymbolGenerator("symbol#")
  private val boxedVariables = scala.collection.mutable.Set[String]()
  var isClosure: Boolean                     = false
  var isStatic: Boolean                      = false
  var isGlobal: Boolean                      = false
  var method: TypedAST.Method                     = _
  var constructor: TypedAST.ConstructorDefinition = _
  private var isMethod: Boolean = false

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

  def lookup(name: String): ClosureLocalBinding = {
    contextFrame.lookup(name)
  }

  def lookupOnlyCurrentScope(name: String): ClosureLocalBinding = {
    contextFrame.lookupOnlyCurrentScope(name)
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

}
