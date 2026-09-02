package onion.compiler.typing

import onion.compiler.TypedAST._
import onion.compiler._
import onion.compiler.SemanticError._
import onion.compiler.typing.session.NameResolutionContext

import scala.collection.mutable.Map

/** A resolved type parameter. `constraints` are the type-class traits from a
  * `[T: Numeric]` context bound (resolved to their interface types); the eventual
  * dictionary-passing derives one dictionary parameter per constraint. */
final case class TypeParam(name: String, variableType: TypedAST.TypeVariableType, upperBound: ClassType, constraints: List[ClassType] = Nil)

final case class TypeParamScope(params: Map[String, TypeParam]) {
  def get(name: String): Option[TypeParam] = params.get(name)
  def ++(ps: Seq[TypeParam]): TypeParamScope = if (ps.isEmpty) this else copy(params ++ ps.map(p => p.name -> p))
}

final case class TypeAliasEntry(
  fqcn: String,
  typeParameters: Seq[TypeParam],
  targetDescriptor: AST.TypeDescriptor,
  node: AST.TypeAliasDeclaration,
  imports: Seq[ImportItem]
)

object NameResolver {
  /** Class-not-found suggestion candidates so a Java/Scala-style lowercase spelling
    * (`int`, `boolean`, ...), which parses as an ordinary unresolved reference type,
    * gets "did you mean `Int`?" instead of a bare "check spelling or add import".
    * Delegates to the shared `onion.compiler.PrimitiveTypeNames` -- see its doc
    * comment for the other call site this must stay in sync with. */
  private[compiler] val PrimitiveTypeNames: Seq[String] = onion.compiler.PrimitiveTypeNames.all
}

class NameResolver(private val context: NameResolutionContext) {
  private def imports: Seq[ImportItem] = context.imports

  def resolveNode(typeNode: AST.TypeNode): Type = map(typeNode.desc)

  /**
   * Java generics erase to reference types, so a primitive used as a type
   * argument is stored as its wrapper (Int -> Integer). The surface type stays
   * `Int`; boxing/unboxing happens transparently at use sites. This unifies
   * primitive and reference types the way Scala/Kotlin do, so `Map[String, Int]`
   * behaves like `Map[String, Integer]` (e.g. extension methods match, `put`
   * returns a nullable Integer rather than NPE-unboxing).
   */
  private def boxTypeArgument(arg: Type): Type = arg match {
    case bt: BasicType if bt != BasicType.VOID =>
      val wrapper = bt match {
        case BasicType.BOOLEAN => "java.lang.Boolean"
        case BasicType.BYTE => "java.lang.Byte"
        case BasicType.SHORT => "java.lang.Short"
        case BasicType.CHAR => "java.lang.Character"
        case BasicType.INT => "java.lang.Integer"
        case BasicType.LONG => "java.lang.Long"
        case BasicType.FLOAT => "java.lang.Float"
        case BasicType.DOUBLE => "java.lang.Double"
        case _ => null
      }
      if (wrapper == null) arg
      else {
        val loaded = context.table.loadOrNull(wrapper)
        if (loaded == null) arg else loaded
      }
    case _ => arg
  }

  def getCandidateClassNames: Array[String] = {
    val localClasses = context.table.classes.values.map(_.name).toSeq
    val importedClasses = imports.filterNot(_.isOnDemand).map(_.simpleName)
    (localClasses ++ importedClasses ++ NameResolver.PrimitiveTypeNames).distinct.toArray
  }

  def map(descriptor: AST.TypeDescriptor): Type = descriptor match {
    case AST.PrimitiveType(AST.KChar)       => BasicType.CHAR
    case AST.PrimitiveType(AST.KByte)       => BasicType.BYTE
    case AST.PrimitiveType(AST.KShort)      => BasicType.SHORT
    case AST.PrimitiveType(AST.KInt)        => BasicType.INT
    case AST.PrimitiveType(AST.KLong)       => BasicType.LONG
    case AST.PrimitiveType(AST.KFloat)      => BasicType.FLOAT
    case AST.PrimitiveType(AST.KDouble)     => BasicType.DOUBLE
    case AST.PrimitiveType(AST.KBoolean)    => BasicType.BOOLEAN
    case AST.PrimitiveType(AST.KVoid)       => BasicType.VOID
    case AST.ReferenceType(name, qualified) =>
      val aliasFqcn =
        if (qualified) name
        else {
          val module = context.currentUnit.module
          val moduleName = if (module != null) module.name else null
          context.createFQCN(moduleName, name)
        }
      val aliasOpt = context.typeAliases.get(aliasFqcn).orElse {
        if (!qualified && context.typeAliases.nonEmpty) {
          imports.iterator.flatMap(_.matches(name)).flatMap(fqcn => context.typeAliases.get(fqcn)).nextOption()
        } else None
      }
      aliasOpt match {
        case Some(entry) if entry.typeParameters.isEmpty =>
          resolveTypeAlias(entry, Nil)
        case _ =>
          forName(name, qualified)
      }
    case AST.ParameterizedType(base, params) =>
      val mappedArgs = params.map(map).map(boxTypeArgument)
      if (mappedArgs.exists(_ == null)) return null

      base match {
        case AST.ReferenceType(name, qualified) =>
          val aliasFqcn =
            if (qualified) name
            else {
              val module = context.currentUnit.module
              val moduleName = if (module != null) module.name else null
              context.createFQCN(moduleName, name)
            }
          val aliasOpt = context.typeAliases.get(aliasFqcn).orElse {
            if (!qualified && context.typeAliases.nonEmpty) {
              imports.iterator.flatMap(_.matches(name)).flatMap(fqcn => context.typeAliases.get(fqcn)).nextOption()
            } else None
          }
          aliasOpt match {
            case Some(aliasEntry) =>
              return resolveTypeAlias(aliasEntry, mappedArgs)
            case None =>
          }
        case _ =>
      }

      val raw = map(base)
      if (raw == null) return null
      raw match {
        case clazz: ClassType =>
          TypedAST.AppliedClassType(clazz, mappedArgs)
        case _ =>
          raw
      }
    case AST.FunctionType(params, result) =>
      val mappedParams = params.map(map)
      var mappedResult = map(result)
      if (mappedParams.exists(_ == null) || mappedResult == null) return null
      // A side-effect-only function type (() -> void / () -> Unit) cannot use
      // the JVM void type as a type argument, so we erase it to Object at the
      // FunctionN interface level. This matches how closure bodies with no
      // meaningful return are already compiled.
      if (mappedResult eq BasicType.VOID) mappedResult = context.rootClass
      val arity = mappedParams.length
      val functionType = context.table.loadOrNull(s"onion.Function$arity")
      if (functionType == null) return null
      TypedAST.AppliedClassType(functionType, (mappedParams :+ mappedResult).toList)
    case AST.ArrayType(component) =>
      val (base, dimension) = context.splitDescriptor(descriptor)
      val mappedBase = map(base)
      // The component class may be unresolvable (already reported); don't
      // construct an ArrayType around null (fuzz: 'val xs: L[]')
      if (mappedBase == null) null
      else context.table.loadArray(mappedBase, dimension)
    case AST.WildcardType(upperBound, lowerBound) =>
      val mappedUpper = upperBound.map(map).getOrElse(context.rootClass)
      val mappedLower = lowerBound.map(map)
      new TypedAST.WildcardType(mappedUpper, mappedLower)
    case AST.NullableType(inner) =>
      val mappedInner = map(inner)
      if (mappedInner == null) null else TypedAST.NullableType.of(mappedInner)
    case _ =>
      null
  }

  private lazy val scanMemo = context.table.importScanMemo(imports)

  private def forName(name: String, qualified: Boolean): ClassType = {
    if (qualified) {
      val direct = context.table.loadOrNull(name)
      if (direct != null) direct else forNestedName(name)
    } else {
      context.currentTypeParams.get(name).map(_.variableType).getOrElse {
        val module = context.currentUnit.module
        val moduleName = if (module != null) module.name else null
        val aliasFqcn = context.createFQCN(moduleName, name)
        val local = context.table.lookup(aliasFqcn)
        if (local != null) {
          local
        } else {
          // The import scan is the expensive path: every on-demand import yields a candidate
          // FQCN, each candidate is looked up and, on a miss, retried as a nested-class name.
          // The same simple name is resolved many times per unit, so the outcome is memoized
          // per resolver. A negative outcome is only trusted while no class has been added
          // to the table since it was recorded -- a class registered later could turn it
          // positive -- which the local class count tracks.
          val generation = context.table.classes.size
          scanMemo.get(name) match {
            case Some((found, gen)) if found != null || gen == generation => found
            case _ =>
              val found = imports.iterator
                .flatMap(_.matches(name))
                .map(fqcn => {
                  // A candidate the imports produced is already qualified: it is a class,
                  // or a nested class under a class the same prefix names (`java.util.Map.*`
                  // yielding `java.util.Map.Entry`). Feeding it back through the import
                  // scan, as `forName(_, qualified = true)` did, generated further
                  // candidates such as `java.lang.onion$Object` for each one.
                  val direct = context.table.loadOrNull(fqcn)
                  if (direct != null) direct else forDollarNested(fqcn)
                })
                .find(_ != null)
                .orNull
              scanMemo(name) = (found, generation)
              found
          }
        }
      }
    }
  }

  /**
   * Resolve dotted names that denote nested classes: Map.Entry becomes
   * java.util.Map$Entry (resolving the head through imports), and
   * a.b.C.D tries a.b.C$D and deeper $-joined variants.
   */
  /**
   * a.b.C.D -> a.b.C$D, a.b$C$D, ... -- but only under an outer class that exists. The
   * unconditional form turned every miss into a fan of misses: `onion.Object` (absent)
   * became `onion$Object`, which went back through the import scan as an unqualified
   * name and produced eight more classpath probes, for every name in every unit.
   */
  private def forDollarNested(name: String): ClassType = {
    if (!name.contains(".")) return null
    val parts = name.split('.')
    var i = parts.length - 1
    while (i >= 1) {
      val outer = parts.take(i).mkString(".")
      if (context.table.loadOrNull(outer) != null) {
        val found = context.table.loadOrNull(outer + "$" + parts.drop(i).mkString("$"))
        if (found != null) return found
      }
      i -= 1
    }
    null
  }

  private def forNestedName(name: String): ClassType = {
    if (!name.contains(".")) return null
    val nested = forDollarNested(name)
    if (nested != null) return nested
    val parts = name.split('.')
    // Head resolved through imports: Map.Entry with java.util.Map imported
    val rest = parts.tail.mkString("$")
    imports.iterator
      .flatMap(_.matches(parts.head))
      .filter(fqcn => context.table.loadOrNull(fqcn) != null)
      .map(fqcn => context.table.loadOrNull(fqcn + "$" + rest))
      .find(_ != null)
      .orNull
  }

  private def resolveTypeAlias(entry: TypeAliasEntry, typeArgs: List[Type]): Type = {
    if (context.typeAliasResolutionStack.contains(entry.fqcn)) {
      context.report(CYCLIC_TYPE_ALIAS, entry.node, Seq(entry.fqcn))
      return null
    }

    val params = entry.typeParameters
    if (params.nonEmpty && typeArgs.length != params.length) {
      context.report(
        TYPE_ARGUMENT_ARITY_MISMATCH,
        entry.node,
        Seq(
          entry.fqcn,
          Integer.valueOf(params.length),
          Integer.valueOf(typeArgs.length)
        )
      )
      return null
    }

    context.typeAliasResolutionStack += entry.fqcn
    try {
      val aliasMapper = new NameResolver(context.copy(imports = entry.imports))
      if (params.nonEmpty && typeArgs.nonEmpty) {
        val substitutions = params.zip(typeArgs).map { case (p, arg) =>
          val varType = arg match {
            case tv: TypeVariableType => tv
            case ct: ClassType => TypeVariableType(p.name, ct)
            // A nullable argument keeps its nullability through the alias:
            // occurrences of the parameter behave as a nullable variable
            // bounded by the inner type
            case n: NullableType =>
              n.innerType match {
                case ct: ClassType => TypeVariableType(p.name, ct, Nullability.Nullable)
                case _ => TypeVariableType(p.name, context.rootClass, Nullability.Nullable)
              }
            case _ => TypeVariableType(p.name, context.rootClass)
          }
          TypeParam(p.name, varType, p.upperBound)
        }
        val savedTypeParams = context.currentTypeParams
        context.updateTypeParams(TypeParamScope(Map.empty) ++ substitutions)
        try {
          aliasMapper.map(entry.targetDescriptor)
        } finally {
          context.updateTypeParams(savedTypeParams)
        }
      } else {
        aliasMapper.map(entry.targetDescriptor)
      }
    } finally {
      context.typeAliasResolutionStack -= entry.fqcn
    }
  }
}

final class NameMapper(typing: Typing, imports: Seq[ImportItem])
  extends NameResolver(NameResolutionContext.fromTyping(typing, imports))
