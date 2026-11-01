package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import org.objectweb.asm.{ClassReader, ClassVisitor, MethodVisitor, Opcodes}

import java.io.StringReader

/**
 * Regression test for issue #2507: a record component typed with a `type` alias to a
 * scalar (`type Port = Int`) broke `from re"..."`, `derive!(Json)` and `shape` synthesis
 * with an E0021 about a constructor call the user never wrote, because the synthesized
 * parser read the component type as written and so never converted the captured text.
 * The alias is now resolved first, so the component behaves like the type it names.
 */
class RecordAliasComponentSpec extends AbstractShellSpec {

  private def runMain(body: String): Shell.Result =
    shell.run(body, "None", Array())

  describe("a record component typed with an alias") {
    it("converts in a from re\"...\" parser") {
      val r = runMain(
        """type Port = Int
          |record Addr(host: String, port: Port) from re"(\S+):(\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Addr? = Addr::parse("h:80")
          |    if a != null { return a.host() + "/" + (a.port() + 1) } else { return "FAIL" }
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("h/81") == r)
    }

    it("converts through a chain of aliases and a nullable alias") {
      val r = runMain(
        """type Count = Int
          |type N = Count
          |type MaybeN = Int?
          |record P(a: N, b: N?, c: MaybeN) from re"(\d+)-(\d+)?-(\d+)?"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p: P? = P::parse("1--3")
          |    if p != null { return (p.a() + 1) + " " + p.b() + " " + p.c() } else { return "FAIL" }
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("2 null 3") == r)
    }

    it("round-trips through derive!(Json)") {
      val r = runMain(
        """type Port = Int
          |type Name = String
          |record Cfg(name: Name, port: Port) derive!(Json)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Cfg? = Cfg::fromJson("{\"name\": \"web\", \"port\": 8080}")
          |    if c != null { return c.name() + ":" + (c.port() + 1) + " " + Cfg::toJson(c) } else { return "FAIL" }
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("web:8081 {\"name\":\"web\",\"port\":8080}") == r)
    }

    it("reads in a regex shape and a json shape") {
      val r = runMain(
        """type Port = Int
          |type Tags = List[String]
          |record Addr(host: String, port: Port, alt: Port?, more: List[Port], tags: Tags) {
          |  shape doc = json
          |}
          |record Hp(host: String, port: Port) {
          |  shape line = re"(\S+):(\d+)"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = Addr::doc().parse("{\"host\": \"h\", \"port\": 80, \"more\": [1, 2], \"tags\": [\"a\"]}").get()
          |    val h = Hp::line().parse("x:9").get()
          |    return a.host() + " " + (a.port() + 1) + " " + a.alt() + " " + a.more() + " " + a.tags() + " " + (h.port() + 1)
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("h 81 null [1, 2] [a] 10") == r)
    }

    it("to an unsupported type is still E0061, not E0021") {
      val result = new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
        Seq(new StreamInputSource(() => new StringReader(
          """type When = java.util.Date
            |record Ev(name: String, at: When) from re"(\S+) (\S+)"
            |""".stripMargin), "Test.on")))
      val codes = result.allErrors.flatMap(_.code)
      assert(codes.contains("E0061"), codes)
      assert(!codes.contains("E0021"), codes)
    }
  }

  /** Compiles `files` (name -> source) together; the classes, or the error codes. */
  private def compileAll(files: (String, String)*): Either[Seq[String], Map[String, Array[Byte]]] =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compile(
      files.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    ) match {
      case CompilationOutcome.Success(classes) => Right(classes.map(c => c.className -> c.content).toMap)
      case CompilationOutcome.Failure(errors)  => Left(errors.map(e => e.code.getOrElse("?") + " " + e.message))
    }

  /** The descriptors of the public constructors of the class compiled to `bytes`. */
  private def constructorDescriptors(bytes: Array[Byte]): List[String] = {
    val found = List.newBuilder[String]
    new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
      override def visitMethod(access: Int, name: String, desc: String, sig: String, ex: Array[String]): MethodVisitor = {
        if (name == "<init>" && (access & Opcodes.ACC_PUBLIC) != 0) found += desc
        null
      }
    }, ClassReader.SKIP_CODE)
    found.result()
  }

  // #2509: an alias declared in another file was written under that file's imports, so
  // the record keeps the type as written and its own file's imports never re-read it.
  describe("a record component typed with an alias from another file") {
    it("keeps a target the other file imported") {
      val out = compileAll(
        "A.on" -> "import { java.awt.Point; }\ntype P = Point\n",
        "B.on" -> "record R(p: P)\nclass Main {\npublic:\n  static def main(args: String[]): void { println(new R(new java.awt.Point(1, 2)).p().x) }\n}\n")
      assert(out.isRight, out)
      assert(constructorDescriptors(out.toOption.get("R")) == List("(Ljava/awt/Point;)V"))
    }

    it("does not re-read a simple name under the record file's imports") {
      val out = compileAll(
        "A.on" -> "import { java.sql.Date; }\ntype D = Date\n",
        "B.on" -> "record R(d: D)\n")
      assert(out.isRight, out)
      assert(constructorDescriptors(out.toOption.get("R")) == List("(Ljava/sql/Date;)V"))
    }

    it("keeps a target named through an import alias") {
      val out = compileAll(
        "A.on" -> "import { java.lang.StringBuilder as SB; }\ntype Buf = SB\n",
        "B.on" -> "record R(b: Buf)\n")
      assert(out.isRight, out)
      assert(constructorDescriptors(out.toOption.get("R")) == List("(Ljava/lang/StringBuilder;)V"))
    }

    it("still converts a primitive alias in a from re\"...\" parser") {
      val out = compileAll(
        "A.on" -> "type Port = Int\n",
        "B.on" -> "record Addr(host: String, port: Port) from re\"(\\S+):(\\d+)\"\n")
      assert(out.isRight, out)
      assert(constructorDescriptors(out.toOption.get("Addr")) == List("(Ljava/lang/String;I)V"))
    }

    // #2517: `String` reads as java.lang.String wherever it is written, so an alias to it
    // from another file expands like a primitive one. It used to stay `Name`, which a json
    // shape took for a nested record: E0005 about `String.onion$$jsonShape()`.
    it("reads a String alias in a json shape, a from re\"...\" parser and derive!(Json)") {
      val out = compileAll(
        "A.on" -> "type Name = String\n",
        "B.on" ->
          """record J(n: Name, m: Name?) {
            |  shape doc = json
            |}
            |record F(n: Name, port: Int) from re"(\S+):(\d+)"
            |record D(n: Name) derive!(Json)
            |class Main {
            |public:
            |  static def run(): String {
            |    val j = J::doc().parse("{\"n\": \"x\"}").get()
            |    val f: F? = F::parse("h:80")
            |    val d: D? = D::fromJson("{\"n\": \"y\"}")
            |    return j.n() + " " + j.m() + " " + f?.n() + " " + d?.n() + " " + D::toJson(new D("z"))
            |  }
            |}
            |""".stripMargin)
      assert(out.isRight, out)
      val classes = out.toOption.get
      assert(constructorDescriptors(classes("J")) == List("(Ljava/lang/String;Ljava/lang/String;)V"))
      val loader = new ClassLoader(getClass.getClassLoader) {
        override def findClass(name: String): Class[?] = classes.get(name) match {
          case Some(b) => defineClass(name, b, 0, b.length)
          case None    => throw new ClassNotFoundException(name)
        }
      }
      val result = loader.loadClass("Main").getMethod("run").invoke(null)
      assert(result == "x null h y {\"n\":\"z\"}")
    }

    it("is E0061, not E0005, when the json shape cannot see the alias's target") {
      val out = compileAll(
        "A.on" -> "import { java.lang.String as JS; }\ntype Name = JS\n",
        "B.on" -> "record J(n: Name) {\n  shape doc = json\n}\n")
      assert(out.isLeft, out)
      val codes = out.swap.toOption.get.map(_.takeWhile(_ != ' '))
      assert(codes.contains("E0061"), codes)
      assert(!codes.contains("E0005"), codes)
    }
  }
}
