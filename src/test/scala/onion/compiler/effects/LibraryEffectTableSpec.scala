package onion.compiler.effects

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.jar.{JarEntry, JarOutputStream}

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.pipeline.CompilationResult
import onion.tools.Shell
import org.objectweb.asm.{ClassWriter, Opcodes}
import org.scalatest.funspec.AnyFunSpec

/**
 * Library-provided effect tables: a jar on the compile classpath ships
 * `META-INF/onion/effect-table.txt`, and its entries classify that jar's own classes for
 * the capability check, `--plan` and `--contract` — including fixed operands
 * (`Class#m=net:host`). Assertions are on codes and plan text, which is not localized.
 */
class LibraryEffectTableSpec extends AnyFunSpec {
  import LibraryEffectTableSpec.*

  private val Weather = "com.example.fx.Weather"

  private lazy val goodJar: Path = jar("good",
    Seq(Weather, "com.example.fx.Weather$Cache"),
    s"""# the fixture's own table
       |$Weather#forecast=net:api.weather.example,env:WEATHER_KEY
       |$Weather#local=pure
       |com.example.fx.Weather$$Cache#*=read
       |com.example.other.Thief#*=pure
       |java.lang.String#*=net
       |""".stripMargin)

  private lazy val malformedJar: Path = jar("malformed", Seq(Weather),
    s"""$Weather#local=pure
       |$Weather#forecast=nett
       |""".stripMargin)

  private def compile(classpath: Seq[Path], source: String): CompilationResult = {
    val config = CompilerConfig(classpath.map(_.toString), null, "UTF-8", "", 100)
    new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Lib.on")))
  }

  private def errorCodes(r: CompilationResult): Seq[String] = r.diagnostics.allErrors.flatMap(_.code)
  private def warningCodes(r: CompilationResult): Seq[String] = r.diagnostics.warnings.map(_.category.code)

  private def run(classpath: Seq[Path], source: String, args: String*): (Shell.Result, String) = {
    val buf = new ByteArrayOutputStream()
    val ps = new PrintStream(buf, true, UTF_8)
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) {
          Shell(classpath.map(_.toString)).run(source, "None", args.toArray)
        }}
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, buf.toString(UTF_8))
  }

  private val forecastTool =
    s"""import { $Weather }
       |tool weather(city: String): Int
       |  requires { net, env, console }
       |{
       |  IO::println(Weather::forecast(city))
       |  return 0
       |}
       |""".stripMargin

  describe("a library's own table") {
    it("lets a tool call the library without `unknown` when the jar declares its effects") {
      val r = compile(Seq(goodJar), forecastTool)
      assert(!r.hasErrors, r.diagnostics.allErrors.map(_.message).mkString("\n"))
    }

    it("still holds the tool to it: an undeclared library effect is E0077") {
      val r = compile(Seq(goodJar),
        s"""import { $Weather }
           |tool weather(city: String): Int requires { console } {
           |  IO::println(Weather::forecast(city))
           |  return 0
           |}
           |""".stripMargin)
      assert(errorCodes(r).contains("E0077"), errorCodes(r))
    }

    it("classifies a pure method as pure, and a nested class by its binary name") {
      val r = compile(Seq(goodJar),
        s"""import { $Weather }
           |tool w(): Int requires { console } {
           |  IO::println(Weather::local())
           |  return 0
           |}
           |""".stripMargin)
      assert(!r.hasErrors, r.diagnostics.allErrors.map(_.message).mkString("\n"))
      val loaded = EffectTable.forClasspath(Seq(goodJar.toString))
      assert(loaded.table.lookup("com.example.fx.Weather$Cache", "get").contains(Set(Effect.Read)))
    }

    it("puts its fixed operands into --plan") {
      val (r, out) = run(Seq(goodJar), forecastTool, "Osaka", "--plan")
      assert(r == Shell.Success(0), out)
      val lines = out.linesIterator.map(_.trim).toSeq
      assert(lines.contains("net     api.weather.example"), out)
      assert(lines.contains("env     WEATHER_KEY"), out)
      assert(!out.contains("(operand not statically known)"), out)
      assert(out.contains("nothing was executed"), out)
    }

    it("puts its fixed operands into --contract's staticOperands") {
      val (r, out) = run(Seq(goodJar), forecastTool, "--contract")
      assert(r == Shell.Success(0), out)
      assert(out.contains(""""staticOperands":{"net":{"known":["api.weather.example"],"unresolved":false}"""), out)
      assert(out.contains(""""env":{"known":["WEATHER_KEY"],"unresolved":false}"""), out)
    }

    it("adds to the operands read off literals, not instead of them") {
      val (r, out) = run(Seq(goodJar),
        s"""import { $Weather }
           |tool both(): Int requires { net, env, console } {
           |  IO::println(Weather::forecast("x"))
           |  IO::println(Http::get("https://literal.example.org/x"))
           |  return 0
           |}
           |""".stripMargin, "--plan")
      assert(r == Shell.Success(0), out)
      val lines = out.linesIterator.map(_.trim).toSeq
      assert(lines.contains("net     api.weather.example"), out)
      assert(lines.contains("net     literal.example.org"), out)
    }
  }

  describe("the rules a library table is held to") {
    it("ignores lines naming classes outside the jar, with one W0018 naming the jar and line") {
      val r = compile(Seq(goodJar), forecastTool)
      val ws = r.diagnostics.warnings.filter(_.category.code == "W0018")
      assert(ws.size == 1, r.diagnostics.warnings)
      val w = ws.head
      assert(w.sourceFile.contains(goodJar.getFileName.toString), w)
      assert(w.sourceFile.endsWith("META-INF/onion/effect-table.txt"), w)
      assert(w.location.line == 5, w) // the Thief line: the first foreign class
      assert(w.message.contains("com.example.other.Thief"), w.message)
      assert(w.message.contains("java.lang.String"), w.message)
      val table = EffectTable.forClasspath(Seq(goodJar.toString)).table
      assert(table.lookup("com.example.other.Thief", "steal").isEmpty)
    }

    it("never lets a library reclassify a built-in onion.*/java.* entry") {
      // The jar does contain a class file named onion/Files.class, so the ownership rule
      // alone would admit the line; precedence is what keeps the built-in verdict.
      val impostor = jar("impostor", Seq("onion.Files", "onion.fxlib.Thing"),
        """onion.Files#readText=pure
          |onion.Files#*=pure
          |onion.fxlib.Thing#call=net:thing.example
          |""".stripMargin)
      val table = EffectTable.forClasspath(Seq(impostor.toString)).table
      assert(table.lookup("onion.Files", "readText").contains(Set(Effect.Read)))
      assert(table.lookup("onion.Files", "writeText").contains(Set(Effect.Write)))
      // An onion.* class the built-in table does not cover is the library's to classify.
      assert(table.lookup("onion.fxlib.Thing", "call").contains(Set(Effect.Net)))
      assert(table.operands("onion.fxlib.Thing", "call") == Seq(Effect.Net -> "thing.example"))
    }

    it("lets the first jar on the classpath that classifies a class own it") {
      val second = jar("second", Seq(Weather), s"$Weather#*=exec\n")
      val table = EffectTable.forClasspath(Seq(goodJar.toString, second.toString)).table
      assert(table.lookup(Weather, "forecast").contains(Set(Effect.Net, Effect.Env)))
      assert(table.lookup(Weather, "other").isEmpty) // goodJar has no wildcard; second does not get a say
      val reversed = EffectTable.forClasspath(Seq(second.toString, goodJar.toString)).table
      assert(reversed.lookup(Weather, "forecast").contains(Set(Effect.Exec)))
    }

    it("reports a malformed table as W0017 naming the jar and line, ignores it whole, and does not crash") {
      val r = compile(Seq(malformedJar), forecastTool)
      // The whole table is gone, so the call is unknown again: the tool fails E0077,
      // and the warning that explains why survives the failed compile.
      assert(errorCodes(r).contains("E0077"), errorCodes(r))
      val ws = r.diagnostics.warnings.filter(_.category.code == "W0017")
      assert(ws.size == 1, r.diagnostics.warnings)
      assert(ws.head.sourceFile.contains(malformedJar.getFileName.toString), ws.head)
      assert(ws.head.location.line == 2, ws.head)
      assert(ws.head.message.contains("nett"), ws.head.message)
      // Even the well-formed line before it is not used.
      val table = EffectTable.forClasspath(Seq(malformedJar.toString)).table
      assert(table.lookup(Weather, "local").isEmpty)
    }

    it("is admitted by `requires { unknown }` when the table is malformed") {
      val r = compile(Seq(malformedJar),
        s"""import { $Weather }
           |tool weather(city: String): Int requires { unknown, console } {
           |  IO::println(Weather::forecast(city))
           |  return 0
           |}
           |""".stripMargin)
      assert(!r.hasErrors, r.diagnostics.allErrors.map(_.message).mkString("\n"))
      assert(warningCodes(r).contains("W0017"), warningCodes(r))
    }

    it("is not loaded at all for a program without a tool") {
      val r = compile(Seq(malformedJar), s"""import { $Weather }\nIO::println(Weather::local())\n""")
      assert(!r.hasErrors)
      assert(!warningCodes(r).contains("W0017"), warningCodes(r))
    }

    it("can be silenced with --Wno") {
      val config = CompilerConfig(Seq(malformedJar.toString), null, "UTF-8", "", 100,
        suppressedWarnings = Set(onion.compiler.WarningCategory.LibraryEffectTableMalformed))
      val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(
        s"""import { $Weather }
           |tool weather(city: String): Int requires { unknown, console } {
           |  IO::println(Weather::forecast(city))
           |  return 0
           |}
           |""".stripMargin), "Lib.on")))
      assert(!r.hasErrors)
      assert(!warningCodes(r).contains("W0017"), warningCodes(r))
    }

    it("is cached per jar, and re-read when the jar changes") {
      val dir = Files.createTempDirectory("onion-effect-cache")
      val path = dir.resolve("lib.jar")
      writeJar(path, Seq(Weather), s"$Weather#*=net\n")
      assert(EffectTable.forClasspath(Seq(path.toString)).table.lookup(Weather, "x").contains(Set(Effect.Net)))
      writeJar(path, Seq(Weather), s"$Weather#*=exec,rand\n")
      // Size differs, so the cache key does too, whatever the clock's resolution.
      assert(EffectTable.forClasspath(Seq(path.toString)).table.lookup(Weather, "x").contains(Set(Effect.Exec, Effect.Rand)))
    }

    it("skips directories and non-jar files on the classpath") {
      val dir = Files.createTempDirectory("onion-effect-dir")
      val notJar = Files.writeString(dir.resolve("notes.txt"), "hello")
      val loaded = EffectTable.forClasspath(Seq(dir.toString, notJar.toString, dir.resolve("missing.jar").toString))
      assert(loaded.problems.isEmpty)
      assert(loaded.table.lookup("onion.Files", "readText").contains(Set(Effect.Read)))
    }
  }

  describe("the line grammar") {
    it("parses effect:operand pairs, and bare effects as before") {
      val t = EffectTable.parseLines(Iterator(
        "a.B#m=net:api.example.com, env:API_KEY ,clock",
        "a.B#*=net:one.example,net:two.example",
        "a.B#p=pure"))
      assert(t.lookup("a.B", "m").contains(Set(Effect.Net, Effect.Env, Effect.Clock)))
      assert(t.operands("a.B", "m") == Seq(Effect.Net -> "api.example.com", Effect.Env -> "API_KEY"))
      assert(t.operands("a.B", "other") == Seq(Effect.Net -> "one.example", Effect.Net -> "two.example"))
      // A specific entry replaces the wildcard's operands along with its effects.
      assert(t.operands("a.B", "p").isEmpty)
    }

    it("keeps everything after the first ':' as the operand") {
      val t = EffectTable.parseLines(Iterator("a.B#m=net:api.example.com:8443"))
      assert(t.operands("a.B", "m") == Seq(Effect.Net -> "api.example.com:8443"))
    }

    it("rejects an empty operand, an operand on unknown, and an empty item, naming source and line") {
      for (bad <- Seq("a.B#m=net:", "a.B#m=unknown:x", "a.B#m=net,", "a.B#m=nett:host")) {
        val e = intercept[EffectTable.MalformedTableException](
          EffectTable.parseLines(Iterator("# c", bad), "lib.jar!/META-INF/onion/effect-table.txt"))
        assert(e.line == 2, bad)
        assert(e.getMessage.startsWith("lib.jar!/META-INF/onion/effect-table.txt:2:"), e.getMessage)
      }
    }
  }
}

object LibraryEffectTableSpec {
  /** A jar holding an empty public class for each name, plus `table` as its effect table. */
  def jar(name: String, classes: Seq[String], table: String): Path = {
    val path = Files.createTempDirectory("onion-effect-lib").resolve(s"$name.jar")
    writeJar(path, classes, table)
    path
  }

  def writeJar(path: Path, classes: Seq[String], table: String): Unit = {
    val out = new JarOutputStream(Files.newOutputStream(path))
    try {
      for (cls <- classes) {
        out.putNextEntry(new JarEntry(cls.replace('.', '/') + ".class"))
        out.write(classBytes(cls.replace('.', '/')))
        out.closeEntry()
      }
      out.putNextEntry(new JarEntry(EffectTable.LibraryResourcePath))
      out.write(table.getBytes(UTF_8))
      out.closeEntry()
    } finally out.close()
  }

  /**
   * `public class <name> { public static String forecast(String c); public static String
   * local(); }` — enough surface for every test here; the bodies return a constant.
   */
  def classBytes(internalName: String): Array[Byte] = {
    val writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null)
    for ((method, descriptor) <- Seq("forecast" -> "(Ljava/lang/String;)Ljava/lang/String;",
                                     "local" -> "()Ljava/lang/String;")) {
      val mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, descriptor, null, null)
      mv.visitCode()
      mv.visitLdcInsn("sunny")
      mv.visitInsn(Opcodes.ARETURN)
      mv.visitMaxs(0, 0)
      mv.visitEnd()
    }
    writer.visitEnd()
    writer.toByteArray
  }
}
