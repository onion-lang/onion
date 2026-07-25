package onion.compiler.verification

import java.lang.reflect.{Constructor, InvocationTargetException}
import java.util.Random

/**
 * Deterministic sample values for parameter type `t`, for property-based law checking.
 *
 * Design constraints:
 *  - Fully deterministic: same (type, count, seed) → same sequence every run (CI-safe).
 *    The seed is a parameter rather than a constant so a reported counterexample can be
 *    reproduced, and so a run can be widened to look for others (issue #346).
 *  - At most `samples` values per type.
 *  - No NaN/Infinity in floating-point samples (would cause false counter-examples under ==).
 *  - Reflection errors are caught and silently converted to None / skipped entries.
 *  - No dependency on any onion.compiler class — only java.lang / java.util.
 */
object ArgGenerator {

  /** Defaults, mirrored by CompilerConfig.lawSamples / lawSeed. */
  val DefaultSamples: Int = 40
  val DefaultSeed: Long = 42L

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Returns up to `samples` deterministic values (as AnyRef) for the given JVM type,
   * or None when the type is unsupported.
   *
   * @param t       JVM class obtained from e.g. Method.getParameterTypes
   * @param loader  ClassLoader (passed through to flat-record instantiation)
   * @param samples upper bound on how many values to produce
   * @param seed    RNG seed; report it with any counterexample so the run reproduces
   */
  def generateValues(
      t: Class[?],
      loader: ClassLoader,
      samples: Int = DefaultSamples,
      seed: Long = DefaultSeed
  ): Option[List[AnyRef]] =
    generateValuesInternal(t, loader, depth = 0, samples, seed)

  // ---------------------------------------------------------------------------
  // Internal implementation
  // ---------------------------------------------------------------------------

  private val MAX_DEPTH = 3  // guard against pathological recursive records

  private def generateValuesInternal(
      t: Class[?],
      loader: ClassLoader,
      depth: Int,
      n: Int,
      seed: Long
  ): Option[List[AnyRef]] = {
    if (depth > MAX_DEPTH) return None

    normalize(t) match {
      case Some(k) => Some(valuesFor(k, n, seed))
      case None    => tryFlatRecord(t, loader, depth, n, seed)
    }
  }

  // Re-created for each value list so that call order does not affect results.
  private def freshRng(seed: Long): Random = new Random(seed)

  // ---------------------------------------------------------------------------
  // Scalar kinds
  // ---------------------------------------------------------------------------

  private sealed trait ScalarKind
  private case object KInt     extends ScalarKind
  private case object KLong    extends ScalarKind
  private case object KShort   extends ScalarKind
  private case object KByte    extends ScalarKind
  private case object KDouble  extends ScalarKind
  private case object KFloat   extends ScalarKind
  private case object KBoolean extends ScalarKind
  private case object KString  extends ScalarKind

  /** Maps primitive and boxed classes to a ScalarKind. */
  private def normalize(t: Class[?]): Option[ScalarKind] = t match {
    case c if c == java.lang.Integer.TYPE || c == classOf[java.lang.Integer] => Some(KInt)
    case c if c == java.lang.Long.TYPE    || c == classOf[java.lang.Long]    => Some(KLong)
    case c if c == java.lang.Short.TYPE   || c == classOf[java.lang.Short]   => Some(KShort)
    case c if c == java.lang.Byte.TYPE    || c == classOf[java.lang.Byte]    => Some(KByte)
    case c if c == java.lang.Double.TYPE  || c == classOf[java.lang.Double]  => Some(KDouble)
    case c if c == java.lang.Float.TYPE   || c == classOf[java.lang.Float]   => Some(KFloat)
    case c if c == java.lang.Boolean.TYPE || c == classOf[java.lang.Boolean] => Some(KBoolean)
    case c if c == classOf[java.lang.String]                                  => Some(KString)
    case _                                                                    => None
  }

  private def valuesFor(k: ScalarKind, n: Int, seed: Long): List[AnyRef] = k match {
    case KInt     => intValues(n, seed)
    case KLong    => longValues(n, seed)
    case KShort   => shortValues(n, seed)
    case KByte    => byteValues(n, seed)
    case KDouble  => doubleValues(n, seed)
    case KFloat   => floatValues(n, seed)
    case KBoolean => booleanValues()
    case KString  => stringValues(n, seed)
  }

  /** Boundary values first, then RNG fill, capped at n (never padded beyond the source). */
  private def fill[A](boundary: List[A], n: Int, seed: Long)(random: Random => A): List[A] = {
    if (n <= boundary.length) boundary.take(n)
    else {
      val rng = freshRng(seed)
      boundary ++ List.fill(n - boundary.length)(random(rng))
    }
  }

  // --- int ---
  private def intValues(n: Int, seed: Long): List[AnyRef] =
    fill(List(0, 1, -1, 2, -2, 10, -10, 100, -100, Int.MaxValue, Int.MinValue), n, seed)(_.nextInt())
      .map(java.lang.Integer.valueOf)

  // --- long ---
  private def longValues(n: Int, seed: Long): List[AnyRef] =
    fill(List(0L, 1L, -1L, 2L, -2L, 10L, -10L, 100L, -100L, Long.MaxValue, Long.MinValue), n, seed)(_.nextLong())
      .map(java.lang.Long.valueOf)

  // --- short ---
  private def shortValues(n: Int, seed: Long): List[AnyRef] =
    fill(List(0, 1, -1, 2, -2, 10, -10, 100, -100, Short.MaxValue, Short.MinValue).map(_.toShort), n, seed)(
      rng => (rng.nextInt(Short.MaxValue.toInt * 2 + 2) + Short.MinValue.toInt).toShort
    ).map(java.lang.Short.valueOf)

  // --- byte ---
  private def byteValues(n: Int, seed: Long): List[AnyRef] =
    fill(List(0, 1, -1, 2, -2, 10, -10, 100, -100, 127, -128).map(_.toByte), n, seed)(
      rng => { val b = Array.ofDim[Byte](1); rng.nextBytes(b); b(0) }
    ).map(java.lang.Byte.valueOf)

  // --- double ---
  // Avoid NaN/Infinity: nextDouble() is in [0.0, 1.0), so scale by a large finite value.
  private def doubleValues(n: Int, seed: Long): List[AnyRef] =
    fill(List(0.0, 1.0, -1.0, 0.5, -0.5, 100.0, -100.0, Double.MaxValue, Double.MinValue), n, seed)(
      rng => (rng.nextDouble() - 0.5) * 2.0e15
    ).map(java.lang.Double.valueOf)

  // --- float ---
  private def floatValues(n: Int, seed: Long): List[AnyRef] =
    fill(List(0.0f, 1.0f, -1.0f, 0.5f, -0.5f, 100.0f, -100.0f, Float.MaxValue, Float.MinValue), n, seed)(
      rng => (rng.nextFloat() - 0.5f) * 2.0e10f
    ).map(java.lang.Float.valueOf)

  // --- boolean ---
  private def booleanValues(): List[AnyRef] =
    List(java.lang.Boolean.TRUE, java.lang.Boolean.FALSE)

  // --- string ---
  private val fixedStrings: List[String] = List(
    "", "a", "abc", "  x  ", "0", "-1", "true", "null", "a:b", "日本語"
  )

  private val AlphaNum: Array[Char] =
    (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toArray

  private def randomString(rng: Random, maxLen: Int): String = {
    val len = rng.nextInt(maxLen + 1)
    val sb = new java.lang.StringBuilder(len)
    var i = 0
    while (i < len) {
      sb.append(AlphaNum(rng.nextInt(AlphaNum.length)))
      i += 1
    }
    sb.toString
  }

  private def stringValues(n: Int, seed: Long): List[AnyRef] =
    fill(fixedStrings, n, seed)(rng => randomString(rng, 16)).map(s => s: AnyRef)

  // ---------------------------------------------------------------------------
  // Flat record support
  // ---------------------------------------------------------------------------

  /**
   * If `t` has exactly one constructor and every parameter type has supported
   * values, build up to `n` instances via the boundary-cross strategy below.
   *
   * Strategy: for each param position we have a list of values.
   *   - We zip all positions together by index (shortest list determines length).
   *   - Then we add boundary-corner combinations by cycling each position through
   *     index 0 while keeping others at index 0 (one-at-a-time boundary sweep).
   *   - Total is capped at `n`, deduplicated by index.
   */
  private def tryFlatRecord(
      t: Class[?],
      loader: ClassLoader,
      depth: Int,
      n: Int,
      seed: Long
  ): Option[List[AnyRef]] = {
    // Must have exactly one constructor; interfaces / arrays / primitives excluded
    if (t.isInterface || t.isArray || t.isPrimitive) return None

    val ctors: Array[Constructor[?]] =
      try t.getDeclaredConstructors
      catch { case _: SecurityException => return None }

    if (ctors.length != 1) return None

    val ctor = ctors(0)
    ctor.setAccessible(true)

    val paramTypes: Array[Class[?]] = ctor.getParameterTypes
    if (paramTypes.isEmpty) return None  // no-arg ctor → not a "data record"

    // Recursively generate values for each param type
    val paramValueLists: Array[List[AnyRef]] = {
      val buf = Array.ofDim[List[AnyRef]](paramTypes.length)
      var i = 0
      while (i < paramTypes.length) {
        generateValuesInternal(paramTypes(i), loader, depth + 1, n, seed) match {
          case None    => return None   // unsupported param → skip whole record
          case Some(v) => buf(i) = v
        }
        i += 1
      }
      buf
    }

    val arity = paramTypes.length

    // Independent sampling: each component advances through its value list at a distinct
    // stride, so components vary independently. A plain diagonal (same index for every
    // component) biases toward correlated tuples like x==y and, once truncated, hides
    // counterexamples that need the components to differ.
    val maxLen = paramValueLists.map(_.length).max
    val allArgArrays: List[Array[AnyRef]] = (0 until math.min(n, maxLen)).map { i =>
      Array.tabulate(arity) { p =>
        val lst = paramValueLists(p)
        lst((i * (p + 1) + p) % lst.length)
      }
    }.toList

    // Instantiate, catch errors per-entry
    val instances: List[AnyRef] = allArgArrays.flatMap { args =>
      try {
        Some(ctor.newInstance(args*).asInstanceOf[AnyRef])
      } catch {
        case _: InvocationTargetException => None
        case _: InstantiationException    => None
        case _: IllegalAccessException    => None
        case _: IllegalArgumentException  => None
        case _: Exception                 => None
      }
    }

    if (instances.isEmpty) None else Some(instances.take(n))
  }
}
