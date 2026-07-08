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
  var reportingSuppressed: Int,
  // When > 0, generic type-argument BOUND checks encountered during type-node
  // resolution are recorded in `deferredBoundChecks` instead of run eagerly.
  // Used while a class/interface resolves its OWN supertypes: a self/forward
  // F-bound like `class Sub : Base[Sub]` (Base[T extends Base[T]]) can only be
  // validated once Sub's supertype chain is established, so the check is
  // deferred and flushed afterwards. Other diagnostics (CLASS_NOT_FOUND, raw
  // types, ...) still report eagerly. See TypingTypeSupport.validateTypeApplication.
  var deferringBoundChecks: Int,
  val deferredBoundChecks: scala.collection.mutable.Buffer[() => Unit]
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
      reportingSuppressed = 0,
      deferringBoundChecks = 0,
      deferredBoundChecks = scala.collection.mutable.Buffer.empty
    )
}
