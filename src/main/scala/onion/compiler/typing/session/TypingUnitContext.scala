package onion.compiler.typing.session

import onion.compiler.{AST, StaticImportList}
import onion.compiler.TypedAST.ClassDefinition
import onion.compiler.typing.{NameResolver, TypeParamScope}

final class TypingUnitContext(
  val unit: AST.CompilationUnit,
  var currentDefinition: ClassDefinition,
  var currentMapper: NameResolver,
  var currentAccess: Int,
  var currentTypeParams: TypeParamScope,
  var staticImports: StaticImportList,
  var reportingSuppressed: Int
)

object TypingUnitContext {
  def initial(unit: AST.CompilationUnit, emptyTypeParams: TypeParamScope): TypingUnitContext =
    new TypingUnitContext(
      unit = unit,
      currentDefinition = null,
      currentMapper = null,
      currentAccess = 0,
      currentTypeParams = emptyTypeParams,
      staticImports = new StaticImportList,
      reportingSuppressed = 0
    )
}
