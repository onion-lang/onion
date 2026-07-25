package onion.compiler.verification

import org.scalatest.funspec.AnyFunSpec

/**
 * ArgGenerator produces the sample values every `law` is checked against, and until
 * now it had no tests at all: the sample count and the RNG seed were private constants
 * duplicated across two files, so nothing pinned that a counterexample was reproducible
 * or that widening the search actually widened it.
 *
 * Both are now settings, and these are the specs that hold them to their contract.
 */
class ArgGeneratorSpec extends AnyFunSpec {

  private val loader = getClass.getClassLoader
  private def gen(t: Class[?], samples: Int = 40, seed: Long = 42L): Option[List[AnyRef]] =
    ArgGenerator.generateValues(t, loader, samples, seed)

  describe("determinism and the seed") {
    it("produces identical values for the same seed") {
      assert(gen(classOf[Int]) == gen(classOf[Int]))
      assert(gen(classOf[String]) == gen(classOf[String]))
    }

    it("produces different values for a different seed") {
      // Without this, reporting the seed in a counterexample would be theatre.
      assert(gen(classOf[Int], seed = 42L) != gen(classOf[Int], seed = 7L))
      assert(gen(classOf[String], seed = 42L) != gen(classOf[String], seed = 7L))
    }

    it("keeps the boundary values regardless of seed") {
      // Boundaries are the part that must not drift: a law is far more likely to be
      // falsified by 0 or Int.MinValue than by a random draw.
      val a = gen(classOf[Int], seed = 42L).get.take(11)
      val b = gen(classOf[Int], seed = 7L).get.take(11)
      assert(a == b)
      assert(a.contains(java.lang.Integer.valueOf(0)))
      assert(a.contains(java.lang.Integer.valueOf(Int.MinValue)))
    }
  }

  describe("the sample count") {
    it("caps the number of values produced") {
      assert(gen(classOf[Int], samples = 5).get.length == 5)
      assert(gen(classOf[String], samples = 12).get.length == 12)
    }

    it("produces more values when asked for more") {
      assert(gen(classOf[Int], samples = 100).get.length == 100)
    }

    it("does not invent values for a two-valued type") {
      // Boolean has exactly two inhabitants; asking for 40 must not fabricate more.
      assert(gen(classOf[Boolean], samples = 40).get.length == 2)
    }
  }

  describe("what can and cannot be generated") {
    it("generates every supported scalar") {
      val supported: List[Class[?]] = List(
        java.lang.Integer.TYPE, java.lang.Long.TYPE, java.lang.Short.TYPE,
        java.lang.Byte.TYPE, java.lang.Double.TYPE, java.lang.Float.TYPE,
        java.lang.Boolean.TYPE, classOf[java.lang.String]
      )
      supported.foreach(t => assert(gen(t).isDefined, s"no generator for $t"))
    }

    it("refuses arrays, interfaces and collections") {
      // These are exactly the cases that used to make a law silently not run (E0074).
      assert(gen(classOf[Array[Int]]).isEmpty)
      assert(gen(classOf[java.util.List[?]]).isEmpty)
      assert(gen(classOf[java.util.Map[?, ?]]).isEmpty)
      assert(gen(classOf[Runnable]).isEmpty)
    }

    it("refuses a class with more than one constructor") {
      assert(gen(classOf[java.lang.StringBuilder]).isEmpty)
    }
  }
}
