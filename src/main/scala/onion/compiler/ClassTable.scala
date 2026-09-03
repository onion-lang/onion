/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.concurrent.ConcurrentHashMap
import onion.compiler.environment.AsmRefs.AsmClassType
import onion.compiler.environment.ClassFileTable
import onion.compiler.environment.ReflectionRefs.ReflectClassType

/**
 * The compiler's view of every class it can name: the ones being compiled (`classes`)
 * and the ones found on the classpath, parsed on demand into `ClassType`s.
 *
 * Classpath classes are expensive to materialize -- the class file is read and run
 * through ASM, and its signatures are mapped into Onion types -- and until this table
 * grew a `parent`, every compilation did that from scratch: a fresh table, so
 * `java.lang.Object`, `java.lang.String`, `onion.IO` and the rest were re-read and
 * re-parsed for each `new OnionCompiler`. That was a fixed cost of roughly two
 * milliseconds on a one-line program, and a large share of typing time on any real one.
 *
 * The JDK and Onion's own runtime never change while the JVM is up, so those classes now
 * come from one process-wide [[ClassTable.shared]] instance that every per-compilation
 * table delegates to for names under the platform and `onion.` prefixes. Sharing the
 * *instances* rather than the bytes matters: the type checker compares class types by
 * identity (`eq`), so two `java.lang.Object` objects would be two unrelated types. With
 * one shared universe, a user class extending `Object` and a library method returning
 * `Object` agree on what that is.
 *
 * Precedence is unchanged. A class being compiled always wins (`classes` is consulted
 * first), then the shared universe for platform names, then this compilation's own
 * classpath. A user cannot define `java.lang.String` -- the JVM forbids it -- and an
 * `onion.*` class the shared loader does not know falls through to the local classpath,
 * so a project that puts its own code under `onion.` still resolves it.
 *
 * The shared table is reached from several compilations at once (the language server, a
 * test suite, `onion test`), so its maps are concurrent and a race to materialize the same
 * class is settled by whichever `putIfAbsent` wins -- both parsed the same bytes, and only
 * one instance is ever handed out.
 *
 * @author Kota Mizushima
 */
class ClassTable(classPath: String, parent: Option[ClassTable] = None) {
  val classes = new OrderedTable[TypedAST.ClassDefinition]
  private val classFiles = new ConcurrentHashMap[String, TypedAST.ClassType]
  private val arrayClasses = new ConcurrentHashMap[String, TypedAST.ArrayType]
  private val missingClasses = ConcurrentHashMap.newKeySet[String]()
  private val table = new ClassFileTable(classPath)

  /**
   * Per-compilation memo for `AppliedTypeViews.collectAppliedViewsFrom`: the generic
   * supertype views of a receiver type, which method resolution rebuilt by walking the
   * whole supertype hierarchy on every call typed against that receiver. The result is a
   * pure function of the type, so within one compilation it is computed once per receiver.
   * Keyed by identity -- class types compare by identity throughout the checker.
   */
  private val appliedViewsMemo =
    new java.util.IdentityHashMap[TypedAST.ClassType, scala.collection.immutable.Map[TypedAST.ClassType, TypedAST.AppliedClassType]]

  /**
   * The import-scan memo for a unit's import list (see `NameResolver.forName`). A resolver
   * is created per top-level declaration, all sharing the unit's imports; keying the memo
   * by that list lets the second class in a file reuse what the first resolved.
   */
  private val importScanMemos = new java.util.IdentityHashMap[AnyRef, scala.collection.mutable.HashMap[String, (TypedAST.ClassType, Int)]]()
  def importScanMemo(imports: AnyRef): scala.collection.mutable.HashMap[String, (TypedAST.ClassType, Int)] = {
    val existing = importScanMemos.get(imports)
    if (existing != null) existing
    else {
      val fresh = scala.collection.mutable.HashMap[String, (TypedAST.ClassType, Int)]()
      importScanMemos.put(imports, fresh)
      fresh
    }
  }

  /** Per-compilation memo of a method's erased parameter descriptor (duplication checks). */
  private val erasedParamsMemo = new java.util.IdentityHashMap[TypedAST.Method, String]()
  def erasedParamsOf(method: TypedAST.Method)(compute: => String): String = {
    val cached = erasedParamsMemo.get(method)
    if (cached != null) cached
    else {
      val d = compute
      erasedParamsMemo.put(method, d)
      d
    }
  }

  /**
   * Per-compilation memo of the overload candidates of (receiver type, method name): the
   * hierarchy walk that collects them is the same at every call site of that pair.
   */
  private val candidateMemo = new java.util.IdentityHashMap[TypedAST.ObjectType, java.util.HashMap[String, Array[TypedAST.Method]]]()
  def methodCandidatesOf(target: TypedAST.ObjectType, name: String)(compute: => Array[TypedAST.Method]): Array[TypedAST.Method] = {
    // The candidates of a platform or runtime class never change: keep them in the shared
    // universe, so the next compilation finds them instead of walking the hierarchy again.
    if (parent.isDefined && isShareableReceiver(target)) return parent.get.sharedMethodCandidatesOf(target, name)(compute)
    var byName = candidateMemo.get(target)
    if (byName == null) { byName = new java.util.HashMap[String, Array[TypedAST.Method]](); candidateMemo.put(target, byName) }
    val cached = byName.get(name)
    if (cached != null) cached
    else {
      val fresh = compute
      byName.put(name, fresh)
      fresh
    }
  }

  /**
   * Per-compilation memos for overload resolution: a method's parameter types specialized to
   * a receiver's supertype views, and its type-parameter names. Both were recomputed by every
   * call site of the same method (`MethodResolutionSupport` is built per call).
   */
  private val specializedArgsMemo = new java.util.IdentityHashMap[AnyRef, java.util.IdentityHashMap[TypedAST.Method, Array[TypedAST.Type]]]()
  def specializedArgsOf(views: AnyRef, method: TypedAST.Method)(compute: => Array[TypedAST.Type]): Array[TypedAST.Type] = {
    var byMethod = specializedArgsMemo.get(views)
    if (byMethod == null) { byMethod = new java.util.IdentityHashMap[TypedAST.Method, Array[TypedAST.Type]](); specializedArgsMemo.put(views, byMethod) }
    val cached = byMethod.get(method)
    if (cached != null) cached
    else { val fresh = compute; byMethod.put(method, fresh); fresh }
  }
  private val typeParamNamesMemo = new java.util.IdentityHashMap[TypedAST.Method, Set[String]]()
  def typeParamNamesOf(method: TypedAST.Method): Set[String] = {
    val cached = typeParamNamesMemo.get(method)
    if (cached != null) cached
    else {
      val tps = method.typeParameters
      val fresh = if (tps.isEmpty) Set.empty[String] else tps.map(_.name).toSet
      typeParamNamesMemo.put(method, fresh)
      fresh
    }
  }

  /** Per-compilation memo of the extension methods named `name` visible from a receiver type. */
  private val extensionMemo = new java.util.IdentityHashMap[TypedAST.ObjectType, java.util.HashMap[String, Seq[onion.compiler.TypedAST.ExtensionMethodDefinition]]]()
  def extensionCandidatesOf(target: TypedAST.ObjectType, name: String)(compute: => Seq[onion.compiler.TypedAST.ExtensionMethodDefinition]): Seq[onion.compiler.TypedAST.ExtensionMethodDefinition] = {
    var byName = extensionMemo.get(target)
    if (byName == null) { byName = new java.util.HashMap[String, Seq[onion.compiler.TypedAST.ExtensionMethodDefinition]](); extensionMemo.put(target, byName) }
    val cached = byName.get(name)
    if (cached != null) cached
    else {
      val fresh = compute
      byName.put(name, fresh)
      fresh
    }
  }

  /** Per-compilation memo of whether a class's hierarchy contains a parameterized type. */
  private val specializedViewsMemo = new java.util.IdentityHashMap[TypedAST.ClassType, java.lang.Boolean]()
  def specializedViewsRequired(target: TypedAST.ClassType)(compute: => Boolean): Boolean = {
    val cached = specializedViewsMemo.get(target)
    if (cached != null) cached.booleanValue
    else {
      val fresh = compute
      specializedViewsMemo.put(target, java.lang.Boolean.valueOf(fresh))
      fresh
    }
  }

  def appliedViewsOf(target: TypedAST.ClassType)(compute: => scala.collection.immutable.Map[TypedAST.ClassType, TypedAST.AppliedClassType]): scala.collection.immutable.Map[TypedAST.ClassType, TypedAST.AppliedClassType] = {
    if (parent.isDefined && isShareableReceiver(target)) return parent.get.sharedAppliedViewsOf(target)(compute)
    val cached = appliedViewsMemo.get(target)
    if (cached != null) cached
    else {
      val views = compute
      appliedViewsMemo.put(target, views)
      views
    }
  }

  def loadArray(component: TypedAST.Type, dimension: Int): TypedAST.ArrayType = {
    // The cache is keyed by name, which is ambiguous for type variables ("T" from
    // different methods with different bounds) and applied types (List[String] vs
    // List[Int]); cache only arrays of simple components.
    if (!isCacheableComponent(component))
      return new TypedAST.ArrayType(component, dimension, this)
    // The shared universe outlives every compilation, so it may only cache arrays of its
    // own classes. A substitution such as `T[] -> Transaction[]` over a runtime method's
    // vararg parameter asks the parameter's table -- this one -- for the array; caching it
    // here by name would hand the next compilation an array of the *previous* `Transaction`.
    if ((this eq ClassTable.shared) && !isSharedComponent(component))
      return new TypedAST.ArrayType(component, dimension, this)
    val arrayName = "[" * dimension + component.name
    val cached = arrayClasses.get(arrayName)
    if (cached != null) return cached
    val fresh = new TypedAST.ArrayType(component, dimension, this)
    val winner = arrayClasses.putIfAbsent(arrayName, fresh)
    if (winner == null) fresh else winner
  }

  /**
   * Whether a receiver type belongs wholly to the shared universe: a platform or runtime
   * class, or such a class applied to shared type arguments. Only then may anything derived
   * from it -- its supertype views, its overload candidates -- outlive this compilation
   * (a user class in a type argument would leak into the next compilation's results).
   */
  private def isShareableReceiver(target: TypedAST.Type): Boolean =
    target match {
      case a: TypedAST.AppliedClassType =>
        ClassTable.isSharedName(a.raw.name) && a.typeArguments.forall(isShareableReceiver)
      case ct: TypedAST.ClassType => ClassTable.isSharedName(ct.name)
      case other => isSharedComponent(other)
    }

  // The shared table's memos are reached from every compilation, possibly on several
  // threads; each is guarded by its own monitor (uncontended in the common case).
  private def sharedMethodCandidatesOf(target: TypedAST.ObjectType, name: String)(compute: => Array[TypedAST.Method]): Array[TypedAST.Method] =
    candidateMemo.synchronized {
      var byName = candidateMemo.get(target)
      if (byName == null) { byName = new java.util.HashMap[String, Array[TypedAST.Method]](); candidateMemo.put(target, byName) }
      val cached = byName.get(name)
      if (cached != null) cached
      else { val fresh = compute; byName.put(name, fresh); fresh }
    }

  private def sharedAppliedViewsOf(target: TypedAST.ClassType)(compute: => scala.collection.immutable.Map[TypedAST.ClassType, TypedAST.AppliedClassType]): scala.collection.immutable.Map[TypedAST.ClassType, TypedAST.AppliedClassType] =
    appliedViewsMemo.synchronized {
      val cached = appliedViewsMemo.get(target)
      if (cached != null) cached
      else { val views = compute; appliedViewsMemo.put(target, views); views }
    }

  private def isSharedComponent(component: TypedAST.Type): Boolean =
    component match {
      case _: TypedAST.BasicType => true
      case array: TypedAST.ArrayType => isSharedComponent(array.component)
      case ct: TypedAST.ClassType => ClassTable.isSharedName(ct.name)
      case _ => false
    }

  private def isCacheableComponent(component: TypedAST.Type): Boolean =
    component match {
      case _: TypedAST.BasicType => true
      case _: TypedAST.TypeVariableType => false
      case _: TypedAST.AppliedClassType => false
      case _: TypedAST.WildcardType => false
      case _: TypedAST.NullableType => false
      case array: TypedAST.ArrayType => isCacheableComponent(array.component)
      case _: TypedAST.ClassType => true
      case _ => false
    }

  /**
   * Null-returning lookup kept for the bytecode/reflection environment internals,
   * where absent classes flow through legacy null-tolerant resolution paths.
   * Prefer [[load]] (Option) or [[loadRequired]] elsewhere.
   */
  def loadOrNull(className: String): TypedAST.ClassType = {
    val known = lookup(className)
    if (known != null) return known
    if (missingClasses.contains(className)) return null
    // Platform and runtime classes live in the shared universe; ask it before touching
    // this compilation's own classpath, so every compilation sees the same instance.
    if (parent.isDefined && ClassTable.isSharedName(className)) {
      val shared = parent.get.loadOrNull(className)
      if (shared != null) return shared
      // `java.*` is the boot loader's alone -- no user classpath can define it -- so the
      // shared universe's "absent" is final and this compilation need not probe its own
      // classpath for it. Other shared prefixes (javax., onion., ...) can legitimately be
      // extended by a user jar, so they fall through.
      if (className.startsWith("java.")) { missingClasses.add(className); return null }
    }
    val bytes = table.loadBytes(className)
    val fresh: TypedAST.ClassType =
      if (bytes != null) new AsmClassType(bytes, this)
      else {
        // The reflective fallback exists for classes only the context loader can see. Asking
        // that loader for the resource first turns an absent class into a null instead of a
        // ClassNotFoundException -- which was thrown, with its stack trace, ~50 times per
        // compilation for names the import scan tried and found nowhere.
        val loader = Thread.currentThread.getContextClassLoader
        if (loader == null || loader.getResource(className.replace('.', '/') + ".class") == null) {
          missingClasses.add(className)
          null
        } else
          try new ReflectClassType(Class.forName(className, false, loader), this)
          catch {
            case _: ClassNotFoundException =>
              missingClasses.add(className)
              null
          }
      }
    if (fresh == null) return null
    val winner = classFiles.putIfAbsent(fresh.name, fresh)
    if (winner == null) fresh else winner
  }

  def load(className: String): Option[TypedAST.ClassType] = Option(loadOrNull(className))

  /** Loads a class that the compiler itself depends on (JDK / onion runtime). */
  def loadRequired(className: String): TypedAST.ClassType =
    loadOrNull(className) match {
      case null => throw new IllegalStateException(s"required class is not on the compiler classpath: $className")
      case clazz => clazz
    }

  def rootClass: TypedAST.ClassType = loadRequired("java.lang.Object")

  def lookup(className: String): TypedAST.ClassType = {
    classes.get(className) match {
      case Some(ref) => ref
      case None => classFiles.get(className)
    }
  }

  /** Option-returning version of lookup for safer null handling */
  def lookupOpt(className: String): Option[TypedAST.ClassType] = Option(lookup(className))

}

object ClassTable {
  /**
   * Prefixes whose classes are immutable for the life of the process and are resolved
   * from the compiler's own class loader: the platform, and Onion's runtime library.
   */
  private val SharedPrefixes = Array("java.", "javax.", "jdk.", "sun.", "com.sun.", "onion.")

  def isSharedName(className: String): Boolean = {
    var i = 0
    while (i < SharedPrefixes.length) {
      if (className.startsWith(SharedPrefixes(i))) return true
      i += 1
    }
    false
  }

  /**
   * The process-wide universe of platform and runtime classes. Built once, on first use,
   * with no classpath of its own: an empty classpath resolves through the compiler's class
   * loader, which is exactly the set of classes that never change while it runs.
   */
  lazy val shared: ClassTable = new ClassTable("", None)
}
