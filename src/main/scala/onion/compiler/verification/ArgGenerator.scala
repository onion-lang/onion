package onion.compiler.verification

import java.lang.reflect.{Constructor, InvocationTargetException}
import java.util.Random

/**
 * Deterministic sample values for parameter type `t`, for property-based law checking.
 *
 * Design constraints:
 *  - Fully deterministic: fixed-seed RNG, same input → same sequence every run (CI-safe).
 *  - At most N ~40 values per type.
 *  - No NaN/Infinity in floating-point samples (would cause false counter-examples under ==).
 *  - Reflection errors are caught and silently converted to None / skipped entries.
 *  - No dependency on any onion.compiler class — only java.lang / java.util.
 */
object ArgGenerator {

  private val N: Int = 40

  // Fixed-seed RNG — the only source of randomness.
  // Re-created for each generateValues call so that call order does not affect results.
  private def freshRng(): Random = new Random(42L)

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Returns up to N deterministic sample values (as AnyRef) for the given JVM type,
   * or None when the type is unsupported.
   *
   * @param t      JVM class obtained from e.g. Method.getParameterTypes
   * @param loader ClassLoader (passed through to flat-record instantiation)
   */
  def generateValues(t: Class[?], loader: ClassLoader): Option[List[AnyRef]] =
    generateValuesInternal(t, loader, depth = 0)

  // ---------------------------------------------------------------------------
  // Internal implementation
  // ---------------------------------------------------------------------------

  private val MAX_DEPTH = 3  // guard against pathological recursive records

  private def generateValuesInternal(
      t: Class[?],
      loader: ClassLoader,
      depth: Int
  ): Option[List[AnyRef]] = {
    if (depth > MAX_DEPTH) return None

    normalize(t) match {
      case Some(k) => Some(valuesFor(k))
      case None    => tryFlatRecord(t, loader, depth)
    }
  }

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

  private def valuesFor(k: ScalarKind): List[AnyRef] = k match {
    case KInt     => intValues()
    case KLong    => longValues()
    case KShort   => shortValues()
    case KByte    => byteValues()
    case KDouble  => doubleValues()
    case KFloat   => floatValues()
    case KBoolean => booleanValues()
    case KString  => stringValues()
  }

  // --- int ---
  private def intValues(): List[AnyRef] = {
    val boundary: List[Int] = List(
      0, 1, -1, 2, -2, 10, -10, 100, -100,
      Int.MaxValue, Int.MinValue
    )
    val rng = freshRng()
    val random: List[Int] = List.fill(N - boundary.size)(rng.nextInt())
    (boundary ++ random).take(N).map(java.lang.Integer.valueOf)
  }

  // --- long ---
  private def longValues(): List[AnyRef] = {
    val boundary: List[Long] = List(
      0L, 1L, -1L, 2L, -2L, 10L, -10L, 100L, -100L,
      Long.MaxValue, Long.MinValue
    )
    val rng = freshRng()
    val random: List[Long] = List.fill(N - boundary.size)(rng.nextLong())
    (boundary ++ random).take(N).map(java.lang.Long.valueOf)
  }

  // --- short ---
  private def shortValues(): List[AnyRef] = {
    val boundary: List[Short] = List(
      0, 1, -1, 2, -2, 10, -10, 100, -100,
      Short.MaxValue, Short.MinValue
    ).map(_.toShort)
    val rng = freshRng()
    val random: List[Short] = List.fill(N - boundary.size)(
      (rng.nextInt(Short.MaxValue.toInt * 2 + 2) + Short.MinValue.toInt).toShort
    )
    (boundary ++ random).take(N).map(java.lang.Short.valueOf)
  }

  // --- byte ---
  private def byteValues(): List[AnyRef] = {
    val boundary: List[Byte] = List(
      0, 1, -1, 2, -2, 10, -10, 100, -100, 127, -128
    ).map(_.toByte)
    val rng = freshRng()
    val bytes = Array.ofDim[Byte](N - boundary.size)
    rng.nextBytes(bytes)
    val random: List[Byte] = bytes.toList
    (boundary ++ random).take(N).map(java.lang.Byte.valueOf)
  }

  // --- double ---
  private def doubleValues(): List[AnyRef] = {
    val boundary: List[Double] = List(
      0.0, 1.0, -1.0, 0.5, -0.5, 100.0, -100.0,
      Double.MaxValue, Double.MinValue
    )
    val rng = freshRng()
    // Avoid NaN/Infinity: nextDouble() is in [0.0, 1.0), so scale by large value
    val random: List[Double] = List.fill(N - boundary.size)(
      (rng.nextDouble() - 0.5) * 2.0e15
    )
    (boundary ++ random).take(N).map(java.lang.Double.valueOf)
  }

  // --- float ---
  private def floatValues(): List[AnyRef] = {
    val boundary: List[Float] = List(
      0.0f, 1.0f, -1.0f, 0.5f, -0.5f, 100.0f, -100.0f,
      Float.MaxValue, Float.MinValue
    )
    val rng = freshRng()
    val random: List[Float] = List.fill(N - boundary.size)(
      ((rng.nextFloat() - 0.5f) * 2.0e10f)
    )
    (boundary ++ random).take(N).map(java.lang.Float.valueOf)
  }

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

  private def stringValues(): List[AnyRef] = {
    val rng = freshRng()
    val extra = List.fill(N - fixedStrings.size)(randomString(rng, 16))
    (fixedStrings ++ extra).take(N).map(s => s: AnyRef)
  }

  // ---------------------------------------------------------------------------
  // Flat record support
  // ---------------------------------------------------------------------------

  /**
   * If `t` has exactly one constructor and every parameter type has supported
   * values, build N instances via boundary-cross zip strategy.
   *
   * Strategy: for each param position we have a list of values.
   *   - We zip all positions together by index (shortest list determines length).
   *   - Then we add boundary-corner combinations by cycling each position through
   *     index 0 while keeping others at index 0 (one-at-a-time boundary sweep).
   *   - Total is capped at N, deduplicated by index.
   */
  private def tryFlatRecord(
      t: Class[?],
      loader: ClassLoader,
      depth: Int
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
        generateValuesInternal(paramTypes(i), loader, depth + 1) match {
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
    // component) biases toward correlated tuples like x==y and, once truncated to N, hides
    // counterexamples that need the components to differ.
    val maxLen = paramValueLists.map(_.length).max
    val allArgArrays: List[Array[AnyRef]] = (0 until math.min(N, maxLen)).map { i =>
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

    if (instances.isEmpty) None else Some(instances.take(N))
  }
}
