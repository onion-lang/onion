/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.util.SymbolGenerator
import util.SymbolGenerator

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
class LocalContext {
  private var contextFrame = new LocalFrame(null)
  private var generator = new SymbolGenerator("symbol#")
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
    return generator.generate
  }

  def returnType: IRT.TypeRef = {
    if (isMethod) {
      return method.returnType
    }
    else {
      return IRT.BasicTypeRef.VOID
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
      return -1
    }
    else {
      return contextFrame.depth
    }
  }

  def getContextFrame: LocalFrame = {
    return contextFrame
  }

  def setContextFrame(frame: LocalFrame): Unit = {
    this.contextFrame = frame
  }

  def openScope(): Unit = {
    contextFrame.openScope
  }

  def closeScope(): Unit = {
    contextFrame.closeScope
  }

  def lookup(name: String): ClosureLocalBinding = {
    return contextFrame.lookup(name)
  }

  def lookupOnlyCurrentScope(name: String): ClosureLocalBinding = {
    return contextFrame.lookupOnlyCurrentScope(name)
  }

  def add(name: String, `type` : IRT.TypeRef): Int = {
    return contextFrame.add(name, `type`)
  }

  def add(`type` : IRT.TypeRef): String = {
    var name: String = newName
    contextFrame.add(name, `type`)
    return name
  }

}