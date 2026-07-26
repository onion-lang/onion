package onion.compiler.effects

import org.scalatest.funspec.AnyFunSpec
import scala.jdk.CollectionConverters._

/**
 * Drift guard for the effect walker (issue #356).
 *
 * `EffectInference`'s Collector enumerates typed-AST nodes by hand — this compiler has
 * no generic traversal — so a node added to `TypedAST.scala` without a matching walker
 * arm would silently drop the effects under it. This spec scans the source of
 * `TypedAST.scala` for every concrete `Term`/`ActionStatement` subclass and fails when
 * one is missing from `EffectInference.handledNodes`.
 */
class EffectWalkerCompletenessSpec extends AnyFunSpec {

  private def declaredNodes: Set[String] = {
    val src = java.nio.file.Files.readAllLines(
      java.nio.file.Path.of("src/main/scala/onion/compiler/TypedAST.scala")).asScala.mkString("\n")
    // The parameter list may span lines and contain one level of nested parens
    // (tuple types like `Array[(ClosureLocalBinding, Term)]` in `Try`).
    val decl = """(?s)class\s+([A-Za-z0-9_]+)\s*\((?:[^()]|\([^()]*\))*\)\s*extends\s+(?:TypedAST\.)?(Term|ActionStatement)\(""".r
    decl.findAllMatchIn(src).map(_.group(1)).toSet
  }

  it("the walker handles every Term/ActionStatement node TypedAST declares") {
    val declared = declaredNodes
    assert(declared.nonEmpty, "source scan found no nodes — the regex has rotted")
    val missing = declared -- EffectInference.handledNodes
    assert(missing.isEmpty,
      s"TypedAST nodes with no walker arm (add them to EffectInference and handledNodes): ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("handledNodes lists no node that TypedAST no longer declares") {
    val declared = declaredNodes
    val stale = EffectInference.handledNodes -- declared
    assert(stale.isEmpty,
      s"handledNodes entries with no TypedAST declaration (remove them): ${stale.toSeq.sorted.mkString(", ")}")
  }
}
