package onion.compiler;
/* ************************************************************** *
* *
* Copyright (c) 2016-, Kota Mizushima, All rights reserved. *
* *
* *
* This software is distributed under the modified BSD License. *
* ************************************************************** */

/**
* @author Kota Mizushima
*
*/
object Modifier {

  final val INTERNAL = 1
  final val SYNCHRONIZED = 2
  final val FINAL = 4
  final val ABSTRACT = 8
  final val VOLATILE = 16
  final val STATIC = 32
  final val INHERITED = 64
  final val PUBLIC = 128
  final val PROTECTED = 256
  final val PRIVATE = 512
  final val FORWARDED = 1024
  final val SEALED = 2048
  final val ENUM = 4096

  def check(modifier: Int, bitFlag: Int): Boolean = {
     (modifier & bitFlag) != 0
  }

  def isInternal(modifier: Int): Boolean = {
     check(modifier, INTERNAL)
  }

  def isStatic(modifier: Int): Boolean = {
     check(modifier, STATIC)
  }

  def isSynchronized(modifier: Int): Boolean = {
     check(modifier, SYNCHRONIZED)
  }

  def isFinal(modifier: Int): Boolean = {
     check(modifier, FINAL)
  }

  def isAbstract(modifier: Int): Boolean = {
     check(modifier, ABSTRACT)
  }

  def isVolatile(modifier: Int): Boolean = {
     check(modifier, VOLATILE)
  }

  def isInherited(modifier: Int): Boolean = {
     check(modifier, INHERITED)
  }

  def isPublic(modifier: Int): Boolean = {
     check(modifier, PUBLIC)
  }

  def isProtected(modifier: Int): Boolean = {
     check(modifier, PROTECTED)
  }

  def isPrivate(modifier: Int): Boolean = {
     check(modifier, PRIVATE)
  }

  def isForwarded(modifier: Int): Boolean = {
     check(modifier, FORWARDED)
  }

  def isSealed(modifier: Int): Boolean = {
     check(modifier, SEALED)
  }

  def isEnum(modifier: Int): Boolean = {
     check(modifier, ENUM)
  }

}
