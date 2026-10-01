package onion.compiler.effects

import onion.compiler.TypedAST
import onion.compiler.TypedAST._

import scala.collection.mutable

/**
 * Statically known operands for `--plan` (FRICTION F11).
 *
 * A capability tied to a tool parameter (`write(out)`) already tells `--plan` what the
 * effect is about. A bare one (`net`, `exec`, `env`) used to print
 * `(operand not statically known)` even when the operand sat in the source as a
 * literal. This object reads those literals off the typed AST: for every external call
 * reachable from a tool (the tool's body, closures it creates, and program-defined
 * methods it calls, transitively), a small table says which argument is the operand of
 * which effect, and a light resolver decides whether that argument is known.
 *
 * The resolver knows exactly these forms, and nothing else:
 *
 *   - a string literal
 *   - a concatenation or interpolation whose leading part is a literal (a PREFIX:
 *     `"https://api.example.com/q=" + x`); only a URL's host is read off a prefix
 *   - a local `val` of the same body, assigned exactly once, whose initializer is one
 *     of these forms
 *   - `System::getenv("NAME")`, rendered `$NAME` where it is used as an operand
 *
 * Soundness: the result is a LOWER BOUND. Every known operand is one the program can
 * really reach, but not every operand is known. Each effect carries `unresolved`, set
 * when at least one contributing call site's operand could not be determined (or the
 * effect came from somewhere with no call site to read, such as a super-constructor
 * call), so the plan can say so instead of implying the list is complete. Operands
 * passed INTO a program-defined helper are not followed (no interprocedural binding):
 * a helper that forwards its parameter to `Http::get` is unresolved even when every
 * caller passes a literal.
 */
object StaticOperands {

  /** What is known about one effect's operands in one tool. */
  final case class Operands(known: Seq[String], unresolved: Boolean)

  /** A statically known string, or what is known about it. */
  private sealed trait Str
  private final case class Exact(value: String) extends Str
  private final case class Prefix(value: String) extends Str
  private final case class EnvVar(name: String) extends Str

  /** Resolution depth bound: a `val` chain longer than this is not followed. */
  private val Fuel = 16

  private val FilesReadPath: Set[String] =
    Set("readText", "readLines", "readBytes", "exists", "isDirectory", "isFile", "size",
        "list", "listFiles", "glob")
  private val FilesWritePath: Set[String] =
    Set("writeText", "writeBytes", "writeLines", "appendText", "delete", "mkdirs")
  private val FilesCopyLike: Set[String] = Set("copy", "copyDir", "move")
  private val HttpUrlFirst: Set[String] =
    Set("get", "post", "put", "delete", "postJson", "getResponse", "postResponse")
  /** `Http.Request` methods that return a copy with the same URL (the builder steps). */
  private val HttpRequestSteps: Set[String] =
    Set("header", "headers", "body", "timeoutSeconds", "timeoutMillis")

  /**
   * The statically known operands of `tool`, per effect, in [[Effect.all]] order. Only
   * effects with at least one known operand appear; for the rest the plan keeps its
   * previous rendering.
   */
  def forTool(tool: MethodDefinition, units: collection.Set[EffectInference.Unit0],
              classes: Seq[ClassDefinition]): Seq[(Effect, Operands)] = {
    val known = mutable.LinkedHashMap[Effect, mutable.LinkedHashSet[String]]()
    val unresolved = mutable.Set[Effect]()
    val seen = mutable.Set[EffectInference.Unit0](tool)
    val queue = mutable.Queue[EffectInference.Unit0](tool)
    while (queue.nonEmpty) {
      val unit = queue.dequeue()
      val facts = EffectInference.bodyFacts(unit, units, classes)
      val locals = singleAssignmentVals(unit, facts)
      unresolved ++= facts.unsitedEffects
      for (site <- facts.sites; effect <- site.effects) {
        operand(site, effect, locals) match {
          case Some(op) => known.getOrElseUpdate(effect, mutable.LinkedHashSet[String]()) += op
          case None     => unresolved += effect
        }
      }
      for (callee <- facts.callees if !seen.contains(callee)) {
        seen += callee
        queue.enqueue(callee)
      }
    }
    Effect.all.flatMap { e =>
      known.get(e).filter(_.nonEmpty).map(ops => e -> Operands(ops.toSeq, unresolved.contains(e)))
    }
  }

  /**
   * The contract fragment for `operands` — `"staticOperands":{"net":{"known":[...],
   * "unresolved":false},...}` — or None when nothing is known (the contract then stays
   * byte-for-byte what it was).
   */
  def contractFragment(operands: Seq[(Effect, Operands)]): Option[String] =
    if (operands.isEmpty) None
    else Some(operands.map { case (e, ops) =>
      val known = ops.known.map(k => "\"" + jsonEscape(k) + "\"").mkString("[", ",", "]")
      s""""${e.name}":{"known":$known,"unresolved":${ops.unresolved}}"""
    }.mkString("\"staticOperands\":{", ",", "}"))

  /**
   * Inserts `fragment` as the last key of `tool`'s entry in the contract JSON built by
   * Rewriting (`{"tool":"name","params":...,"capabilities":[...]}`). Returns the
   * contract unchanged if the entry cannot be found — a plan without literal operands
   * is the old plan, never a broken one.
   */
  def insertIntoContract(contract: String, toolName: String, fragment: String): String = {
    val head = "{\"tool\":\"" + jsonEscape(toolName) + "\",\"params\":"
    val start = contract.indexOf(head)
    if (start < 0) return contract
    val capsKey = "\"capabilities\":["
    val capsAt = contract.indexOf(capsKey, start)
    if (capsAt < 0) return contract
    // Capability entries are `name` or `name(param)` (anything else is E0079 and
    // never reaches here), so the first `]}` after the key closes this entry.
    val close = contract.indexOf("]}", capsAt + capsKey.length)
    if (close < 0) return contract
    contract.substring(0, close + 1) + "," + fragment + contract.substring(close + 1)
  }

  def jsonEscape(s: String): String =
    s.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => f"\\u${c.toInt}%04x"
      case c    => c.toString
    }

  // ---- locals ------------------------------------------------------------------

  /** Locals of the unit's own frame that are immutable and written exactly once, at
   *  the body's top frame (not from inside a closure), mapped to their initializer. */
  private def singleAssignmentVals(unit: EffectInference.Unit0,
                                   facts: EffectInference.BodyFacts): Map[Int, Term] = {
    val frame = unit match {
      case md: MethodDefinition       => md.getFrame
      case cd: ConstructorDefinition  => cd.frame
      case _                          => null
    }
    if (frame == null) return Map.empty
    val immutable = frame.allBindings.filter(b => !b.isMutable).map(_.index).toSet
    facts.localWrites.groupBy(_._1).collect {
      case (index, Seq((_, value, 0))) if immutable.contains(index) => index -> value
    }
  }

  // ---- the operand table ---------------------------------------------------------

  private def operand(site: EffectInference.ExternalSite, effect: Effect,
                      locals: Map[Int, Term]): Option[String] = {
    def arg(i: Int): Option[Str] =
      if (i < site.args.length) resolve(site.args(i), locals, site.depth, Fuel) else None
    def stringParam(i: Int): Boolean =
      site.method != null && i < site.method.arguments.length && isString(site.method.arguments(i))

    (site.className, site.methodName, effect) match {
      case ("onion.Files", m, Effect.Read) if FilesReadPath(m) && stringParam(0)  => path(arg(0))
      case ("onion.Files", m, Effect.Write) if FilesWritePath(m) && stringParam(0) => path(arg(0))
      case ("onion.Files", m, Effect.Read) if FilesCopyLike(m) && stringParam(0)   => path(arg(0))
      case ("onion.Files", m, Effect.Write) if FilesCopyLike(m) && stringParam(1)  => path(arg(1))
      case ("onion.FileResource", _, Effect.Read | Effect.Write) =>
        path(resource(site.target, locals, site.depth, "file", "onion.FileResource", Fuel))
      case ("onion.Http", m, Effect.Net) if HttpUrlFirst(m) && stringParam(0) => host(arg(0))
      case ("onion.HttpResource", _, Effect.Net) =>
        host(resource(site.target, locals, site.depth, "http", "onion.HttpResource", Fuel))
      case ("onion.Http$Request", "send", Effect.Net) =>
        host(httpRequest(site.target, locals, site.depth, Fuel))
      case ("onion.Net", "connect", Effect.Net) if stringParam(0) => exactOrEnv(arg(0))
      case ("onion.Proc", "capture" | "run" | "exec", Effect.Exec)          => command(site, 0, locals)
      case ("onion.Proc", "captureIn" | "runIn" | "execIn", Effect.Exec)    => command(site, 1, locals)
      case ("java.lang.System", "getenv", Effect.Env) if site.args.length == 1 => envName(arg(0))
      case ("onion.Config", "getEnv", Effect.Env) if stringParam(0)            => envName(arg(0))
      case _ => None
    }
  }

  private def isString(t: TypedAST.Type): Boolean = t match {
    case null              => false
    case n: NullableType   => isString(n.innerType)
    case other             => other.name == "java.lang.String"
  }

  /** The command name of a varargs `Proc` call: the first element of the packed array. */
  private def command(site: EffectInference.ExternalSite, index: Int,
                      locals: Map[Int, Term]): Option[String] =
    if (index >= site.args.length) None
    else site.args(index) match {
      case a: NewArrayWithValues if a.values.nonEmpty =>
        exactOrEnv(resolve(a.values(0), locals, site.depth, Fuel))
      case _ => None
    }

  private def exactOrEnv(s: Option[Str]): Option[String] = s match {
    case Some(Exact(v)) if v.nonEmpty => Some(v)
    case Some(EnvVar(n))              => Some("$" + n)
    case _                            => None
  }

  private def path(s: Option[Str]): Option[String] = exactOrEnv(s)

  private def envName(s: Option[Str]): Option[String] = s match {
    case Some(Exact(v)) if v.nonEmpty => Some(v)
    case _                            => None
  }

  private val Scheme = """^[A-Za-z][A-Za-z0-9+.\-]*://""".r

  /** The host (with port, without any user-info) of a known URL. A prefix counts only
   *  when it already runs past the authority (`/`, `?` or `#` follows the host). */
  private def host(s: Option[Str]): Option[String] = s match {
    case Some(EnvVar(n))  => Some("$" + n)
    case Some(Exact(u))   => authority(u, complete = true)
    case Some(Prefix(u))  => authority(u, complete = false)
    case _                => None
  }

  private def authority(url: String, complete: Boolean): Option[String] =
    Scheme.findPrefixMatchOf(url).flatMap { m =>
      val rest = url.substring(m.end)
      val end = rest.indexWhere(c => c == '/' || c == '?' || c == '#')
      if (end < 0 && !complete) None
      else {
        val auth = if (end < 0) rest else rest.substring(0, end)
        val hostPort = auth.substring(auth.lastIndexOf('@') + 1)
        if (hostPort.isEmpty) None else Some(hostPort)
      }
    }

  // ---- the resolver --------------------------------------------------------------

  private def is(m: TypedAST.Method, cls: String, name: String): Boolean =
    m != null && m.name == name && m.affiliation != null && m.affiliation.name == cls

  private def resolve(t: Term, locals: Map[Int, Term], depth: Int, fuel: Int): Option[Str] =
    if (fuel <= 0) None
    else t match {
      case s: StringValue   => Option(s.value).map(Exact(_))
      case a: AsInstanceOf  => resolve(a.target, locals, depth, fuel - 1)
      case n: NonNullAssert => resolve(n.target, locals, depth, fuel - 1)
      case r: RefLocal if r.frame == depth =>
        // The initializer was written at the body's top frame (depth 0).
        locals.get(r.index).flatMap(v => resolve(v, locals, 0, fuel - 1))
      case c: CallStatic if is(c.method, "java.lang.String", "valueOf") && c.parameters.length == 1 =>
        resolve(c.parameters(0), locals, depth, fuel - 1)
      case c: CallStatic if is(c.method, "java.lang.System", "getenv") && c.parameters.length == 1 =>
        resolve(c.parameters(0), locals, depth, fuel - 1) match {
          case Some(Exact(n)) if n.nonEmpty => Some(EnvVar(n))
          case _                            => None
        }
      case c: Call if is(c.method, "java.lang.String", "concat") && c.parameters.length == 1 =>
        concat(resolve(c.target, locals, depth, fuel - 1), resolve(c.parameters(0), locals, depth, fuel - 1))
      case c: Call if is(c.method, "java.lang.StringBuilder", "toString") && c.parameters.isEmpty =>
        builder(c.target, locals, depth, fuel - 1)
      case _ => None
    }

  /** A fresh `StringBuilder` chain, as string interpolation lowers to. */
  private def builder(t: Term, locals: Map[Int, Term], depth: Int, fuel: Int): Option[Str] =
    if (fuel <= 0) None
    else t match {
      case n: NewObject if n.parameters.isEmpty && n.constructor.affiliation != null
          && n.constructor.affiliation.name == "java.lang.StringBuilder" => Some(Exact(""))
      case c: Call if is(c.method, "java.lang.StringBuilder", "append") && c.parameters.length == 1 =>
        concat(builder(c.target, locals, depth, fuel - 1), resolve(c.parameters(0), locals, depth, fuel - 1))
      case _ => None
    }

  /** Only an exact left part survives a concatenation; anything unknown after it turns
   *  the result into a prefix, and anything unknown before it loses everything. */
  private def concat(left: Option[Str], right: Option[Str]): Option[Str] = (left, right) match {
    case (Some(Exact(a)), Some(Exact(b))) => Some(Exact(a + b))
    case (Some(Exact(a)), _)              => if (a.nonEmpty) Some(Prefix(a)) else None
    case (Some(Prefix(a)), _)             => Some(Prefix(a))
    case _                                => None
  }

  /** The URL an `Http.Request` receiver was built from: back through its builder steps
   *  (`.header(..)`, `.body(..)`, ...) and once-assigned `val`s to `Http::request(m, url)`. */
  private def httpRequest(t: Term, locals: Map[Int, Term], depth: Int, fuel: Int): Option[Str] =
    if (fuel <= 0 || t == null) None
    else t match {
      case a: AsInstanceOf  => httpRequest(a.target, locals, depth, fuel - 1)
      case n: NonNullAssert => httpRequest(n.target, locals, depth, fuel - 1)
      case r: RefLocal if r.frame == depth =>
        locals.get(r.index).flatMap(v => httpRequest(v, locals, 0, fuel - 1))
      case c: Call if c.method != null && HttpRequestSteps(c.method.name) && c.method.affiliation != null
          && c.method.affiliation.name == "onion.Http$Request" =>
        httpRequest(c.target, locals, depth, fuel - 1)
      case c: CallStatic if is(c.method, "onion.Http", "request") && c.parameters.length == 2 =>
        resolve(c.parameters(1), locals, depth, fuel - 1)
      case _ => None
    }

  /** The path/URL a `FileResource`/`HttpResource` receiver was built from. */
  private def resource(t: Term, locals: Map[Int, Term], depth: Int, factory: String,
                       cls: String, fuel: Int): Option[Str] =
    if (fuel <= 0 || t == null) None
    else t match {
      case a: AsInstanceOf  => resource(a.target, locals, depth, factory, cls, fuel - 1)
      case n: NonNullAssert => resource(n.target, locals, depth, factory, cls, fuel - 1)
      case r: RefLocal if r.frame == depth =>
        locals.get(r.index).flatMap(v => resource(v, locals, 0, factory, cls, fuel - 1))
      case c: CallStatic if is(c.method, "onion.Resources", factory) && c.parameters.length == 1 =>
        resolve(c.parameters(0), locals, depth, fuel - 1)
      case n: NewObject if n.parameters.length == 1 && n.constructor.affiliation != null
          && n.constructor.affiliation.name == cls =>
        resolve(n.parameters(0), locals, depth, fuel - 1)
      case _ => None
    }
}
