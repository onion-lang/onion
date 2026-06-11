package onion.compiler.typing

import onion.compiler.TypedAST._
import onion.compiler._
import onion.compiler.SemanticError._
import onion.compiler.typing.session.NameResolutionContext

import scala.collection.mutable.Map

final case class TypeParam(name: String, variableType: TypedAST.TypeVariableType, upperBound: ClassType)

final case class TypeParamScope(params: Map[String, TypeParam]) {
  def get(name: String): Option[TypeParam] = params.get(name)
  def ++(ps: Seq[TypeParam]): TypeParamScope = copy(params ++ ps.map(p => p.name -> p))
}

final case class TypeAliasEntry(
  fqcn: String,
  typeParameters: Seq[TypeParam],
  targetDescriptor: AST.TypeDescriptor,
  node: AST.TypeAliasDeclaration,
  imports: Seq[ImportItem]
)

class NameResolver(private val context: NameResolutionContext) {
  private def imports: Seq[ImportItem] = context.imports

  def resolveNode(typeNode: AST.TypeNode): Type = map(typeNode.desc)

  def getCandidateClassNames: Array[String] = {
    val localClasses = context.table.classes.values.map(_.name).toSeq
    val importedClasses = imports.filterNot(_.isOnDemand).map(_.simpleName)
    (localClasses ++ importedClasses).distinct.toArray
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
        if (!qualified) {
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
      val mappedArgs = params.map(map)
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
            if (!qualified) {
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
      val mappedResult = map(result)
      if (mappedParams.exists(_ == null) || mappedResult == null) return null
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
          imports.iterator
            .flatMap(_.matches(name))
            .map(fqcn => forName(fqcn, qualified = true))
            .find(_ != null)
            .orNull
        }
      }
    }
  }

  /**
   * Resolve dotted names that denote nested classes: Map.Entry becomes
   * java.util.Map$Entry (resolving the head through imports), and
   * a.b.C.D tries a.b.C$D and deeper $-joined variants.
   */
  private def forNestedName(name: String): ClassType = {
    if (!name.contains(".")) return null
    val parts = name.split("\\.")
    // a.b.C.D -> a.b.C$D, a.b$C$D, ...
    val dollarVariants = (parts.length - 1 to 1 by -1).iterator.map { i =>
      parts.take(i).mkString(".") + "$" + parts.drop(i).mkString("$")
    }
    dollarVariants.map(context.table.loadOrNull).find(_ != null).getOrElse {
      // Head resolved through imports: Map.Entry with java.util.Map imported
      val rest = parts.tail.mkString("$")
      imports.iterator
        .flatMap(_.matches(parts.head))
        .map(fqcn => context.table.loadOrNull(fqcn + "$" + rest))
        .find(_ != null)
        .orNull
    }
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
