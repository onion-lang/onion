package onion.compiler.tools

import onion.tools.Shell

/**
 * `tool name(args) [: T] [requires { caps }] { body }` documents the return type as
 * optional, and the grammar backs that up (`tool_decl`'s `[":" ty=return_type()]` is
 * genuinely optional). But the type checker's `processFunctionDeclaration` treated a
 * missing return type as always an error (E0051), so a `tool` written without `: T`
 * failed to compile even though nothing about the `tool` form requires one — an
 * omitted return type should default to `void`, same as it would if the author had
 * written `: void` explicitly.
 */
class ToolImplicitVoidReturnSpec extends AbstractShellSpec {

  describe("a tool with no declared return type") {
    it("compiles and runs, defaulting to void") {
      val (r, out) = run(
        """tool greet(name: String) requires { console } {
          |  IO::println("Hello, " + name)
          |}
          |""".stripMargin, "world")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.contains("Hello, world"), out)
    }
  }

  private def run(source: String, args: String*): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) {
          shell.run(source, "None", args.toArray)
        }}
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, new String(buf.toByteArray, "UTF-8"))
  }
}
