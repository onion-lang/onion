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
  }
}

