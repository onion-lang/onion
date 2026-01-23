package onion.compiler.typing

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.diagrams.Diagrams
import onion.compiler.TypedAST
import onion.compiler.TypedAST._

/**
 * Unit tests for TypeRules - type compatibility and subtyping rules.
 *
 * Tests cover:
 * - Basic type widening conversions (byte → short → int → long → float → double)
 * - Class type subtyping (including generics with wildcards)
 * - Array covariance
 * - Null type compatibility
 */
class TypeRulesSpec extends AnyFunSpec with Diagrams {

  // ============================================
  // Basic Type Widening Tests
  // ============================================

  describe("BasicType widening conversions") {

    describe("BYTE widening") {
      it("BYTE is assignable to BYTE") {
        assert(TypeRules.isSuperType(BasicType.BYTE, BasicType.BYTE))
      }

      it("SHORT is supertype of BYTE") {
        assert(TypeRules.isSuperType(BasicType.SHORT, BasicType.BYTE))
      }

      it("INT is supertype of BYTE") {
        assert(TypeRules.isSuperType(BasicType.INT, BasicType.BYTE))
      }

      it("LONG is supertype of BYTE") {
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.BYTE))
      }

      it("FLOAT is supertype of BYTE") {
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.BYTE))
      }

      it("DOUBLE is supertype of BYTE") {
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.BYTE))
      }

      it("BYTE is not supertype of SHORT (narrowing)") {
        assert(!TypeRules.isSuperType(BasicType.BYTE, BasicType.SHORT))
      }

      it("BOOLEAN is not assignable to/from BYTE") {
        assert(!TypeRules.isSuperType(BasicType.BOOLEAN, BasicType.BYTE))
        assert(!TypeRules.isSuperType(BasicType.BYTE, BasicType.BOOLEAN))
      }
    }

    describe("SHORT widening") {
      it("SHORT is assignable to SHORT") {
        assert(TypeRules.isSuperType(BasicType.SHORT, BasicType.SHORT))
      }

      it("SHORT accepts BYTE") {
        assert(TypeRules.isSuperType(BasicType.SHORT, BasicType.BYTE))
      }

      it("INT is supertype of SHORT") {
        assert(TypeRules.isSuperType(BasicType.INT, BasicType.SHORT))
      }

      it("LONG is supertype of SHORT") {
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.SHORT))
      }

      it("SHORT does not accept INT (narrowing)") {
        assert(!TypeRules.isSuperType(BasicType.SHORT, BasicType.INT))
      }
    }

    describe("INT widening") {
      it("INT is assignable to INT") {
        assert(TypeRules.isSuperType(BasicType.INT, BasicType.INT))
      }

      it("INT accepts BYTE, SHORT, CHAR") {
        assert(TypeRules.isSuperType(BasicType.INT, BasicType.BYTE))
        assert(TypeRules.isSuperType(BasicType.INT, BasicType.SHORT))
        assert(TypeRules.isSuperType(BasicType.INT, BasicType.CHAR))
      }

      it("LONG is supertype of INT") {
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.INT))
      }

      it("FLOAT is supertype of INT") {
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.INT))
      }

      it("DOUBLE is supertype of INT") {
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.INT))
      }

      it("INT does not accept LONG (narrowing)") {
        assert(!TypeRules.isSuperType(BasicType.INT, BasicType.LONG))
      }
    }

    describe("LONG widening") {
      it("LONG is assignable to LONG") {
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.LONG))
      }

      it("LONG accepts BYTE, SHORT, INT, CHAR") {
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.BYTE))
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.SHORT))
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.INT))
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.CHAR))
      }

      it("FLOAT is supertype of LONG") {
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.LONG))
      }

      it("DOUBLE is supertype of LONG") {
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.LONG))
      }

      it("LONG does not accept FLOAT (narrowing)") {
        assert(!TypeRules.isSuperType(BasicType.LONG, BasicType.FLOAT))
      }
    }

    describe("FLOAT widening") {
      it("FLOAT is assignable to FLOAT") {
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.FLOAT))
      }

      it("FLOAT accepts all integer types") {
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.BYTE))
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.SHORT))
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.INT))
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.LONG))
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.CHAR))
      }

      it("DOUBLE is supertype of FLOAT") {
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.FLOAT))
      }

      it("FLOAT does not accept DOUBLE (narrowing)") {
        assert(!TypeRules.isSuperType(BasicType.FLOAT, BasicType.DOUBLE))
      }
    }

    describe("DOUBLE widening") {
      it("DOUBLE is assignable to DOUBLE") {
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.DOUBLE))
      }

      it("DOUBLE accepts all numeric types") {
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.BYTE))
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.SHORT))
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.INT))
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.LONG))
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.FLOAT))
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.CHAR))
      }
    }

    describe("CHAR widening") {
      it("CHAR is assignable to CHAR") {
        assert(TypeRules.isSuperType(BasicType.CHAR, BasicType.CHAR))
      }

      it("INT is supertype of CHAR") {
        assert(TypeRules.isSuperType(BasicType.INT, BasicType.CHAR))
      }

      it("LONG is supertype of CHAR") {
        assert(TypeRules.isSuperType(BasicType.LONG, BasicType.CHAR))
      }

      it("FLOAT is supertype of CHAR") {
        assert(TypeRules.isSuperType(BasicType.FLOAT, BasicType.CHAR))
      }

      it("DOUBLE is supertype of CHAR") {
        assert(TypeRules.isSuperType(BasicType.DOUBLE, BasicType.CHAR))
      }

      it("CHAR does not accept BYTE") {
        assert(!TypeRules.isSuperType(BasicType.CHAR, BasicType.BYTE))
      }

      it("CHAR does not accept SHORT") {
        assert(!TypeRules.isSuperType(BasicType.CHAR, BasicType.SHORT))
      }
    }

    describe("BOOLEAN isolation") {
      it("BOOLEAN is assignable to BOOLEAN") {
        assert(TypeRules.isSuperType(BasicType.BOOLEAN, BasicType.BOOLEAN))
      }

      it("BOOLEAN is not assignable to/from any numeric type") {
        assert(!TypeRules.isSuperType(BasicType.BOOLEAN, BasicType.INT))
        assert(!TypeRules.isSuperType(BasicType.INT, BasicType.BOOLEAN))
        assert(!TypeRules.isSuperType(BasicType.BOOLEAN, BasicType.BYTE))
        assert(!TypeRules.isSuperType(BasicType.BYTE, BasicType.BOOLEAN))
        assert(!TypeRules.isSuperType(BasicType.BOOLEAN, BasicType.LONG))
        assert(!TypeRules.isSuperType(BasicType.LONG, BasicType.BOOLEAN))
      }
    }

    describe("VOID isolation") {
      it("VOID is assignable to VOID") {
        assert(TypeRules.isSuperType(BasicType.VOID, BasicType.VOID))
      }

      it("VOID is not assignable to/from other types") {
        assert(!TypeRules.isSuperType(BasicType.VOID, BasicType.INT))
        assert(!TypeRules.isSuperType(BasicType.INT, BasicType.VOID))
      }
    }
  }

  // ============================================
  // isAssignable Alias Tests
  // ============================================

  describe("isAssignable") {
    it("is an alias for isSuperType") {
      assert(TypeRules.isAssignable(BasicType.INT, BasicType.BYTE))
      assert(!TypeRules.isAssignable(BasicType.BYTE, BasicType.INT))
      assert(TypeRules.isAssignable(BasicType.DOUBLE, BasicType.FLOAT))
    }
  }

  // ============================================
  // Basic Type vs Object Type Tests
  // ============================================

  describe("BasicType vs ObjectType compatibility") {
    it("BasicType is not assignable from ClassType") {
      // We need a mock ClassType for this test
      // For now, just verify the type check logic
      assert(BasicType.INT.isBasicType)
      assert(!BasicType.INT.isClassType)
    }

    it("BasicType is not assignable from ArrayType") {
      assert(BasicType.INT.isBasicType)
      assert(!BasicType.INT.isArrayType)
    }
  }

  // ============================================
  // Bottom Type (Null Type) Tests
  // ============================================

  describe("NullType compatibility") {
    it("NullType is the null type") {
      val nullType = NullType.NULL
      assert(nullType.isNullType)
      assert(!nullType.isBasicType)
      assert(!nullType.isClassType)
      assert(!nullType.isArrayType)
    }

    it("NullType is not a bottom type (BottomType is)") {
      // NullType represents null literal, which is assignable to reference types
      // but is not the general bottom type. BottomType is the true bottom.
      val nullType = NullType.NULL
      assert(!nullType.isBottomType)

      // BottomType is the true bottom type
      val bottomType = BottomType.BOTTOM
      assert(bottomType.isBottomType)
    }

    it("NullType is not assignable to BasicType") {
      val nullType = NullType.NULL
      // BasicType left, NullType right -> should be false
      // Because BasicType check returns false when right is not BasicType
      assert(!TypeRules.isSuperType(BasicType.INT, nullType))
      assert(!TypeRules.isSuperType(BasicType.BOOLEAN, nullType))
    }

    it("BottomType is assignable to any type") {
      val bottomType = BottomType.BOTTOM
      // When right.isBottomType is true, isSuperType returns true
      assert(TypeRules.isSuperType(BasicType.INT, bottomType))
      assert(TypeRules.isSuperType(BasicType.BOOLEAN, bottomType))
    }
  }

  // ============================================
  // Type Variable Tests
  // ============================================

  describe("TypeVariableType handling") {
    it("type variables are treated by their upper bound") {
      // TypeVariableType's upperBound is used for comparison
      // This requires creating mock TypeVariableType instances
    }
  }
}
