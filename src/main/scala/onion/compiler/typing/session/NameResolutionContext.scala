package onion.compiler.typing.session

import onion.compiler.*
import onion.compiler.typing.{TypeAliasEntry, TypeParamScope}

import scala.collection.mutable

final case class NameResolutionContext(
  table: ClassTable,
  imports: Seq[ImportItem],
  unitProvider: () => AST.CompilationUnit,
  typeParamsProvider: () => TypeParamScope,
  typeParamsUpdater: TypeParamScope => Unit,
  typeAliases: mutable.Map[String, TypeAliasEntry],
  typeAliasResolutionStack: mutable.Set[String],
  rootClassProvider: () => TypedAST.ClassType,
  splitDescriptor: AST.TypeDescriptor => (AST.TypeDescriptor, Int),
  createFQCN: (String, String) => String,
  report: (SemanticError, AST.Node, Seq[AnyRef]) => Unit
) {
  def currentUnit: AST.CompilationUnit = unitProvider()
  def currentTypeParams: TypeParamScope = typeParamsProvider()
  def updateTypeParams(scope: TypeParamScope): Unit = typeParamsUpdater(scope)
  def rootClass: TypedAST.ClassType = rootClassProvider()
}

object NameResolutionContext {
  def fromTyping(typing: Typing, imports: Seq[ImportItem]): NameResolutionContext =
    NameResolutionContext(
      table = typing.table_,
      imports = imports,
      unitProvider = () => typing.unit_,
      typeParamsProvider = () => typing.typeParams_,
      typeParamsUpdater = typing.setTypeParams,
      typeAliases = typing.typeAliases_,
      typeAliasResolutionStack = typing.typeAliasResolutionStack_,
      rootClassProvider = () => typing.rootClass,
      splitDescriptor = typing.split,
      createFQCN = typing.createFQCN,
      report = (error, node, items) => typing.report(error, node, items*)
    )
}
