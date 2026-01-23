package onion.compiler.bytecode

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.diagrams.Diagrams
import org.objectweb.asm.{Type => AsmType, Opcodes}

/**
 * Unit tests for ASM bytecode generation utilities.
 *
 * Tests cover:
 * - AsmUtil helper functions (internalName, objectType)
 * - Type descriptor conversions
 * - Basic bytecode patterns
 */
class AsmCodeGenerationSpec extends AnyFunSpec with Diagrams {

  // ============================================
  // AsmUtil.internalName Tests
  // ============================================

  describe("AsmUtil.internalName") {
    it("converts simple class name") {
      assert(AsmUtil.internalName("String") == "String")
    }

    it("converts single-level package name") {
      assert(AsmUtil.internalName("java.String") == "java/String")
    }

    it("converts multi-level package name") {
      assert(AsmUtil.internalName("java.lang.Object") == "java/lang/Object")
    }

    it("converts deeply nested package name") {
      assert(AsmUtil.internalName("com.example.foo.bar.Baz") == "com/example/foo/bar/Baz")
    }

    it("handles already-internal format (no change needed)") {
      // Input without dots should remain unchanged
      assert(AsmUtil.internalName("Object") == "Object")
    }

    it("converts Onion standard library classes") {
      assert(AsmUtil.internalName("onion.IO") == "onion/IO")
      assert(AsmUtil.internalName("onion.Function1") == "onion/Function1")
    }
  }

  // ============================================
  // AsmUtil.objectType Tests
  // ============================================

  describe("AsmUtil.objectType") {
    it("creates correct AsmType for java.lang.Object") {
      val objType = AsmUtil.objectType("java.lang.Object")
      assert(objType.getInternalName == "java/lang/Object")
      assert(objType.getDescriptor == "Ljava/lang/Object;")
    }

    it("creates correct AsmType for java.lang.String") {
      val strType = AsmUtil.objectType("java.lang.String")
      assert(strType.getInternalName == "java/lang/String")
      assert(strType.getDescriptor == "Ljava/lang/String;")
    }

    it("creates correct AsmType for simple class name") {
      val simpleType = AsmUtil.objectType("MyClass")
      assert(simpleType.getInternalName == "MyClass")
      assert(simpleType.getDescriptor == "LMyClass;")
    }
  }

  // ============================================
  // AsmUtil Constants Tests
  // ============================================

  describe("AsmUtil constants") {
    it("JavaLangObject is correct") {
      assert(AsmUtil.JavaLangObject == "java.lang.Object")
    }

    it("JavaUtilArrayList is correct") {
      assert(AsmUtil.JavaUtilArrayList == "java.util.ArrayList")
    }
  }

  // ============================================
  // ASM Type Descriptor Tests
  // ============================================

  describe("ASM Type descriptors") {
    it("primitive type descriptors") {
      assert(AsmType.INT_TYPE.getDescriptor == "I")
      assert(AsmType.LONG_TYPE.getDescriptor == "J")
      assert(AsmType.DOUBLE_TYPE.getDescriptor == "D")
      assert(AsmType.FLOAT_TYPE.getDescriptor == "F")
      assert(AsmType.BOOLEAN_TYPE.getDescriptor == "Z")
      assert(AsmType.BYTE_TYPE.getDescriptor == "B")
      assert(AsmType.SHORT_TYPE.getDescriptor == "S")
      assert(AsmType.CHAR_TYPE.getDescriptor == "C")
      assert(AsmType.VOID_TYPE.getDescriptor == "V")
    }

    it("array type descriptors") {
      val intArrayType = AsmType.getType("[I")
      assert(intArrayType.getDescriptor == "[I")
      assert(intArrayType.getElementType == AsmType.INT_TYPE)
    }

    it("object array type descriptors") {
      val stringArrayType = AsmType.getType("[Ljava/lang/String;")
      assert(stringArrayType.getDescriptor == "[Ljava/lang/String;")
    }

    it("multi-dimensional array type descriptors") {
      val intArray2D = AsmType.getType("[[I")
      assert(intArray2D.getDescriptor == "[[I")
      assert(intArray2D.getDimensions == 2)
    }
  }

  // ============================================
  // Method Descriptor Tests
  // ============================================

  describe("Method descriptors") {
    it("method with no arguments and void return") {
      val desc = AsmType.getMethodDescriptor(AsmType.VOID_TYPE)
      assert(desc == "()V")
    }

    it("method with int argument and int return") {
      val desc = AsmType.getMethodDescriptor(AsmType.INT_TYPE, AsmType.INT_TYPE)
      assert(desc == "(I)I")
    }

    it("method with multiple arguments") {
      val desc = AsmType.getMethodDescriptor(
        AsmType.BOOLEAN_TYPE,
        AsmType.INT_TYPE,
        AsmType.LONG_TYPE,
        AsmType.getObjectType("java/lang/String")
      )
      assert(desc == "(IJLjava/lang/String;)Z")
    }

    it("method with array argument") {
      val stringArrayType = AsmType.getType("[Ljava/lang/String;")
      val desc = AsmType.getMethodDescriptor(AsmType.VOID_TYPE, stringArrayType)
      assert(desc == "([Ljava/lang/String;)V")
    }
  }

  // ============================================
  // Opcode Constants Tests
  // ============================================

  describe("JVM Opcodes used in code generation") {
    it("load instructions") {
      assert(Opcodes.ILOAD == 21)
      assert(Opcodes.LLOAD == 22)
      assert(Opcodes.FLOAD == 23)
      assert(Opcodes.DLOAD == 24)
      assert(Opcodes.ALOAD == 25)
    }

    it("store instructions") {
      assert(Opcodes.ISTORE == 54)
      assert(Opcodes.LSTORE == 55)
      assert(Opcodes.FSTORE == 56)
      assert(Opcodes.DSTORE == 57)
      assert(Opcodes.ASTORE == 58)
    }

    it("return instructions") {
      assert(Opcodes.IRETURN == 172)
      assert(Opcodes.LRETURN == 173)
      assert(Opcodes.FRETURN == 174)
      assert(Opcodes.DRETURN == 175)
      assert(Opcodes.ARETURN == 176)
      assert(Opcodes.RETURN == 177)
    }

    it("constant instructions") {
      assert(Opcodes.ACONST_NULL == 1)
      assert(Opcodes.ICONST_0 == 3)
      assert(Opcodes.ICONST_1 == 4)
      assert(Opcodes.LCONST_0 == 9)
      assert(Opcodes.LCONST_1 == 10)
      assert(Opcodes.FCONST_0 == 11)
      assert(Opcodes.DCONST_0 == 14)
    }

    it("invoke instructions") {
      assert(Opcodes.INVOKEVIRTUAL == 182)
      assert(Opcodes.INVOKESPECIAL == 183)
      assert(Opcodes.INVOKESTATIC == 184)
      assert(Opcodes.INVOKEINTERFACE == 185)
    }
  }

  // ============================================
  // Type Size Tests (for stack slot calculation)
  // ============================================

  describe("Type sizes for stack/local calculations") {
    it("primitive type sizes") {
      assert(AsmType.INT_TYPE.getSize == 1)
      assert(AsmType.LONG_TYPE.getSize == 2) // long takes 2 slots
      assert(AsmType.DOUBLE_TYPE.getSize == 2) // double takes 2 slots
      assert(AsmType.FLOAT_TYPE.getSize == 1)
      assert(AsmType.BOOLEAN_TYPE.getSize == 1)
      assert(AsmType.BYTE_TYPE.getSize == 1)
      assert(AsmType.SHORT_TYPE.getSize == 1)
      assert(AsmType.CHAR_TYPE.getSize == 1)
    }

    it("reference type sizes") {
      val objType = AsmType.getObjectType("java/lang/Object")
      assert(objType.getSize == 1)

      val arrayType = AsmType.getType("[I")
      assert(arrayType.getSize == 1)
    }
  }

  // ============================================
  // Type Sort Tests
  // ============================================

  describe("Type sort classification") {
    it("identifies primitive types") {
      assert(AsmType.INT_TYPE.getSort == AsmType.INT)
      assert(AsmType.LONG_TYPE.getSort == AsmType.LONG)
      assert(AsmType.DOUBLE_TYPE.getSort == AsmType.DOUBLE)
      assert(AsmType.FLOAT_TYPE.getSort == AsmType.FLOAT)
      assert(AsmType.BOOLEAN_TYPE.getSort == AsmType.BOOLEAN)
      assert(AsmType.BYTE_TYPE.getSort == AsmType.BYTE)
      assert(AsmType.SHORT_TYPE.getSort == AsmType.SHORT)
      assert(AsmType.CHAR_TYPE.getSort == AsmType.CHAR)
      assert(AsmType.VOID_TYPE.getSort == AsmType.VOID)
    }

    it("identifies object types") {
      val objType = AsmType.getObjectType("java/lang/String")
      assert(objType.getSort == AsmType.OBJECT)
    }

    it("identifies array types") {
      val arrayType = AsmType.getType("[I")
      assert(arrayType.getSort == AsmType.ARRAY)
    }
  }
}
