package onion.compiler

import java.io.StringReader
import onion.compiler.parser.JJOnionParser
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.diagrams.Diagrams

class GenericParserSpec extends AnyFunSpec with Diagrams {

  describe("Parser with generic syntax") {

    it("parses class type parameters with extends bounds and type applications") {
      val source =
        """
          |class Box[T extends Object] {
          |public:
          |  def id[A](x: A): A = x
          |}
          |class Use {
          |public:
          |  def mk(): Box[Int] = new Box[Int]()
          |}
          |""".stripMargin

      val unit = new JJOnionParser(new StringReader(source)).unit()

      val classes = unit.toplevels.collect { case c: AST.ClassDeclaration => c }
      assert(classes.exists(_.name == "Box"))
      val box = classes.find(_.name == "Box").get
      assert(box.typeParameters.nonEmpty)
      assert(box.typeParameters.head.name == "T")
      assert(box.typeParameters.head.upperBound.isDefined)

      val boxMethods = box.sections.flatMap(_.members).collect { case m: AST.MethodDeclaration => m }
      val id = boxMethods.find(_.name == "id").get
      assert(id.typeParameters.map(_.name) == List("A"))

      val use = classes.find(_.name == "Use").get
      val useMethods = use.sections.flatMap(_.members).collect { case m: AST.MethodDeclaration => m }
      assert(useMethods.exists(_.returnType.desc.isInstanceOf[AST.ParameterizedType]))
    }

    it("parses method call type arguments") {
      val source =
        """
          |class Util {
          |public:
          |  static def id[A extends Object](x: A): A = x
          |
          |  static def main(args: String[]): String {
          |    return Util::id[String]("ok")
          |  }
          |}
          |""".stripMargin

      val unit = new JJOnionParser(new StringReader(source)).unit()
      val util = unit.toplevels.collectFirst { case c: AST.ClassDeclaration if c.name == "Util" => c }.get
      val methods = util.sections.flatMap(_.members).collect { case m: AST.MethodDeclaration => m }
      val main = methods.find(_.name == "main").get
      val returns = main.block.elements.collect { case r: AST.ReturnExpression => r }
      val returned = returns.head.result
      val call = returned.asInstanceOf[AST.StaticMethodCall]
      assert(call.typeArgs.nonEmpty)
      assert(call.typeArgs.head.desc.toString == "String")
    }

    it("parses function type syntax") {
      val source =
        """
          |class FuncTypes {
          |public:
          |  def applyTwice(f: (String) -> String, x: String): String = f.call(f.call(x))
          |}
          |""".stripMargin

      val unit = new JJOnionParser(new StringReader(source)).unit()
      val clazz = unit.toplevels.collectFirst { case c: AST.ClassDeclaration if c.name == "FuncTypes" => c }.get
      val methods = clazz.sections.flatMap(_.members).collect { case m: AST.MethodDeclaration => m }
      val applyTwice = methods.find(_.name == "applyTwice").get
      val fArg = applyTwice.args.head
      assert(fArg.typeRef.desc.isInstanceOf[AST.FunctionType])
    }
  }
}
