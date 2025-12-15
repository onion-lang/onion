package onion.compiler.typing

import onion.compiler.TypedAST
import onion.compiler.TypedAST.{ArrayType, Type}

import scala.collection.mutable.HashMap

private[compiler] object TypeSubstitution {
  def classSubstitution(tp: Type): scala.collection.immutable.Map[String, Type] = tp match {
    case applied: TypedAST.AppliedClassType =>
      val rawParams = applied.raw.typeParameters
      val mapping = HashMap[String, Type]()
      var i = 0
      while (i < rawParams.length && i < applied.typeArguments.length) {
        mapping += rawParams(i).name -> applied.typeArguments(i)
        i += 1
      }
      mapping.toMap
    case _ =>
      scala.collection.immutable.Map.empty
  }

  def substituteType(
    tp: Type,
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type],
    defaultToBound: Boolean
  ): Type = {
    def lookup(name: String): Option[Type] = methodSubst.get(name).orElse(classSubst.get(name))
    tp match {
      case tv: TypedAST.TypeVariableType =>
        lookup(tv.name).getOrElse(if (defaultToBound) tv.upperBound else tv)
      case applied: TypedAST.AppliedClassType =>
        val newArgs = applied.typeArguments.map(arg => substituteType(arg, classSubst, methodSubst, defaultToBound))
        if (newArgs.sameElements(applied.typeArguments)) applied
        else TypedAST.AppliedClassType(applied.raw, newArgs.toList)
      case at: ArrayType =>
        val newComponent = substituteType(at.component, classSubst, methodSubst, defaultToBound)
        if (newComponent eq at.component) at
        else at.table.loadArray(newComponent, at.dimension)
      case w: TypedAST.WildcardType =>
        val newUpper = substituteType(w.upperBound, classSubst, methodSubst, defaultToBound)
        val newLower = w.lowerBound.map(lb => substituteType(lb, classSubst, methodSubst, defaultToBound))
        if ((newUpper eq w.upperBound) && newLower == w.lowerBound) w
        else new TypedAST.WildcardType(newUpper, newLower)
      case other =>
        other
    }
  }
}
