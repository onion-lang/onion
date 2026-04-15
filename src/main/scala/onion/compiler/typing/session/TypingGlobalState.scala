package onion.compiler.typing.session

import onion.compiler.{AST, ClassTable, SemanticErrorReporter, WarningReporter}
import onion.compiler.typing.{NameResolver, TypeParam}

import scala.collection.mutable.Map

final case class TypingGlobalState(
  table: ClassTable,
  bindings: AstBindingIndex,
  mappers: Map[String, NameResolver],
  declaredTypeParams: Map[AST.Node, Seq[TypeParam]],
  typeAliases: TypeAliasRegistry,
  extensions: ExtensionRegistry,
  diagnostics: SemanticErrorReporter,
  warnings: WarningReporter
)
