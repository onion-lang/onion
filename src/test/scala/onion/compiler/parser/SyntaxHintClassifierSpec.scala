package onion.compiler.parser

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SyntaxHintClassifierSpec extends AnyFunSpec with Matchers {
  describe("SyntaxHintClassifier") {
    it("prefers extension-declaration advice over primitive static-call advice") {
      val hint = classify(
        found = "Int",
        context = "Int.doubled(): Int {",
        sourceLine = "fun Int.doubled(): Int {"
      )

      hint.messageKey shouldBe "error.parsing.hint.fun_extension_declaration"
      hint.arguments shouldBe Seq("Int", "doubled")
    }

    it("prefers foreach-parentheses advice over generic old-for-in advice") {
      val hint = classify(
        found = "in",
        expected = "\",\"",
        sourceLine = "foreach (item in items) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.foreach_parens"
      hint.arguments shouldBe empty
    }

    it("keeps generic old-for-in advice ahead of the control-flow family") {
      val hint = classify(
        found = "in",
        sourceLine = "switch item in items {"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_for_in"
    }

    it("prefers destructuring-foreach advice over generic C-style-for advice") {
      val hint = classify(
        found = ",",
        sourceLine = "for (key, value) in entries {"
      )

      hint.messageKey shouldBe "error.parsing.hint.for_each_destructure"
      hint.arguments shouldBe Seq("key, value", "entries")
    }

    it("strips the outer paren when the whole `for (vars in coll)` clause is wrapped") {
      val hint = classify(
        found = ",",
        sourceLine = "for (key, value in entries) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.for_each_destructure"
      hint.arguments shouldBe Seq("key, value", "entries")
    }

    it("keeps a call's own parens when the collection is a function call") {
      val hint = classify(
        found = ",",
        sourceLine = "for (key, value) in getEntries() {"
      )

      hint.messageKey shouldBe "error.parsing.hint.for_each_destructure"
      hint.arguments shouldBe Seq("key, value", "getEntries()")
    }

    it("keeps a call's own parens when the whole wrapped clause's collection is a call") {
      val hint = classify(
        found = ",",
        sourceLine = "for (key, value in getEntries()) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.for_each_destructure"
      hint.arguments shouldBe Seq("key, value", "getEntries()")
    }

    it("prefers enhanced-for advice over generic C-style-for advice") {
      val hint = classify(
        found = "s",
        sourceLine = "for (String s : list) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_enhanced_for"
      hint.arguments shouldBe Seq("s", "String", "list")
    }

    it("recognizes an enhanced-for loop over a qualified/generic element type") {
      val hint = classify(
        found = "entry",
        sourceLine = "for (Map.Entry[String, Int] entry : map.entrySet()) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_enhanced_for"
      hint.arguments shouldBe Seq("entry", "Map.Entry[String, Int]", "map.entrySet()")
    }

    it("captures Java-style import alias arguments in Onion order") {
      val hint = classify(
        found = "=",
        expected = "\".\"",
        sourceLine = "Alias = java.lang.String;"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_import_alias"
      hint.arguments shouldBe Seq("java.lang.String", "Alias")
    }

    it("distinguishes Java-style methods from the nearby constructor fallback") {
      val cases = Seq(
        ("Widget build(", "error.parsing.hint.java_style_method"),
        ("Widget(", "error.parsing.hint.java_style_constructor")
      )

      cases.foreach { case (sourceLine, expectedMessageKey) =>
        val hint = classify(
          found = "(",
          expected = "\"def\"",
          sourceLine = sourceLine
        )

        hint.messageKey shouldBe expectedMessageKey
        hint.arguments shouldBe empty
      }
    }

    it("recognizes an old arrow in a parenthesized trailing lambda") {
      val hint = classify(
        found = "{",
        context = "{ (item) =>"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_trailing_arrow"
      hint.arguments shouldBe empty
    }

    it("recognizes an old arrow in a standalone (non-trailing) lambda") {
      val hint = classify(
        found = "=",
        expected = "<EOF>, <EOL>, \";\"",
        context = "=> x * 2",
        sourceLine = "val f = (x) => x * 2"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_lambda_arrow"
      hint.arguments shouldBe empty
    }

    it("recognizes an old arrow in a bare-parameter standalone lambda") {
      val hint = classify(
        found = "=",
        expected = "<EOF>, <EOL>, \";\"",
        context = "=> x * 2",
        sourceLine = "val f = x => x * 2"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_lambda_arrow"
    }

    it("prefers the Ruby-style map-arrow reading when a `:` is expected next") {
      SyntaxHintClassifier.classify(
        found = "=",
        expected = "<EOL>, \":\", \",\", \"]\"",
        context = "=> 1]",
        sourceLine = "val m = [\"a\" => 1]"
      ).map(_.messageKey) should not be Some("error.parsing.hint.old_lambda_arrow")
    }

    it("keeps the reserved-word and missing-block fallbacks") {
      val reserved = classify(found = "class", expected = "<ID>")
      val block = classify(found = ")", expected = "\"{\"")

      reserved.messageKey shouldBe "error.parsing.hint.reserved_word_identifier"
      reserved.arguments shouldBe Seq("class")
      block.messageKey shouldBe "error.parsing.hint.block_expected"
      block.arguments shouldBe empty
    }

    it("recognizes a C-style cast") {
      val hint = classify(
        found = "o",
        expected = "<EOL>",
        sourceLine = "    val s: String = (String) o"
      )

      hint.messageKey shouldBe "error.parsing.hint.c_style_cast"
      hint.arguments shouldBe Seq("String")
    }

    it("recognizes a C-style cast of a generic type") {
      val hint = classify(
        found = "coll",
        expected = "<EOL>",
        sourceLine = "val xs = (List[String]) coll"
      )

      hint.messageKey shouldBe "error.parsing.hint.c_style_cast"
      hint.arguments shouldBe Seq("List[String]")
    }

    it("recognizes a Java-style `instanceof` type check") {
      val hint = classify(
        found = "instanceof",
        expected = "\"{\"",
        sourceLine = "    if x instanceof String {"
      )

      hint.messageKey shouldBe "error.parsing.hint.instanceof_not_supported"
      hint.arguments shouldBe Seq("x", "String")
    }

    it("recognizes an `instanceof` check on a method call result") {
      val hint = classify(
        found = "instanceof",
        expected = "\"{\"",
        sourceLine = "    if get() instanceof Foo {"
      )

      hint.messageKey shouldBe "error.parsing.hint.instanceof_not_supported"
      hint.arguments shouldBe Seq("get()", "Foo")
    }

    it("does not flag `instanceof` used as an ordinary identifier") {
      SyntaxHintClassifier.classify(
        found = "instanceof",
        expected = "\"=\"",
        context = "",
        sourceLine = "val instanceof: Int"
      ).map(_.messageKey) should not be Some("error.parsing.hint.instanceof_not_supported")
    }

    it("recognizes a Python/Rust/TypeScript-style `-> Type` return type arrow") {
      val hint = classify(
        found = "->",
        expected = "\"=\"",
        sourceLine = "  def bar(x: Int) -> Int {"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_return_arrow"
      hint.arguments shouldBe Seq("bar", "Int")
    }

    it("recognizes a Python-style `def name():` with no return type before the block") {
      val hint = classify(
        found = "\n",
        expected = "\"Boolean\", \"Byte\", \"Char\", \"Double\", ... (11 more)",
        sourceLine = "  def foo():"
      )

      hint.messageKey shouldBe "error.parsing.hint.dangling_return_type_colon"
      hint.arguments shouldBe Seq("foo")
    }

    it("does not flag a method colon that already has a same-line return type") {
      SyntaxHintClassifier.classify(
        found = "\n",
        expected = "\"Boolean\", \"Byte\", \"Char\", \"Double\", ... (11 more)",
        context = "",
        sourceLine = "  def foo(): Int {"
      ) shouldBe None
    }

    it("does not confuse the real `(expr as Type)` cast with a C-style cast") {
      SyntaxHintClassifier.classify(
        found = "as",
        expected = "\")\"",
        context = "",
        sourceLine = "val s: String = (o as String)"
      ) shouldBe None
    }

    it("recognizes a parameter missing its type annotation, not a Java-style method") {
      val hint = classify(
        found = ")",
        expected = "\":\"",
        sourceLine = "def foo(x): Int {"
      )

      hint.messageKey shouldBe "error.parsing.hint.missing_parameter_type"
      hint.arguments shouldBe Seq("x")
    }

    it("does not confuse a fully-typed parameter list with a missing-type parameter") {
      SyntaxHintClassifier.classify(
        found = ":",
        expected = "\"def\"",
        context = "",
        sourceLine = "  void foo(x: Int) {"
      ).map(_.messageKey) shouldBe Some("error.parsing.hint.java_style_method")
    }

    it("recognizes a C#/Python-style `using` resource statement") {
      val hint = classify(
        found = "r",
        sourceLine = "using r = new Res() {"
      )

      hint.messageKey shouldBe "error.parsing.hint.using_resource_statement"
      hint.arguments shouldBe Seq("r", "new Res()")
    }

    it("recognizes a C#-style `using` namespace-import directive") {
      val hint = classify(
        found = "System",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "using System.Collections.Generic;"
      )

      hint.messageKey shouldBe "error.parsing.hint.csharp_style_using_import"
      hint.arguments shouldBe Seq("System.Collections.Generic")
    }

    it("does not confuse a C#-style resource `using` statement with the import-directive form") {
      classify(
        found = "r",
        sourceLine = "using r = new Res() {"
      ).messageKey shouldBe "error.parsing.hint.using_resource_statement"
    }

    it("recognizes a Java/Kotlin/C#-style dotted `package` declaration") {
      val hint = classify(
        found = "com",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "package com.example.app;"
      )

      hint.messageKey shouldBe "error.parsing.hint.package_declaration"
      hint.arguments shouldBe Seq("com.example.app")
    }

    it("recognizes a Go-style bare `package main` declaration") {
      val hint = classify(
        found = "main",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "package main"
      )

      hint.messageKey shouldBe "error.parsing.hint.package_declaration"
      hint.arguments shouldBe Seq("main")
    }

    it("recognizes a Python-style `from ... import ...` statement") {
      val hint = classify(
        found = "import",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "from os import path"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_from_import"
      hint.arguments shouldBe Seq("os.path", "os", "path")
    }

    it("recognizes a Python-style `from ... import ...` statement with a wildcard") {
      val hint = classify(
        found = "import",
        sourceLine = "from os.path import *"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_from_import"
      hint.arguments shouldBe Seq("os.path.*", "os.path", "*")
    }

    it("recognizes a Python-style `from ... import ...` statement with multiple names") {
      val hint = classify(
        found = "import",
        sourceLine = "from os.path import join, exists"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_from_import"
      hint.arguments shouldBe Seq("os.path.join; os.path.exists", "os.path", "join, exists")
    }

    it("recognizes a Go-style `:=` short variable declaration") {
      val hint = classify(
        found = ":",
        expected = "<EOL>, \";\", <EOF>",
        sourceLine = "x := 5"
      )

      hint.messageKey shouldBe "error.parsing.hint.go_style_short_var_decl"
      hint.arguments shouldBe Seq("x", "5")
    }

    it("recognizes a Rust-style `val mut` declaration") {
      val hint = classify(
        found = "x",
        expected = "\"=\", \":\"",
        sourceLine = "val mut x = 5"
      )

      hint.messageKey shouldBe "error.parsing.hint.rust_style_mut"
      hint.arguments shouldBe Seq("val", "x")
    }

    it("recognizes a Rust-style `var mut` declaration") {
      val hint = classify(
        found = "count",
        expected = "\"=\", \":\"",
        sourceLine = "var mut count = 0"
      )

      hint.messageKey shouldBe "error.parsing.hint.rust_style_mut"
      hint.arguments shouldBe Seq("var", "count")
    }

    it("recognizes a Ruby-style `unless` statement") {
      val hint = classify(
        found = "unless",
        expected = "<ID>",
        sourceLine = "unless done {"
      )

      hint.messageKey shouldBe "error.parsing.hint.unless_not_supported"
      hint.arguments shouldBe empty
    }

    it("recognizes a Ruby-style `until` loop") {
      val hint = classify(
        found = "until",
        expected = "<ID>",
        sourceLine = "until done {"
      )

      hint.messageKey shouldBe "error.parsing.hint.until_not_supported"
      hint.arguments shouldBe empty
    }

    it("recognizes a Ruby-style `elsif` clause") {
      val hint = classify(
        found = "x",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "} elsif x == 2 {"
      )

      hint.messageKey shouldBe "error.parsing.hint.elsif_not_supported"
      hint.arguments shouldBe empty
    }

    it("recognizes a PHP-style `elseif` clause") {
      val hint = classify(
        found = "x",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "} elseif x == 2 {"
      )

      hint.messageKey shouldBe "error.parsing.hint.elseif_not_supported"
      hint.arguments shouldBe empty
    }

    it("keeps unsupported control-flow advice ahead of the generic block fallback") {
      val hint = classify(
        found = ")",
        expected = "\"{\"",
        sourceLine = "switch (value) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.switch_not_supported"
    }

    it("recognizes a Python-style `not` boolean negation in an `if` condition") {
      val hint = classify(
        found = "done",
        expected = "\"{\"",
        sourceLine = "if not done {"
      )

      hint.messageKey shouldBe "error.parsing.hint.not_operator"
      hint.arguments shouldBe empty
    }

    it("recognizes a Python-style `not` boolean negation in a `while` condition") {
      val hint = classify(
        found = "empty",
        expected = "\"{\"",
        sourceLine = "while not empty {"
      )

      hint.messageKey shouldBe "error.parsing.hint.not_operator"
      hint.arguments shouldBe empty
    }

    it("recognizes a Python-style `not` boolean negation in an `else if` condition") {
      val hint = classify(
        found = "ready",
        expected = "\"{\"",
        sourceLine = "else if not ready {"
      )

      hint.messageKey shouldBe "error.parsing.hint.not_operator"
      hint.arguments shouldBe empty
    }

    it("recognizes a TypeScript-style optional field `name?: Type`") {
      val hint = classify(
        found = "?:",
        expected = "\":\"",
        sourceLine = "  var name?: String"
      )
      hint.messageKey shouldBe "error.parsing.hint.typescript_style_optional_annotation"
      hint.arguments shouldBe Seq("name", "String")
    }

    it("recognizes a TypeScript-style optional parameter `name?: Type`") {
      val hint = classify(
        found = "?:",
        expected = "\":\"",
        sourceLine = "  def bar(count?: Int): Int {"
      )
      hint.messageKey shouldBe "error.parsing.hint.typescript_style_optional_annotation"
      hint.arguments shouldBe Seq("count", "Int")
    }

    it("does not flag a real Elvis expression that happens to precede a colon-expecting context") {
      SyntaxHintClassifier.classify(
        found = "?:",
        expected = "\",\"",
        context = "",
        sourceLine = "  val x = a ?: b"
      ).map(_.messageKey) should not be Some("error.parsing.hint.typescript_style_optional_annotation")
    }

    it("recognizes a JS/TS/Kotlin-style `??` nullish-coalescing operator") {
      val hint = classify(
        found = "?",
        expected = "<EOF>, <EOL>, \";\"",
        context = "?? 5",
        sourceLine = "val b: Int = a ?? 5"
      )
      hint.messageKey shouldBe "error.parsing.hint.nullish_coalescing"
    }

    it("keeps the plain ternary hint for a lone `?` that isn't `??`") {
      val hint = classify(
        found = "?",
        context = "? 1 : 2",
        sourceLine = "val y = (x > 3) ? 1 : 0"
      )
      hint.messageKey shouldBe "error.parsing.hint.ternary"
    }

    it("does not flag `not` when no block is expected next") {
      SyntaxHintClassifier.classify(
        found = "x",
        expected = "\";\"",
        context = "",
        sourceLine = "if not x"
      ) shouldBe None
    }

    it("recognizes a JS/Python-style `typeof` operator in an `if` condition") {
      val hint = classify(
        found = "x",
        expected = "\"{\"",
        sourceLine = "if typeof x == \"Int\" {"
      )

      hint.messageKey shouldBe "error.parsing.hint.typeof_not_supported"
      hint.arguments shouldBe Seq("x")
    }

    it("recognizes a JS/Python-style `typeof` operator in a `while` condition") {
      val hint = classify(
        found = "y",
        expected = "\"{\"",
        sourceLine = "while typeof y == \"String\" {"
      )

      hint.messageKey shouldBe "error.parsing.hint.typeof_not_supported"
      hint.arguments shouldBe Seq("y")
    }

    it("recognizes a JS/Python-style `typeof` operator in an `else if` condition") {
      val hint = classify(
        found = "z",
        expected = "\"{\"",
        sourceLine = "else if typeof z == \"Boolean\" {"
      )

      hint.messageKey shouldBe "error.parsing.hint.typeof_not_supported"
      hint.arguments shouldBe Seq("z")
    }

    it("does not flag `typeof` when no block is expected next") {
      SyntaxHintClassifier.classify(
        found = "x",
        expected = "\";\"",
        context = "",
        sourceLine = "if typeof x"
      ) shouldBe None
    }

    it("recognizes a Python-style `if cond:` header with a trailing colon") {
      val hint = classify(
        found = ":",
        expected = "\"{\"",
        sourceLine = "    if args.length > 0:"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_colon_block"
      hint.arguments shouldBe Seq("if", "args.length > 0")
    }

    it("recognizes a Python-style `while cond:` header with a trailing colon") {
      val hint = classify(
        found = ":",
        expected = "\"{\"",
        sourceLine = "while x > 0:"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_colon_block"
      hint.arguments shouldBe Seq("while", "x > 0")
    }

    it("recognizes a Python-style `else if cond:` header with a trailing colon") {
      val hint = classify(
        found = ":",
        expected = "\"{\"",
        sourceLine = "else if x > 0:"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_colon_block"
      hint.arguments shouldBe Seq("else if", "x > 0")
    }

    it("prefers the `not`-operator hint over the colon-block hint when both apply") {
      val hint = classify(
        found = ":",
        expected = "\"{\"",
        sourceLine = "if not done:"
      )

      hint.messageKey shouldBe "error.parsing.hint.not_operator"
    }

    it("keeps the colon-block hint out of a case type pattern") {
      SyntaxHintClassifier.classify(
        found = ":",
        expected = "\"{\"",
        context = "",
        sourceLine = "case s: String:"
      ).map(_.messageKey) should not be Some("error.parsing.hint.python_style_colon_block")
    }

    it("recognizes a Python-style `class Foo:` header with a trailing colon") {
      val hint = classify(
        found = ":",
        expected = "<EOF>, \"extends\", \";\", \"{\"",
        sourceLine = "class Foo:"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_colon_block"
      hint.arguments shouldBe Seq("class", "Foo")
    }

    it("recognizes a Python-style `class Foo(x: Int):` header with a primary constructor") {
      val hint = classify(
        found = ":",
        expected = "<EOF>, \"extends\", \";\", \"{\"",
        sourceLine = "class Foo(x: Int):"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_colon_block"
      hint.arguments shouldBe Seq("class", "Foo(x: Int)")
    }

    it("keeps the old-extends hint for a real `class Foo: Bar` superclass mistake") {
      val hint = classify(
        found = ":",
        expected = "<EOF>, \"extends\", \";\", \"{\"",
        sourceLine = "class Foo: Bar {"
      )

      hint.messageKey shouldBe "error.parsing.hint.old_extends"
    }

    it("recognizes a Python-style `lambda x: expr` expression") {
      val hint = classify(
        found = "x",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "val f = lambda x: x + 1"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_lambda"
      hint.arguments shouldBe Seq("x", "x + 1")
    }

    it("recognizes a Python-style multi-parameter `lambda x, y: expr` expression") {
      val hint = classify(
        found = "x",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "val f = lambda x, y: x + y"
      )

      hint.messageKey shouldBe "error.parsing.hint.python_style_lambda"
      hint.arguments shouldBe Seq("x, y", "x + y")
    }

    it("recognizes a Kotlin-style `data class` declaration") {
      val hint = classify(
        found = "class",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "data class Point(val x: Int, val y: Int)"
      )

      hint.messageKey shouldBe "error.parsing.hint.data_class_declaration"
      hint.arguments shouldBe Seq("Point", "x: Int, y: Int")
    }

    it("recognizes a Scala-style `case class` declaration") {
      val hint = classify(
        found = "case",
        expected = "\"abstract\", \"class\", \"record\", \"def\"",
        sourceLine = "case class Point(x: Int, y: Int)"
      )

      hint.messageKey shouldBe "error.parsing.hint.case_class_declaration"
      hint.arguments shouldBe Seq("Point", "x: Int, y: Int")
    }

    it("strips val/var from a Scala-style `case class` declaration's components") {
      val hint = classify(
        found = "case",
        sourceLine = "case class Point(val x: Int, val y: Int)"
      )

      hint.messageKey shouldBe "error.parsing.hint.case_class_declaration"
      hint.arguments shouldBe Seq("Point", "x: Int, y: Int")
    }

    it("recognizes a Rust/Go/Swift/C#/TS-style `struct` declaration") {
      val hint = classify(
        found = "Point",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "struct Point {"
      )

      hint.messageKey shouldBe "error.parsing.hint.struct_declaration"
      hint.arguments shouldBe Seq("Point")
    }

    it("recognizes a Kotlin/Scala-style `object` singleton declaration") {
      val hint = classify(
        found = "Foo",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "object Foo {"
      )

      hint.messageKey shouldBe "error.parsing.hint.object_declaration"
      hint.arguments shouldBe Seq("Foo")
    }

    it("recognizes a Kotlin-style `companion object` nested inside a class") {
      val hint = classify(
        found = "object",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "companion object {"
      )

      hint.messageKey shouldBe "error.parsing.hint.companion_object_declaration"
      hint.arguments shouldBe Seq.empty
    }

    it("recognizes a Kotlin-style named `companion object`") {
      val hint = classify(
        found = "object",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "companion object Named {"
      )

      hint.messageKey shouldBe "error.parsing.hint.companion_object_declaration"
    }

    it("recognizes a Java-style diamond-operator constructor call") {
      val hint = classify(
        found = "<",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "val list = new ArrayList<>()"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_diamond_operator"
      hint.arguments shouldBe Seq("ArrayList")
    }

    it("recognizes a Java-style generic constructor call with explicit type arguments") {
      val hint = classify(
        found = ")",
        expected = "\"Boolean\", \"break\", \"Byte\"",
        sourceLine = "val list = new ArrayList<String>()"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_generic_constructor_call"
      hint.arguments shouldBe Seq("ArrayList", "String")
    }

    it("recognizes a Java-style generic constructor call with multiple type arguments") {
      val hint = classify(
        found = ")",
        expected = "\"Boolean\", \"break\", \"Byte\"",
        sourceLine = "val m = new HashMap<String, Int>()"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_generic_constructor_call"
      hint.arguments shouldBe Seq("HashMap", "String, Int")
    }

    it("recognizes a Java-style `final` local variable declaration") {
      val hint = classify(
        found = "final",
        expected = "\"}\"",
        sourceLine = "final Int x = 5"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_final_local"
      hint.arguments shouldBe Seq("Int", "x")
    }

    it("recognizes a Java-style `final` local variable declaration with no initializer") {
      val hint = classify(
        found = "final",
        expected = "\"}\"",
        sourceLine = "final String name;"
      )

      hint.messageKey shouldBe "error.parsing.hint.java_style_final_local"
      hint.arguments shouldBe Seq("String", "name")
    }

    it("does not flag a `final class` declaration as a final-local mistake") {
      SyntaxHintClassifier.classify(
        found = "class",
        expected = "<ID>",
        context = "",
        sourceLine = "final class Point {"
      ).map(_.messageKey) should not be Some("error.parsing.hint.java_style_final_local")
    }

    it("recognizes a JS/TS-style `constructor(...)` method") {
      val hint = classify(
        found = "constructor",
        expected = "\"abstract\", \"def\", \"final\", \"forward\", ... (13 more)",
        sourceLine = "constructor(x: Int, y: Int) {"
      )

      hint.messageKey shouldBe "error.parsing.hint.js_style_constructor"
      hint.arguments shouldBe empty
    }

    it("recognizes a C#-style `$\"...\"` interpolated string prefix") {
      val hint = classify(
        found = "$",
        expected = "<EOL>, \"Boolean\", \"break\", ... (44 more)",
        sourceLine = "IO::println($\"Hello, {name}!\")"
      )

      hint.messageKey shouldBe "error.parsing.hint.dollar_sigil"
      hint.arguments shouldBe empty
    }

    it("recognizes a PHP/Bash-style `$name` variable sigil") {
      val hint = classify(
        found = "$",
        expected = "<EOL>, \"Boolean\", \"break\", ... (44 more)",
        sourceLine = "val greeting = \"Hi \" + $name"
      )

      hint.messageKey shouldBe "error.parsing.hint.dollar_sigil"
      hint.arguments shouldBe empty
    }

    it("recognizes a function call missing its parentheses") {
      val hint = classify(
        found = "bar",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "foo bar"
      )

      hint.messageKey shouldBe "error.parsing.hint.missing_call_parens"
      hint.arguments shouldBe Seq("foo")
    }

    it("recognizes a Rust-style `name!(...)` macro call") {
      val hint = classify(
        found = "!",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "println!(\"hi\")"
      )

      hint.messageKey shouldBe "error.parsing.hint.rust_style_macro_call"
      hint.arguments shouldBe Seq("println")
    }

    it("recognizes a Rust-style macro call with a longer name, e.g. `assert_eq!`") {
      val hint = classify(
        found = "!",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "assert_eq!(x, y)"
      )

      hint.messageKey shouldBe "error.parsing.hint.rust_style_macro_call"
      hint.arguments shouldBe Seq("assert_eq")
    }

    it("recognizes a Python2-style `print` statement missing its parentheses") {
      val hint = classify(
        found = "\"Hello\"",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "println \"Hello\""
      )

      hint.messageKey shouldBe "error.parsing.hint.missing_call_parens"
      hint.arguments shouldBe Seq("println")
    }

    it("does not flag a bare identifier when a block is expected next, not end-of-statement") {
      SyntaxHintClassifier.classify(
        found = "bar",
        expected = "\"{\"",
        context = "",
        sourceLine = "foo bar {"
      ).map(_.messageKey) should not be Some("error.parsing.hint.missing_call_parens")
    }

    it("does not flag a leading reserved word as a missing-parens call") {
      SyntaxHintClassifier.classify(
        found = "5",
        expected = "<EOF>, <EOL>, \";\"",
        context = "",
        sourceLine = "val x 5"
      ).map(_.messageKey) should not be Some("error.parsing.hint.missing_call_parens")
    }

    it("keeps the Ruby-style `unless` advice ahead of the missing-call-parens fallback") {
      classify(
        found = "unless",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "unless done {"
      ).messageKey shouldBe "error.parsing.hint.unless_not_supported"
    }

    it("returns no hint when no classification rule matches") {
      SyntaxHintClassifier.classify(
        found = ")",
        expected = "<EOF>",
        context = "",
        sourceLine = "value)"
      ) shouldBe None
    }

    it("recognizes a Swift-style `if let` optional binding") {
      val hint = classify(
        found = "x",
        expected = "\"{\"",
        sourceLine = "if let x = opt {"
      )

      hint.messageKey shouldBe "error.parsing.hint.if_let_binding"
      hint.arguments shouldBe Seq("if", "x", "opt")
    }

    it("recognizes a Rust-style `while let` optional binding") {
      val hint = classify(
        found = "x",
        expected = "\"{\"",
        sourceLine = "while let x = opt {"
      )

      hint.messageKey shouldBe "error.parsing.hint.if_let_binding"
      hint.arguments shouldBe Seq("while", "x", "opt")
    }

    it("recognizes a Rust-style `if let Some(x) = ...` pattern binding") {
      val hint = classify(
        found = "Some",
        expected = "\"{\"",
        sourceLine = "if let Some(x) = opt {"
      )

      hint.messageKey shouldBe "error.parsing.hint.if_let_binding"
      hint.arguments shouldBe Seq("if", "x", "opt")
    }

    it("keeps the if-let-binding advice ahead of the generic block-expected fallback") {
      classify(
        found = "x",
        expected = "\"{\"",
        sourceLine = "if let x = compute(1, 2) {"
      ).messageKey shouldBe "error.parsing.hint.if_let_binding"
    }

    it("does not flag an ordinary `if` condition as an if-let binding") {
      SyntaxHintClassifier.classify(
        found = "y",
        expected = "\"{\"",
        context = "",
        sourceLine = "if x == y {"
      ).map(_.messageKey) should not be Some("error.parsing.hint.if_let_binding")
    }

    it("recognizes a Swift-style `guard let x = ... else` early exit") {
      val hint = classify(
        found = "let",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "guard let x = 5 else {"
      )

      hint.messageKey shouldBe "error.parsing.hint.guard_let_else"
      hint.arguments shouldBe Seq("x", "5")
    }

    it("recognizes a Swift-style `guard let Some(x) = ... else` pattern binding") {
      val hint = classify(
        found = "let",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "guard let Some(y) = opt else {"
      )

      hint.messageKey shouldBe "error.parsing.hint.guard_let_else"
      hint.arguments shouldBe Seq("y", "opt")
    }

    it("keeps the guard-let advice ahead of the generic missing-call-parens fallback") {
      classify(
        found = "let",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "guard let x = compute(1, 2) else {"
      ).messageKey shouldBe "error.parsing.hint.guard_let_else"
    }

    it("does not flag an ordinary `guard(...)` call as a guard-let binding") {
      SyntaxHintClassifier.classify(
        found = "let",
        expected = "<EOF>, <EOL>, \";\"",
        context = "",
        sourceLine = "guard(x)"
      ).map(_.messageKey) should not be Some("error.parsing.hint.guard_let_else")
    }

    it("recognizes a Swift-style bare `guard cond else` early exit") {
      val hint = classify(
        found = "cond",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "guard cond else {"
      )

      hint.messageKey shouldBe "error.parsing.hint.guard_else"
      hint.arguments shouldBe Seq("cond")
    }

    it("keeps the guard-let advice ahead of the bare guard-else fallback") {
      classify(
        found = "let",
        expected = "<EOF>, <EOL>, \";\"",
        sourceLine = "guard let x = compute(1, 2) else {"
      ).messageKey shouldBe "error.parsing.hint.guard_let_else"
    }

    it("does not flag an ordinary `guard(...)` call as a guard-else binding") {
      SyntaxHintClassifier.classify(
        found = "x",
        expected = "<EOF>, <EOL>, \";\"",
        context = "",
        sourceLine = "guard(x)"
      ).map(_.messageKey) should not be Some("error.parsing.hint.guard_else")
    }
  }

  private def classify(
    found: String,
    expected: String = "",
    context: String = "",
    sourceLine: String = ""
  ): SyntaxHint =
    SyntaxHintClassifier
      .classify(found, expected, context, sourceLine)
      .getOrElse(fail("expected a syntax hint"))
}
