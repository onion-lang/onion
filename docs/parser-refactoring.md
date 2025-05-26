# Parser Refactoring: Separating Grammar from AST Building

## Overview

This refactoring introduces the Builder pattern to separate parsing concerns from AST construction in the Onion compiler. This separation provides several benefits:

1. **Testability**: AST construction can be tested independently of parsing
2. **Flexibility**: Different AST builders can be used for different purposes
3. **Maintainability**: Grammar changes don't require AST construction changes and vice versa
4. **Extensibility**: New behaviors can be added without modifying the parser

## Architecture

### Before Refactoring

The original parser (`JJOnionParser.jj`) directly constructs AST nodes within the grammar rules:

```java
AST.ClassDeclaration class_decl(int mset) : {
  // ... variable declarations ...
}{
  t1="class" t2=<ID> /* ... parsing ... */ {
    return new AST.ClassDeclaration(  // Direct AST construction
      p(t1), mset, t2.image, ty1, toList(ty2s), sec3, toList(sec2s)
    );
  }
}
```

### After Refactoring

The refactored parser uses an `ASTBuilder` interface:

```java
AST.ClassDeclaration class_decl(int mset) : {
  // ... variable declarations ...
}{
  t1="class" t2=<ID> /* ... parsing ... */ {
    return builder.createClassDeclaration(  // Delegated to builder
      p(t1), mset, t2.image, ty1, toList(ty2s), sec3, toList(sec2s)
    );
  }
}
```

## Components

### 1. ASTBuilder Trait (`ASTBuilder.scala`)

Defines the interface for AST construction:

```scala
trait ASTBuilder {
  def createCompilationUnit(...): AST.CompilationUnit
  def createClassDeclaration(...): AST.ClassDeclaration
  def createMethodDeclaration(...): AST.MethodDeclaration
  // ... other AST node creation methods
}
```

### 2. DefaultASTBuilder (`ASTBuilder.scala`)

Provides the default implementation that simply constructs AST nodes:

```scala
class DefaultASTBuilder extends ASTBuilder {
  def createClassDeclaration(...) = {
    AST.ClassDeclaration(location, modifiers, name, ...)
  }
}
```

### 3. ASTBuilderAdapter (`ASTBuilderAdapter.java`)

Java adapter for seamless integration with JavaCC:

```java
public class ASTBuilderAdapter {
  private final ASTBuilder builder;
  
  // Handles Java-Scala interop complexities
  public AST.ClassDeclaration createClassDeclaration(...) {
    return builder.createClassDeclaration(...);
  }
}
```

### 4. JJOnionParserRefactored (`JJOnionParserRefactored.jj`)

Modified JavaCC grammar that uses the builder pattern instead of direct AST construction.

## Use Cases

### 1. Custom Analysis

```scala
class AnalyzingASTBuilder extends DefaultASTBuilder {
  var methodCount = 0
  
  override def createMethodDeclaration(...) = {
    methodCount += 1
    super.createMethodDeclaration(...)
  }
}
```

### 2. Validation

```scala
class ValidatingASTBuilder extends DefaultASTBuilder {
  override def createMethodDeclaration(...) = {
    if (args.length > 10) {
      throw new IllegalArgumentException("Too many parameters")
    }
    super.createMethodDeclaration(...)
  }
}
```

### 3. Transformation

```scala
class TransformingASTBuilder extends DefaultASTBuilder {
  override def createMethodDeclaration(...) = {
    val modifiedBody = addLogging(body)
    super.createMethodDeclaration(..., modifiedBody)
  }
}
```

### 4. Debugging

```scala
class LoggingASTBuilder extends DefaultASTBuilder {
  override def createClassDeclaration(...) = {
    println(s"Creating class: $name at $location")
    super.createClassDeclaration(...)
  }
}
```

## Migration Strategy

1. **Phase 1**: Create builder infrastructure (completed)
   - ASTBuilder trait
   - DefaultASTBuilder implementation
   - ASTBuilderAdapter for Java interop

2. **Phase 2**: Refactor parser gradually
   - Start with simple constructs (literals, identifiers)
   - Move to complex constructs (classes, methods)
   - Maintain backward compatibility

3. **Phase 3**: Update existing code
   - Modify Parsing.scala to use new parser
   - Update tests to use refactored components

4. **Phase 4**: Leverage new capabilities
   - Add validation builders
   - Implement transformation builders
   - Create specialized builders for different compilation modes

## Benefits Realized

1. **Separation of Concerns**: Grammar rules focus on syntax; builders focus on semantics
2. **Testability**: AST construction logic can be unit tested without parsing
3. **Extensibility**: New compilation features can be added via custom builders
4. **Maintainability**: Changes to AST structure don't require grammar modifications
5. **Debugging**: Logging/tracing can be added without touching the parser

## Future Enhancements

1. **Builder Composition**: Chain multiple builders for complex transformations
2. **Context-Aware Building**: Builders that maintain compilation context
3. **Error Recovery**: Builders that can construct partial ASTs for better error messages
4. **Optimization**: Builders that perform early optimizations during parsing