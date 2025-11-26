package onion.compiler

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.diagrams.Diagrams
import scala.collection.immutable.List

/**
 * Test implementation of ASTBuilder that demonstrates how the builder pattern
 * allows for custom AST construction behavior without modifying the parser.
 */
class TestASTBuilder extends DefaultASTBuilder {
  
  // Track all method declarations for analysis
  var methodCount = 0
  var methodNames = List.empty[String]
  
  override def createMethodDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument],
    returnType: AST.TypeNode,
    body: AST.BlockExpression,
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.MethodDeclaration = {
    // Custom behavior: track method statistics
    methodCount += 1
    methodNames = methodNames :+ name
    
    // Could also perform transformations here, e.g.:
    // - Add logging to method bodies
    // - Inject security checks
    // - Optimize certain patterns
    
    super.createMethodDeclaration(location, modifiers, name, args, returnType, body, typeParameters)
  }
  
  // Example: Automatically add toString methods to records
  override def createRecordDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument]
  ): AST.RecordDeclaration = {
    // Could inject additional methods here
    super.createRecordDeclaration(location, modifiers, name, args)
  }
}

/**
 * Logging AST builder that prints AST construction for debugging
 */
class LoggingASTBuilder extends DefaultASTBuilder {
  
  override def createClassDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    superClass: AST.TypeNode,
    interfaces: List[AST.TypeNode],
    defaultSection: Option[AST.AccessSection],
    sections: List[AST.AccessSection],
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.ClassDeclaration = {
    println(s"Creating class: $name at $location")
    super.createClassDeclaration(location, modifiers, name, superClass, interfaces, defaultSection, sections, typeParameters)
  }
  
  override def createMethodDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument],
    returnType: AST.TypeNode,
    body: AST.BlockExpression,
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.MethodDeclaration = {
    println(s"Creating method: $name with ${args.length} arguments at $location")
    super.createMethodDeclaration(location, modifiers, name, args, returnType, body, typeParameters)
  }
}

/**
 * Validating AST builder that enforces coding standards
 */
class ValidatingASTBuilder extends DefaultASTBuilder {
  
  override def createMethodDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument],
    returnType: AST.TypeNode,
    body: AST.BlockExpression,
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.MethodDeclaration = {
    // Enforce naming conventions
    if (!name.matches("[a-z][a-zA-Z0-9]*")) {
      throw new IllegalArgumentException(s"Method name '$name' does not follow camelCase convention at $location")
    }
    
    // Enforce parameter limits
    if (args.length > 10) {
      throw new IllegalArgumentException(s"Method '$name' has too many parameters (${args.length}) at $location")
    }
    
    super.createMethodDeclaration(location, modifiers, name, args, returnType, body, typeParameters)
  }
  
  override def createClassDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    superClass: AST.TypeNode,
    interfaces: List[AST.TypeNode],
    defaultSection: Option[AST.AccessSection],
    sections: List[AST.AccessSection],
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.ClassDeclaration = {
    // Enforce naming conventions
    if (!name.matches("[A-Z][a-zA-Z0-9]*")) {
      throw new IllegalArgumentException(s"Class name '$name' does not follow PascalCase convention at $location")
    }
    
    super.createClassDeclaration(location, modifiers, name, superClass, interfaces, defaultSection, sections, typeParameters)
  }
}

class ASTBuilderSpec extends AnyFunSpec with Diagrams {
  describe("ASTBuilder pattern") {
    it("allows custom AST construction behavior") {
      val testBuilder = new TestASTBuilder()
      val location = new Location(1, 1)
      
      // Create some methods
      testBuilder.createMethodDeclaration(
        location, 
        AST.M_PUBLIC, 
        "doSomething", 
        List.empty, 
        null, 
        AST.BlockExpression(location, List.empty)
      )
      
      testBuilder.createMethodDeclaration(
        location, 
        AST.M_PRIVATE, 
        "helper", 
        List.empty, 
        null, 
        AST.BlockExpression(location, List.empty)
      )
      
      assert(testBuilder.methodCount == 2)
      assert(testBuilder.methodNames == List("doSomething", "helper"))
    }
    
    it("supports validation during AST construction") {
      val validatingBuilder = new ValidatingASTBuilder()
      val location = new Location(1, 1)
      
      // Valid method name
      validatingBuilder.createMethodDeclaration(
        location,
        AST.M_PUBLIC,
        "validMethodName",
        List.empty,
        null,
        AST.BlockExpression(location, List.empty)
      )
      
      // Invalid method name should throw
      intercept[IllegalArgumentException] {
        validatingBuilder.createMethodDeclaration(
          location,
          AST.M_PUBLIC,
          "InvalidMethodName", // Starts with capital
          List.empty,
          null,
          AST.BlockExpression(location, List.empty)
        )
      }
      
      // Too many parameters should throw
      val tooManyArgs = (1 to 11).map(i => AST.Argument(location, s"arg$i", null)).toList
      intercept[IllegalArgumentException] {
        validatingBuilder.createMethodDeclaration(
          location,
          AST.M_PUBLIC,
          "methodWithTooManyArgs",
          tooManyArgs,
          null,
          AST.BlockExpression(location, List.empty)
        )
      }
    }
  }
}
