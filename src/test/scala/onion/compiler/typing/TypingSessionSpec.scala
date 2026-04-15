package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST
import onion.compiler.TypedAST.*
import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

class TypingSessionSpec extends AnyFunSpec with Diagrams {
  private val config = CompilerConfig(Seq("."), "", "UTF-8", "", 10)
  private val location = Location(1, 1)

  private def unit(name: String): AST.CompilationUnit =
    AST.CompilationUnit(
      location = location,
      sourceFile = s"$name.on",
      module = null,
      imports = AST.ImportClause(location, Nil),
      toplevels = Nil
    )

  private def typeNode(name: String): AST.TypeNode =
    AST.TypeNode(location, AST.ReferenceType(name, qualified = true), isRelaxed = false)

  describe("Typing session") {
    it("resets unit-local state while preserving unit-specific static imports") {
      val typing = new Typing(config)
      val unit1 = unit("One")
      val unit2 = unit("Two")

      typing.session.activate(unit1)
      val staticImports = new StaticImportList
      staticImports.add(new StaticImportItem("java.lang.Math", true))
      typing.setStaticImportedList(staticImports)
      typing.setAccess(42)
      val typeParam = TypeParam("T", new TypedAST.TypeVariableType("T", typing.rootClass), typing.rootClass)
      typing.setTypeParams(typing.emptyTypeParams ++ Seq(typeParam))

      typing.withSuppressedReporting {
        assert(!typing.reportingEnabled)
      }
      assert(typing.reportingEnabled)

      typing.session.activate(unit2)
      assert(typing.unit_ eq unit2)
      assert(typing.definition_ == null)
      assert(typing.mapper_ == null)
      assert(typing.access_ == 0)
      assert(typing.typeParams_ == typing.emptyTypeParams)
      assert(typing.staticImportedList_.getItems.isEmpty)
      assert(typing.reportingEnabled)

      typing.session.activate(unit1)
      assert(typing.unit_ eq unit1)
      assert(typing.staticImportedList_ eq staticImports)
      assert(typing.access_ == 0)
      assert(typing.typeParams_ == typing.emptyTypeParams)
      assert(typing.reportingEnabled)
    }

    it("keeps global type alias and extension registries visible across unit switches") {
      val typing = new Typing(config)
      val unit1 = unit("One")
      val unit2 = unit("Two")

      val aliasDecl = AST.TypeAliasDeclaration(location, 0, "Alias", Nil, typeNode("java.lang.String"))
      val aliasEntry = TypeAliasEntry(
        fqcn = "example.Alias",
        typeParameters = Seq.empty,
        targetDescriptor = aliasDecl.targetType.desc,
        node = aliasDecl,
        imports = Seq.empty
      )

      val container = ClassDefinition.newClass(location, Modifier.PUBLIC, "example.ExtensionContainer", typing.rootClass, Array.empty[ClassType])
      val extensionMethod = new TypedAST.ExtensionMethodDefinition(
        location = location,
        modifier = Modifier.PUBLIC | Modifier.STATIC,
        receiverType = typing.rootClass,
        containerClass = container,
        name = "ext",
        arguments = Array.empty,
        returnType = typing.rootClass,
        block = null
      )

      typing.session.activate(unit1)
      typing.typeAliases_("example.Alias") = aliasEntry
      typing.registerExtensionMethod("java.lang.String", extensionMethod)

      typing.session.activate(unit2)
      assert(typing.typeAliases_.get("example.Alias").contains(aliasEntry))
      assert(typing.lookupExtensionMethods("java.lang.String") == Seq(extensionMethod))
    }
  }
}
