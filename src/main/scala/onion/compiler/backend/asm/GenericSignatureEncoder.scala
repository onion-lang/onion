package onion.compiler.backend.asm

import onion.compiler.TypedAST
import onion.compiler.TypedAST.{
  AppliedClassType,
  ArrayType,
  BasicType,
  BottomType,
  ClassType,
  NullType,
  NullableType,
  Type,
  TypeParameter,
  TypeVariableType,
  WildcardType
}

/**
 * Encodes Onion [[TypedAST.Type]] values and type-parameter lists into JVM
 * generic signature strings (JVMS 4.7.9.1). This is the emission counterpart of
 * the decoding done in [[onion.compiler.environment.AsmRefs]] (which reads
 * signatures written into `.class` files back into typed AST). Without an
 * emitted signature the generic shape is erased and separate compilation cannot
 * see a class's type parameters.
 *
 * All encoders return `null` when the entity carries no generic content so that
 * non-generic bytecode stays byte-for-byte unchanged (the ASM `visit*` calls
 * treat a `null` signature as "same as the erased descriptor").
 */
object GenericSignatureEncoder:

  /** Internal name with '/' separators, e.g. `java/util/List`. */
  private def internal(name: String): String = name.replace('.', '/')

  /** True if a type mentions a type variable or is parameterized. */
  private def isGeneric(tp: Type): Boolean = tp match
    case _: TypeVariableType => true
    case _: AppliedClassType => true
    case w: WildcardType     => true
    case at: ArrayType       => isGeneric(at.component)
    case nt: NullableType    => isGeneric(nt.innerType)
    case _                   => false

  /**
   * Encode a single type into a JVM type signature.
   *
   *   - type variable T        -> `TT;`
   *   - primitive              -> its descriptor (I, J, D, Z, ...)
   *   - array                  -> `[` * dimension + component signature
   *   - plain class C          -> `LC;`
   *   - parameterized C<A,...>  -> `LC<sigA...>;`
   */
  def typeSignature(tp: Type): String = tp match
    case tv: TypeVariableType =>
      s"T${tv.name};"
    case bt: BasicType =>
      AsmCodeGeneration.asmType(bt).getDescriptor
    case at: ArrayType =>
      "[" * at.dimension + typeSignature(at.component)
    case ap: AppliedClassType =>
      val args = ap.typeArguments.map(typeArgumentSignature).mkString
      s"L${internal(ap.raw.name)}<$args>;"
    case nt: NullableType =>
      // Nullability is not representable in JVM signatures; encode the boxed
      // reference form (the same erased shape codegen already uses).
      nt.innerType match
        case _: BasicType => AsmCodeGeneration.asmType(nt).getDescriptor
        case inner        => typeSignature(inner)
    case w: WildcardType =>
      // A bare wildcard should only appear inside a type-argument position, but
      // encode defensively to its upper bound if it reaches here.
      typeSignature(w.upperBound)
    case _: NullType =>
      "Ljava/lang/Object;"
    case _: BottomType =>
      "V"
    case ct: ClassType =>
      s"L${internal(ct.name)};"
    case other =>
      // Defensive fallback (unknown types should not reach codegen): erase to
      // java.lang.Object so we never emit a malformed signature.
      "Ljava/lang/Object;"

  /** Encode a type argument, handling wildcard bounds (`+`, `-`, `*`). */
  private def typeArgumentSignature(tp: Type): String = tp match
    case w: WildcardType =>
      w.lowerBound match
        case Some(lb) => "-" + typeSignature(lb)
        case None =>
          w.upperBound match
            case ct: ClassType if ct.name == "java.lang.Object" => "*"
            case upper                                          => "+" + typeSignature(upper)
    case other =>
      typeSignature(other)

  /**
   * Encode the formal type parameter section `<T:Lbound;...>` (JVMS
   * ClassSignature/MethodSignature prefix). A parameter with upper bound B
   * encodes as `T:LB;`; the default bound is `java/lang/Object`. Returns the
   * empty string when there are no type parameters.
   */
  private def formalTypeParameters(typeParameters: Array[TypeParameter]): String =
    if typeParameters == null || typeParameters.isEmpty then ""
    else
      val sb = new StringBuilder("<")
      for tp <- typeParameters do
        sb.append(tp.name)
        // Class bound. If the bound is an interface JVMS still accepts a single
        // class-bound slot followed by interface bounds; we emit the bound as a
        // class bound which round-trips through AsmRefs' visitClassBound.
        val boundSig = tp.upperBound match
          case Some(b) => typeSignature(b)
          case None    => "Ljava/lang/Object;"
        sb.append(':').append(boundSig)
      sb.append('>')
      sb.toString

  /**
   * Class signature: `<typeparams>Lsuper;Liface;...`.
   *
   * Emitted only when the class has type parameters OR its superclass/any
   * interface is parameterized; otherwise `null` (erased header unchanged).
   */
  def classSignature(
    typeParameters: Array[TypeParameter],
    superClass: ClassType,
    interfaces: Seq[ClassType]
  ): String =
    val hasTypeParams = typeParameters != null && typeParameters.nonEmpty
    val superGeneric = superClass != null && superClass.isInstanceOf[AppliedClassType]
    val ifaceGeneric = interfaces != null && interfaces.exists(_.isInstanceOf[AppliedClassType])
    if !hasTypeParams && !superGeneric && !ifaceGeneric then null
    else
      val sb = new StringBuilder
      sb.append(formalTypeParameters(typeParameters))
      val superSig =
        if superClass == null then "Ljava/lang/Object;"
        else typeSignature(superClass)
      sb.append(superSig)
      if interfaces != null then
        for iface <- interfaces do sb.append(typeSignature(iface))
      sb.toString

  /**
   * Method signature: `<typeparams>(argSig*)retSig`.
   *
   * Emitted only when the method declares type parameters OR any argument /
   * the return type is generic; otherwise `null`.
   */
  def methodSignature(
    typeParameters: Array[TypeParameter],
    argumentTypes: Array[Type],
    returnType: Type
  ): String =
    val hasTypeParams = typeParameters != null && typeParameters.nonEmpty
    val anyArgGeneric = argumentTypes != null && argumentTypes.exists(isGeneric)
    val retGeneric = returnType != null && isGeneric(returnType)
    if !hasTypeParams && !anyArgGeneric && !retGeneric then null
    else
      val sb = new StringBuilder
      sb.append(formalTypeParameters(typeParameters))
      sb.append('(')
      if argumentTypes != null then
        for arg <- argumentTypes do sb.append(typeSignature(arg))
      sb.append(')')
      val retSig = if returnType == null then "V" else typeSignature(returnType)
      sb.append(retSig)
      sb.toString

  /**
   * Field type signature. Returns `null` when the field type has no generic
   * content (descriptor already carries all the information).
   */
  def fieldSignature(fieldType: Type): String =
    if fieldType == null || !isGeneric(fieldType) then null
    else typeSignature(fieldType)
