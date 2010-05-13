package org.onion_lang.onion.compiler.pass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import onion.compiler.CompilerConfig;
import onion.compiler.Option;
import onion.compiler.Pair;
import onion.compiler.env.ClassTable;
import onion.compiler.env.ClosureLocalBinding;
import onion.compiler.env.ImportItem;
import onion.compiler.env.ImportList;
import onion.compiler.env.LocalContext;
import onion.compiler.env.LocalFrame;
import onion.compiler.env.NameResolution;
import onion.compiler.env.StaticImportItem;
import onion.compiler.env.StaticImportList;

import org.onion_lang.onion.compiler.error.CompilationException;
import org.onion_lang.onion.compiler.error.CompileError;
import org.onion_lang.onion.compiler.error.SemanticErrorReporter;
import org.onion_lang.onion.compiler.util.Boxing;
import org.onion_lang.onion.compiler.util.Classes;
import org.onion_lang.onion.compiler.util.Paths;
import org.onion_lang.onion.compiler.util.Systems;
import org.onion_lang.onion.lang.core.IrArrayLength;
import org.onion_lang.onion.lang.core.IrArrayRef;
import org.onion_lang.onion.lang.core.IrArraySet;
import org.onion_lang.onion.lang.core.IrBegin;
import org.onion_lang.onion.lang.core.IrBinExp;
import org.onion_lang.onion.lang.core.IrBlock;
import org.onion_lang.onion.lang.core.IrBool;
import org.onion_lang.onion.lang.core.IrBreak;
import org.onion_lang.onion.lang.core.IrCall;
import org.onion_lang.onion.lang.core.IrCallStatic;
import org.onion_lang.onion.lang.core.IrCallSuper;
import org.onion_lang.onion.lang.core.IrCast;
import org.onion_lang.onion.lang.core.IrChar;
import org.onion_lang.onion.lang.core.IrClass;
import org.onion_lang.onion.lang.core.IrClosure;
import org.onion_lang.onion.lang.core.IrConstructor;
import org.onion_lang.onion.lang.core.IrContinue;
import org.onion_lang.onion.lang.core.IrDouble;
import org.onion_lang.onion.lang.core.IrExpStmt;
import org.onion_lang.onion.lang.core.IrExpression;
import org.onion_lang.onion.lang.core.IrField;
import org.onion_lang.onion.lang.core.IrFieldRef;
import org.onion_lang.onion.lang.core.IrFieldSet;
import org.onion_lang.onion.lang.core.IrFloat;
import org.onion_lang.onion.lang.core.IrIf;
import org.onion_lang.onion.lang.core.IrInstanceOf;
import org.onion_lang.onion.lang.core.IrInt;
import org.onion_lang.onion.lang.core.IrList;
import org.onion_lang.onion.lang.core.IrLocalRef;
import org.onion_lang.onion.lang.core.IrLocalSet;
import org.onion_lang.onion.lang.core.IrLong;
import org.onion_lang.onion.lang.core.IrLoop;
import org.onion_lang.onion.lang.core.IrMethod;
import org.onion_lang.onion.lang.core.IrNOP;
import org.onion_lang.onion.lang.core.IrNew;
import org.onion_lang.onion.lang.core.IrNewArray;
import org.onion_lang.onion.lang.core.IrNode;
import org.onion_lang.onion.lang.core.IrNull;
import org.onion_lang.onion.lang.core.IrReturn;
import org.onion_lang.onion.lang.core.IrStatement;
import org.onion_lang.onion.lang.core.IrStaticFieldRef;
import org.onion_lang.onion.lang.core.IrString;
import org.onion_lang.onion.lang.core.IrSuper;
import org.onion_lang.onion.lang.core.IrSynchronized;
import org.onion_lang.onion.lang.core.IrThis;
import org.onion_lang.onion.lang.core.IrThrow;
import org.onion_lang.onion.lang.core.IrTry;
import org.onion_lang.onion.lang.core.IrUnaryExp;
import org.onion_lang.onion.lang.core.type.ArraySymbol;
import org.onion_lang.onion.lang.core.type.BasicTypeRef;
import org.onion_lang.onion.lang.core.type.ClassSymbol;
import org.onion_lang.onion.lang.core.type.ConstructorSymbol;
import org.onion_lang.onion.lang.core.type.ConstructorSymbolComparator;
import org.onion_lang.onion.lang.core.type.FieldSymbol;
import org.onion_lang.onion.lang.core.type.FieldSymbolComparator;
import org.onion_lang.onion.lang.core.type.MemberSymbol;
import org.onion_lang.onion.lang.core.type.MethodSymbol;
import org.onion_lang.onion.lang.core.type.MethodSymbolComparator;
import org.onion_lang.onion.lang.core.type.ObjectTypeRef;
import org.onion_lang.onion.lang.core.type.TypeRules;
import org.onion_lang.onion.lang.core.type.TypeRef;
import org.onion_lang.onion.lang.syntax.AccessSection;
import org.onion_lang.onion.lang.syntax.Addition;
import org.onion_lang.onion.lang.syntax.AdditionAssignment;
import org.onion_lang.onion.lang.syntax.Argument;
import org.onion_lang.onion.lang.syntax.Assignment;
import org.onion_lang.onion.lang.syntax.AstNode;
import org.onion_lang.onion.lang.syntax.BinaryExpression;
import org.onion_lang.onion.lang.syntax.BitAnd;
import org.onion_lang.onion.lang.syntax.BitOr;
import org.onion_lang.onion.lang.syntax.BlockStatement;
import org.onion_lang.onion.lang.syntax.BooleanLiteral;
import org.onion_lang.onion.lang.syntax.BreakStatement;
import org.onion_lang.onion.lang.syntax.CaseBranch;
import org.onion_lang.onion.lang.syntax.Cast;
import org.onion_lang.onion.lang.syntax.CharacterLiteral;
import org.onion_lang.onion.lang.syntax.ClassDeclaration;
import org.onion_lang.onion.lang.syntax.ClosureExpression;
import org.onion_lang.onion.lang.syntax.CompilationUnit;
import org.onion_lang.onion.lang.syntax.CondStatement;
import org.onion_lang.onion.lang.syntax.ConstructorDeclaration;
import org.onion_lang.onion.lang.syntax.ContinueStatement;
import org.onion_lang.onion.lang.syntax.CurrentInstance;
import org.onion_lang.onion.lang.syntax.DelegationDeclaration;
import org.onion_lang.onion.lang.syntax.Division;
import org.onion_lang.onion.lang.syntax.DivisionAssignment;
import org.onion_lang.onion.lang.syntax.DoubleLiteral;
import org.onion_lang.onion.lang.syntax.Elvis;
import org.onion_lang.onion.lang.syntax.EmptyStatement;
import org.onion_lang.onion.lang.syntax.Equal;
import org.onion_lang.onion.lang.syntax.Expression;
import org.onion_lang.onion.lang.syntax.ExpressionStatement;
import org.onion_lang.onion.lang.syntax.FieldDeclaration;
import org.onion_lang.onion.lang.syntax.FieldOrMethodRef;
import org.onion_lang.onion.lang.syntax.FloatLiteral;
import org.onion_lang.onion.lang.syntax.ForStatement;
import org.onion_lang.onion.lang.syntax.ForeachStatement;
import org.onion_lang.onion.lang.syntax.FunctionDeclaration;
import org.onion_lang.onion.lang.syntax.GlobalVariableDeclaration;
import org.onion_lang.onion.lang.syntax.GreaterOrEqual;
import org.onion_lang.onion.lang.syntax.GreaterThan;
import org.onion_lang.onion.lang.syntax.Id;
import org.onion_lang.onion.lang.syntax.IfStatement;
import org.onion_lang.onion.lang.syntax.ImportListDeclaration;
import org.onion_lang.onion.lang.syntax.Indexing;
import org.onion_lang.onion.lang.syntax.IntegerLiteral;
import org.onion_lang.onion.lang.syntax.InterfaceDeclaration;
import org.onion_lang.onion.lang.syntax.InterfaceMethodDeclaration;
import org.onion_lang.onion.lang.syntax.IsInstance;
import org.onion_lang.onion.lang.syntax.LessOrEqual;
import org.onion_lang.onion.lang.syntax.LessThan;
import org.onion_lang.onion.lang.syntax.ListLiteral;
import org.onion_lang.onion.lang.syntax.LocalVariableDeclaration;
import org.onion_lang.onion.lang.syntax.LogicalAnd;
import org.onion_lang.onion.lang.syntax.LogicalOr;
import org.onion_lang.onion.lang.syntax.LogicalRightShift;
import org.onion_lang.onion.lang.syntax.LongLiteral;
import org.onion_lang.onion.lang.syntax.MathLeftShift;
import org.onion_lang.onion.lang.syntax.MathRightShift;
import org.onion_lang.onion.lang.syntax.MemberDeclaration;
import org.onion_lang.onion.lang.syntax.MethodCall;
import org.onion_lang.onion.lang.syntax.MethodDeclaration;
import org.onion_lang.onion.lang.syntax.Modifier;
import org.onion_lang.onion.lang.syntax.ModuleDeclaration;
import org.onion_lang.onion.lang.syntax.Modulo;
import org.onion_lang.onion.lang.syntax.ModuloAssignment;
import org.onion_lang.onion.lang.syntax.Multiplication;
import org.onion_lang.onion.lang.syntax.MultiplicationAssignment;
import org.onion_lang.onion.lang.syntax.Negate;
import org.onion_lang.onion.lang.syntax.NewArray;
import org.onion_lang.onion.lang.syntax.NewObject;
import org.onion_lang.onion.lang.syntax.Not;
import org.onion_lang.onion.lang.syntax.NotEqual;
import org.onion_lang.onion.lang.syntax.NullLiteral;
import org.onion_lang.onion.lang.syntax.Posit;
import org.onion_lang.onion.lang.syntax.PostDecrement;
import org.onion_lang.onion.lang.syntax.PostIncrement;
import org.onion_lang.onion.lang.syntax.ReferenceEqual;
import org.onion_lang.onion.lang.syntax.ReferenceNotEqual;
import org.onion_lang.onion.lang.syntax.ReturnStatement;
import org.onion_lang.onion.lang.syntax.SelectStatement;
import org.onion_lang.onion.lang.syntax.SelfFieldReference;
import org.onion_lang.onion.lang.syntax.SelfMethodCall;
import org.onion_lang.onion.lang.syntax.Statement;
import org.onion_lang.onion.lang.syntax.StaticIDExpression;
import org.onion_lang.onion.lang.syntax.StaticMethodCall;
import org.onion_lang.onion.lang.syntax.StringLiteral;
import org.onion_lang.onion.lang.syntax.Subtraction;
import org.onion_lang.onion.lang.syntax.SubtractionAssignment;
import org.onion_lang.onion.lang.syntax.SuperMethodCall;
import org.onion_lang.onion.lang.syntax.SynchronizedStatement;
import org.onion_lang.onion.lang.syntax.ThrowStatement;
import org.onion_lang.onion.lang.syntax.TopLevelElement;
import org.onion_lang.onion.lang.syntax.TryStatement;
import org.onion_lang.onion.lang.syntax.TypeDeclaration;
import org.onion_lang.onion.lang.syntax.TypeSpec;
import org.onion_lang.onion.lang.syntax.WhileStatement;
import org.onion_lang.onion.lang.syntax.XOR;
import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;

public class CodeAnalysis implements SemanticErrorReporter.Constants {
  private SemanticErrorReporter reporter;
  private CompilerConfig conf;
  private ClassTable table;
  private Map irt2ast;
  private Map ast2irt;
  private Map solvers;
  
  private CompilationUnit unit;
  private StaticImportList staticImport;
  private ImportList currentImport;
  private IrClass contextClass;
  private NameResolution solver;
  private int access;
  
  public String topClass(){
    ModuleDeclaration module = unit.getModuleDeclaration();
    String moduleName = module != null ? module.getName() : null;
    return createName(moduleName, Paths.cutExtension(unit.getSourceFileName()) + "Main");
  }
  
//---------------------------------------------------------------------------//
  public ClassTable getTable(){
    return table;
  }
  
  public void setSolver(NameResolution resolver){
    this.solver = resolver;
  }
  
  public NameResolution getSolver(){
    return solver;
  }
  
  public void setUnit(CompilationUnit unit){
    this.unit = unit;
  }
  
  public CompilationUnit getUnit(){
    return unit;
  }
  
  public void setImport(ImportList imports){
    currentImport = imports;
  }
  
  public ImportList getImport(){
    return currentImport;
  }
  
  public void setStaticImport(StaticImportList imports){
    staticImport = imports;
  }
  
  public StaticImportList getStaticImport(){
    return staticImport;
  }

  public void setContextClass(IrClass contextClass){
    this.contextClass = contextClass;
  }
  
  public IrClass getContextClass(){
    return contextClass;
  }
  
  public void setAccess(int access){
    this.access = access;
  }
  
  public int getAccess(){
    return access;
  }
//---------------------------------------------------------------------------//
  
  public void put(AstNode astNode, IrNode kernelNode){
    ast2irt.put(astNode, kernelNode);
    irt2ast.put(kernelNode, astNode);
  }
  
  public AstNode lookupAST(IrNode kernelNode){
    return (AstNode) irt2ast.get(kernelNode);
  }
  
  public IrNode lookupKernelNode(AstNode astNode){
    return (IrNode) ast2irt.get(astNode);
  }
  
  public void addSolver(String className, NameResolution solver) {
    solvers.put(className, solver);
  }
  
  public NameResolution findSolver(String className){
    return (NameResolution) solvers.get(className);
  }
  
  private String createName(String moduleName, String simpleName){
    return (moduleName != null ? moduleName + "." : "") + simpleName;
  }
    
  private static String classpath(String[] classPaths){
    StringBuffer path = new StringBuffer();
    if(classPaths.length > 0){
      path.append(classPaths[0]);
      for(int i = 1; i < classPaths.length; i++){
        path.append(Systems.getPathSeparator());
        path.append(classPaths[i]);
      }
    }
    return new String(path);
  }
//----------------------------------------------------------------------------// 
//----------------------------------------------------------------------------//

  public TypeRef resolve(TypeSpec type, NameResolution resolver) {
    TypeRef resolvedType = (TypeRef) resolver.resolve(type);
    if(resolvedType == null){
      report(CLASS_NOT_FOUND, type, new Object[]{type.getComponentName()});      
    }
    return resolvedType;
  }
  
  public TypeRef resolve(TypeSpec type) {
    return resolve(type, getSolver());
  }

  public void report(int error, AstNode node, Object[] items) {
    reporter.setSourceFile(unit.getSourceFileName());
    reporter.report(error, node.getLocation(), items);
  }

  public static TypeRef promoteNumericTypes(TypeRef left,  TypeRef right) {
    if(!numeric(left) || !numeric(right)) return null;
    if(left == BasicTypeRef.DOUBLE || right == BasicTypeRef.DOUBLE){
      return BasicTypeRef.DOUBLE;
    }
    if(left == BasicTypeRef.FLOAT || right == BasicTypeRef.FLOAT){
      return BasicTypeRef.FLOAT;
    }
    if(left == BasicTypeRef.LONG || right == BasicTypeRef.LONG){
      return BasicTypeRef.LONG;
    }
    return BasicTypeRef.INT;
  }
  
  public static boolean hasNumericType(IrExpression expression) {
    return numeric(expression.type());
  }
  
  private static boolean numeric(TypeRef symbol) {
    return 
    (symbol.isBasicType()) &&
    ( symbol == BasicTypeRef.BYTE || symbol == BasicTypeRef.SHORT ||
      symbol == BasicTypeRef.INT || symbol == BasicTypeRef.LONG ||
      symbol == BasicTypeRef.FLOAT || symbol == BasicTypeRef.DOUBLE);
  }
  
  public ClassSymbol load(String name) {
    return table.load(name);
  }
  
  public ClassSymbol loadTopClass() {
    return table.load(topClass());
  }
  
  public ArraySymbol loadArray(TypeRef type, int dimension) {
    return table.loadArray(type, dimension);
  }
  
  public ClassSymbol rootClass() {
    return table.rootClass();
  }
  
  public CompileError[] getProblems() {
    return reporter.getProblems();
  }
  
  public IrClass[] getSourceClasses() {
    return table.getSourceClasses();
  }
  
  private class ClassTableBuilder extends ASTVisitor<String> {
    public ClassTableBuilder() {
    }
    
    public void process(CompilationUnit unit){
      setUnit(unit);
      ModuleDeclaration module = unit.getModuleDeclaration();
      ImportListDeclaration imports = unit.getImportListDeclaration();
      String moduleName = module != null ? module.getName() : null;
      ImportList list = new ImportList();
      list.add(new ImportItem("*", "java.lang.*"));
      list.add(new ImportItem("*", "java.io.*"));
      list.add(new ImportItem("*", "java.util.*"));
      list.add(new ImportItem("*", "javax.swing.*"));
      list.add(new ImportItem("*", "java.awt.event.*"));
      list.add(new ImportItem("*", moduleName != null ? moduleName + ".*" : "*"));
      if(imports != null){
        for(int i = 0; i < imports.size(); i++){
          list.add(new ImportItem(imports.getName(i), imports.getFQCN(i)));
        }
      }
      StaticImportList staticList = new StaticImportList();
      staticList.add(new StaticImportItem("java.lang.System", true));
      staticList.add(new StaticImportItem("java.lang.Runtime", true));
      staticList.add(new StaticImportItem("java.lang.Math", true));
      
      setImport(list);
      setStaticImport(staticList);
      TopLevelElement[] tops = unit.getTopLevels();
      int count = 0;
      for(int i = 0; i < tops.length; i++){
        if(tops[i] instanceof TypeDeclaration){
          accept(tops[i], moduleName);
          continue;
        }
        count++;
      }
      ClassTable table = getTable();
      if(count > 0){
        IrClass node = IrClass.newClass(0, topClass(), table.rootClass(), new ClassSymbol[0]);
        node.setSourceFile(Paths.getName(unit.getSourceFileName()));
        node.setResolutionComplete(true);
        table.addSourceClass(node);
        node.addDefaultConstructor();
        put(unit, node);
        addSolver(node.getName(), new NameResolution(list, table));
      }
    }
    
    public Object visit(ClassDeclaration ast, String context) {
      String module = context;
      IrClass node = IrClass.newClass(ast.getModifier(), createFQCN(module, ast.getName()));
      node.setSourceFile(Paths.getName(getUnit().getSourceFileName()));
      if(getTable().lookup(node.getName()) != null){
        report(DUPLICATE_CLASS,  ast, new Object[]{node.getName()});
        return null;
      }
      ClassTable table = getTable();
      getTable().addSourceClass(node);
      put(ast, node);
      addSolver(node.getName(), new NameResolution(getImport(), table));
      return null;    
    }
    
    public Object visit(InterfaceDeclaration ast, String context) {
      String module = context;
      IrClass node = IrClass.newInterface(ast.getModifier(), createFQCN(module, ast.getName()), null);
      node.setSourceFile(Paths.getName(getUnit().getSourceFileName()));
      ClassTable table = getTable();
      if(table.lookup(node.getName()) != null){
        report(DUPLICATE_CLASS,  ast, new Object[]{node.getName()});
        return null;
      }
      table.addSourceClass(node);
      put(ast, node);
      addSolver(
        node.getName(), new NameResolution(getImport(), getTable())
      );
      return null;
    }
    
    private String createFQCN(String moduleName, String simpleName) {
      return (moduleName != null ? moduleName + "." : "") + simpleName;
    }
  }
  
  private class TypeHeaderAnalysis extends ASTVisitor<Void> {
    private int countConstructor;
  
    public TypeHeaderAnalysis() {
    }
    
    public void process(CompilationUnit unit){
      setUnit(unit);
      TopLevelElement[] toplevels = unit.getTopLevels();    
      for(int i = 0; i < toplevels.length; i++){      
        setSolver(findSolver(topClass()));
        accept(toplevels[i]);
      }
    }
    
    public Object visit(ClassDeclaration ast, Void context) {
      countConstructor = 0;
      IrClass node = (IrClass) lookupKernelNode(ast);
      setContextClass(node);
      setSolver(findSolver(node.getName()));
      constructTypeHierarchy(node, new ArrayList());
      if(hasCyclicity(node)){
        report(CYCLIC_INHERITANCE, ast, new Object[]{node.getName()});
      }
      if(ast.getDefaultSection() != null){
        accept(ast.getDefaultSection());
      }
      AccessSection[] sections = ast.getSections();
      for(int i = 0; i < sections.length; i++){
        accept(sections[i]);
      }
      if(countConstructor == 0){
        node.addDefaultConstructor();
      }
      return null;
    }
      
    public Object visit(InterfaceDeclaration ast, Void context) {
      IrClass node = (IrClass) lookupKernelNode(ast);
      setContextClass(node);
      setSolver(findSolver(node.getName()));
      constructTypeHierarchy(node, new ArrayList());
      if(hasCyclicity(node)){
        report(CYCLIC_INHERITANCE, ast, new Object[]{node.getName()});
      }
      InterfaceMethodDeclaration[] members = ast.getDeclarations();
      for(int i = 0; i < members.length; i++){
        accept(members[i], context);
      }
      return null;
    }
    
    public Object visit(DelegationDeclaration ast, Void context) {
      TypeRef type = resolve(ast.getType());
      if(type == null) return null;  
      if(!(type.isObjectType() && ((ObjectTypeRef)type).isInterface())){
        report(INTERFACE_REQUIRED, ast.getType(), new Object[]{type});
        return null;
      }
      IrClass contextClass = getContextClass();
      int modifier = ast.getModifier() | getAccess() | Modifier.FORWARDED;
      String name = ast.getName();
      IrField node = new IrField(modifier, contextClass, name, type);
      put(ast, node);
      contextClass.addField(node);
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, Void context) {
      countConstructor++;
      TypeRef[] args = acceptTypes(ast.getArguments());
      IrClass contextClass = getContextClass();
      if(args == null) return null;
      int modifier = ast.getModifier() | getAccess();
      IrConstructor node = new IrConstructor(modifier, contextClass, args, null, null);
      put(ast, node);
      contextClass.addConstructor(node);
      return null;
    }

    public Object visit(MethodDeclaration ast, Void context) {
      TypeRef[] args = acceptTypes(ast.getArguments());
      TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      IrClass contextClass = getContextClass();
      int modifier = ast.getModifier() | getAccess();
      if(ast.getBlock() == null) modifier |= Modifier.ABSTRACT;
      String name = ast.getName();    
      IrMethod node = new IrMethod(modifier, contextClass, name, args, returnType, null);     
      put(ast, node);
      contextClass.addMethod(node);
      return null;
    }
    
    public Object visit(FieldDeclaration ast, Void context) {
      TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      IrClass contextClass = getContextClass();
      int modifier = ast.getModifier() | getAccess();
      String name = ast.getName();    
      IrField node = new IrField(modifier, contextClass, name, type);
      put(ast, node);
      contextClass.addField(node);
      return node;
    }
    
    private TypeRef[] acceptTypes(Argument[] ast){
      TypeRef[] types = new TypeRef[ast.length];
      boolean success = true;
      for (int i = 0; i < ast.length; i++) {
        types[i] = (TypeRef) accept(ast[i]);
        if(types[i] == null) success = false;
      }
      if(success){
        return types;
      }else{
        return null;
      }
    }
    
    public Object visit(Argument ast, Void context){
      TypeRef type = resolve(ast.getType());
      return type;
    }
    
    private IrField createFieldNode(FieldDeclaration ast){
      TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      IrField node = new IrField(
        ast.getModifier() | getAccess(), getContextClass(), 
        ast.getName(), type);
      return node;
    }
      
    public Object visit(InterfaceMethodDeclaration ast, Void context) {
      TypeRef[] args = acceptTypes(ast.getArguments());
      TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      int modifier = Modifier.PUBLIC | Modifier.ABSTRACT;
      IrClass classType = getContextClass();
      String name = ast.getName();    
      IrMethod node = 
        new IrMethod(modifier, classType, name, args, returnType, null);
      put(ast, node);
      classType.addMethod(node);
      return null;
    }
    
    public Object visit(FunctionDeclaration ast, Void context) {
      TypeRef[] args = acceptTypes(ast.getArguments());
      TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      IrClass classType = (IrClass) loadTopClass();
      int modifier = ast.getModifier() | Modifier.PUBLIC;
      String name = ast.getName();
      
      IrMethod node = 
        new IrMethod(modifier, classType, name, args, returnType, null);
      put(ast, node);
      classType.addMethod(node);
      return null;
    }
    
    public Object visit(GlobalVariableDeclaration ast, Void context) {
      TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      
      int modifier = ast.getModifier() | Modifier.PUBLIC;
      IrClass classType = (IrClass)loadTopClass();
      String name = ast.getName();
      
      IrField node = new IrField(modifier, classType, name, type);
      put(ast, node);
      classType.addField(node);
      return null;
    }
      
    public Object visit(AccessSection section, Void context){
      if(section == null) return null;
      
      setAccess(section.getID());
      MemberDeclaration[] members = section.getMembers();
      for(int i = 0; i < members.length; i++){
        accept(members[i], context);
      }
      return null;
    }
    
    public boolean hasCyclicity(IrClass start){
      return hasCylicitySub(start, new HashSet());
    }
    
    private boolean hasCylicitySub(ClassSymbol symbol, HashSet visit){
      if(symbol == null) return false;
      if(visit.contains(symbol)){
        return true;      
      }
      visit.add(symbol);
      if(hasCylicitySub(symbol.getSuperClass(), (HashSet)visit.clone())){
        return true;      
      }
      ClassSymbol[] interfaces = symbol.getInterfaces();
      for (int i = 0; i < interfaces.length; i++) {
        if(hasCylicitySub(interfaces[i], (HashSet)visit.clone())){
          return true;        
        }
      }
      return false;
    }
  
    private void constructTypeHierarchy(ClassSymbol symbol, List visit) {
      if(symbol == null || visit.indexOf(symbol) >= 0) return;
      visit.add(symbol);
      if(symbol instanceof IrClass){
        IrClass node = (IrClass) symbol;
        if(node.isResolutionComplete()) return;
        ClassSymbol superClass = null;
        List interfaces = new ArrayList();
        NameResolution resolver = findSolver(node.getName());
        if(node.isInterface()){
          InterfaceDeclaration ast = (InterfaceDeclaration) lookupAST(node);
          superClass = rootClass();
          TypeSpec[] typeSpecifiers = ast.getInterfaces();
          for(int i = 0; i < typeSpecifiers.length; i++){
            ClassSymbol superType = validateSuperType(typeSpecifiers[i], true, resolver);
            if(superType != null){
              interfaces.add(superType);
            }
          }
        }else{
          ClassDeclaration ast = (ClassDeclaration) lookupAST(node);
          superClass = 
            validateSuperType(ast.getSuperClass(), false, resolver);
          TypeSpec[] typeSpecifiers = ast.getInterfaces();
          for(int i = 0; i < typeSpecifiers.length; i++){
            ClassSymbol superType = validateSuperType(typeSpecifiers[i], true, resolver);
            if(superType != null){
              interfaces.add(superType);
            }
          }
        }
        constructTypeHierarchy(superClass, visit);
        for(Iterator i = interfaces.iterator(); i.hasNext();){
          ClassSymbol superType = (ClassSymbol) i.next();
          constructTypeHierarchy(superType, visit);
        }
        node.setSuperClass(superClass);
        node.setInterfaces((ClassSymbol[]) interfaces.toArray(new ClassSymbol[0]));
        node.setResolutionComplete(true);
      }else{
        constructTypeHierarchy(symbol.getSuperClass(), visit);
        ClassSymbol[] interfaces = symbol.getInterfaces();
        for(int i = 0; i < interfaces.length; i++){
          constructTypeHierarchy(interfaces[i], visit);
        }
      }
    }
    
    private ClassSymbol validateSuperType(
      TypeSpec ast, boolean shouldInterface, NameResolution resolver){
      
      ClassSymbol symbol = null;
      if(ast == null){
        symbol = getTable().rootClass();
      }else{
        symbol = (ClassSymbol) resolve(ast, resolver);
      }
      if(symbol == null) return null;
      boolean isInterface = symbol.isInterface();
      if(((!isInterface) && shouldInterface) || (isInterface && (!shouldInterface))){
        AstNode astNode = null;
        if(symbol instanceof IrClass){
          astNode = lookupAST((IrClass)symbol);
        }
        report(ILLEGAL_INHERITANCE, astNode, new Object[]{symbol.getName()});
      }
      return symbol;
    }
    
    private void report(int error, AstNode ast, Object[] items){
      report(error, ast, items);
    }
  }
  
  private class DuplicationChecker extends ASTVisitor<Void> {
    private Set methods;
    private Set constructors;
    private Set fields;
    private Set variables;
    private Set functions;
    
    public DuplicationChecker() {
      this.methods      = new TreeSet(new MethodSymbolComparator());
      this.fields       = new TreeSet(new FieldSymbolComparator());
      this.constructors = new TreeSet(new ConstructorSymbolComparator());
      this.variables    = new TreeSet(new FieldSymbolComparator());
      this.functions    = new TreeSet(new MethodSymbolComparator());
    }
    
    public void process(CompilationUnit unit){
      setUnit(unit);
      variables.clear();
      functions.clear();
      TopLevelElement[] toplevels = unit.getTopLevels();    
      for(int i = 0; i < toplevels.length; i++){      
        setSolver(findSolver(topClass()));
        accept(toplevels[i]);
      }
    }
    
    public Object visit(ClassDeclaration ast, Void context) {
      IrClass node = (IrClass) lookupKernelNode(ast);
      if(node == null) return null;
      methods.clear();
      fields.clear();
      constructors.clear();
      setContextClass(node);
      setSolver(findSolver(node.getName()));
      if(ast.getDefaultSection() != null){
        accept(ast.getDefaultSection());
      }
      AccessSection[] sections = ast.getSections();
      for(int i = 0; i < sections.length; i++){
        accept(sections[i]);
      }
      generateMethods();
      return null;
    }
    
    private void generateMethods(){
      Set generated = new TreeSet(new MethodSymbolComparator());
      Set methodSet = new TreeSet(new MethodSymbolComparator());
      for(Iterator i = fields.iterator(); i.hasNext();){
        IrField node = (IrField)i.next();
        if(Modifier.isForwarded(node.getModifier())){
          generateDelegationMethods(node ,generated, methodSet);
        }
      }
    }
    
    private void generateDelegationMethods(IrField node, Set generated, Set methodSet){    
      ClassSymbol type = (ClassSymbol) node.getType();
      Set src = Classes.getInterfaceMethods(type);
      for (Iterator i = src.iterator(); i.hasNext();) {
        MethodSymbol method = (MethodSymbol) i.next();
        if(methodSet.contains(method)) continue;
        if(generated.contains(method)){
          report(
            DUPLICATE_GENERATED_METHOD, lookupAST(node),
            new Object[]{
              method.getClassType(), method.getName(), method.getArguments()
            });
          continue;
        }
        IrMethod generatedMethod = createEmptyMethod(node, method);
        generated.add(generatedMethod);
        getContextClass().addMethod(generatedMethod);
      }
    }
    
    private IrMethod createEmptyMethod(FieldSymbol field, MethodSymbol method){
      IrExpression target;
      target = new IrFieldRef(new IrThis(getContextClass()), field);
      TypeRef[] args = method.getArguments();
      IrExpression[] params = new IrExpression[args.length];
      LocalFrame frame = new LocalFrame(null);
      for(int i = 0; i < params.length; i++){
        int index = frame.addEntry("arg" + i, args[i]);
        params[i] = new IrLocalRef(new ClosureLocalBinding(0, index, args[i]));
      }
      target = new IrCall(target, method, params);
      IrBlock statement;
      if(method.getReturnType() != BasicTypeRef.VOID){
        statement = new IrBlock(new IrReturn(target));
      }else{
        statement = new IrBlock(new IrExpStmt(target), new IrReturn(null));
      }
      IrMethod node = new IrMethod(
        Modifier.PUBLIC, getContextClass(), method.getName(),
        method.getArguments(), method.getReturnType(), statement
      );
      node.setFrame(frame);
      return node;
    }
      
    public Object visit(InterfaceDeclaration ast, Void context) {
      IrClass node = (IrClass) lookupKernelNode(ast);
      if(node == null) return null;
      methods.clear();
      fields.clear();
      constructors.clear();
      setContextClass(node);
      setSolver(findSolver(node.getName()));
      InterfaceMethodDeclaration[] members = ast.getDeclarations();
      for(int i = 0; i < members.length; i++){
        accept(members[i], context);
      }
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, Void context) {
      IrConstructor node = (IrConstructor) lookupKernelNode(ast);
      if(node == null) return null;
      if(constructors.contains(node)){
        ClassSymbol classType = node.getClassType();
        TypeRef[] args = node.getArgs();
        report(DUPLICATE_CONSTRUCTOR, ast, new Object[]{classType, args});
      }else{
        constructors.add(node);
      }
      return null;
    }
    
    public Object visit(DelegationDeclaration ast, Void context) {
      IrField node = (IrField) lookupKernelNode(ast);    
      if(node == null) return null;
      if(fields.contains(node)){
        ClassSymbol classType = node.getClassType();
        String name = node.getName();
        report(DUPLICATE_FIELD, ast, new Object[]{classType, name});
      }else{
        fields.add(node);
      }
      return null;
    }
    
    public Object visit(MethodDeclaration ast, Void context) {
      IrMethod node = (IrMethod) lookupKernelNode(ast);
      if(node == null) return null;
      if(methods.contains(node)){
        ClassSymbol classType = node.getClassType();
        String name = node.getName();
        TypeRef[] args = node.getArguments();
        report(DUPLICATE_METHOD, ast, new Object[]{classType, name, args});
      }else{
        methods.add(node);
      }
      return null;
    }
    
    public Object visit(FieldDeclaration ast, Void context) {
      IrField node = (IrField) lookupKernelNode(ast);
      if(node == null) return null;
      if(fields.contains(node)){
        ClassSymbol classType = node.getClassType();
        String name = node.getName();
        report(DUPLICATE_FIELD, ast, new Object[]{classType, name});
      }else{
        fields.add(node);
      }
      return null;
    }
    
    public Object visit(InterfaceMethodDeclaration ast, Void context) {
      IrMethod node = (IrMethod) lookupKernelNode(ast);
      if(node == null) return null;
      if(methods.contains(node)){
        ClassSymbol classType = node.getClassType();
        String name = node.getName();
        TypeRef[] args = node.getArguments();
        report(DUPLICATE_METHOD, ast, new Object[]{classType, name, args});
      }else{
        methods.add(node);
      }
      return null;
    }
    
    public Object visit(FunctionDeclaration ast, Void context) {
      IrMethod node = (IrMethod) lookupKernelNode(ast);
      if(node == null) return null;
      if(functions.contains(node)){
        String name = node.getName();
        TypeRef[] args = node.getArguments();
        report(DUPLICATE_FUNCTION, ast, new Object[]{name, args});
      }else{
        functions.add(node);
      }
      return null;
    }
    
    public Object visit(GlobalVariableDeclaration ast, Void context) {
      IrField node = (IrField) lookupKernelNode(ast);
      if(node == null) return null;
      if(variables.contains(node)){
        String name = node.getName();
        report(DUPLICATE_GLOBAL_VARIABLE, ast, new Object[]{name});      
      }else{
        variables.add(node);
      }
      return null;
    }
      
    public Object visit(AccessSection section, Void context){
      if(section == null) return null;    
      MemberDeclaration[] members = section.getMembers();
      for(int i = 0; i < members.length; i++){
        accept(members[i], context);
      }
      return null;
    }
    
    private void report(int error, AstNode ast, Object[] items){
      report(error, ast, items);
    }
  }
  
  private class TypeChecker extends ASTVisitor<LocalContext> 
  implements IrBinExp.Constants, IrUnaryExp.Constants {
    public TypeChecker(){
    }
    
    public void process(CompilationUnit unit){
      accept(unit);
    }
    
  //------------------------------ top level --------------------------------------//
    public Object visit(CompilationUnit unit, LocalContext object) {
      setUnit(unit);
      TopLevelElement[] toplevels = unit.getTopLevels();
      LocalContext context = new LocalContext();
      List statements = new ArrayList();
      String className = topClass();
      setSolver(findSolver(className));
      IrClass klass = (IrClass) loadTopClass();
      ArraySymbol argsType = loadArray(load("java.lang.String"), 1);
      
      IrMethod method = new IrMethod(
        Modifier.PUBLIC, klass, 
        "start", new TypeRef[]{argsType}, BasicTypeRef.VOID, null
      );
      context.addEntry("args", argsType);
      for(int i = 0; i < toplevels.length; i++){
        TopLevelElement element = toplevels[i];
        if(!(element instanceof TypeDeclaration)){
          setContextClass(klass);
        }
        if(element instanceof Statement){
          context.setMethod(method);
          IrStatement statement = (IrStatement) accept(toplevels[i], context);
          statements.add(statement);
        }else{
          accept(toplevels[i], null);
        }
      }    
      
      if(klass != null){
        statements.add(new IrReturn(null));
        method.setBlock(new IrBlock(statements));
        method.setFrame(context.getContextFrame());
        klass.addMethod(method);      
        klass.addMethod(mainMethod(klass, method, "main", new TypeRef[]{argsType}, BasicTypeRef.VOID));
      }
      return null;
    }
    
    private IrMethod mainMethod(ClassSymbol top, MethodSymbol ref, String name, TypeRef[] args, TypeRef ret) {
      IrMethod method = new IrMethod(
        Modifier.STATIC | Modifier.PUBLIC, top, name, args, ret, null);
      LocalFrame frame = new LocalFrame(null);
      IrExpression[] params = new IrExpression[args.length];
      for(int i = 0; i < args.length; i++){
        int index = frame.addEntry("args" + i, args[i]);
        params[i] = new IrLocalRef(0, index, args[i]);
      }
      method.setFrame(frame);
      ConstructorSymbol c = top.findConstructor(new IrExpression[0])[0];
      IrExpression exp = new IrNew(c, new IrExpression[0]);
      exp = new IrCall(exp, ref, params);
      IrBlock block = new IrBlock(new IrExpStmt(exp));
      block = addReturnNode(block, BasicTypeRef.VOID);
      method.setBlock(block);
      return method;
    }
    
    public Object visit(InterfaceDeclaration ast, LocalContext context) {
      setContextClass((IrClass) lookupKernelNode(ast));
      return null;
    }
    
    public Object visit(ClassDeclaration ast, LocalContext context) {
      setContextClass((IrClass) lookupKernelNode(ast));
      setSolver(findSolver(getContextClass().getName()));
      if(ast.getDefaultSection() != null){
        accept(ast.getDefaultSection(), context);
      }
      acceptEach(ast.getSections(), context);
      return null;
    }
    
    public Object visit(AccessSection ast, LocalContext context) {
      acceptEach(ast.getMembers(), context);
      return null;
    }
  //-------------------------------------------------------------------------------//
  
    
  //------------------------- binary expressions ----------------------------------//
    public Object visit(Addition ast, LocalContext context) {
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if(left.isBasicType() && right.isBasicType()){
        return checkNumExp(ADD, ast, left, right, context);
      }
      if(left.isBasicType()){
        if(left.type() == BasicTypeRef.VOID){
          report(IS_NOT_BOXABLE_TYPE, ast.getLeft(), new Object[]{left.type()});
          return null;
        }else{
          left = Boxing.boxing(getTable(), left);
        }
      }
      if(right.isBasicType()){
        if(right.type() == BasicTypeRef.VOID){
          report(IS_NOT_BOXABLE_TYPE, ast.getRight(), new Object[]{right.type()});
          return null;
        }else{
          right = Boxing.boxing(getTable(), right);
        }
      }
      MethodSymbol toString;
      toString = findMethod(ast.getLeft(), (ObjectTypeRef)left.type(), "toString");
      left = new IrCall(left, toString, new IrExpression[0]);
      toString = findMethod(ast.getRight(), (ObjectTypeRef)right.type(), "toString");
      right = new IrCall(right, toString, new IrExpression[0]);
      MethodSymbol concat =
        findMethod(ast, (ObjectTypeRef)left.type(), "concat", new IrExpression[]{right});
      return new IrCall(left, concat, new IrExpression[]{right});
    }
    
    public Object visit(PostIncrement node, LocalContext context) {
      LocalContext local = (LocalContext)context;
      Expression operand = node.getTarget();
      IrExpression irOperand = typeCheck(operand, context);
      if(irOperand == null) return null;
      if((!irOperand.isBasicType()) || !hasNumericType(irOperand)){
        report(INCOMPATIBLE_OPERAND_TYPE, node, new Object[]{node.getSymbol(), new TypeRef[]{irOperand.type()}});
        return null;
      }
      int varIndex = local.addEntry(local.newName(), irOperand.type());
      IrExpression result = null;
      if(irOperand instanceof IrLocalRef){
        IrLocalRef ref = (IrLocalRef)irOperand;
        result = new IrBegin(
          new IrLocalSet(0, varIndex, irOperand.type(), irOperand),
          new IrLocalSet(
            ref.frame(), ref.index(), ref.type(),
            new IrBinExp(
              ADD, irOperand.type(), 
              new IrLocalRef(0, varIndex, irOperand.type()), 
              new IrInt(1)
            )
          ),
          new IrLocalRef(0, varIndex, irOperand.type())
        );
      }else{
        report(UNIMPLEMENTED_FEATURE, node, new Object[0]);
      }
      return result;
    }
    
    public Object visit(PostDecrement node, LocalContext context) {
      LocalContext local = (LocalContext)context;
      Expression operand = node.getTarget();
      IrExpression irOperand = typeCheck(operand, context);
      if(irOperand == null) return null;
      if((!irOperand.isBasicType()) || !hasNumericType(irOperand)){
        report(INCOMPATIBLE_OPERAND_TYPE, node, new Object[]{node.getSymbol(), new TypeRef[]{irOperand.type()}});
        return null;
      }
      int varIndex = local.addEntry(local.newName(), irOperand.type());
      IrExpression result = null;
      if(irOperand instanceof IrLocalRef){
        IrLocalRef ref = (IrLocalRef)irOperand;
        result = new IrBegin(
          new IrLocalSet(0, varIndex, irOperand.type(), irOperand),
          new IrLocalSet(
            ref.frame(), ref.index(), ref.type(),
            new IrBinExp(
              SUBTRACT, irOperand.type(), 
              new IrLocalRef(0, varIndex, irOperand.type()), 
              new IrInt(1)
            )
          ),
          new IrLocalRef(0, varIndex, irOperand.type())
        );
      }else{
        report(UNIMPLEMENTED_FEATURE, node, new Object[0]);
      }
      return result;
    }
    
    @Override
    public Object visit(Elvis ast, LocalContext context) {
      IrExpression l = typeCheck(ast.getLeft(), context);
      IrExpression r = typeCheck(ast.getRight(), context);
      if(l.isBasicType() || r.isBasicType() || !TypeRules.isAssignable(l.type(), r.type())) {
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new TypeRef[]{l.type(), r.type()}});       
        return null;
      }
      return new IrBinExp(ELVIS, l.type(), l, r);
    }
    
    public Object visit(Subtraction ast, LocalContext context) {
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(SUBTRACT, ast, left, right, context);
    }
    
    public Object visit(Multiplication ast, LocalContext context) {
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(MULTIPLY,  ast, left, right, context);
    }
    
    public Object visit(Division ast, LocalContext context) {
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(DIVIDE, ast, left, right, context);
    }
    
    public Object visit(Modulo ast, LocalContext context) {
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(MOD, ast, left, right, context);
    }
      
    public Object visit(XOR ast, LocalContext context) {
      return checkBitExp(XOR, ast, context);
    }
    
    public Object visit(Equal ast, LocalContext context) {
      return checkEqualExp(EQUAL, ast, context);
    }
    
    public Object visit(NotEqual ast, LocalContext context) {
      return checkEqualExp(NOT_EQUAL, ast, context);
    }
    
    public Object visit(ReferenceEqual ast, LocalContext context) {
      return checkRefEqualsExp(EQUAL, ast, context);
    }
    
    public Object visit(ReferenceNotEqual ast, LocalContext context) {
      return checkRefEqualsExp(NOT_EQUAL, ast, context);
    }
    
    public Object visit(LessOrEqual ast, LocalContext context) {
      IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null){
        return null;
      }
      return new IrBinExp(LESS_OR_EQUAL, BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LessThan ast, LocalContext context) {
      IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;
      return new IrBinExp(
        LESS_THAN, BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(GreaterOrEqual ast, LocalContext context) {
      IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;    
      return new IrBinExp(
        GREATER_OR_EQUAL, BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(GreaterThan ast, LocalContext context) {
      IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;
      return new IrBinExp(
        GREATER_THAN, BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LogicalAnd ast, LocalContext context) {
      IrExpression[] ops = processLogicalExpression(ast, context);
      if(ops == null) return null;
      return new IrBinExp(
        LOGICAL_AND, BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LogicalOr ast, LocalContext context) {
      IrExpression[] ops = processLogicalExpression(ast, context);
      if(ops == null) return null;
      return new IrBinExp(
        LOGICAL_OR, BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LogicalRightShift ast, LocalContext context) {
      return processShiftExpression(BIT_SHIFT_R3, ast, context);
    }
    
    public Object visit(MathLeftShift ast, LocalContext context) {
      return processShiftExpression(BIT_SHIFT_L2, ast, context);
    }
    
    public Object visit(MathRightShift ast, LocalContext context) {
      return processShiftExpression(BIT_SHIFT_R2, ast, context);
    }
      
    public Object visit(BitAnd ast, LocalContext context) {
      return checkBitExp(BIT_AND, ast, context);
    }
    
    public Object visit(BitOr expression, LocalContext context) {
      return checkBitExp(BIT_AND, expression, context);
    }
    
    IrExpression[] processLogicalExpression(BinaryExpression ast, LocalContext context){
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      TypeRef leftType = left.type(), rightType = right.type();
      if((leftType != BasicTypeRef.BOOLEAN) || (rightType != BasicTypeRef.BOOLEAN)){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new TypeRef[]{left.type(), right.type()}});
        return null;
      }
      return new IrExpression[]{left, right};
    }
    
    IrExpression processShiftExpression(
      int kind, BinaryExpression ast, LocalContext context){
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if(!left.type().isBasicType()){
        IrExpression[] params = new IrExpression[]{right};
        Pair<Boolean, MethodSymbol> result = tryFindMethod(ast, (ObjectTypeRef)left.type(), "add", params);
        if(result._2 == null){
          report(METHOD_NOT_FOUND, ast, new Object[]{left.type(), "add", types(params)});
          return null;
        }
        return new IrCall(left, result._2, params);
      }
      if(!right.type().isBasicType()){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new TypeRef[]{left.type(), right.type()}});
        return null;
      }
      BasicTypeRef leftType = (BasicTypeRef)left.type();
      BasicTypeRef rightType = (BasicTypeRef)right.type();
      if((!leftType.isInteger()) || (!rightType.isInteger())){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new TypeRef[]{left.type(), right.type()}});
        return null;
      }
      TypeRef leftResultType = promoteInteger(leftType);
      if(leftResultType != leftType){
        left = new IrCast(left, leftResultType);
      }
      if(rightType != BasicTypeRef.INT){
        right = new IrCast(right, BasicTypeRef.INT);
      }
      return new IrBinExp(kind, BasicTypeRef.BOOLEAN, left, right);
    }
    
    TypeRef promoteInteger(TypeRef type){
      if(type == BasicTypeRef.BYTE || type == BasicTypeRef.SHORT ||
         type == BasicTypeRef.CHAR || type == BasicTypeRef.INT){
        return BasicTypeRef.INT;
      }
      if(type == BasicTypeRef.LONG){
        return BasicTypeRef.LONG;
      }
      return null;
    }  
      
    IrExpression checkBitExp(int kind, BinaryExpression ast, LocalContext context){
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if((!left.isBasicType()) || (!right.isBasicType())){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new TypeRef[]{left.type(), right.type()}});
        return null;
      }
      BasicTypeRef leftType = (BasicTypeRef)left.type();
      BasicTypeRef rightType = (BasicTypeRef)right.type();
      TypeRef resultType = null;
      if(leftType.isInteger() && rightType.isInteger()){
        resultType = promote(leftType, rightType);    
      }else if(leftType.isBoolean() && rightType.isBoolean()){
        resultType = BasicTypeRef.BOOLEAN;
      }else{
        report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new TypeRef[]{leftType, rightType}});
        return null;
      }
      if(left.type() != resultType){
        left = new IrCast(left, resultType);
      }
      if(right.type() != resultType){
        right = new IrCast(right, resultType);
      }
      return new IrBinExp(kind, resultType, left, right);
    }
    
    IrExpression checkNumExp(int kind, BinaryExpression ast, IrExpression left, IrExpression right, LocalContext context) {
      if((!hasNumericType(left)) || (!hasNumericType(right))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new TypeRef[]{left.type(), right.type()}});
        return null;
      }
      TypeRef resultType = promote(left.type(), right.type());
      if(left.type() != resultType){
        left = new IrCast(left, resultType);
      }
      if(right.type() != resultType){
        right = new IrCast(right, resultType);
      }
      return new IrBinExp(kind, resultType, left, right);
    }
    
    IrExpression checkRefEqualsExp(int kind, BinaryExpression ast, LocalContext context){
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      TypeRef leftType = left.type();
      TypeRef rightType = right.type();
      if(
        (left.isBasicType() && (!right.isBasicType())) ||
        ((!left.isBasicType()) && (right.isBasicType()))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new TypeRef[]{leftType, rightType}});
        return null;
      }
      if(left.isBasicType() && right.isBasicType()){
        if(hasNumericType(left) && hasNumericType(right)){
          TypeRef resultType = promote(leftType, rightType);
          if(resultType != left.type()){
            left = new IrCast(left, resultType);
          }
          if(resultType != right.type()){
            right = new IrCast(right, resultType);
          }
        }else if(leftType != BasicTypeRef.BOOLEAN || rightType != BasicTypeRef.BOOLEAN){
          report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new TypeRef[]{leftType, rightType}});
          return null;
        }
      }
      return new IrBinExp(kind, BasicTypeRef.BOOLEAN, left, right);
    }
    
    IrExpression checkEqualExp(int kind, BinaryExpression ast, LocalContext context){
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      TypeRef leftType = left.type(), rightType = right.type();
      if(
        (left.isBasicType() && (!right.isBasicType())) ||
        ((!left.isBasicType()) && (right.isBasicType()))
      ){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new TypeRef[]{leftType, rightType}}
        );
        return null;
      }
      if(left.isBasicType() && right.isBasicType()){      
        if(hasNumericType(left) && hasNumericType(right)){
          TypeRef resultType = promote(leftType, rightType);
          if(resultType != left.type()){
            left = new IrCast(left, resultType);
          }
          if(resultType != right.type()){
            right = new IrCast(right, resultType);
          }
        }else if(leftType != BasicTypeRef.BOOLEAN || rightType != BasicTypeRef.BOOLEAN){
          report(
            INCOMPATIBLE_OPERAND_TYPE, ast,
            new Object[]{ast.getSymbol(), new TypeRef[]{leftType, rightType}}
          );
          return null;
        }
      }else if(left.isReferenceType() && right.isReferenceType()){
        return createEquals(kind, left, right);
      }
      return new IrBinExp(kind, BasicTypeRef.BOOLEAN, left, right);
    }
    
    IrExpression createEquals(int kind, IrExpression left, IrExpression right){
      right = new IrCast(right, rootClass());
      IrExpression[] params = {right};
      ObjectTypeRef target = (ObjectTypeRef) left.type();
      MethodSymbol[] methods = target.findMethod("equals", params);
      IrExpression node = new IrCall(left, methods[0], params);
      if(kind == IrBinExp.Constants.NOT_EQUAL){
        node = new IrUnaryExp(NOT, BasicTypeRef.BOOLEAN, node);
      }
      return node;
    }
    
    IrExpression[] processComparableExpression(BinaryExpression ast, LocalContext context) {
      IrExpression left = typeCheck(ast.getLeft(), context);
      IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      TypeRef leftType = left.type(), rightType = right.type();
      if((!numeric(left.type())) || (!numeric(right.type()))){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new TypeRef[]{left.type(), right.type()}});
        return null;
      }
      TypeRef resultType = promote(leftType, rightType);
      if(leftType != resultType){
        left = new IrCast(left, resultType);
      }
      if(rightType != resultType){
        right = new IrCast(right, resultType);
      }
      return new IrExpression[]{left, right};
    }
  //-------------------------------------------------------------------------------//  
  //------------------------- literals --------------------------------------------//
    public Object visit(FloatLiteral ast, LocalContext context) {
      return new IrFloat(ast.getValue());
    }
    
    public Object visit(SuperMethodCall ast, LocalContext context) {
      IrExpression[] params;    
      params = typeCheckExps(ast.getParams(), context);
      if(params == null) return null;
      ClassSymbol contextClass = getContextClass();
      Pair<Boolean, MethodSymbol> result = tryFindMethod(ast, contextClass.getSuperClass(), ast.getName(), params);
      if(result._2 == null){
        if(result._1){
          report(
            METHOD_NOT_FOUND, ast, 
            new Object[]{contextClass, ast.getName(), types(params)});
        }
        return null;
      }
      return new IrCallSuper(new IrThis(contextClass), result._2, params);
    }
    
    public Object visit(DoubleLiteral ast, LocalContext context) {
      return new IrDouble(ast.getValue());
    }
    
    public Object visit(IntegerLiteral node, LocalContext context) {
      return new IrInt(node.getValue());
    }
    
    public Object visit(CharacterLiteral node, LocalContext context) {
      return new IrChar(node.getValue());
    }
    
    public Object visit(LongLiteral ast, LocalContext context) {
      return new IrLong(ast.getValue());
    }
    
    public Object visit(BooleanLiteral ast, LocalContext context) {
      return new IrBool(ast.getValue());
    }
    
    public Object visit(ListLiteral ast, LocalContext context) {
      IrExpression[] elements = new IrExpression[ast.size()];
      for(int i = 0; i < ast.size(); i++){
        elements[i] = typeCheck(ast.getExpression(i), context);
      }
      IrList node = new IrList(elements, load("java.util.List"));
      return node;
    }
    
    public Object visit(StringLiteral ast, LocalContext context) {
      return new IrString(ast.getValue(), load("java.lang.String"));
    }  
    
    public Object visit(NullLiteral ast, LocalContext context) {
      return new IrNull();
    }
  //-----------------------------------------------------------------------------//
    
  //---------------------------- unary expressions ------------------------------//
    public Object visit(Posit ast, LocalContext context) {
      IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(!hasNumericType(node)){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast, 
          new Object[]{"+", new TypeRef[]{node.type()}});
        return null;
      }
      node = new IrUnaryExp(PLUS, node.type(), node);
      return node;
    }
    
    public Object visit(Negate ast, LocalContext context) {
      IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(!hasNumericType(node)){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{"-", new TypeRef[]{node.type()}}
        );
        return null;
      }
      node = new IrUnaryExp(MINUS, node.type(), node);
      return node;
    }
    
    public Object visit(Not ast, LocalContext context) {
      IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(node.type() != BasicTypeRef.BOOLEAN){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{"!", new TypeRef[]{node.type()}});
        return null;
      }
      node = new IrUnaryExp(NOT, BasicTypeRef.BOOLEAN, node);
      return node;
    }
  //-----------------------------------------------------------------------------//
    
  //---------------------------- assignment operators ---------------------------//
    public Object visit(Assignment ast, LocalContext context) {
      Expression left = ast.getLeft();
      if(left instanceof Id){
        return processLocalAssign(ast, context);
      }else if(left instanceof SelfFieldReference){
        return processSelfFieldAssign(ast, context);
      }else if(left instanceof Indexing){
        return processArrayAssign(ast, context);
      }else if(left instanceof FieldOrMethodRef){
        return processFieldOrMethodAssign(ast, context);
      }
      return null;
    }
    
    private IrExpression processLocalAssign(Assignment ast, LocalContext context){
      IrExpression value = typeCheck(ast.getRight(), context);
      if(value == null) return null;
      Id id = (Id) ast.getLeft();
      LocalContext local = ((LocalContext)context);
      ClosureLocalBinding bind = local.lookup(id.getName());
      int frame, index;
      TypeRef leftType, rightType = value.type();
      if(bind != null){
        frame = bind.getFrame();
        index = bind.getIndex();
        leftType = bind.getType();
      }else{
        frame = 0;
        if(rightType.isNullType()){
          leftType = rootClass();
        }else{
          leftType = rightType;
        }
        index = local.addEntry(id.getName(), leftType);
      }
      value = processAssignable(ast.getRight(), leftType, value);
      if(value == null) return null;
      return new IrLocalSet(frame, index, leftType, value);
    }
    
    private Object processSelfFieldAssign(Assignment ast, LocalContext context){
      IrExpression value = typeCheck(ast.getRight(), context);
      if(value == null) return null;
      SelfFieldReference ref = (SelfFieldReference) ast.getLeft();
      LocalContext local = context;
      ClassSymbol selfClass;
      if(local.isGlobal()){
        selfClass = loadTopClass();
      }else {
        if(local.getMethod() != null){
          selfClass = local.getMethod().getClassType();
        }else{
          selfClass = local.getConstructor().getClassType();
        }
      }
      FieldSymbol field = findField(selfClass, ref.getName());
      if(field == null){
        report(FIELD_NOT_FOUND, ref, new Object[]{selfClass, ref.getName()});
        return null;
      }
      if(!isAccessible(field, selfClass)){
        report(
          FIELD_NOT_ACCESSIBLE, ast, 
          new Object[]{field.getClassType(), field.getName(), selfClass});
        return null;
      }
      value = processAssignable(ast.getRight(), field.getType(), value);
      if(value == null) return null;
      return new IrFieldSet(new IrThis(selfClass), field, value);
    }
    
    Object processArrayAssign(Assignment ast, LocalContext context){
      IrExpression value = typeCheck(ast.getRight(), context);
      Indexing indexing = (Indexing) ast.getLeft();
      IrExpression target = typeCheck(indexing.getLeft(), context);
      IrExpression index = typeCheck(indexing.getRight(), context);
      if(value == null || target == null || index == null) return null;
      if(target.isBasicType()){
        report(
          INCOMPATIBLE_TYPE,
          indexing.getLeft(), new Object[]{rootClass(), target.type()});
        return null;
      }
      if(target.isArrayType()){
        ArraySymbol targetType = ((ArraySymbol)target.type());
        if(!(index.isBasicType() && ((BasicTypeRef)index.type()).isInteger())){
          report(
            INCOMPATIBLE_TYPE, 
            indexing.getRight(), new Object[]{BasicTypeRef.INT, index.type()});
          return null;
        }
        TypeRef base = targetType.getBase();
        value = processAssignable(ast.getRight(), base, value);
        if(value == null) return null;
        return new IrArraySet(target, index, value);      
      }
      IrExpression[] params;
      params = new IrExpression[]{index, value};
      Pair<Boolean, MethodSymbol> result = tryFindMethod(ast, (ObjectTypeRef)target.type(), "set", new IrExpression[]{index, value});
      if(result._2 == null){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{target.type(), "set", types(params)});
        return null;
      }
      return new IrCall(target, result._2, params);
    }
    
    Object processFieldOrMethodAssign(Assignment ast, LocalContext context){
      IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(AdditionAssignment ast, LocalContext context) {
      IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(SubtractionAssignment ast, LocalContext context) {
      IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(MultiplicationAssignment ast, LocalContext context) {
      IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);    
      return null;
    }
    
    public Object visit(DivisionAssignment ast, LocalContext context) {
      IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(ModuloAssignment ast, LocalContext context) {
      IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
  //-----------------------------------------------------------------------------//
  
  //---------------------------- other expressions ------------------------------//
    public Object visit(Id ast, LocalContext context) {
      LocalContext local = context;
      ClosureLocalBinding bind = local.lookup(ast.getName());
      if(bind == null){
        report(VARIABLE_NOT_FOUND, ast, new Object[]{ast.getName()});
        return null;
      }
      return new IrLocalRef(bind);
    }
    
    MethodSymbol findMethod(AstNode ast, ObjectTypeRef type, String name) {
      IrExpression[] params = new IrExpression[0];
      MethodSymbol[] methods = type.findMethod(name, params);    
      if(methods.length == 0){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{type, name, types(params)});
        return null;
      }
      return methods[0];
    }
    
    MethodSymbol findMethod(
      AstNode ast, ObjectTypeRef type, String name, IrExpression[] params
    ) {
      MethodSymbol[] methods = type.findMethod(name, params);
      return methods[0];
    }
    
    public Object visit(CurrentInstance ast, LocalContext context) {
      LocalContext local = context;
      if(local.isStatic()) return null;
      ClassSymbol selfClass = getContextClass();
      return new IrThis(selfClass);
    }
    
    boolean hasSamePackage(ClassSymbol a, ClassSymbol b) {
      String name1 = a.getName();
      String name2 = b.getName();
      int index;
      index = name1.lastIndexOf(".");
      if(index >= 0){
        name1 = name1.substring(0, index);
      }else{
        name1 = "";
      }
      index = name2.lastIndexOf(".");
      if(index >= 0){
        name2 = name2.substring(0, index);
      }else{
        name2 = "";
      }
      return name1.equals(name2);
    }
    
    boolean isAccessible(ClassSymbol target, ClassSymbol context) {
      if(hasSamePackage(target, context)){
        return true;
      }else{
        if(Modifier.isInternal(target.getModifier())){
          return false;
        }else{
          return true;
        }
      }
    }
    
    boolean isAccessible(MemberSymbol member, ClassSymbol context) {
      ClassSymbol targetType = member.getClassType();
      if(targetType == context) return true;
      int modifier = member.getModifier();
      if(TypeRules.isSuperType(targetType, context)){
        if(Modifier.isProtected(modifier) || Modifier.isPublic(modifier)){
          return true;
        }else{
          return false;
        }
      }else{
        if(Modifier.isPublic(modifier)){
          return true;
        }else{
          return false;
        }
      }
    }
    
    private FieldSymbol findField(ObjectTypeRef target, String name) {
      if(target == null) return null;
      FieldSymbol[] fields = target.getFields();
      for (int i = 0; i < fields.length; i++) {
        if(fields[i].getName().equals(name)){
          return fields[i];
        }
      }
      FieldSymbol field = findField(target.getSuperClass(), name);
      if(field != null) return field;
      ClassSymbol[] interfaces = target.getInterfaces();
      for(int i = 0; i < interfaces.length; i++){
        field = findField(interfaces[i], name);
        if(field != null) return field;
      }
      return null;
    }
    
    private boolean checkAccessible(
      AstNode ast, ObjectTypeRef target, ClassSymbol context
    ) {
      if(target.isArrayType()){
        TypeRef component = ((ArraySymbol)target).getComponent();
        if(!component.isBasicType()){
          if(!isAccessible((ClassSymbol)component, getContextClass())){
            report(CLASS_NOT_ACCESSIBLE, ast, new Object[]{target, context});
            return false;
          }
        }
      }else{
        if(!isAccessible((ClassSymbol)target, context)){
          report(CLASS_NOT_ACCESSIBLE, ast, new Object[]{target, context});
          return false;
        }
      }
      return true;
    }
    
    public Object visit(FieldOrMethodRef ast, LocalContext context) {
      IrClass contextClass = getContextClass();
      IrExpression target = typeCheck(ast.getTarget(), context);
      if(target == null) return null;
      if(target.type().isBasicType() || target.type().isNullType()){
        report(
          INCOMPATIBLE_TYPE, ast.getTarget(),
          new TypeRef[]{rootClass(), target.type()});
        return null;
      }
      ObjectTypeRef targetType = (ObjectTypeRef) target.type();
      if(!checkAccessible(ast, targetType, contextClass)) return null;
      String name = ast.getName();
      if(target.type().isArrayType()){
        if(name.equals("length") || name.equals("size")){
          return new IrArrayLength(target);
        }else{
          return null;
        }
      }
      FieldSymbol field = findField(targetType, name);
      if(field != null && isAccessible(field, getContextClass())){
        return new IrFieldRef(target, field);
      }
      Pair<Boolean, MethodSymbol> result;
      boolean continuable;
      
      result = tryFindMethod(ast, targetType, name, new IrExpression[0]);
      if(result._2 != null){
        return new IrCall(target, result._2, new IrExpression[0]);
      }
      continuable = result._1;
      if(!continuable) return null;
      
      String getterName;
      getterName = getter(name);
      result = tryFindMethod(ast, targetType, getterName, new IrExpression[0]);
      if(result._2 != null){
        return new IrCall(target, result._2, new IrExpression[0]);
      }
      continuable = result._1;
      if(!continuable) return null;
      
      getterName = getterBoolean(name);
      result = tryFindMethod(ast, targetType, getterName, new IrExpression[0]);
      if(result._2 != null){
        return new IrCall(target, result._2, new IrExpression[0]);
      }
      
      if(field == null){
        report(FIELD_NOT_FOUND, ast, new Object[]{targetType, ast.getName()});
      }else{
        report(
          FIELD_NOT_ACCESSIBLE, ast, 
          new Object[]{targetType, ast.getName(), getContextClass()});
      }
      return null;
    }
    
    private Pair<Boolean, MethodSymbol> tryFindMethod(
      AstNode ast, ObjectTypeRef target, String name, IrExpression[] params
    ) {
      MethodSymbol[] methods;
      methods = target.findMethod(name, params);
      if(methods.length > 0){
        if(methods.length > 1){
          report(
            AMBIGUOUS_METHOD, ast,
            new Object[]{
              new Object[]{
                methods[0].getClassType(), name, methods[0].getArguments()
              },
              new Object[]{
                methods[1].getClassType(), name, methods[1].getArguments()
              }
            });
          return Pair.make(false, null);
        }
        if(!isAccessible(methods[0], getContextClass())){
          report(
            METHOD_NOT_ACCESSIBLE, ast,
            new Object[]{
              methods[0].getClassType(), name, methods[0].getArguments(), 
              getContextClass()
            }
          );
          return Pair.make(false, null);
        }
        return Pair.make(false, methods[0]);
      }
      return Pair.make(true, null);
    }
    
    private String getter(String name) {
      return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    
    private String getterBoolean(String name) {
      return "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    
    private String setter(String name) {
      return "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    
    public Object visit(Argument ast, LocalContext context) {
      LocalContext local = ((LocalContext)context);
      String name = ast.getName();
      ClosureLocalBinding binding = local.lookupOnlyCurrentScope(name);
      if(binding != null){
        report(DUPLICATE_LOCAL_VARIABLE, ast, new Object[]{name});
        return null;
      }
      TypeRef type = resolve(ast.getType(), getSolver());
      if(type == null) return null;
      local.addEntry(name, type);
      return type;
    }
    
    public Object visit(NewArray ast, LocalContext context) {
      TypeRef type = resolve(ast.getType(), getSolver());
      IrExpression[] parameters = typeCheckExps(ast.getArguments(), context);
      if(type == null || parameters == null) return null;
      ArraySymbol resultType = loadArray(type, parameters.length);
      return new IrNewArray(resultType, parameters);
    }
      
    public Object visit(Cast ast, LocalContext context) {
      IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      TypeRef conversion = resolve(ast.getType(), getSolver());
      if(conversion == null) return null;
      node = new IrCast(node, conversion);
      return node;
    }
    
    public boolean equals(TypeRef[] types1, TypeRef[] types2) {
      if(types1.length != types2.length) return false;
      for(int i = 0; i < types1.length; i++){
        if(types1[i] != types2[i]) return false;
      }
      return true;
    }
    
    public Object visit(ClosureExpression ast, LocalContext context) {
      LocalContext local = ((LocalContext)context);
      ClassSymbol type = (ClassSymbol) CodeAnalysis.this.resolve(ast.getType());
      Argument[] args = ast.getArguments();
      TypeRef[] argTypes = new TypeRef[args.length];
      String name = ast.getName();
      try {
        local.openFrame();
        boolean error = false;
        for(int i = 0; i < args.length; i++){
          argTypes[i] = (TypeRef)accept(args[i], context);
          if(argTypes[i] == null){
            error = true;
          }
        }     
        if(type == null) return null;
        if(!type.isInterface()){
          report(INTERFACE_REQUIRED, ast.getType(), new Object[]{type});
          return null;
        }
        if(error) return null;
        MethodSymbol[] methods = type.getMethods();
        MethodSymbol method = null;
        for(int i = 0; i < methods.length; i++){
          TypeRef[] types = methods[i].getArguments();
          if(name.equals(methods[i].getName()) && equals(argTypes, types)){
            method = methods[i];
            break;
          }
        }
        if(method == null){
          report(METHOD_NOT_FOUND, ast, new Object[]{type, name, argTypes});
          return null;
        }
        local.setMethod(method);
        local.getContextFrame().getParent().setAllClosed(true);
        IrStatement block = translate(ast.getBlock(), context);
        block = addReturnNode(block, method.getReturnType());
        IrClosure node = new IrClosure(type, method, block);
        node.setFrame(local.getContextFrame());
        return node;
      }finally{
        local.closeFrame();
      }     
    }
    
    public Object visit(Indexing ast, LocalContext context) {
      IrExpression target = typeCheck(ast.getLeft(), context);
      IrExpression index = typeCheck(ast.getRight(), context);
      if(target == null || index == null) return null;
      if(target.isArrayType()){
        if(!(index.isBasicType() && ((BasicTypeRef)index.type()).isInteger())){
          report(
            INCOMPATIBLE_TYPE, ast, new Object[]{BasicTypeRef.INT, index.type()});
          return null;
        }
        return new IrArrayRef(target, index);
      }    
      if(target.isBasicType()){
        report(
          INCOMPATIBLE_TYPE,
          ast.getLeft(), new Object[]{rootClass(), target.type()});
        return null;
      }
      if(target.isArrayType()){
        if(!(index.isBasicType() && ((BasicTypeRef)index.type()).isInteger())){
          report(
            INCOMPATIBLE_TYPE, 
            ast.getRight(), new Object[]{BasicTypeRef.INT, index.type()}
          );
          return null;
        }
        return new IrArrayRef(target, index);
      }    
      IrExpression[] params = {index};
      Pair<Boolean, MethodSymbol> result = tryFindMethod(ast, (ObjectTypeRef)target.type(), "get", new IrExpression[]{index});
      if(result._2 == null){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{target.type(), "get", types(params)});
        return null;
      }
      return new IrCall(target, result._2, params);    
    }
    
    public Object visit(SelfFieldReference ast, LocalContext context) {
      LocalContext local = context;
      ClassSymbol selfClass = null;
      if(local.isStatic()) return null;
      selfClass = getContextClass();
      FieldSymbol field = findField(selfClass, ast.getName());
      if(field == null){
        report(FIELD_NOT_FOUND, ast, new Object[]{selfClass, ast.getName()});
        return null;
      }
      if(!isAccessible(field, selfClass)){
        report(
          FIELD_NOT_ACCESSIBLE, ast, 
          new Object[]{field.getClassType(), ast.getName(), selfClass});
        return null;
      }    
      return new IrFieldRef(new IrThis(selfClass), field);
    }
    
    public Object visit(NewObject ast, LocalContext context) {
      ClassSymbol type = (ClassSymbol) CodeAnalysis.this.resolve(ast.getType());
      IrExpression[] parameters = typeCheckExps(ast.getArguments(), context);
      if(parameters == null || type == null) return null;
      ConstructorSymbol[] constructors = type.findConstructor(parameters);
      if(constructors.length == 0){
        report(CONSTRUCTOR_NOT_FOUND, ast, new Object[]{type, types(parameters)});
        return null;
      }
      if(constructors.length > 1){
        report(
          AMBIGUOUS_CONSTRUCTOR, ast,
          new Object[]{
            new Object[]{
              constructors[0].getClassType(), 
              constructors[0].getArgs()
            },
            new Object[]{
              constructors[1].getClassType(),
              constructors[1].getArgs()
            }
          });
        return null;
      }
      return new IrNew(constructors[0], parameters);
    }
        
    public Object visit(IsInstance ast, LocalContext context) {
      IrExpression target = typeCheck(ast.getTarget(), context);
      TypeRef checkType = resolve(ast.getType(), getSolver());
      if(target == null || checkType == null) return null;
      return new IrInstanceOf(target, checkType);      
    }
    
    private TypeRef[] types(IrExpression[] parameters){
      TypeRef[] types = new TypeRef[parameters.length];
      for(int i = 0; i < types.length; i++){
        types[i] = parameters[i].type();
      }
      return types;
    }
    
    public Object visit(SelfMethodCall ast, LocalContext context) {
      IrExpression[] params = typeCheckExps(ast.getArguments(), context);
      if(params == null) return null;
      IrClass targetType = getContextClass();
      String name = ast.getName();
      MethodSymbol[] methods = targetType.findMethod(ast.getName(), params);
      
      if(methods.length == 0){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{targetType, name, types(params)});
        return null;
      }
      
      if(methods.length > 1){
        report(
          AMBIGUOUS_METHOD, ast,
          new Object[]{
            new Object[]{
              methods[0].getClassType(), name, methods[0].getArguments()
            },
            new Object[]{
              methods[1].getClassType(), name, methods[1].getArguments()
            }
          });
        return null;
      }
      
      /*
       * TODO check illegal method call
       * ex. instance method call in the static context 
       */
      
      params = convert(methods[0].getArguments(), params);
      
      if((methods[0].getModifier() & Modifier.STATIC) != 0){
        return new IrCallStatic(targetType, methods[0], params);
      }else {
        return new IrCall(new IrThis(targetType), methods[0], params);
      }
    }
    
    private IrExpression[] convert(TypeRef[] arguments, IrExpression[] params){
      for(int i = 0; i < params.length; i++){
        if(arguments[i] != params[i].type()){
          params[i] = new IrCast(params[i], arguments[i]);
        }
      }
      return params;
    }
    
    public Object visit(MethodCall ast, LocalContext context) {
      IrExpression target = typeCheck(ast.getTarget(), context);
      if(target == null) return null;
      IrExpression[] params = typeCheckExps(ast.getArguments(), context);
      if(params == null) return null;
      ObjectTypeRef targetType = (ObjectTypeRef) target.type();
      final String name = ast.getName();
      MethodSymbol[] methods = targetType.findMethod(name, params);
      
      if(methods.length == 0){
        report(
          METHOD_NOT_FOUND, ast, new Object[]{targetType, name, types(params)});
        return null;
      }
      if(methods.length > 1){
        report(
          AMBIGUOUS_METHOD, ast,
          new Object[]{
            new Object[]{
              methods[0].getClassType(), name, methods[0].getArguments()
            },
            new Object[]{
              methods[1].getClassType(), name, methods[1].getArguments()
            }
          });
        return null;
      }    
      if((methods[0].getModifier() & Modifier.STATIC) != 0){
        report(
          ILLEGAL_METHOD_CALL, ast,
          new Object[]{
            methods[0].getClassType(), name, methods[0].getArguments()
          });
        return null;
      }    
      params = convert(methods[0].getArguments(), params);
      return new IrCall(target, methods[0], params);
    }
    
    public Object visit(StaticIDExpression ast, LocalContext context) {
      ClassSymbol type = (ClassSymbol) CodeAnalysis.this.resolve(ast.getType());
      if(type == null) return null;
      FieldSymbol field = findField(type, ast.getName());
      if(field == null){
        report(FIELD_NOT_FOUND, ast, new Object[]{type, ast.getName()});
        return null;
      }
      return new IrStaticFieldRef(type, field);
    }
    
    public Object visit(StaticMethodCall ast, LocalContext context) {
      ClassSymbol type = (ClassSymbol) CodeAnalysis.this.resolve(ast.getTarget());
      IrExpression[] params = typeCheckExps(ast.getArgs(), context);
      if(type == null) return null;
      if(params == null) return null;
      MethodSymbol[] methods = type.findMethod(ast.getName(), params);
      
      if(methods.length == 0){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{type, ast.getName(), types(params)});
        return null;
      }
      if(methods.length > 1){
        report(
          AMBIGUOUS_METHOD, ast,
          new Object[]{
            ast.getName(),
            typeNames(methods[0].getArguments()), typeNames(methods[1].getArguments())
          });
        return null;
      }
      
      params = convert(methods[0].getArguments(), params);
      return new IrCallStatic(type, methods[0], params);
    }
      
    private String[] typeNames(TypeRef[] types) {
      String[] names = new String[types.length];
      for(int i = 0; i < names.length; i++){
        names[i] = types[i].getName();
      }
      return names;
    }
    
    private IrExpression[] typeCheckExps(Expression[] ast, LocalContext context){
      IrExpression[] expressions = new IrExpression[ast.length];
      boolean success = true;
      for(int i = 0; i < ast.length; i++){
        expressions[i] = typeCheck(ast[i], context);
        if(expressions[i] == null){
          success = false;
        }
      }
      if(success){
        return expressions;
      }else{
        return null;
      }
    }
  //-------------------------------------------------------------------------//
  
  //------------------------- statements ------------------------------------//
    public Object visit(ForeachStatement ast, LocalContext context) {
      Expression collectionAST = ast.getCollection();
      LocalContext local = context;
      try {
        local.openScope();
        IrExpression collection = typeCheck(collectionAST, context);
        Argument arg = ast.getDeclaration();
        accept(arg, context);
        ClosureLocalBinding bind = local.lookupOnlyCurrentScope(arg.getName());
        IrStatement block = translate(ast.getStatement(), context);
        
        if(collection.isBasicType()){
          report(
            INCOMPATIBLE_TYPE, collectionAST,
            new Object[]{load("java.util.Collection"), collection.type()});
          return null;
        }
        ClosureLocalBinding bind2 = new ClosureLocalBinding(
          0, local.addEntry(local.newName(), collection.type()), collection.type());
        IrStatement init = 
          new IrExpStmt(new IrLocalSet(bind2, collection));
        if(collection.isArrayType()){
          ClosureLocalBinding bind3 = new ClosureLocalBinding(
            0, local.addEntry(local.newName(), BasicTypeRef.INT), BasicTypeRef.INT
          );
          init = new IrBlock(init, new IrExpStmt(new IrLocalSet(bind3, new IrInt(0))));
          block = new IrLoop(
            new IrBinExp(
              LESS_THAN, BasicTypeRef.BOOLEAN,
              ref(bind3),
              new IrArrayLength(ref(bind2))
            ),
            new IrBlock(
              assign(bind, indexref(bind2, ref(bind3))),
              block,
              assign(
                bind3, 
                new IrBinExp(ADD, BasicTypeRef.INT, ref(bind3), new IrInt(1))
              )
            )
          );
          return new IrBlock(init, block);
        }else{
          ObjectTypeRef iterator = load("java.util.Iterator");
          ClosureLocalBinding bind3 = new ClosureLocalBinding(
            0, local.addEntry(local.newName(), iterator), iterator
          );
          MethodSymbol mIterator, mNext, mHasNext;
          mIterator = findMethod(collectionAST, (ObjectTypeRef) collection.type(), "iterator");
          mNext = findMethod(ast.getCollection(), iterator, "next");
          mHasNext = findMethod(ast.getCollection(), iterator, "hasNext");
          init = new IrBlock(
            init,
            assign(bind3, new IrCall(ref(bind2), mIterator, new IrExpression[0]))
          );
          IrExpression callNext = new IrCall(
            ref(bind3), mNext, new IrExpression[0]
          );
          if(bind.getType() != rootClass()){
            callNext = new IrCast(callNext, bind.getType());
          }
          block = new IrLoop(
            new IrCall(ref(bind3), mHasNext, new IrExpression[0]),
            new IrBlock(assign(bind, callNext), block)
          );
          return new IrBlock(init, block);
        }
      }finally{
        local.closeScope();
      }
    }
    
    private IrExpression indexref(ClosureLocalBinding bind, IrExpression value) {
      return new IrArrayRef(new IrLocalRef(bind), value);
    }
    
    private IrStatement assign(ClosureLocalBinding bind, IrExpression value) {
      return new IrExpStmt(new IrLocalSet(bind, value));
    }
    
    private IrExpression ref(ClosureLocalBinding bind) {
      return new IrLocalRef(bind);
    }
    
    public Object visit(ExpressionStatement ast, LocalContext context) {
      IrExpression expression = typeCheck(ast.getExpression(), context);
      return new IrExpStmt(expression);
    }
    
    public Object visit(CondStatement node, LocalContext context) {
      LocalContext local = context;
      try {
        local.openScope();
        
        int size = node.size();
        Stack exprs = new Stack();
        Stack stmts = new Stack();
        for(int i = 0; i < size; i++){        
          Expression expr = node.getCondition(i);
          Statement  stmt = node.getBlock(i);
          IrExpression texpr = typeCheck(expr, context);
          if(texpr != null && texpr.type() != BasicTypeRef.BOOLEAN){
            TypeRef expect = BasicTypeRef.BOOLEAN;
            TypeRef actual = texpr.type();
            report(INCOMPATIBLE_TYPE, expr, new Object[]{expect, actual});
          }
          exprs.push(texpr);
          IrStatement tstmt = translate(stmt, context);
          stmts.push(tstmt);
        }
        
        Statement elseStmt = node.getElseBlock();
        IrStatement result = null;
        if(elseStmt != null){
          result = translate(elseStmt, context);
        }
        
        for(int i = 0; i < size; i++){
          IrExpression expr = (IrExpression)exprs.pop();
          IrStatement  stmt = (IrStatement)stmts.pop();
          result = new IrIf(expr, stmt, result);
        }
        
        return result;
      }finally{
        local.closeScope();
      }
    }
    
    public Object visit(ForStatement ast, LocalContext context) {
      LocalContext local = context;
      try{
        local.openScope();
        
        IrStatement init = null;
        if(ast.getInit() != null){
          init = translate(ast.getInit(), context);
        }else{
          init = new IrNOP();
        }
        IrExpression condition;
        Expression astCondition = ast.getCondition();
        if(astCondition != null){
          condition = typeCheck(ast.getCondition(), context);
          TypeRef expected = BasicTypeRef.BOOLEAN;
          if(condition != null && condition.type() != expected){
            TypeRef appeared = condition.type();
            report(INCOMPATIBLE_TYPE, astCondition, new Object[]{expected, appeared});
          }
        }else{
          condition = new IrBool(true);
        }
        IrExpression update = null;
        if(ast.getUpdate() != null){
          update = typeCheck(ast.getUpdate(), context);
        }
        IrStatement loop = translate(
          ast.getBlock(), context);
        if(update != null){
          loop = new IrBlock(loop, new IrExpStmt(update));
        }
        IrStatement result = new IrLoop(condition, loop);
        result = new IrBlock(init, result);
        return result;
      }finally{
        local.closeScope();
      }
    }
    
    public Object visit(BlockStatement ast, LocalContext context) {
      Statement[] astStatements = ast.getStatements();
      IrStatement[] statements = new IrStatement[astStatements.length];
      LocalContext local = context;
      try{
        local.openScope();
        for(int i = 0; i < astStatements.length; i++){
          statements[i] = translate(astStatements[i], context);
        }
        return new IrBlock(statements);
      }finally{
        local.closeScope();
      }
    }
    
    public Object visit(IfStatement ast, LocalContext context) {
      LocalContext local = ((LocalContext)context);
      try{
        local.openScope();
        
        IrExpression condition = typeCheck(ast.getCondition(), context);
        TypeRef expected = BasicTypeRef.BOOLEAN;
        if(condition != null && condition.type() != expected){
          report(INCOMPATIBLE_TYPE, ast.getCondition(), new Object[]{expected, condition.type()});
        }
        IrStatement thenBlock = translate(ast.getThenBlock(), context);
        IrStatement elseBlock = ast.getElseBlock() == null ? null : translate(ast.getElseBlock(), context);
        return new IrIf(condition, thenBlock, elseBlock);
      }finally{     
        local.closeScope();
      }
    }
    
    public Object visit(WhileStatement ast, LocalContext context) {
      LocalContext local = ((LocalContext)context);
      try{
        local.openScope();
        
        IrExpression condition = typeCheck(ast.getCondition(), context);
        TypeRef expected = BasicTypeRef.BOOLEAN;
        if(condition != null && condition.type() != expected){
          TypeRef actual = condition.type();
          report(INCOMPATIBLE_TYPE, ast, new Object[]{expected, actual});
        }
        IrStatement thenBlock = translate(ast.getBlock(), context);
        return new IrLoop(condition, thenBlock);
      }finally{
        local.closeScope();
      }     
    }
    
    public Object visit(ReturnStatement ast, LocalContext context) {
      TypeRef returnType = ((LocalContext)context).getReturnType();
      if(ast.getExpression() == null){
        TypeRef expected = BasicTypeRef.VOID;
        if(returnType != expected){
          report(CANNOT_RETURN_VALUE, ast, new Object[0]);
        }
        return new IrReturn(null);
      }else{
        IrExpression returned = typeCheck(ast.getExpression(), context);
        if(returned == null) return new IrReturn(null);
        if(returned.type() == BasicTypeRef.VOID){
          report(CANNOT_RETURN_VALUE, ast, new Object[0]);
        }else {
          returned = processAssignable(ast.getExpression(), returnType, returned);
          if(returned == null) return new IrReturn(null);
        }
        return new IrReturn(returned);
      }
    }
    
    IrExpression processAssignable(AstNode ast, TypeRef a, IrExpression b){
      if(b == null) return null;
      if(a == b.type()) return b;
      if(!TypeRules.isAssignable(a, b.type())){
        report(INCOMPATIBLE_TYPE, ast, new Object[]{ a, b.type() });
        return null;
      }
      b = new IrCast(b, a);
      return b;
    }
    
    public Object visit(SelectStatement ast, LocalContext context) {
      LocalContext local = context;
      try{
        local.openScope();
        IrExpression condition = typeCheck(ast.getCondition(), context);
        if(condition == null){
          return new IrNOP();
        }
        String name = local.newName();
        int index = local.addEntry(name, condition.type());
        IrStatement statement;
        if(ast.getCases().length == 0){
          if(ast.getElseBlock() != null){
            statement = translate(ast.getElseBlock(), context);
          }else{
            statement = new IrNOP();
          }
        }else{
          statement = processCases(ast, condition, name, context);
        }
        IrBlock block = new IrBlock(
          new IrExpStmt(new IrLocalSet(0, index, condition.type(), condition)),
          statement
        );
        return block;
      }finally{
        local.closeScope();
      }
    }
    
    IrStatement processCases(
      SelectStatement ast, IrExpression cond, String var, LocalContext context
    ) {
      CaseBranch[] cases = ast.getCases();
      List nodes = new ArrayList();
      List thens = new ArrayList();
      for(int i = 0; i < cases.length; i++){
        Expression[] astExpressions = cases[i].getExpressions();
        ClosureLocalBinding bind = context.lookup(var);
        nodes.add(processNodes(astExpressions, cond.type(), bind, context));
        thens.add(translate(cases[i].getBlock(), context));
      }
      IrStatement statement;
      if(ast.getElseBlock() != null){
        statement = translate(ast.getElseBlock(), context);
      }else{
        statement = null;
      }
      for(int i = cases.length - 1; i >= 0; i--){
        IrExpression value = (IrExpression) nodes.get(i);
        IrStatement then = (IrStatement) thens.get(i);
        statement = new IrIf(value, then, statement);
      }
      return statement;
    }
    
    IrExpression processNodes(
      Expression[] asts, TypeRef type, ClosureLocalBinding bind, LocalContext context
    ) {
      IrExpression[] nodes = new IrExpression[asts.length];
      boolean error = false;
      for(int i = 0; i < asts.length; i++){
        nodes[i] = typeCheck(asts[i], context);
        if(nodes[i] == null){
          error = true;
          continue;
        }
        if(!TypeRules.isAssignable(type, nodes[i].type())){
          report(INCOMPATIBLE_TYPE, asts[i], new TypeRef[]{type, nodes[i].type()});
          error = true;
          continue;
        }
        if(nodes[i].isBasicType() && nodes[i].type() != type){
          nodes[i] = new IrCast(nodes[i], type);
        }
        if(nodes[i].isReferenceType() && nodes[i].type() != rootClass()){
          nodes[i] = new IrCast(nodes[i], rootClass());
        }
      }
      if(!error){
        IrExpression node;
        if(nodes[0].isReferenceType()){
          node = createEquals(IrBinExp.Constants.EQUAL, new IrLocalRef(bind), nodes[0]);
        }else{
          node = new IrBinExp(EQUAL, BasicTypeRef.BOOLEAN, new IrLocalRef(bind), nodes[0]);
        }
        for(int i = 1; i < nodes.length; i++){
          node = new IrBinExp(
            LOGICAL_OR, BasicTypeRef.BOOLEAN, node,
            new IrBinExp(EQUAL, BasicTypeRef.BOOLEAN, new IrLocalRef(bind), nodes[i])
          );
        }
        return node;
      }else{
        return null;
      }
    }
    
    public Object visit(ThrowStatement ast, LocalContext context) {
      IrExpression expression = typeCheck(ast.getExpression(), context);
      if(expression != null){
        TypeRef expected = load("java.lang.Throwable");
        TypeRef detected = expression.type();
        if(!TypeRules.isSuperType(expected, detected)){
          report(
            INCOMPATIBLE_TYPE, ast.getExpression(), 
            new Object[]{ expected, detected}
          );
        }
      }
      return new IrThrow(expression);
    }
    
    public Object visit(LocalVariableDeclaration ast, LocalContext context) {
      Expression initializer = ast.getInit();
      LocalContext localContext = ((LocalContext)context);
      ClosureLocalBinding 
        binding = localContext.lookupOnlyCurrentScope(ast.getName());
      if(binding != null){
        report(
          DUPLICATE_LOCAL_VARIABLE, ast,
          new Object[]{ast.getName()});
        return new IrNOP();
      }
      TypeRef leftType = CodeAnalysis.this.resolve(ast.getType());
      if(leftType == null) return new IrNOP();
      int index = localContext.addEntry(ast.getName(), leftType);
      IrLocalSet node;
      if(initializer != null){
        IrExpression valueNode = typeCheck(initializer, context);
        if(valueNode == null) return new IrNOP();
        valueNode = processAssignable(initializer, leftType, valueNode);
        if(valueNode == null) return new IrNOP();
        node = new IrLocalSet(0, index, leftType, valueNode);
      }else{
        node = new IrLocalSet(0, index, leftType, defaultValue(leftType));
      }
      return new IrExpStmt(node);
    }
    
    public IrExpression defaultValue(TypeRef type) {
      return IrExpression.defaultValue(type);
    }
    
    public Object visit(EmptyStatement ast, LocalContext context) {
      return new IrNOP();
    }
    
    public Object visit(TryStatement ast, LocalContext context) {
      LocalContext local = context;   
      IrStatement tryStatement = translate(ast.getTryBlock(), context);
      ClosureLocalBinding[] binds = new ClosureLocalBinding[ast.getArguments().length];
      IrStatement[] catchBlocks = new IrStatement[ast.getArguments().length];
      for(int i = 0; i < ast.getArguments().length; i++){
        local.openScope();
        TypeRef arg = (TypeRef)accept(ast.getArguments()[i], context);
        TypeRef expected = load("java.lang.Throwable");
        if(!TypeRules.isSuperType(expected, arg)){
          report(INCOMPATIBLE_TYPE, ast.getArguments()[i], new Object[]{expected, arg});
        }
        binds[i] = local.lookupOnlyCurrentScope(ast.getArguments()[i].getName());
        catchBlocks[i] = translate(ast.getRecBlocks()[i], context);
        local.closeScope();
      }
      return new IrTry(tryStatement, binds, catchBlocks);
    }
    
    public Object visit(SynchronizedStatement ast, LocalContext context) {
      IrExpression lock = typeCheck(ast.getTarget(), context);
      IrStatement block = translate(ast.getBlock(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return new IrSynchronized(lock, block);
    }
    
    public Object visit(BreakStatement ast, LocalContext context) {
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return new IrBreak();
    }
    
    public Object visit(ContinueStatement ast, LocalContext context) {
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return new IrContinue();
    }
  //-------------------------------------------------------------------------//
    
  //----------------------------- members ----------------------------------------//  
    public Object visit(FunctionDeclaration ast, LocalContext context) {
      IrMethod function = (IrMethod) lookupKernelNode(ast);
      if(function == null) return null;
      LocalContext local = new LocalContext();
      if(Modifier.isStatic(function.getModifier())){
        local.setStatic(true);
      }
      local.setMethod(function);
      TypeRef[] arguments = function.getArguments();
      for(int i = 0; i < arguments.length; i++){
        local.addEntry(ast.getArguments()[i].getName(), arguments[i]);
      }
      IrBlock block = (IrBlock) accept(ast.getBlock(), local);
      block = addReturnNode(block, function.getReturnType());
      function.setBlock(block);
      function.setFrame(local.getContextFrame());
      return null;
    }
    
    public IrBlock addReturnNode(IrStatement node, TypeRef returnType) {
      return new IrBlock(node, new IrReturn(defaultValue(returnType)));
    }
    
    public Object visit(GlobalVariableDeclaration ast, LocalContext context) {
      return null;
    }
    
    public Object visit(FieldDeclaration ast, LocalContext context) {
      return null;
    }
    
    public Object visit(DelegationDeclaration ast, LocalContext context) {
      return null;
    }
    
    public Object visit(InterfaceMethodDeclaration ast, LocalContext context) {
      return null;
    }
    
    public Object visit(MethodDeclaration ast, LocalContext context) {
      IrMethod method = (IrMethod) lookupKernelNode(ast);
      if(method == null) return null;
      if(ast.getBlock() == null) return null;
      LocalContext local = new LocalContext();
      if(Modifier.isStatic(method.getModifier())){
        local.setStatic(true);
      }
      local.setMethod(method);
      TypeRef[] arguments = method.getArguments();
      for(int i = 0; i < arguments.length; i++){
        local.addEntry(ast.getArguments()[i].getName(), arguments[i]);
      }    
      IrBlock block = (IrBlock) accept(ast.getBlock(), local);
      block = addReturnNode(block, method.getReturnType());
      method.setBlock(block);
      method.setFrame(local.getContextFrame());
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, LocalContext context) {
      IrConstructor constructor = (IrConstructor) lookupKernelNode(ast);
      if(constructor == null) return null;
      LocalContext local = new LocalContext();
      local.setConstructor(constructor);
      TypeRef[] args = constructor.getArgs();
      for(int i = 0; i < args.length; i++){
        local.addEntry(ast.getArguments()[i].getName(), args[i]);
      }
      IrExpression[] params = typeCheckExps(ast.getInitializers(), local);
      IrBlock block = (IrBlock) accept(ast.getBody(), local);
      IrClass currentClass = getContextClass();
      ClassSymbol superClass = currentClass.getSuperClass();
      ConstructorSymbol[] matched = superClass.findConstructor(params);
      if(matched.length == 0){
        report(CONSTRUCTOR_NOT_FOUND, ast, new Object[]{superClass, types(params)});
        return null;
      }
      if(matched.length > 1){
        report(
          AMBIGUOUS_CONSTRUCTOR, ast,
          new Object[]{
            new Object[]{superClass, types(params)},
            new Object[]{superClass, types(params)}
          });
        return null;
      }
      IrSuper init = new IrSuper(
        superClass, matched[0].getArgs(), params
      );
      constructor.setSuperInitializer(init);
      block = addReturnNode(block, BasicTypeRef.VOID);
      constructor.setBlock(block);
      constructor.setFrame(local.getContextFrame());
      return null;
    }
  //----------------------------------------------------------------------------// 
  //----------------------------------------------------------------------------//
  
    public TypeRef resolve(TypeSpec type, NameResolution resolver) {
      TypeRef resolvedType = (TypeRef) resolver.resolve(type);
      if(resolvedType == null){
        report(CLASS_NOT_FOUND, type, new Object[]{type.getComponentName()});      
      }
      return resolvedType;
    }
    
    TypeRef promote(TypeRef left,  TypeRef right) {
      if(!numeric(left) || !numeric(right)) return null;
      if(left == BasicTypeRef.DOUBLE || right == BasicTypeRef.DOUBLE){
        return BasicTypeRef.DOUBLE;
      }
      if(left == BasicTypeRef.FLOAT || right == BasicTypeRef.FLOAT){
        return BasicTypeRef.FLOAT;
      }
      if(left == BasicTypeRef.LONG || right == BasicTypeRef.LONG){
        return BasicTypeRef.LONG;
      }
      return BasicTypeRef.INT;
    }
    
    boolean hasNumericType(IrExpression expression){
      return numeric(expression.type());
    }
    
    boolean numeric(TypeRef symbol){
      return 
      (symbol.isBasicType()) &&
      (symbol == BasicTypeRef.BYTE   || 
       symbol == BasicTypeRef.SHORT  ||
       symbol == BasicTypeRef.CHAR   || 
       symbol == BasicTypeRef.INT    || 
       symbol == BasicTypeRef.LONG   || 
       symbol == BasicTypeRef.FLOAT  ||
       symbol == BasicTypeRef.DOUBLE
      );
    }
    
    IrExpression typeCheck(Expression expression, LocalContext context){
      return (IrExpression) expression.accept(this, context);
    }
      
    IrStatement translate(Statement statement, LocalContext context){
      return (IrStatement) statement.accept(this, context);
    }
  }
  
  public CodeAnalysis(CompilerConfig conf) {
    this.conf     = conf;
    this.table    = new ClassTable(classpath(this.conf.getClassPath()));
    this.irt2ast  = new HashMap();
    this.ast2irt  = new HashMap();
    this.solvers  = new HashMap();
    this.reporter = new SemanticErrorReporter(this.conf.getMaxErrorReports()); 
  }
  
  public IrClass[] process(CompilationUnit[] units){
    ClassTableBuilder builder = new ClassTableBuilder();
    for(int i = 0; i < units.length; i++){
      builder.process(units[i]);
    }
    TypeHeaderAnalysis analysis = new TypeHeaderAnalysis();
    for(int i = 0; i < units.length; i++){
      analysis.process(units[i]);
    }
    TypeChecker checker = new TypeChecker();
    for(int i = 0; i < units.length; i++){
      checker.process(units[i]);
    }
    DuplicationChecker duplicationChecker = new DuplicationChecker();
    for(int i = 0; i < units.length; i++){
      duplicationChecker.process(units[i]);
    }
    CompileError[] problems = getProblems();
    if(problems.length > 0){
      throw new CompilationException(Arrays.asList(problems));
    }
    return getSourceClasses();
  }
}
