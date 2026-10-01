package onion.compiler.tools

import onion.tools.Shell

/**
 * `--plan` prints the operands it can read off the source (FRICTION F11): a literal
 * URL's host, a `Proc` command name, a `System::getenv` variable name, a literal path.
 * The list is a lower bound — a call site whose operand is not a literal (or a literal
 * prefix, or a once-assigned `val` of one) still reports `(operand not statically
 * known)`, and nothing about parameter-bound capabilities changes.
 *
 * Plan text is not localized (ToolCli prints English only), so these assertions hold in
 * every locale. Nothing here touches the network or starts a process: `--plan` runs
 * nothing.
 */
class ToolPlanStaticOperandsSpec extends AbstractShellSpec {

  private val Unknown = "(operand not statically known)"

  /** The plan lines for `effect`, with the effect name and padding stripped. */
  private def operands(out: String, effect: String): Seq[String] =
    out.linesIterator.map(_.trim).collect {
      case l if l == effect => ""
      case l if l.startsWith(effect + " ") => l.substring(effect.length).trim
    }.toSeq

  describe("--plan with statically known operands") {
    it("shows the host of a literal URL") {
      val (r, out) = run(
        """tool ping(): Int
          |  requires { net, console }
          |{
          |  IO::println(Http::get("https://api.example.com/v1/status"))
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq("api.example.com"), out)
      assert(!out.contains(Unknown), out)
      assert(out.contains("nothing was executed"), out)
    }

    it("reads the host off a literal prefix of a concatenation or an interpolation") {
      val (r, out) = run(
        """tool search(q: String): Int
          |  requires { net, console }
          |{
          |  IO::println(Http::get("https://api.github.com/search/issues?q=" + q + "&per_page=100"))
          |  IO::println(Http::get("https://interp.example.org/find/#{q}"))
          |  return 0
          |}
          |""".stripMargin, "x", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq("api.github.com", "interp.example.org"), out)
    }

    it("follows a local val initialised with a literal (prefix)") {
      val (r, out) = run(
        """tool fetch(since: String): Int
          |  requires { net, console }
          |{
          |  val base = "https://val.example.net"
          |  val url = base + "/items?since=" + since
          |  IO::println(Http::get(url))
          |  return 0
          |}
          |""".stripMargin, "2026-01-01", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq("val.example.net"), out)
    }

    it("finds literal operands in program-defined helpers the tool calls") {
      val (r, out) = run(
        """class Gh {
          |public:
          |  static def list(kind: String): String =
          |    Proc::capture("gh", kind, "list", "--repo", "onion-lang/onion").stdout()
          |  static def status(): String = Proc::captureIn(".", "git", "status").stdout()
          |}
          |tool digest(): Int
          |  requires { exec, console }
          |{
          |  IO::println(Gh::list("pr") + Gh::status())
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "exec") == Seq("gh", "git"), out)
    }

    it("names the variable of System::getenv, and a URL taken from one as $NAME") {
      val (r, out) = run(
        """tool notify(): Int
          |  requires { net, env, console }
          |{
          |  val token = System::getenv("GITHUB_TOKEN")
          |  val hook = System::getenv("SLACK_WEBHOOK_URL")
          |  if hook != null {
          |    IO::println(Http::postJson(hook, "{}"))
          |  }
          |  IO::println(token ?: "none")
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "env") == Seq("GITHUB_TOKEN", "SLACK_WEBHOOK_URL"), out)
      assert(operands(out, "net") == Seq("$SLACK_WEBHOOK_URL"), out)
    }

    it("shows literal paths for a bare read/write capability") {
      val (r, out) = run(
        """tool touch(): Int
          |  requires { read, write }
          |{
          |  Files::writeText("out/log.txt", Files::readText("config/app.conf"))
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "read") == Seq("config/app.conf"), out)
      assert(operands(out, "write") == Seq("out/log.txt"), out)
    }

    it("reads the host of an Http::request builder, through its steps and a val") {
      val (r, out) = run(
        """tool ask(prompt: String): Int
          |  requires { net, env, console }
          |{
          |  val res = Http::request("POST", "https://api.example.com/v1/messages")
          |    .header("content-type", "application/json")
          |    .header("x-api-key", System::getenv("API_KEY") ?: "")
          |    .body(prompt)
          |    .timeoutSeconds(120)
          |    .send()
          |  val base = Http::request("GET", "https://builder.example.org/" + prompt)
          |  val status = base.headers(["accept", "text/plain"]).send().status
          |  IO::println(res.status + status)
          |  return 0
          |}
          |""".stripMargin, "hi", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq("api.example.com", "builder.example.org"), out)
      assert(!out.contains(Unknown), out)
    }

    it("leaves an Http::request whose URL is a parameter unresolved") {
      val (r, out) = run(
        """tool hit(url: String): Int
          |  requires { net, console }
          |{
          |  IO::println(Http::request("DELETE", url).send().status)
          |  return 0
          |}
          |""".stripMargin, "https://x.example.com", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq(Unknown), out)
    }

    it("never prints user-info from a literal URL") {
      val (r, out) = run(
        """tool secret(): Int
          |  requires { net, console }
          |{
          |  IO::println(Http::get("https://bot:hunter2@internal.example.com/x"))
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq("internal.example.com"), out)
      assert(!out.contains("hunter2"), out)
    }
  }

  describe("--plan stays a lower bound") {
    it("still prints (operand not statically known) for a non-literal operand") {
      val (r, out) = run(
        """tool get(host: String): Int
          |  requires { net, console }
          |{
          |  var url = "https://a.example.com/"
          |  url = "https://" + host + "/"
          |  IO::println(Http::get(url))
          |  return 0
          |}
          |""".stripMargin, "h", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq(Unknown), out)
    }

    it("does not read a host out of a prefix that stops inside it") {
      val (r, out) = run(
        """tool get(sub: String): Int
          |  requires { net, console }
          |{
          |  IO::println(Http::get("https://api." + sub + "/"))
          |  return 0
          |}
          |""".stripMargin, "x", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq(Unknown), out)
    }

    it("lists the known operands AND says some are unknown when both occur") {
      val (r, out) = run(
        """tool mixed(url: String): Int
          |  requires { net, console }
          |{
          |  IO::println(Http::get("https://known.example.com/a"))
          |  IO::println(Http::get(url))
          |  return 0
          |}
          |""".stripMargin, "https://elsewhere.example.com", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq("known.example.com", Unknown), out)
    }

    it("does not follow a literal passed into a helper's parameter") {
      val (r, out) = run(
        """class Api {
          |public:
          |  static def fetch(u: String): String = Http::get(u)
          |}
          |tool go(): Int
          |  requires { net, console }
          |{
          |  IO::println(Api::fetch("https://looks-known.example.com/"))
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "net") == Seq(Unknown), out)
      assert(!out.contains("looks-known"), out)
    }

    it("leaves ambient env bare when no variable name is known") {
      val (r, out) = run(
        """tool home(): Int
          |  requires { env, console }
          |{
          |  IO::println(System::getProperty("user.home"))
          |  return 0
          |}
          |""".stripMargin, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "env") == Seq(""), out)
    }
  }

  describe("parameter-bound capabilities") {
    it("render exactly as before") {
      val dir = java.nio.file.Files.createTempDirectory("onion-plan-static")
      val src = dir.resolve("in.txt").toString; val dst = dir.resolve("out.txt").toString
      val (r, out) = run(
        """tool copy(src: String, dst: String): Int
          |  requires { read(src), write(dst) }
          |{
          |  Files::writeText(dst, Files::readText(src))
          |  return 0
          |}
          |""".stripMargin, src, dst, "--plan")
      assert(Shell.Success(0) == r, r.toString)
      val expected =
        "plan: `copy` would\n" +
        "  read    derived from src = " + src + "\n" +
        "  write   derived from dst = " + dst + "\n" +
        "(nothing was executed; operands are the arguments the effects are\n" +
        " derived from, not necessarily the exact paths or hosts touched)\n"
      assert(out.replace("\r\n", "\n") == expected, out)
    }

    it("add a literal operand of the same effect below the parameter line") {
      val (r, out) = run(
        """tool save(out: String): Int
          |  requires { write(out) }
          |{
          |  Files::writeText(out, "data")
          |  Files::appendText("audit.log", "saved")
          |  return 0
          |}
          |""".stripMargin, "result.txt", "--plan")
      assert(Shell.Success(0) == r, r.toString)
      assert(operands(out, "write") == Seq("derived from out = result.txt", "audit.log"), out)
    }
  }

  describe("--contract") {
    it("is unchanged for a tool with no statically known operand") {
      val (r, out) = run(
        """tool copy(src: String, dst: String): Int
          |  requires { read(src), write(dst) }
          |{
          |  Files::writeText(dst, Files::readText(src))
          |  return 0
          |}
          |""".stripMargin, "--contract")
      assert(Shell.Success(0) == r, r.toString)
      assert(out.trim ==
        """[{"tool":"copy","params":[{"name":"src","type":"String","role":"positional"},""" +
        """{"name":"dst","type":"String","role":"positional"}],"returns":"Int",""" +
        """"capabilities":["read(src)","write(dst)"]}]""", out)
    }

    it("adds staticOperands as one extra key, keeping every existing key as it was") {
      val (r, out) = run(
        """tool ping(n: Int = 1): Int
          |  requires { net, exec, console }
          |{
          |  IO::println(Http::get("https://api.example.com/v1/status"))
          |  IO::println(Proc::capture("gh", "--version").stdout())
          |  return n
          |}
          |""".stripMargin, "--contract")
      assert(Shell.Success(0) == r, r.toString)
      val json = out.trim
      assert(json.startsWith(
        """[{"tool":"ping","params":[{"name":"n","type":"Int","role":"flag","default":"1"}],""" +
        """"returns":"Int","capabilities":["net","exec","console"],"""), json)
      assert(json.contains(
        """"staticOperands":{"net":{"known":["api.example.com"],"unresolved":false},""" +
        """"exec":{"known":["gh"],"unresolved":false}}"""), json)
      // Still one well-formed contract a JSON reader accepts.
      val parsed = onion.Json.parse(json).asInstanceOf[java.util.List[?]]
      assert(parsed.size == 1, json)
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
