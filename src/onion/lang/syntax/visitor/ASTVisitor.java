/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.syntax.visitor;

import onion.lang.syntax.*;


/**
 * @author mizu
 */
public class ASTVisitor<C> {
  
  public Object visit(FieldOrMethodRef ast, C context){ return null; }

  public Object visit(Addition ast, C context) { return null; }
  
  public Object visit(Modifier ast, C context){ return null; }

  public Object visit(StaticIDExpression ast, C context) { return null; }
  
  public Object visit(ListLiteral ast,  C context){ return null; }
  
  public Object visit(ClosureExpression ast, C context){ return null; }

  public Object visit(StaticMethodCall ast, C context) { return null; }

  public Object visit(AdditionAssignment ast, C context) { return null; }

  public Object visit(Argument ast, C context) { return null; }

  public Object visit(Assignment ast, C context) { return null; }

  public Object visit(BitAnd ast, C context) { return null; }

  public Object visit(BitOr ast, C context) { return null; }

  public Object visit(BlockStatement ast, C context) { return null; }

  public Object visit(BooleanLiteral ast, C context) { return null; }

  public Object visit(BreakStatement ast, C context) { return null; }

  public Object visit(Cast ast, C context) { return null; }

  public Object visit(ClassDeclaration ast, C context) { return null; }

  public Object visit(CompilationUnit ast, C context) { return null; }

  public Object visit(CondStatement ast, C context) { return null; }

  public Object visit(ConstructorDeclaration ast, C context) { return null; }

  public Object visit(ContinueStatement ast, C context) { return null; }

  public Object visit(CurrentInstance ast, C context) { return null; }

  public Object visit(DelegationDeclaration ast, C context) { return null; }

  public Object visit(Division ast, C context) { return null; }

  public Object visit(DivisionAssignment ast, C context) { return null; }

  public Object visit(DoubleLiteral ast, C context) { return null; }

  public Object visit(EmptyStatement ast, C context) { return null; }
  
  public Object visit(Elvis ast, C context) { return null; }

  public Object visit(Equal ast, C context) { return null; }

  public Object visit(ExpressionStatement ast, C context) { return null; }

  public Object visit(FieldDeclaration ast, C context) { return null; }

  public Object visit(SelfFieldReference ast, C context) { return null; }

  public Object visit(FloatLiteral ast, C context) { return null; }

  public Object visit(ForStatement ast, C context) { return null; }
  
  public Object visit(ForeachStatement ast, C context){ return null; }

  public Object visit(GreaterOrEqual ast, C context) { return null; }

  public Object visit(GreaterThan ast, C context) { return null; }

  public Object visit(Id ast, C context) { return null; }

  public Object visit(IfStatement ast, C context) { return null; }
  
  public Object visit(SynchronizedStatement ast, C context) { return null; }

  public Object visit(ImportListDeclaration ast, C context) { return null; }

  public Object visit(ImportFileDeclaration ast, C context) { return null; }

  public Object visit(Indexing ast, C context) { return null; }

  public Object visit(NewObject ast, C context) { return null; }
  
  public Object visit(NewArray ast, C context) { return null; }

  public Object visit(IntegerLiteral ast, C context) { return null; }

  public Object visit(InterfaceDeclaration ast, C context) { return null; }
  
  public Object visit(IsInstance ast, C context) { return null; }
  
  public Object visit(LessOrEqual ast, C context) { return null; }
  
  public Object visit(LessThan ast, C context) { return null; }

  public Object visit(LocalVariableDeclaration ast, C context) { return null; }

  public Object visit(LogicalAnd ast, C context) { return null; }
  
  public Object visit(LogicalOr or, C context) { return null; }
  
  public Object visit(LogicalRightShift ast, C context) { return null; }

  public Object visit(LongLiteral ast, C context) { return null; }
  
  public Object visit(MathLeftShift ast, C context) { return null; }
  
  public Object visit(MathRightShift ast, C context) { return null; }

  public Object visit(MethodCall ast, C context) { return null; }
  
  public Object visit(SelfMethodCall ast, C context) { return null; }
  
  public Object visit(SuperMethodCall ast, C context) { return null; }
  
  public Object visit(MethodDeclaration ast, C context) { return null; }

  public Object visit(InterfaceMethodDeclaration ast, C context) { return null; }

  public Object visit(ModuleDeclaration ast, C context) { return null; }

  public Object visit(Modulo ast, C context) { return null; }

  public Object visit(ModuloAssignment ast, C context) { return null; }

  public Object visit(Multiplication ast, C context) { return null; }
  
  public Object visit(MultiplicationAssignment ast, C context) { return null; }

  public Object visit(Negate ast, C context) { return null; }
  
  public Object visit(NullLiteral ast, C context) { return null; }

  public Object visit(Not ast, C context) { return null; }

  public Object visit(NotEqual ast, C context) { return null; }

  public Object visit(Posit ast, C context) { return null; }
  
  public Object visit(ReturnStatement ast, C context) { return null; }
  
  public Object visit(SelectStatement ast, C context) { return null; }
  
  public Object visit(StringLiteral ast, C context) { return null; }
  
  public Object visit(Subtraction ast, C context) { return null; }

  public Object visit(SubtractionAssignment ast, C context) { return null; }

  public Object visit(ThrowStatement ast, C context) { return null; }
  
  public Object visit(TypeArgument ast, C context){ return null; }
  
  public Object visit(RawTypeNode ast, C context){ return null; }
  
  public Object visit(TypeSpec ast, C context){ return null; }

  public Object visit(TryStatement ast, C context) { return null; }

  public Object visit(WhileStatement ast, C context) { return null; }

  public Object visit(XOR ast, C context) { return null; }
  
  public Object visit(AccessSection ast, C context) { return null; }

  public Object visit(CaseBranch ast, C context) { return null; }

  public Object visit(FunctionDeclaration ast, C context) { return null; }

  public Object visit(GlobalVariableDeclaration ast, C context) { return null; }

  public Object visit(ReferenceEqual ast, C context) { return null; }
  
  public Object visit(ReferenceNotEqual ast, C context) { return null; }
  
  public Object visit(CharacterLiteral literal, C context) { return null; }
  
  public Object visit(PostIncrement node, C context) { return null; }
  
  public Object visit(PostDecrement node, C context) { return null; }
  
  public Object accept(AstNode node){
    return node.accept(this, null);
  }
  
  public Object accept(AstNode node, C context){
    return node.accept(this, context);
  }
  
  public Object[] acceptReduce(AstNode[] nodes, C context){
    Object[] results = new Object[nodes.length];
    for(int i = 0; i < nodes.length; i++){
      results[i] = nodes[i].accept(this, context);
    }
    return results;
  }
  
  public void acceptEach(AstNode[] asts, C context){
    for(int i = 0; i < asts.length; i++){
      asts[i].accept(this, context);
    }    
  }

}