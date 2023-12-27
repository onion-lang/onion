package onion.compiler

import java.util.{TreeSet => JTreeSet}

import _root_.onion.compiler.IRT.BinaryTerm.Constants._
import _root_.onion.compiler.IRT.UnaryTerm.Constants._
import _root_.onion.compiler.IRT._
import _root_.onion.compiler.SemanticError._
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.toolbox.{Boxing, Classes, Paths, Systems}
import onion.compiler.AST.{ClassDeclaration, InterfaceDeclaration, RecordDeclaration}

import _root_.scala.collection.JavaConverters._
import scala.collection.mutable.{Buffer, Map, Set => MutableSet}

class Rewriting(config: CompilerConfig) extends AnyRef with Processor[Seq[AST.CompilationUnit], Seq[AST.CompilationUnit]] {

  class TypingEnvironment

  type Environment = TypingEnvironment

  def newEnvironment(source: Seq[AST.CompilationUnit]) = new TypingEnvironment

  def processBody(source: Seq[AST.CompilationUnit], environment: TypingEnvironment): Seq[AST.CompilationUnit] = {
    val rewritten = Buffer.empty[AST.CompilationUnit]
    for (unit <- source) {
      rewritten += rewrite(unit)
    }
    rewritten.toSeq
  }

  def rewrite(unit: AST.CompilationUnit): AST.CompilationUnit = {
    val newToplevels = Buffer.empty[AST.Toplevel]
    for (top <- unit.toplevels) top match {
      case declaration: AST.ClassDeclaration =>
        newToplevels += rewriteClassDeclaration(declaration)
      case declaration: AST.InterfaceDeclaration =>
        newToplevels += rewriteInterfaceDeclaration(declaration)
      case declaration: AST.RecordDeclaration =>
        newToplevels += rewriteRecordDeclaration(declaration)
      case otherwise =>
        newToplevels += otherwise
    }
    unit.copy(toplevels = newToplevels.toList)
  }

  def rewriteClassDeclaration(declaration: ClassDeclaration): ClassDeclaration = {
    declaration
  }

  def rewriteInterfaceDeclaration(declaration: AST.InterfaceDeclaration): InterfaceDeclaration = {
    declaration
  }

  def rewriteRecordDeclaration(declaration: AST.RecordDeclaration): RecordDeclaration = {
    declaration
  }
}
