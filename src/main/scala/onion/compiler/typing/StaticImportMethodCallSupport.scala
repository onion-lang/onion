package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

private[typing] enum MethodFallbackLookup[+A] {
  case Found(value: A)
  case NotFound
  case Error
}

private[compiler] final class StaticImportMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)

  private sealed trait StaticImportResolution
  private case class StaticImportResolved(method: Method, term: Term) extends StaticImportResolution
  private case class StaticImportAmbiguous(first: Method, second: Method) extends StaticImportResolution
  private case object StaticImportNoMatch extends StaticImportResolution

  def resolveStaticImportMethodCall(
    node: AST.UnqualifiedMethodCall,
    params: Array[Term],
    expected: Type
  ): MethodFallbackLookup[Term] = {
    val mappedTypeArgs =
      if (node.typeArgs.nonEmpty) {
        calls.mapTypeArgs(node.typeArgs) match {
          case Some(mapped) => Some(mapped)
          case None => return MethodFallbackLookup.Error
        }
      } else {
        None
      }

    val resolved = scala.collection.mutable.Buffer[StaticImportResolved]()
    var ambiguous: Option[StaticImportAmbiguous] = None
    bodyContext.staticImportedList.getItems.foreach { item =>
      if (!item.importsMethod(node.name)) ()
      else bodyContext.loadOption(item.getName).foreach { typeRef =>
        resolveStaticImportOnType(node, typeRef, params, expected, mappedTypeArgs) match {
          case found: StaticImportResolved =>
            // Deduplicate: the same method may be reachable through both a
            // class-level static import and a single-method static import.
            if (!resolved.exists(_.method eq found.method)) resolved += found
          case amb: StaticImportAmbiguous =>
            if (ambiguous.isEmpty) ambiguous = Some(amb)
          case StaticImportNoMatch =>
        }
      }
    }

    if (resolved.length == 1) {
      MethodFallbackLookup.Found(resolved.head.term)
    } else if (resolved.length > 1) {
      calls.reportAmbiguousMethod(node, resolved(0).method, resolved(1).method)
      MethodFallbackLookup.Error
    } else {
      ambiguous match {
        case Some(amb) =>
          calls.reportAmbiguousMethod(node, amb.first, amb.second)
          MethodFallbackLookup.Error
        case None =>
          MethodFallbackLookup.NotFound
      }
    }
  }

  private def resolveStaticImportOnType(
    node: AST.UnqualifiedMethodCall,
    typeRef: ClassType,
    params: Array[Term],
    expected: Type,
    mappedTypeArgs: Option[Array[Type]]
  ): StaticImportResolution = {
    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(typeRef, node.name, candidates, calls.isStaticMethod)
    if (candidates.isEmpty) return StaticImportNoMatch

    val applicable = overloadSupport.collectStaticApplicables(
      typeRef,
      candidates.asScala,
      node,
      params,
      expected,
      mappedTypeArgs
    )

    if (applicable.isEmpty) return StaticImportNoMatch

    overloadSupport.selectMostSpecificApplicable(applicable) match {
      case CandidateSelection.Ambiguous(first, second) =>
        StaticImportAmbiguous(first.method, second.method)
      case CandidateSelection.Selected(chosen) =>
        val classSubst = TypeSubstitution.classSubstitution(typeRef)
        // prepareCallParams wraps trailing arguments into the vararg array (and
        // fills defaults), so bare default-static-import calls to varargs
        // methods like `format`/`printf` resolve the same as `IO::format`.
        calls.prepareCallParams(node, node.args, chosen.method, params, chosen.expectedArgs) match {
          case Some(adjusted) =>
            overloadSupport.buildStaticCall(typeRef, chosen.method, adjusted, classSubst, chosen.methodSubst) match {
              case Some(term) => StaticImportResolved(chosen.method, term)
              case None => StaticImportNoMatch
            }
          case None => StaticImportNoMatch
        }
      case CandidateSelection.NoMatch =>
        StaticImportNoMatch
    }
  }
}
