/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.toolbox.SymbolGenerator
import toolbox.SymbolGenerator

/**
 * @author Kota Mizushima
 *
 */
class LocalContext {
  private var contextFrame = new LocalFrame(null)
  private val generator = new SymbolGenerator("symbol#")
  var isClosure: Boolean = false
  var isStatic: Boolean = false
  var isGlobal: Boolean = false
  var method: IRT.MethodRef = null
  var constructor: IRT.ConstructorDefinition = null
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

  def returnType: IRT.TypeRef = {
    if (isMethod) {
      method.returnType
    } else {
      IRT.BasicTypeRef.VOID
    }
  }

  def setMethod(method: IRT.MethodRef): Unit = {
    this.method = method
    this.isMethod = true
  }

  def setConstructor(constructor: IRT.ConstructorDefinition): Unit = {
    this.constructor = constructor
    this.isMethod = false
  }

  def openFrame(): Unit = {
    contextFrame = new LocalFrame(contextFrame)
  }

  def closeFrame(): Unit = {
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

  def openScope[A](body: => A): A = try {
    contextFrame.openScope()
    body
  } finally {
    contextFrame.closeScope()
  }

  def open[A](block: => A): A = contextFrame.open(block)

  def lookup(name: String): ClosureLocalBinding = {
    contextFrame.lookup(name)
  }

  def lookupOnlyCurrentScope(name: String): ClosureLocalBinding = {
    contextFrame.lookupOnlyCurrentScope(name)
  }

  def add(name: String, `type` : IRT.TypeRef): Int = {
    contextFrame.add(name, `type`)
  }

  def add(`type` : IRT.TypeRef): String = {
    var name: String = newName
    contextFrame.add(name, `type`)
    name
  }

}