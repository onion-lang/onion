package onion.compiler

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.diagrams.Diagrams
import onion.compiler.TestSupport._
import onion.compiler.exceptions.CompilationException

/**
 * Unit tests for the Rewriting phase of the Onion compiler.
 *
 * The Rewriting phase performs AST transformations before type checking,
 * including desugaring of do notation and recursive rewriting of nested expressions.
 */
class RewritingSpec extends AnyFunSpec with Diagrams {

  // ============================================
  // Do Notation Desugaring Tests
  // ============================================

  describe("Do notation desugaring") {

    /** Helper to extract do expression from parsed class */
    def extractDoExpression(source: String): AST.Expression = {
      val unit = parseUnit(source)
      val rewritten = rewrite(unit)
      val clazz = rewritten.toplevels.head.asInstanceOf[AST.ClassDeclaration]
      val method = clazz.sections.head.members.head.asInstanceOf[AST.MethodDeclaration]
      // Find the variable declaration with the do expression
      method.block.elements.collectFirst {
        case AST.LocalVariableDeclaration(_, _, "result", _, init) => init
        case AST.ReturnExpression(_, result) => result
      }.getOrElse(fail("Could not find do expression in rewritten AST"))
    }

    it("desugars a single ret statement to M::successful(e)") {
      // do[Future] { ret 42 } => Future::successful(42)
      val source =
        """class Test {
          |public:
          |  def test(): Object {
          |    val result = do[Future] { ret 42 };
          |    return result
          |  }
          |}""".stripMargin

      val result = extractDoExpression(source)
      result match {
        case AST.StaticMethodCall(_, typeRef, "successful", List(AST.IntegerLiteral(_, 42)), _) =>
          assert(typeRef.desc.toString == "Future")
        case other =>
          fail(s"Expected StaticMethodCall to Future::successful, got: $other")
      }
    }

    it("desugars a single expression to M::successful(e)") {
      // do[Future] { 42 } => Future::successful(42)
      val source =
        """class Test {
          |public:
          |  def test(): Object {
          |    val result = do[Future] { 42 };
          |    return result
          |  }
          |}""".stripMargin

      val result = extractDoExpression(source)
      result match {
        case AST.StaticMethodCall(_, typeRef, "successful", List(AST.IntegerLiteral(_, 42)), _) =>
          assert(typeRef.desc.toString == "Future")
        case other =>
          fail(s"Expected StaticMethodCall to Future::successful, got: $other")
      }
    }

    it("desugars a binding chain to nested bind calls") {
      // do[Future] { x <- getX(); ret x }
      // => getX().bind((x) -> { Future::successful(x) })
      val source =
        """class Test {
          |public:
          |  def test(): Object {
          |    val result = do[Future] { x <- getX(); ret x };
          |    return result
          |  }
          |}""".stripMargin

      val result = extractDoExpression(source)
      result match {
        case AST.MethodCall(_, _: AST.UnqualifiedMethodCall, "bind", List(closure: AST.ClosureExpression), _) =>
          assert(closure.args.head.name == "x")
          closure.body.elements.head match {
            case AST.ExpressionBox(_, AST.StaticMethodCall(_, typeRef, "successful", _, _)) =>
              assert(typeRef.desc.toString == "Future")
            case other =>
              fail(s"Expected closure body to be StaticMethodCall, got: $other")
          }
        case other =>
          fail(s"Expected MethodCall to bind, got: $other")
      }
    }

    it("desugars multiple bindings to nested bind calls") {
      // do[Future] { x <- getX(); y <- getY(); ret x + y }
      // => getX().bind((x) -> { getY().bind((y) -> { Future::successful(x + y) }) })
      val source =
        """class Test {
          |public:
          |  def test(): Object {
          |    val result = do[Future] { x <- getX(); y <- getY(); ret x + y };
          |    return result
          |  }
          |}""".stripMargin

      val result = extractDoExpression(source)
      result match {
        case AST.MethodCall(_, _, "bind", List(outerClosure: AST.ClosureExpression), _) =>
          assert(outerClosure.args.head.name == "x")
          outerClosure.body.elements.head match {
            case AST.ExpressionBox(_, AST.MethodCall(_, _, "bind", List(innerClosure: AST.ClosureExpression), _)) =>
              assert(innerClosure.args.head.name == "y")
            case other =>
              fail(s"Expected nested MethodCall to bind, got: $other")
          }
        case other =>
          fail(s"Expected MethodCall to bind, got: $other")
      }
    }

    it("desugars intermediate expression as side-effect bind") {
      // do[Future] { sideEffect(); ret 42 }
      // => sideEffect().bind((_unused) -> { Future::successful(42) })
      val source =
        """class Test {
          |public:
          |  def test(): Object {
          |    val result = do[Future] { sideEffect(); ret 42 };
          |    return result
          |  }
          |}""".stripMargin

      val result = extractDoExpression(source)
      result match {
        case AST.MethodCall(_, _, "bind", List(closure: AST.ClosureExpression), _) =>
          assert(closure.args.head.name == "_unused")
        case other =>
          fail(s"Expected MethodCall to bind with _unused arg, got: $other")
      }
    }

    it("rejects empty do expression at parse time") {
      // Empty do expressions are rejected by the parser, not the rewriter
      import onion.compiler.parser.ParseException
      val source =
        """class Test {
          |public:
          |  def test(): Object {
          |    return do[Future] { }
          |  }
          |}""".stripMargin

      assertThrows[ParseException] {
        parseUnit(source)
      }
    }
  }

  // ============================================
  // Binary Expression Passthrough Tests
  // ============================================

  describe("Binary expression rewriting") {

    it("passes through addition with recursive rewriting") {
      val lhs = intLit(1)
      val rhs = intLit(2)
      val expr = add(lhs, rhs)
      val result = rewriteExpression(expr)

      result match {
        case AST.Addition(_, AST.IntegerLiteral(_, 1), AST.IntegerLiteral(_, 2)) =>
          // Success
        case other =>
          fail(s"Expected Addition(1, 2), got: $other")
      }
    }

    it("passes through subtraction with recursive rewriting") {
      val lhs = intLit(10)
      val rhs = intLit(5)
      val expr = sub(lhs, rhs)
      val result = rewriteExpression(expr)

      result match {
        case AST.Subtraction(_, AST.IntegerLiteral(_, 10), AST.IntegerLiteral(_, 5)) =>
          // Success
        case other =>
          fail(s"Expected Subtraction(10, 5), got: $other")
      }
    }

    it("passes through assignment with recursive rewriting") {
      val lhs = id("x")
      val rhs = intLit(42)
      val expr = assign(lhs, rhs)
      val result = rewriteExpression(expr)

      result match {
        case AST.Assignment(_, AST.Id(_, "x"), AST.IntegerLiteral(_, 42)) =>
          // Success
        case other =>
          fail(s"Expected Assignment(x, 42), got: $other")
      }
    }

    it("recursively rewrites nested binary expressions") {
      // (1 + 2) * 3
      val nested = add(intLit(1), intLit(2))
      val expr = mul(nested, intLit(3))
      val result = rewriteExpression(expr)

      result match {
        case AST.Multiplication(_, AST.Addition(_, _, _), AST.IntegerLiteral(_, 3)) =>
          // Success
        case other =>
          fail(s"Expected Multiplication(Addition, 3), got: $other")
      }
    }
  }

  // ============================================
  // Unary Expression Passthrough Tests
  // ============================================

  describe("Unary expression rewriting") {

    it("passes through negation") {
      val expr = AST.Negate(defaultLoc, intLit(42))
      val result = rewriteExpression(expr)

      result match {
        case AST.Negate(_, AST.IntegerLiteral(_, 42)) =>
          // Success
        case other =>
          fail(s"Expected Negate(42), got: $other")
      }
    }

    it("passes through logical not") {
      val expr = AST.Not(defaultLoc, boolLit(true))
      val result = rewriteExpression(expr)

      result match {
        case AST.Not(_, AST.BooleanLiteral(_, true)) =>
          // Success
        case other =>
          fail(s"Expected Not(true), got: $other")
      }
    }

    it("recursively rewrites nested unary expressions") {
      // -(-42)
      val inner = AST.Negate(defaultLoc, intLit(42))
      val expr = AST.Negate(defaultLoc, inner)
      val result = rewriteExpression(expr)

      result match {
        case AST.Negate(_, AST.Negate(_, AST.IntegerLiteral(_, 42))) =>
          // Success
        case other =>
          fail(s"Expected Negate(Negate(42)), got: $other")
      }
    }
  }

  // ============================================
  // Literal Passthrough Tests
  // ============================================

  describe("Literal passthrough") {

    it("passes through integer literal unchanged") {
      val expr = intLit(42)
      val result = rewriteExpression(expr)
      assert(result == expr)
    }

    it("passes through long literal unchanged") {
      val expr = longLit(123456789L)
      val result = rewriteExpression(expr)
      assert(result == expr)
    }

    it("passes through double literal unchanged") {
      val expr = doubleLit(3.14)
      val result = rewriteExpression(expr)
      assert(result == expr)
    }

    it("passes through boolean literal unchanged") {
      val trueExpr = boolLit(true)
      val falseExpr = boolLit(false)
      assert(rewriteExpression(trueExpr) == trueExpr)
      assert(rewriteExpression(falseExpr) == falseExpr)
    }

    it("passes through string literal unchanged") {
      val expr = stringLit("hello")
      val result = rewriteExpression(expr)
      assert(result == expr)
    }

    it("passes through null literal unchanged") {
      val expr = nullLit
      val result = rewriteExpression(expr)
      assert(result == expr)
    }
  }

  // ============================================
  // Control Flow Rewriting Tests
  // ============================================

  describe("If expression rewriting") {

    it("rewrites if condition, then block, and else block") {
      val condition = boolLit(true)
      val thenBlock = block(exprBox(intLit(1)))
      val elseBlock = block(exprBox(intLit(2)))
      val expr = ifExpr(condition, thenBlock, elseBlock)
      val result = rewriteExpression(expr)

      result match {
        case AST.IfExpression(_, AST.BooleanLiteral(_, true), thenB, elseB) =>
          assert(thenB != null)
          assert(elseB != null)
        case other =>
          fail(s"Expected IfExpression, got: $other")
      }
    }

    it("handles if without else") {
      val condition = boolLit(true)
      val thenBlock = block(exprBox(intLit(1)))
      val expr = ifExpr(condition, thenBlock, null)
      val result = rewriteExpression(expr)

      result match {
        case AST.IfExpression(_, _, _, elseB) =>
          assert(elseB == null)
        case other =>
          fail(s"Expected IfExpression without else, got: $other")
      }
    }
  }

  describe("While expression rewriting") {

    it("rewrites while condition and body") {
      val condition = boolLit(true)
      val body = block(exprBox(intLit(1)))
      val expr = whileExpr(condition, body)
      val result = rewriteExpression(expr)

      result match {
        case AST.WhileExpression(_, AST.BooleanLiteral(_, true), bodyBlock) =>
          assert(bodyBlock != null)
        case other =>
          fail(s"Expected WhileExpression, got: $other")
      }
    }
  }

  describe("For expression rewriting") {

    it("rewrites for init, condition, update, and body") {
      val init = localVar("i", intType, intLit(0))
      val condition = AST.LessThan(defaultLoc, id("i"), intLit(10))
      val update = AST.PostIncrement(defaultLoc, id("i"))
      val body = block(exprBox(intLit(1)))
      val expr = forExpr(init, condition, update, body)
      val result = rewriteExpression(expr)

      result match {
        case AST.ForExpression(_, _, cond, upd, bodyBlock) =>
          assert(cond != null)
          assert(upd != null)
          assert(bodyBlock != null)
        case other =>
          fail(s"Expected ForExpression, got: $other")
      }
    }

    it("handles for with null condition") {
      val init = emptyExpr
      val body = block(exprBox(intLit(1)))
      val expr = forExpr(init, null, null, body)
      val result = rewriteExpression(expr)

      result match {
        case AST.ForExpression(_, _, cond, upd, _) =>
          assert(cond == null)
          assert(upd == null)
        case other =>
          fail(s"Expected ForExpression with null condition, got: $other")
      }
    }
  }

  describe("Block expression rewriting") {

    it("rewrites all elements in a block") {
      val blk = block(exprBox(intLit(1)), exprBox(intLit(2)), exprBox(intLit(3)))
      val result = rewriteBlock(blk)

      assert(result.elements.length == 3)
    }

    it("handles empty block") {
      val blk = block()
      val result = rewriteBlock(blk)
      assert(result.elements.isEmpty)
    }

    it("handles null block") {
      val result = rewriteBlock(null)
      assert(result == null)
    }
  }

  // ============================================
  // Method Call Rewriting Tests
  // ============================================

  describe("Method call rewriting") {

    it("rewrites target and arguments") {
      val target = id("obj")
      val arg1 = intLit(1)
      val arg2 = intLit(2)
      val expr = methodCall(target, "foo", arg1, arg2)
      val result = rewriteExpression(expr)

      result match {
        case AST.MethodCall(_, AST.Id(_, "obj"), "foo", List(_, _), _) =>
          // Success
        case other =>
          fail(s"Expected MethodCall, got: $other")
      }
    }

    it("rewrites unqualified method call arguments") {
      val expr = call("foo", intLit(1), intLit(2))
      val result = rewriteExpression(expr)

      result match {
        case AST.UnqualifiedMethodCall(_, "foo", List(_, _), _) =>
          // Success
        case other =>
          fail(s"Expected UnqualifiedMethodCall, got: $other")
      }
    }

    it("rewrites static method call arguments") {
      val expr = staticCall(refType("Math"), "abs", intLit(-42))
      val result = rewriteExpression(expr)

      result match {
        case AST.StaticMethodCall(_, _, "abs", List(AST.IntegerLiteral(_, -42)), _) =>
          // Success
        case other =>
          fail(s"Expected StaticMethodCall, got: $other")
      }
    }
  }

  // ============================================
  // Closure Rewriting Tests
  // ============================================

  describe("Closure expression rewriting") {

    it("rewrites closure body") {
      val closureArg = arg("x", intType)
      val body = block(exprBox(add(id("x"), intLit(1))))
      val expr = closure(List(closureArg), body)
      val result = rewriteExpression(expr)

      result match {
        case AST.ClosureExpression(_, _, _, args, _, body) =>
          assert(args.head.name == "x")
          assert(body != null)
        case other =>
          fail(s"Expected ClosureExpression, got: $other")
      }
    }
  }

  // ============================================
  // Declaration Rewriting Tests
  // ============================================

  describe("Declaration rewriting") {

    it("rewrites method body") {
      val body = block(returnExpr(intLit(42)))
      val method = methodDecl("test", Nil, intType, body)
      val section = accessSection(AST.M_PUBLIC, method)
      val clazz = classDecl("Test", List(section))
      val unit = compilationUnit(List(clazz))

      val result = rewrite(unit)
      val resultClass = result.toplevels.head.asInstanceOf[AST.ClassDeclaration]
      val resultMethod = resultClass.sections.head.members.head.asInstanceOf[AST.MethodDeclaration]

      assert(resultMethod.block != null)
    }

    it("rewrites field initializer") {
      val field = fieldDecl("x", intType, intLit(42))
      val section = accessSection(AST.M_PUBLIC, field)
      val clazz = classDecl("Test", List(section))
      val unit = compilationUnit(List(clazz))

      val result = rewrite(unit)
      val resultClass = result.toplevels.head.asInstanceOf[AST.ClassDeclaration]
      val resultField = resultClass.sections.head.members.head.asInstanceOf[AST.FieldDeclaration]

      result.toplevels.head match {
        case AST.ClassDeclaration(_, _, _, _, _, _, sections, _) =>
          sections.head.members.head match {
            case AST.FieldDeclaration(_, _, "x", _, AST.IntegerLiteral(_, 42)) =>
              // Success
            case other =>
              fail(s"Expected FieldDeclaration, got: $other")
          }
        case other =>
          fail(s"Expected ClassDeclaration, got: $other")
      }
    }

    it("rewrites global variable initializer") {
      // Create a GlobalVariableDeclaration directly since the parser
      // treats top-level val/var as LocalVariableDeclaration
      val globalVar = AST.GlobalVariableDeclaration(
        defaultLoc, 0, "x", intType, add(intLit(40), intLit(2))
      )
      val unit = compilationUnit(List(globalVar))
      val result = rewrite(unit)

      result.toplevels.head match {
        case AST.GlobalVariableDeclaration(_, _, "x", _, AST.Addition(_, _, _)) =>
          // Success - initializer expression was rewritten
        case other =>
          fail(s"Expected GlobalVariableDeclaration with rewritten init, got: $other")
      }
    }
  }

  // ============================================
  // Complex Nested Rewriting Tests
  // ============================================

  describe("Complex nested rewriting") {

    it("rewrites deeply nested expressions") {
      // if (true) { while (x < 10) { x = x + 1 } }
      val source =
        """
          |class Test {
          |public:
          |  def test(): Void {
          |    var x: Int = 0
          |    if (true) {
          |      while (x < 10) {
          |        x = x + 1
          |      }
          |    }
          |  }
          |}
          |""".stripMargin

      val unit = parseUnit(source)
      val result = rewrite(unit)

      // If it parses and rewrites without exception, the test passes
      assert(result.toplevels.nonEmpty)
    }

    it("rewrites do expression inside loop") {
      val source =
        """
          |class Test {
          |public:
          |  def test(): Object {
          |    var i: Int = 0
          |    while (i < 10) {
          |      i = i + 1
          |    }
          |    return do[Future] { ret i }
          |  }
          |}
          |""".stripMargin

      val unit = parseUnit(source)
      val result = rewrite(unit)

      // Verify the do expression was desugared
      val testClass = result.toplevels.head.asInstanceOf[AST.ClassDeclaration]
      val testMethod = testClass.sections.head.members.head.asInstanceOf[AST.MethodDeclaration]
      val returnExpr = testMethod.block.elements.last.asInstanceOf[AST.ReturnExpression]

      returnExpr.result match {
        case AST.StaticMethodCall(_, typeRef, "successful", _, _) =>
          assert(typeRef.desc.toString == "Future")
        case other =>
          fail(s"Expected StaticMethodCall to Future::successful, got: $other")
      }
    }
  }
}
