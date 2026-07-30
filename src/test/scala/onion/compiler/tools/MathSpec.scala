package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the Math standard library module.
 */
class MathSpec extends AbstractShellSpec {

  describe("Math module") {

    describe("trigonometric functions") {
      it("computes sin(0) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::sin(0.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0.0))
      }

      it("computes cos(0) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::cos(0.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1.0))
      }

      it("computes sin(PI/2) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::sin(OnionMath::PI / 2.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1.0))
      }

      it("computes asin correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::asin(1.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.asin(1.0)))
      }

      it("computes acos correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::acos(0.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.acos(0.0)))
      }

      it("computes atan correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::atan(1.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.atan(1.0)))
      }

      it("computes atan2 correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::atan2(1.0, 1.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.atan2(1.0, 1.0)))
      }
    }

    describe("hyperbolic functions") {
      it("computes sinh(0) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::sinh(0.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0.0))
      }

      it("computes cosh(0) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::cosh(0.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1.0))
      }

      it("computes tanh(0) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::tanh(0.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0.0))
      }
    }

    describe("exponential and logarithmic functions") {
      it("computes sqrt correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::sqrt(16.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(4.0))
      }

      it("computes pow correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::pow(2.0, 10.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1024.0))
      }

      it("computes log(e) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::log(OnionMath::E)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1.0))
      }

      it("computes exp(0) correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::exp(0.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1.0))
      }

      it("computes log10 correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::log10(100.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2.0))
      }

      it("computes cbrt correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::cbrt(27.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.cbrt(27.0)))
      }
    }

    describe("absolute value") {
      it("computes abs of negative double") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::abs(-5.5)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(5.5))
      }

      it("computes absInt of negative int") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return OnionMath::absInt(-42)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(42))
      }

      it("computes absFloat of negative float") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Float {
            |    return OnionMath::absFloat(-3.5f)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3.5f))
      }

      it("computes absLong of negative long") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Long {
            |    return OnionMath::absLong(-100L)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(100L))
      }
    }

    describe("min and max") {
      it("computes min correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::min(3.14, 2.71)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(2.71))
      }

      it("computes max correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::max(3.14, 2.71)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3.14))
      }

      it("computes minInt correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return OnionMath::minInt(10, 20)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(10))
      }

      it("computes maxInt correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return OnionMath::maxInt(10, 20)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(20))
      }

      it("computes minLong correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Long {
            |    return OnionMath::minLong(10L, 20L)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(10L))
      }

      it("computes maxLong correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Long {
            |    return OnionMath::maxLong(10L, 20L)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(20L))
      }
    }

    describe("rounding functions") {
      it("computes floor correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::floor(3.7)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(3.0))
      }

      it("computes ceil correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::ceil(3.2)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(4.0))
      }

      it("computes round correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Long {
            |    return OnionMath::round(3.5)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(4L))
      }

      it("computes roundFloat correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return OnionMath::roundFloat(3.5f)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(4))
      }
    }

    describe("sign functions") {
      it("computes signum of a negative double") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::signum(-5.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(-1.0))
      }

      it("computes signumFloat of a positive float") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Float {
            |    return OnionMath::signumFloat(5.0f)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(1.0f))
      }
    }

    describe("angle conversion") {
      it("converts degrees to radians correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::toRadians(180.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.toRadians(180.0)))
      }

      it("converts radians to degrees correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::toDegrees(OnionMath::PI)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.toDegrees(java.lang.Math.PI)))
      }
    }

    describe("hypotenuse") {
      it("computes hypot correctly") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::hypot(3.0, 4.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(5.0))
      }
    }

    describe("clamp") {
      it("clamps value within range") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::clamp(5.0, 0.0, 10.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(5.0))
      }

      it("clamps value below min") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::clamp(-5.0, 0.0, 10.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0.0))
      }

      it("clamps value above max") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::clamp(15.0, 0.0, 10.0)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(10.0))
      }

      it("clampInt clamps an int value within range") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return OnionMath::clampInt(5, 0, 10)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(5))
      }

      it("clampInt clamps an int value below min") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return OnionMath::clampInt(-5, 0, 10)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(0))
      }

      it("clampInt clamps an int value above max") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Int {
            |    return OnionMath::clampInt(15, 0, 10)
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(10))
      }
    }

    describe("random") {
      it("generates random number in range [0, 1)") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val x = OnionMath::random()
            |    if(x < 0.0) { return false; }
            |    if(x > 1.0) { return false; }
            |    return true
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }

      it("generates random int in range") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Boolean {
            |    val x = OnionMath::randomInt(1, 10)
            |    if(x < 1) { return false; }
            |    if(x > 10) { return false; }
            |    return true
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(true))
      }
    }

    describe("constants") {
      it("has correct PI value") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::PI
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.PI))
      }

      it("has correct E value") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): Double {
            |    return OnionMath::E
            |  }
            |}
          """.stripMargin,
          "None",
          Array()
        )
        assert(result == Shell.Success(java.lang.Math.E))
      }
    }
  }
}
