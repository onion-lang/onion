package onion.compiler.typing

import onion.compiler.TypedAST._
import onion.compiler._
import onion.compiler.SemanticError._

import scala.collection.mutable.{Map, Set => MutableSet}

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

final class NameMapper(private val typing: Typing, imports: Seq[ImportItem]) {
  def resolveNode(typeNode: AST.TypeNode): Type = map(typeNode.desc)

  def getCandidateClassNames: Array[String] = {
    val localClasses = typing.table_.classes.values.map(_.name).toSeq
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
          val module = typing.unit_.module
          val moduleName = if (module != null) module.name else null
          typing.createFQCN(moduleName, name)
        }
      val aliasOpt = typing.typeAliases_.get(aliasFqcn).orElse {
        if (!qualified) {
          imports.iterator.flatMap(_.matches(name)).flatMap(fqcn => typing.typeAliases_.get(fqcn)).nextOption()
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
              val module = typing.unit_.module
              val moduleName = if (module != null) module.name else null
              typing.createFQCN(moduleName, name)
            }
          val aliasOpt = typing.typeAliases_.get(aliasFqcn).orElse {
            if (!qualified) {
              imports.iterator.flatMap(_.matches(name)).flatMap(fqcn => typing.typeAliases_.get(fqcn)).nextOption()
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
      val functionType = typing.table_.load(s"onion.Function$arity")
      if (functionType == null) return null
      TypedAST.AppliedClassType(functionType, (mappedParams :+ mappedResult).toList)
    case AST.ArrayType(component) =>
      val (base, dimension) = typing.split(descriptor)
      typing.table_.loadArray(map(base), dimension)
    case AST.WildcardType(upperBound, lowerBound) =>
      val mappedUpper = upperBound.map(map).getOrElse(typing.rootClass)
      val mappedLower = lowerBound.map(map)
      new TypedAST.WildcardType(mappedUpper, mappedLower)
    case AST.NullableType(inner) =>
      val mappedInner = map(inner)
      if (mappedInner == null) null else new TypedAST.NullableType(mappedInner)
    case _ =>
      null
  }

  private def forName(name: String, qualified: Boolean): ClassType = {
    if (qualified) {
      typing.table_.load(name)
    } else {
      typing.typeParams_.get(name).map(_.variableType).getOrElse {
        val module = typing.unit_.module
        val moduleName = if (module != null) module.name else null
        val aliasFqcn = typing.createFQCN(moduleName, name)
        val local = typing.table_.lookup(aliasFqcn)
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

  private def resolveTypeAlias(entry: TypeAliasEntry, typeArgs: List[Type]): Type = {
    if (typing.typeAliasResolutionStack_.contains(entry.fqcn)) {
      typing.report(CYCLIC_TYPE_ALIAS, entry.node, entry.fqcn)
      return null
    }

    val params = entry.typeParameters
    if (params.nonEmpty && typeArgs.length != params.length) {
      typing.report(
        TYPE_ARGUMENT_ARITY_MISMATCH,
        entry.node,
        entry.fqcn,
        Integer.valueOf(params.length),
        Integer.valueOf(typeArgs.length)
      )
      return null
    }

    typing.typeAliasResolutionStack_ += entry.fqcn
    try {
      val aliasMapper = new NameMapper(typing, entry.imports)
      if (params.nonEmpty && typeArgs.nonEmpty) {
        val substitutions = params.zip(typeArgs).map { case (p, arg) =>
          val varType = arg match {
            case tv: TypeVariableType => tv
            case ct: ClassType => TypeVariableType(p.name, ct)
            case _ => TypeVariableType(p.name, typing.rootClass)
          }
          TypeParam(p.name, varType, p.upperBound)
        }
        val savedTypeParams = typing.typeParams_
        typing.typeParams_ = typing.emptyTypeParams ++ substitutions
        try {
          aliasMapper.map(entry.targetDescriptor)
        } finally {
          typing.typeParams_ = savedTypeParams
        }
      } else {
        aliasMapper.map(entry.targetDescriptor)
      }
    } finally {
      typing.typeAliasResolutionStack_ -= entry.fqcn
    }
  }
}
