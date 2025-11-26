package onion.compiler.parser;

import onion.compiler.*;
import scala.collection.immutable.List;
import scala.collection.mutable.ArrayBuffer;
import scala.Option;
import scala.Option$;

/**
 * Java adapter for the Scala ASTBuilder trait to be used from JavaCC parser.
 * This class handles the Java-Scala interop complexities.
 */
public class ASTBuilderAdapter {
    private final ASTBuilder builder;
    
    public ASTBuilderAdapter(ASTBuilder builder) {
        this.builder = builder;
    }
    
    public ASTBuilderAdapter() {
        this(new DefaultASTBuilder());
    }
    
    // Helper methods for Java-Scala interop
    @SuppressWarnings("unchecked")
    public static <A> List<A> toList(ArrayBuffer<A> buffer) {
        return (List<A>)buffer.toList();
    }
    
    @SuppressWarnings("unchecked")
    public static <A> List<A> asList(A element) {
        ArrayBuffer<A> buffer = new ArrayBuffer<A>();
        AST.append(buffer, element);
        return (List<A>)buffer.toList();
    }
    
    public static <T> Option<T> option(T value) {
        return Option$.MODULE$.apply(value);
    }
    
    public static <T> Option<T> none() {
        return Option$.MODULE$.empty();
    }
    
    // Delegation methods
    public AST.CompilationUnit createCompilationUnit(
        Location location,
        String sourceFile,
        AST.ModuleDeclaration module,
        AST.ImportClause imports,
        List<AST.Toplevel> toplevels
    ) {
        return builder.createCompilationUnit(location, sourceFile, module, imports, toplevels);
    }
    
    public AST.ModuleDeclaration createModuleDeclaration(Location location, String name) {
        return builder.createModuleDeclaration(location, name);
    }
    
    public AST.ImportClause createImportClause(Location location, List<scala.Tuple2<String, String>> mapping) {
        return builder.createImportClause(location, mapping);
    }
    
    public AST.ClassDeclaration createClassDeclaration(
        Location location,
        int modifiers,
        String name,
        AST.TypeNode superClass,
        List<AST.TypeNode> interfaces,
        Option<AST.AccessSection> defaultSection,
        List<AST.AccessSection> sections,
        List<AST.TypeParameter> typeParameters
    ) {
        return builder.createClassDeclaration(location, modifiers, name, superClass, interfaces, defaultSection, sections, typeParameters);
    }

    // Backward-compatible overload without type parameters
    public AST.ClassDeclaration createClassDeclaration(
        Location location,
        int modifiers,
        String name,
        AST.TypeNode superClass,
        List<AST.TypeNode> interfaces,
        Option<AST.AccessSection> defaultSection,
        List<AST.AccessSection> sections
    ) {
        return createClassDeclaration(location, modifiers, name, superClass, interfaces, defaultSection, sections, scala.collection.immutable.List$.MODULE$.<AST.TypeParameter>empty());
    }
    
    public AST.InterfaceDeclaration createInterfaceDeclaration(
        Location location,
        int modifiers,
        String name,
        List<AST.TypeNode> superTypes,
        List<AST.MethodDeclaration> methods,
        List<AST.TypeParameter> typeParameters
    ) {
        return builder.createInterfaceDeclaration(location, modifiers, name, superTypes, methods, typeParameters);
    }

    // Backward-compatible overload without type parameters
    public AST.InterfaceDeclaration createInterfaceDeclaration(
        Location location,
        int modifiers,
        String name,
        List<AST.TypeNode> superTypes,
        List<AST.MethodDeclaration> methods
    ) {
        return createInterfaceDeclaration(location, modifiers, name, superTypes, methods, scala.collection.immutable.List$.MODULE$.<AST.TypeParameter>empty());
    }
    
    public AST.RecordDeclaration createRecordDeclaration(
        Location location,
        int modifiers,
        String name,
        List<AST.Argument> args
    ) {
        return builder.createRecordDeclaration(location, modifiers, name, args);
    }
    
    public AST.MethodDeclaration createMethodDeclaration(
        Location location,
        int modifiers,
        String name,
        List<AST.Argument> args,
        AST.TypeNode returnType,
        AST.BlockExpression body,
        List<AST.TypeParameter> typeParameters
    ) {
        return builder.createMethodDeclaration(location, modifiers, name, args, returnType, body, typeParameters);
    }

    // Backward-compatible overload without type parameters
    public AST.MethodDeclaration createMethodDeclaration(
        Location location,
        int modifiers,
        String name,
        List<AST.Argument> args,
        AST.TypeNode returnType,
        AST.BlockExpression body
    ) {
        return createMethodDeclaration(location, modifiers, name, args, returnType, body, scala.collection.immutable.List$.MODULE$.<AST.TypeParameter>empty());
    }

    public AST.TypeParameter createTypeParameter(Location location, String name, Option<AST.TypeNode> upperBound) {
        return builder.createTypeParameter(location, name, upperBound);
    }
    
    public AST.ConstructorDeclaration createConstructorDeclaration(
        Location location,
        int modifiers,
        List<AST.Argument> args,
        List<AST.Expression> params,
        AST.BlockExpression body
    ) {
        return builder.createConstructorDeclaration(location, modifiers, args, params, body);
    }
    
    public AST.FieldDeclaration createFieldDeclaration(
        Location location,
        int modifiers,
        String name,
        AST.TypeNode typeRef,
        AST.Expression init
    ) {
        return builder.createFieldDeclaration(location, modifiers, name, typeRef, init);
    }
    
    public AST.BlockExpression createBlockExpression(
        Location location,
        List<AST.CompoundExpression> statements
    ) {
        return builder.createBlockExpression(location, statements);
    }
    
    public AST.Expression createBinaryExpression(
        Location location,
        String operator,
        AST.Expression lhs,
        AST.Expression rhs
    ) {
        return builder.createBinaryExpression(location, operator, lhs, rhs);
    }
    
    public AST.Expression createUnaryExpression(
        Location location,
        String operator,
        AST.Expression operand
    ) {
        return builder.createUnaryExpression(location, operator, operand);
    }
    
    public AST.Expression createLiteral(Location location, Object value) {
        return builder.createLiteral(location, value);
    }
    
    public AST.Id createIdentifier(Location location, String name) {
        return builder.createIdentifier(location, name);
    }
    
    public AST.TypeNode createTypeNode(
        Location location,
        AST.TypeDescriptor desc,
        boolean isRelaxed
    ) {
        return builder.createTypeNode(location, desc, isRelaxed);
    }
    
    public AST.Argument createArgument(
        Location location,
        String name,
        AST.TypeNode typeRef
    ) {
        return builder.createArgument(location, name, typeRef);
    }
    
    public AST.AccessSection createAccessSection(
        Location location,
        int modifiers,
        List<AST.MemberDeclaration> members
    ) {
        return builder.createAccessSection(location, modifiers, members);
    }
    
    // Additional convenience methods for creating specific expressions
    public AST.Expression createAddition(Location location, AST.Expression lhs, AST.Expression rhs) {
        return new AST.Addition(location, lhs, rhs);
    }
    
    public AST.Expression createSubtraction(Location location, AST.Expression lhs, AST.Expression rhs) {
        return new AST.Subtraction(location, lhs, rhs);
    }
    
    public AST.Expression createMultiplication(Location location, AST.Expression lhs, AST.Expression rhs) {
        return new AST.Multiplication(location, lhs, rhs);
    }
    
    public AST.Expression createDivision(Location location, AST.Expression lhs, AST.Expression rhs) {
        return new AST.Division(location, lhs, rhs);
    }
    
    public AST.Expression createAssignment(Location location, AST.Expression lhs, AST.Expression rhs) {
        return new AST.Assignment(location, lhs, rhs);
    }
    
    public AST.Expression createMethodCall(
        Location location,
        AST.Expression receiver,
        String name,
        List<AST.Expression> args
    ) {
        return new AST.MethodCall(location, receiver, name, args);
    }
    
    public AST.Expression createNewObject(
        Location location,
        AST.TypeNode typeRef,
        List<AST.Expression> args
    ) {
        return new AST.NewObject(location, typeRef, args);
    }
    
    public AST.Expression createCurrentInstance(Location location) {
        return new AST.CurrentInstance(location);
    }
    
    public AST.Expression createCast(Location location, AST.Expression src, AST.TypeNode to) {
        return new AST.Cast(location, src, to);
    }
    
    public AST.Expression createIsInstance(Location location, AST.Expression target, AST.TypeNode typeRef) {
        return new AST.IsInstance(location, target, typeRef);
    }
    
    public AST.Expression createIndexing(Location location, AST.Expression lhs, AST.Expression rhs) {
        return new AST.Indexing(location, lhs, rhs);
    }
    
    public AST.Expression createListLiteral(Location location, List<AST.Expression> elements) {
        return new AST.ListLiteral(location, elements);
    }
    
    public AST.Expression createStringInterpolation(Location location, List<String> parts, List<AST.Expression> expressions) {
        return new AST.StringInterpolation(location, parts, expressions);
    }
    
    // Statement creation methods
    public AST.ReturnExpression createReturn(Location location, AST.Expression value) {
        return new AST.ReturnExpression(location, value);
    }
    
    public AST.BreakExpression createBreak(Location location) {
        return new AST.BreakExpression(location);
    }
    
    public AST.ContinueExpression createContinue(Location location) {
        return new AST.ContinueExpression(location);
    }
    
    public AST.IfExpression createIf(
        Location location,
        AST.Expression cond,
        AST.BlockExpression pos,
        AST.BlockExpression neg
    ) {
        return new AST.IfExpression(location, cond, pos, neg);
    }
    
    public AST.WhileExpression createWhile(
        Location location,
        AST.Expression cond,
        AST.BlockExpression body
    ) {
        return new AST.WhileExpression(location, cond, body);
    }
    
    public AST.ForExpression createFor(
        Location location,
        AST.CompoundExpression init,
        AST.Expression cond,
        AST.Expression update,
        AST.BlockExpression body
    ) {
        return new AST.ForExpression(location, init, cond, update, body);
    }
    
    public AST.ForeachExpression createForeach(
        Location location,
        AST.Argument arg,
        AST.Expression collection,
        AST.BlockExpression body
    ) {
        return new AST.ForeachExpression(location, arg, collection, body);
    }
}
