package onion.compiler.tools

import onion.tools.Shell

/** Writing a lambda's parameter type must not change which extension overload runs (#2463,
 *  panel block on #2489): the typed fast path must not pick `w(Object)` when the lambda,
 *  re-typed against the function overload, fits it. */
class ExtensionLambdaAnnotationOverloadSpec extends AbstractShellSpec {
  private def picks(fnParam: String, call: String): Shell.Result = {
    val src =
      "extension String {\n" +
      "  def w(o: Object): String = \"obj\"\n" +
      "  def w(f: " + fnParam + "): String = \"fn:\" + f(\"x\")\n" +
      "}\n" +
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" +
      "    val r: String = " + call + "\n" +
      "    if r == \"fn:x!\" { return 1 }\n" +
      "    if r == \"obj\" { return 2 }\n" +
      "    return 0\n  }\n}\n"
    shell.run(src, "ExtLambdaAnnot.on", Array())
  }

  describe("an annotated and an unannotated lambda passed to w(Object) / w(function)") {
    it("both pick the function overload when it returns Object") {
      assert(picks("(String) -> Object", "\"a\".w((s: String) -> s + \"!\")") == Shell.Success(1))
      assert(picks("(String) -> Object", "\"a\".w(s -> s + \"!\")") == Shell.Success(1))
    }

    it("both pick the function overload when it returns CharSequence") {
      assert(picks("(String) -> CharSequence", "\"a\".w((s: String) -> s + \"!\")") == Shell.Success(1))
      assert(picks("(String) -> CharSequence", "\"a\".w(s -> s + \"!\")") == Shell.Success(1))
    }

    it("both pick the function overload when its type matches the lambda exactly") {
      assert(picks("(String) -> String", "\"a\".w((s: String) -> s + \"!\")") == Shell.Success(1))
      assert(picks("(String) -> String", "\"a\".w(s -> s + \"!\")") == Shell.Success(1))
    }
  }
}
