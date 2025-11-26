package onion.compiler

import scala.collection.immutable.List

trait ASTBuilder {
  def createCompilationUnit(
    location: Location,
    sourceFile: String,
    module: AST.ModuleDeclaration,
    imports: AST.ImportClause,
    toplevels: List[AST.Toplevel]
  ): AST.CompilationUnit

  def createModuleDeclaration(location: Location, name: String): AST.ModuleDeclaration

  def createImportClause(location: Location, mapping: List[(String, String)]): AST.ImportClause

  def createClassDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    superClass: AST.TypeNode,
    interfaces: List[AST.TypeNode],
    defaultSection: Option[AST.AccessSection],
    sections: List[AST.AccessSection],
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.ClassDeclaration

  def createInterfaceDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    superTypes: List[AST.TypeNode],
    methods: List[AST.MethodDeclaration],
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.InterfaceDeclaration

  def createRecordDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument]
  ): AST.RecordDeclaration

  def createMethodDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument],
    returnType: AST.TypeNode,
    body: AST.BlockExpression,
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.MethodDeclaration

  def createTypeParameter(
    location: Location,
    name: String,
    upperBound: Option[AST.TypeNode]
  ): AST.TypeParameter

  def createConstructorDeclaration(
    location: Location,
    modifiers: Int,
    args: List[AST.Argument],
    params: List[AST.Expression],
    body: AST.BlockExpression
  ): AST.ConstructorDeclaration

  def createFieldDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    typeRef: AST.TypeNode,
    init: AST.Expression
  ): AST.FieldDeclaration

  def createBlockExpression(
    location: Location,
    statements: List[AST.CompoundExpression]
  ): AST.BlockExpression

  def createBinaryExpression(
    location: Location,
    operator: String,
    lhs: AST.Expression,
    rhs: AST.Expression
  ): AST.Expression

  def createUnaryExpression(
    location: Location,
    operator: String,
    operand: AST.Expression
  ): AST.Expression

  def createLiteral(location: Location, value: Any): AST.Expression

  def createIdentifier(location: Location, name: String): AST.Id

  def createTypeNode(
    location: Location,
    desc: AST.TypeDescriptor,
    isRelaxed: Boolean
  ): AST.TypeNode

  def createArgument(
    location: Location,
    name: String,
    typeRef: AST.TypeNode
  ): AST.Argument

  def createAccessSection(
    location: Location,
    modifiers: Int,
    members: List[AST.MemberDeclaration]
  ): AST.AccessSection
}

class DefaultASTBuilder extends ASTBuilder {
  def createCompilationUnit(
    location: Location,
    sourceFile: String,
    module: AST.ModuleDeclaration,
    imports: AST.ImportClause,
    toplevels: List[AST.Toplevel]
  ): AST.CompilationUnit = {
    AST.CompilationUnit(location, sourceFile, module, imports, toplevels)
  }

  def createModuleDeclaration(location: Location, name: String): AST.ModuleDeclaration = {
    AST.ModuleDeclaration(location, name)
  }

  def createImportClause(location: Location, mapping: List[(String, String)]): AST.ImportClause = {
    AST.ImportClause(location, mapping)
  }

  def createClassDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    superClass: AST.TypeNode,
    interfaces: List[AST.TypeNode],
    defaultSection: Option[AST.AccessSection],
    sections: List[AST.AccessSection],
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.ClassDeclaration = {
    AST.ClassDeclaration(location, modifiers, name, superClass, interfaces, defaultSection, sections, typeParameters)
  }

  def createInterfaceDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    superTypes: List[AST.TypeNode],
    methods: List[AST.MethodDeclaration],
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.InterfaceDeclaration = {
    AST.InterfaceDeclaration(location, modifiers, name, superTypes, methods, typeParameters)
  }

  def createRecordDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument]
  ): AST.RecordDeclaration = {
    AST.RecordDeclaration(location, modifiers, name, args)
  }

  def createMethodDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    args: List[AST.Argument],
    returnType: AST.TypeNode,
    body: AST.BlockExpression,
    typeParameters: List[AST.TypeParameter] = Nil
  ): AST.MethodDeclaration = {
    AST.MethodDeclaration(location, modifiers, name, args, returnType, body, typeParameters)
  }

  def createTypeParameter(
    location: Location,
    name: String,
    upperBound: Option[AST.TypeNode]
  ): AST.TypeParameter = AST.TypeParameter(location, name, upperBound)

  def createConstructorDeclaration(
    location: Location,
    modifiers: Int,
    args: List[AST.Argument],
    params: List[AST.Expression],
    body: AST.BlockExpression
  ): AST.ConstructorDeclaration = {
    AST.ConstructorDeclaration(location, modifiers, args, params, body)
  }

  def createFieldDeclaration(
    location: Location,
    modifiers: Int,
    name: String,
    typeRef: AST.TypeNode,
    init: AST.Expression
  ): AST.FieldDeclaration = {
    AST.FieldDeclaration(location, modifiers, name, typeRef, init)
  }

  def createBlockExpression(
    location: Location,
    statements: List[AST.CompoundExpression]
  ): AST.BlockExpression = {
    AST.BlockExpression(location, statements)
  }

  def createBinaryExpression(
    location: Location,
    operator: String,
    lhs: AST.Expression,
    rhs: AST.Expression
  ): AST.Expression = {
    operator match {
      case "+" => AST.Addition(location, lhs, rhs)
      case "-" => AST.Subtraction(location, lhs, rhs)
      case "*" => AST.Multiplication(location, lhs, rhs)
      case "/" => AST.Division(location, lhs, rhs)
      case "%" => AST.Modulo(location, lhs, rhs)
      case "==" => AST.Equal(location, lhs, rhs)
      case "!=" => AST.NotEqual(location, lhs, rhs)
      case "<" => AST.LessThan(location, lhs, rhs)
      case "<=" => AST.LessOrEqual(location, lhs, rhs)
      case ">" => AST.GreaterThan(location, lhs, rhs)
      case ">=" => AST.GreaterOrEqual(location, lhs, rhs)
      case "&&" => AST.LogicalAnd(location, lhs, rhs)
      case "||" => AST.LogicalOr(location, lhs, rhs)
      case "=" => AST.Assignment(location, lhs, rhs)
      case "+=" => AST.AdditionAssignment(location, lhs, rhs)
      case "-=" => AST.SubtractionAssignment(location, lhs, rhs)
      case "*=" => AST.MultiplicationAssignment(location, lhs, rhs)
      case "/=" => AST.DivisionAssignment(location, lhs, rhs)
      case "%=" => AST.ModuloAssignment(location, lhs, rhs)
      case ":?" => AST.Elvis(location, lhs, rhs)
      case _ => throw new IllegalArgumentException(s"Unknown binary operator: $operator")
    }
  }

  def createUnaryExpression(
    location: Location,
    operator: String,
    operand: AST.Expression
  ): AST.Expression = {
    operator match {
      case "+" => AST.Posit(location, operand)
      case "-" => AST.Negate(location, operand)
      case "!" => AST.Not(location, operand)
      case "++" => AST.PostIncrement(location, operand)  // Note: Parser needs to handle pre/post
      case "--" => AST.PostDecrement(location, operand)  // Note: Parser needs to handle pre/post
      case _ => throw new IllegalArgumentException(s"Unknown unary operator: $operator")
    }
  }

  def createLiteral(location: Location, value: Any): AST.Expression = {
    value match {
      case b: Boolean => AST.BooleanLiteral(location, b)
      case c: Char => AST.CharacterLiteral(location, c)
      case i: Int => AST.IntegerLiteral(location, i)
      case l: Long => AST.LongLiteral(location, l)
      case f: Float => AST.FloatLiteral(location, f)
      case d: Double => AST.DoubleLiteral(location, d)
      case s: String => AST.StringLiteral(location, s)
      case null => AST.NullLiteral(location)
      case _ => throw new IllegalArgumentException(s"Unknown literal type: ${value.getClass}")
    }
  }

  def createIdentifier(location: Location, name: String): AST.Id = {
    AST.Id(location, name)
  }

  def createTypeNode(
    location: Location,
    desc: AST.TypeDescriptor,
    isRelaxed: Boolean
  ): AST.TypeNode = {
    AST.TypeNode(location, desc, isRelaxed)
  }

  def createArgument(
    location: Location,
    name: String,
    typeRef: AST.TypeNode
  ): AST.Argument = {
    AST.Argument(location, name, typeRef)
  }

  def createAccessSection(
    location: Location,
    modifiers: Int,
    members: List[AST.MemberDeclaration]
  ): AST.AccessSection = {
    AST.AccessSection(location, modifiers, members)
  }
}
