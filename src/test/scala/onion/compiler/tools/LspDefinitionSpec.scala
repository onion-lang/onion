package onion.compiler.tools

import onion.tools.lsp.{OnionTextDocumentService, OnionLanguageServer, SymbolTable, SymbolDefinition}
import org.eclipse.lsp4j._
import org.scalatest.funspec.AnyFunSpec

/**
 * Tests for LSP go-to-definition functionality.
 */
class LspDefinitionSpec extends AnyFunSpec {

  describe("SymbolTable") {

    it("stores and retrieves class definitions") {
      val table = new SymbolTable()
      val symbol = SymbolDefinition("MyClass", SymbolKind.Class, "file:///test.on", 5, 6, 13)
      table.add(symbol)

      val results = table.lookup("MyClass")
      assert(results.size == 1)
      assert(results.head.name == "MyClass")
      assert(results.head.kind == SymbolKind.Class)
      assert(results.head.line == 5)
    }

    it("stores and retrieves method definitions") {
      val table = new SymbolTable()
      val symbol = SymbolDefinition("calculate", SymbolKind.Method, "file:///test.on", 10, 4, 13)
      table.add(symbol)

      val results = table.lookup("calculate")
      assert(results.size == 1)
      assert(results.head.name == "calculate")
      assert(results.head.kind == SymbolKind.Method)
    }

    it("handles multiple definitions with same name") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("process", SymbolKind.Method, "file:///a.on", 5, 4, 11))
      table.add(SymbolDefinition("process", SymbolKind.Method, "file:///b.on", 10, 4, 11))

      val results = table.lookup("process")
      assert(results.size == 2)
    }

    it("filters by URI") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("Helper", SymbolKind.Class, "file:///a.on", 5, 6, 12))
      table.add(SymbolDefinition("Helper", SymbolKind.Class, "file:///b.on", 10, 6, 12))

      val resultsA = table.lookupInUri("Helper", "file:///a.on")
      assert(resultsA.size == 1)
      assert(resultsA.head.line == 5)

      val resultsB = table.lookupInUri("Helper", "file:///b.on")
      assert(resultsB.size == 1)
      assert(resultsB.head.line == 10)
    }

    it("clears symbols for a specific URI") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("ClassA", SymbolKind.Class, "file:///a.on", 5, 6, 12))
      table.add(SymbolDefinition("ClassB", SymbolKind.Class, "file:///b.on", 10, 6, 12))

      table.clear("file:///a.on")

      assert(table.lookup("ClassA").isEmpty)
      assert(table.lookup("ClassB").size == 1)
    }

    it("converts to Location correctly") {
      val symbol = SymbolDefinition("Test", SymbolKind.Class, "file:///test.on", 5, 6, 10)
      val location = symbol.toLocation

      assert(location.getUri == "file:///test.on")
      assert(location.getRange.getStart.getLine == 5)
      assert(location.getRange.getStart.getCharacter == 6)
      assert(location.getRange.getEnd.getLine == 5)
      assert(location.getRange.getEnd.getCharacter == 10)
    }

    it("returns empty for unknown symbols") {
      val table = new SymbolTable()
      val results = table.lookup("NonExistent")
      assert(results.isEmpty)
    }

    it("stores variable definitions") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("counter", SymbolKind.Variable, "file:///test.on", 15, 8, 15))

      val results = table.lookup("counter")
      assert(results.size == 1)
      assert(results.head.kind == SymbolKind.Variable)
    }

    it("stores field definitions") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("name", SymbolKind.Field, "file:///test.on", 3, 6, 10))

      val results = table.lookup("name")
      assert(results.size == 1)
      assert(results.head.kind == SymbolKind.Field)
    }

    it("stores enum definitions") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("Color", SymbolKind.Enum, "file:///test.on", 1, 5, 10))

      val results = table.lookup("Color")
      assert(results.size == 1)
      assert(results.head.kind == SymbolKind.Enum)
    }

    it("stores interface definitions") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("Drawable", SymbolKind.Interface, "file:///test.on", 1, 10, 18))

      val results = table.lookup("Drawable")
      assert(results.size == 1)
      assert(results.head.kind == SymbolKind.Interface)
    }

    it("lists all symbols") {
      val table = new SymbolTable()
      table.add(SymbolDefinition("ClassA", SymbolKind.Class, "file:///a.on", 1, 6, 12))
      table.add(SymbolDefinition("methodB", SymbolKind.Method, "file:///a.on", 5, 4, 11))
      table.add(SymbolDefinition("ClassC", SymbolKind.Class, "file:///b.on", 1, 6, 12))

      val all = table.allSymbols
      assert(all.size == 3)
    }
  }
}
