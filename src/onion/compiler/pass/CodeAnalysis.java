package onion.compiler.pass;

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
import onion.compiler.IxCode;
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
import onion.compiler.error.CompilationException;
import onion.compiler.error.CompileError;
import onion.compiler.error.SemanticErrorReporter;
import onion.compiler.util.Boxing;
import onion.compiler.util.Classes;
import onion.compiler.util.Paths;
import onion.compiler.util.Systems;
import onion.lang.syntax.AccessSection;
import onion.lang.syntax.Addition;
import onion.lang.syntax.AdditionAssignment;
import onion.lang.syntax.Argument;
import onion.lang.syntax.Assignment;
import onion.lang.syntax.AstNode;
import onion.lang.syntax.BinaryExpression;
import onion.lang.syntax.BitAnd;
import onion.lang.syntax.BitOr;
import onion.lang.syntax.BlockStatement;
import onion.lang.syntax.BooleanLiteral;
import onion.lang.syntax.BreakStatement;
import onion.lang.syntax.CaseBranch;
import onion.lang.syntax.Cast;
import onion.lang.syntax.CharacterLiteral;
import onion.lang.syntax.ClassDeclaration;
import onion.lang.syntax.ClosureExpression;
import onion.lang.syntax.CompilationUnit;
import onion.lang.syntax.CondStatement;
import onion.lang.syntax.ConstructorDeclaration;
import onion.lang.syntax.ContinueStatement;
import onion.lang.syntax.CurrentInstance;
import onion.lang.syntax.DelegationDeclaration;
import onion.lang.syntax.Division;
import onion.lang.syntax.DivisionAssignment;
import onion.lang.syntax.DoubleLiteral;
import onion.lang.syntax.Elvis;
import onion.lang.syntax.EmptyStatement;
import onion.lang.syntax.Equal;
import onion.lang.syntax.Expression;
import onion.lang.syntax.ExpressionStatement;
import onion.lang.syntax.FieldDeclaration;
import onion.lang.syntax.FieldOrMethodRef;
import onion.lang.syntax.FloatLiteral;
import onion.lang.syntax.ForStatement;
import onion.lang.syntax.ForeachStatement;
import onion.lang.syntax.FunctionDeclaration;
import onion.lang.syntax.GlobalVariableDeclaration;
import onion.lang.syntax.GreaterOrEqual;
import onion.lang.syntax.GreaterThan;
import onion.lang.syntax.Id;
import onion.lang.syntax.IfStatement;
import onion.lang.syntax.ImportListDeclaration;
import onion.lang.syntax.Indexing;
import onion.lang.syntax.IntegerLiteral;
import onion.lang.syntax.InterfaceDeclaration;
import onion.lang.syntax.InterfaceMethodDeclaration;
import onion.lang.syntax.IsInstance;
import onion.lang.syntax.LessOrEqual;
import onion.lang.syntax.LessThan;
import onion.lang.syntax.ListLiteral;
import onion.lang.syntax.LocalVariableDeclaration;
import onion.lang.syntax.LogicalAnd;
import onion.lang.syntax.LogicalOr;
import onion.lang.syntax.LogicalRightShift;
import onion.lang.syntax.LongLiteral;
import onion.lang.syntax.MathLeftShift;
import onion.lang.syntax.MathRightShift;
import onion.lang.syntax.MemberDeclaration;
import onion.lang.syntax.MethodCall;
import onion.lang.syntax.MethodDeclaration;
import onion.lang.syntax.Modifier;
import onion.lang.syntax.ModuleDeclaration;
import onion.lang.syntax.Modulo;
import onion.lang.syntax.ModuloAssignment;
import onion.lang.syntax.Multiplication;
import onion.lang.syntax.MultiplicationAssignment;
import onion.lang.syntax.Negate;
import onion.lang.syntax.NewArray;
import onion.lang.syntax.NewObject;
import onion.lang.syntax.Not;
import onion.lang.syntax.NotEqual;
import onion.lang.syntax.NullLiteral;
import onion.lang.syntax.Posit;
import onion.lang.syntax.PostDecrement;
import onion.lang.syntax.PostIncrement;
import onion.lang.syntax.ReferenceEqual;
import onion.lang.syntax.ReferenceNotEqual;
import onion.lang.syntax.ReturnStatement;
import onion.lang.syntax.SelectStatement;
import onion.lang.syntax.SelfFieldReference;
import onion.lang.syntax.SelfMethodCall;
import onion.lang.syntax.Statement;
import onion.lang.syntax.StaticIDExpression;
import onion.lang.syntax.StaticMethodCall;
import onion.lang.syntax.StringLiteral;
import onion.lang.syntax.Subtraction;
import onion.lang.syntax.SubtractionAssignment;
import onion.lang.syntax.SuperMethodCall;
import onion.lang.syntax.SynchronizedStatement;
import onion.lang.syntax.ThrowStatement;
import onion.lang.syntax.TopLevelElement;
import onion.lang.syntax.TryStatement;
import onion.lang.syntax.TypeDeclaration;
import onion.lang.syntax.TypeSpec;
import onion.lang.syntax.WhileStatement;
import onion.lang.syntax.XOR;
import onion.lang.syntax.visitor.ASTVisitor;


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
  private IxCode.IrClass contextClass;
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

  public void setContextClass(IxCode.IrClass contextClass){
    this.contextClass = contextClass;
  }
  
  public IxCode.IrClass getContextClass(){
    return contextClass;
  }
  
  public void setAccess(int access){
    this.access = access;
  }
  
  public int getAccess(){
    return access;
  }
//---------------------------------------------------------------------------//
  
  public void put(AstNode astNode, IxCode.IrNode kernelNode){
    ast2irt.put(astNode, kernelNode);
    irt2ast.put(kernelNode, astNode);
  }
  
  public AstNode lookupAST(IxCode.IrNode kernelNode){
    return (AstNode) irt2ast.get(kernelNode);
  }
  
  public IxCode.IrNode lookupKernelNode(AstNode astNode){
    return (IxCode.IrNode) ast2irt.get(astNode);
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

  public IxCode.TypeRef resolve(TypeSpec type, NameResolution resolver) {
    IxCode.TypeRef resolvedType = (IxCode.TypeRef) resolver.resolve(type);
    if(resolvedType == null){
      report(CLASS_NOT_FOUND, type, new Object[]{type.getComponentName()});      
    }
    return resolvedType;
  }
  
  public IxCode.TypeRef resolve(TypeSpec type) {
    return resolve(type, getSolver());
  }

  public void report(int error, AstNode node, Object[] items) {
    reporter.setSourceFile(unit.getSourceFileName());
    reporter.report(error, node.getLocation(), items);
  }

  public static IxCode.TypeRef promoteNumericTypes(IxCode.TypeRef left,  IxCode.TypeRef right) {
    if(!numeric(left) || !numeric(right)) return null;
    if(left == IxCode.BasicTypeRef.DOUBLE || right == IxCode.BasicTypeRef.DOUBLE){
      return IxCode.BasicTypeRef.DOUBLE;
    }
    if(left == IxCode.BasicTypeRef.FLOAT || right == IxCode.BasicTypeRef.FLOAT){
      return IxCode.BasicTypeRef.FLOAT;
    }
    if(left == IxCode.BasicTypeRef.LONG || right == IxCode.BasicTypeRef.LONG){
      return IxCode.BasicTypeRef.LONG;
    }
    return IxCode.BasicTypeRef.INT;
  }
  
  public static boolean hasNumericType(IxCode.IrExpression expression) {
    return numeric(expression.type());
  }
  
  private static boolean numeric(IxCode.TypeRef symbol) {
    return 
    (symbol.isBasicType()) &&
    ( symbol == IxCode.BasicTypeRef.BYTE || symbol == IxCode.BasicTypeRef.SHORT ||
      symbol == IxCode.BasicTypeRef.INT || symbol == IxCode.BasicTypeRef.LONG ||
      symbol == IxCode.BasicTypeRef.FLOAT || symbol == IxCode.BasicTypeRef.DOUBLE);
  }
  
  public IxCode.ClassSymbol load(String name) {
    return table.load(name);
  }
  
  public IxCode.ClassSymbol loadTopClass() {
    return table.load(topClass());
  }
  
  public IxCode.ArraySymbol loadArray(IxCode.TypeRef type, int dimension) {
    return table.loadArray(type, dimension);
  }
  
  public IxCode.ClassSymbol rootClass() {
    return table.rootClass();
  }
  
  public CompileError[] getProblems() {
    return reporter.getProblems();
  }
  
  public IxCode.IrClass[] getSourceClasses() {
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
        IxCode.IrClass node = IxCode.IrClass.newClass(0, topClass(), table.rootClass(), new IxCode.ClassSymbol[0]);
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
      IxCode.IrClass node = IxCode.IrClass.newClass(ast.getModifier(), createFQCN(module, ast.getName()));
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
      IxCode.IrClass node = IxCode.IrClass.newInterface(ast.getModifier(), createFQCN(module, ast.getName()), null);
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
      IxCode.IrClass node = (IxCode.IrClass) lookupKernelNode(ast);
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
      IxCode.IrClass node = (IxCode.IrClass) lookupKernelNode(ast);
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
      IxCode.TypeRef type = resolve(ast.getType());
      if(type == null) return null;  
      if(!(type.isObjectType() && ((IxCode.ObjectTypeRef)type).isInterface())){
        report(INTERFACE_REQUIRED, ast.getType(), new Object[]{type});
        return null;
      }
      IxCode.IrClass contextClass = getContextClass();
      int modifier = ast.getModifier() | getAccess() | Modifier.FORWARDED;
      String name = ast.getName();
      IxCode.IrField node = new IxCode.IrField(modifier, contextClass, name, type);
      put(ast, node);
      contextClass.addField(node);
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, Void context) {
      countConstructor++;
      IxCode.TypeRef[] args = acceptTypes(ast.getArguments());
      IxCode.IrClass contextClass = getContextClass();
      if(args == null) return null;
      int modifier = ast.getModifier() | getAccess();
      IxCode.IrConstructor node = new IxCode.IrConstructor(modifier, contextClass, args, null, null);
      put(ast, node);
      contextClass.addConstructor(node);
      return null;
    }

    public Object visit(MethodDeclaration ast, Void context) {
      IxCode.TypeRef[] args = acceptTypes(ast.getArguments());
      IxCode.TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = IxCode.BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      IxCode.IrClass contextClass = getContextClass();
      int modifier = ast.getModifier() | getAccess();
      if(ast.getBlock() == null) modifier |= Modifier.ABSTRACT;
      String name = ast.getName();    
      IxCode.IrMethod node = new IxCode.IrMethod(modifier, contextClass, name, args, returnType, null);
      put(ast, node);
      contextClass.addMethod(node);
      return null;
    }
    
    public Object visit(FieldDeclaration ast, Void context) {
      IxCode.TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      IxCode.IrClass contextClass = getContextClass();
      int modifier = ast.getModifier() | getAccess();
      String name = ast.getName();    
      IxCode.IrField node = new IxCode.IrField(modifier, contextClass, name, type);
      put(ast, node);
      contextClass.addField(node);
      return node;
    }
    
    private IxCode.TypeRef[] acceptTypes(Argument[] ast){
      IxCode.TypeRef[] types = new IxCode.TypeRef[ast.length];
      boolean success = true;
      for (int i = 0; i < ast.length; i++) {
        types[i] = (IxCode.TypeRef) accept(ast[i]);
        if(types[i] == null) success = false;
      }
      if(success){
        return types;
      }else{
        return null;
      }
    }
    
    public Object visit(Argument ast, Void context){
      IxCode.TypeRef type = resolve(ast.getType());
      return type;
    }
    
    private IxCode.IrField createFieldNode(FieldDeclaration ast){
      IxCode.TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      IxCode.IrField node = new IxCode.IrField(
        ast.getModifier() | getAccess(), getContextClass(), 
        ast.getName(), type);
      return node;
    }
      
    public Object visit(InterfaceMethodDeclaration ast, Void context) {
      IxCode.TypeRef[] args = acceptTypes(ast.getArguments());
      IxCode.TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = IxCode.BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      int modifier = Modifier.PUBLIC | Modifier.ABSTRACT;
      IxCode.IrClass classType = getContextClass();
      String name = ast.getName();    
      IxCode.IrMethod node =
        new IxCode.IrMethod(modifier, classType, name, args, returnType, null);
      put(ast, node);
      classType.addMethod(node);
      return null;
    }
    
    public Object visit(FunctionDeclaration ast, Void context) {
      IxCode.TypeRef[] args = acceptTypes(ast.getArguments());
      IxCode.TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = IxCode.BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      IxCode.IrClass classType = (IxCode.IrClass) loadTopClass();
      int modifier = ast.getModifier() | Modifier.PUBLIC;
      String name = ast.getName();
      
      IxCode.IrMethod node =
        new IxCode.IrMethod(modifier, classType, name, args, returnType, null);
      put(ast, node);
      classType.addMethod(node);
      return null;
    }
    
    public Object visit(GlobalVariableDeclaration ast, Void context) {
      IxCode.TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      
      int modifier = ast.getModifier() | Modifier.PUBLIC;
      IxCode.IrClass classType = (IxCode.IrClass)loadTopClass();
      String name = ast.getName();
      
      IxCode.IrField node = new IxCode.IrField(modifier, classType, name, type);
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
    
    public boolean hasCyclicity(IxCode.IrClass start){
      return hasCylicitySub(start, new HashSet());
    }
    
    private boolean hasCylicitySub(IxCode.ClassSymbol symbol, HashSet visit){
      if(symbol == null) return false;
      if(visit.contains(symbol)){
        return true;      
      }
      visit.add(symbol);
      if(hasCylicitySub(symbol.getSuperClass(), (HashSet)visit.clone())){
        return true;      
      }
      IxCode.ClassSymbol[] interfaces = symbol.getInterfaces();
      for (int i = 0; i < interfaces.length; i++) {
        if(hasCylicitySub(interfaces[i], (HashSet)visit.clone())){
          return true;        
        }
      }
      return false;
    }
  
    private void constructTypeHierarchy(IxCode.ClassSymbol symbol, List visit) {
      if(symbol == null || visit.indexOf(symbol) >= 0) return;
      visit.add(symbol);
      if(symbol instanceof IxCode.IrClass){
        IxCode.IrClass node = (IxCode.IrClass) symbol;
        if(node.isResolutionComplete()) return;
        IxCode.ClassSymbol superClass = null;
        List interfaces = new ArrayList();
        NameResolution resolver = findSolver(node.getName());
        if(node.isInterface()){
          InterfaceDeclaration ast = (InterfaceDeclaration) lookupAST(node);
          superClass = rootClass();
          TypeSpec[] typeSpecifiers = ast.getInterfaces();
          for(int i = 0; i < typeSpecifiers.length; i++){
            IxCode.ClassSymbol superType = validateSuperType(typeSpecifiers[i], true, resolver);
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
            IxCode.ClassSymbol superType = validateSuperType(typeSpecifiers[i], true, resolver);
            if(superType != null){
              interfaces.add(superType);
            }
          }
        }
        constructTypeHierarchy(superClass, visit);
        for(Iterator i = interfaces.iterator(); i.hasNext();){
          IxCode.ClassSymbol superType = (IxCode.ClassSymbol) i.next();
          constructTypeHierarchy(superType, visit);
        }
        node.setSuperClass(superClass);
        node.setInterfaces((IxCode.ClassSymbol[]) interfaces.toArray(new IxCode.ClassSymbol[0]));
        node.setResolutionComplete(true);
      }else{
        constructTypeHierarchy(symbol.getSuperClass(), visit);
        IxCode.ClassSymbol[] interfaces = symbol.getInterfaces();
        for(int i = 0; i < interfaces.length; i++){
          constructTypeHierarchy(interfaces[i], visit);
        }
      }
    }
    
    private IxCode.ClassSymbol validateSuperType(
      TypeSpec ast, boolean shouldInterface, NameResolution resolver){
      
      IxCode.ClassSymbol symbol = null;
      if(ast == null){
        symbol = getTable().rootClass();
      }else{
        symbol = (IxCode.ClassSymbol) resolve(ast, resolver);
      }
      if(symbol == null) return null;
      boolean isInterface = symbol.isInterface();
      if(((!isInterface) && shouldInterface) || (isInterface && (!shouldInterface))){
        AstNode astNode = null;
        if(symbol instanceof IxCode.IrClass){
          astNode = lookupAST((IxCode.IrClass)symbol);
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
      this.methods      = new TreeSet(new IxCode.MethodSymbolComparator());
      this.fields       = new TreeSet(new IxCode.FieldSymbolComparator());
      this.constructors = new TreeSet(new IxCode.ConstructorSymbolComparator());
      this.variables    = new TreeSet(new IxCode.FieldSymbolComparator());
      this.functions    = new TreeSet(new IxCode.MethodSymbolComparator());
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
      IxCode.IrClass node = (IxCode.IrClass) lookupKernelNode(ast);
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
      Set generated = new TreeSet(new IxCode.MethodSymbolComparator());
      Set methodSet = new TreeSet(new IxCode.MethodSymbolComparator());
      for(Iterator i = fields.iterator(); i.hasNext();){
        IxCode.IrField node = (IxCode.IrField)i.next();
        if(Modifier.isForwarded(node.getModifier())){
          generateDelegationMethods(node ,generated, methodSet);
        }
      }
    }
    
    private void generateDelegationMethods(IxCode.IrField node, Set generated, Set methodSet){
      IxCode.ClassSymbol type = (IxCode.ClassSymbol) node.getType();
      Set src = Classes.getInterfaceMethods(type);
      for (Iterator i = src.iterator(); i.hasNext();) {
        IxCode.MethodSymbol method = (IxCode.MethodSymbol) i.next();
        if(methodSet.contains(method)) continue;
        if(generated.contains(method)){
          report(
            DUPLICATE_GENERATED_METHOD, lookupAST(node),
            new Object[]{
              method.getClassType(), method.getName(), method.getArguments()
            });
          continue;
        }
        IxCode.IrMethod generatedMethod = createEmptyMethod(node, method);
        generated.add(generatedMethod);
        getContextClass().addMethod(generatedMethod);
      }
    }
    
    private IxCode.IrMethod createEmptyMethod(IxCode.FieldSymbol field, IxCode.MethodSymbol method){
      IxCode.IrExpression target;
      target = new IxCode.IrFieldRef(new IxCode.IrThis(getContextClass()), field);
      IxCode.TypeRef[] args = method.getArguments();
      IxCode.IrExpression[] params = new IxCode.IrExpression[args.length];
      LocalFrame frame = new LocalFrame(null);
      for(int i = 0; i < params.length; i++){
        int index = frame.addEntry("arg" + i, args[i]);
        params[i] = new IxCode.IrLocalRef(new ClosureLocalBinding(0, index, args[i]));
      }
      target = new IxCode.IrCall(target, method, params);
      IxCode.IrBlock statement;
      if(method.getReturnType() != IxCode.BasicTypeRef.VOID){
        statement = new IxCode.IrBlock(new IxCode.IrReturn(target));
      }else{
        statement = new IxCode.IrBlock(new IxCode.IrExpStmt(target), new IxCode.IrReturn(null));
      }
      IxCode.IrMethod node = new IxCode.IrMethod(
        Modifier.PUBLIC, getContextClass(), method.getName(),
        method.getArguments(), method.getReturnType(), statement
      );
      node.setFrame(frame);
      return node;
    }
      
    public Object visit(InterfaceDeclaration ast, Void context) {
      IxCode.IrClass node = (IxCode.IrClass) lookupKernelNode(ast);
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
      IxCode.IrConstructor node = (IxCode.IrConstructor) lookupKernelNode(ast);
      if(node == null) return null;
      if(constructors.contains(node)){
        IxCode.ClassSymbol classType = node.getClassType();
        IxCode.TypeRef[] args = node.getArgs();
        report(DUPLICATE_CONSTRUCTOR, ast, new Object[]{classType, args});
      }else{
        constructors.add(node);
      }
      return null;
    }
    
    public Object visit(DelegationDeclaration ast, Void context) {
      IxCode.IrField node = (IxCode.IrField) lookupKernelNode(ast);
      if(node == null) return null;
      if(fields.contains(node)){
        IxCode.ClassSymbol classType = node.getClassType();
        String name = node.getName();
        report(DUPLICATE_FIELD, ast, new Object[]{classType, name});
      }else{
        fields.add(node);
      }
      return null;
    }
    
    public Object visit(MethodDeclaration ast, Void context) {
      IxCode.IrMethod node = (IxCode.IrMethod) lookupKernelNode(ast);
      if(node == null) return null;
      if(methods.contains(node)){
        IxCode.ClassSymbol classType = node.getClassType();
        String name = node.getName();
        IxCode.TypeRef[] args = node.getArguments();
        report(DUPLICATE_METHOD, ast, new Object[]{classType, name, args});
      }else{
        methods.add(node);
      }
      return null;
    }
    
    public Object visit(FieldDeclaration ast, Void context) {
      IxCode.IrField node = (IxCode.IrField) lookupKernelNode(ast);
      if(node == null) return null;
      if(fields.contains(node)){
        IxCode.ClassSymbol classType = node.getClassType();
        String name = node.getName();
        report(DUPLICATE_FIELD, ast, new Object[]{classType, name});
      }else{
        fields.add(node);
      }
      return null;
    }
    
    public Object visit(InterfaceMethodDeclaration ast, Void context) {
      IxCode.IrMethod node = (IxCode.IrMethod) lookupKernelNode(ast);
      if(node == null) return null;
      if(methods.contains(node)){
        IxCode.ClassSymbol classType = node.getClassType();
        String name = node.getName();
        IxCode.TypeRef[] args = node.getArguments();
        report(DUPLICATE_METHOD, ast, new Object[]{classType, name, args});
      }else{
        methods.add(node);
      }
      return null;
    }
    
    public Object visit(FunctionDeclaration ast, Void context) {
      IxCode.IrMethod node = (IxCode.IrMethod) lookupKernelNode(ast);
      if(node == null) return null;
      if(functions.contains(node)){
        String name = node.getName();
        IxCode.TypeRef[] args = node.getArguments();
        report(DUPLICATE_FUNCTION, ast, new Object[]{name, args});
      }else{
        functions.add(node);
      }
      return null;
    }
    
    public Object visit(GlobalVariableDeclaration ast, Void context) {
      IxCode.IrField node = (IxCode.IrField) lookupKernelNode(ast);
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
  implements IxCode.IrBinExp.Constants, IxCode.IrUnaryExp.Constants {
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
      IxCode.IrClass klass = (IxCode.IrClass) loadTopClass();
      IxCode.ArraySymbol argsType = loadArray(load("java.lang.String"), 1);
      
      IxCode.IrMethod method = new IxCode.IrMethod(
        Modifier.PUBLIC, klass, 
        "start", new IxCode.TypeRef[]{argsType}, IxCode.BasicTypeRef.VOID, null
      );
      context.addEntry("args", argsType);
      for(int i = 0; i < toplevels.length; i++){
        TopLevelElement element = toplevels[i];
        if(!(element instanceof TypeDeclaration)){
          setContextClass(klass);
        }
        if(element instanceof Statement){
          context.setMethod(method);
          IxCode.IrStatement statement = (IxCode.IrStatement) accept(toplevels[i], context);
          statements.add(statement);
        }else{
          accept(toplevels[i], null);
        }
      }    
      
      if(klass != null){
        statements.add(new IxCode.IrReturn(null));
        method.setBlock(new IxCode.IrBlock(statements));
        method.setFrame(context.getContextFrame());
        klass.addMethod(method);      
        klass.addMethod(mainMethod(klass, method, "main", new IxCode.TypeRef[]{argsType}, IxCode.BasicTypeRef.VOID));
      }
      return null;
    }
    
    private IxCode.IrMethod mainMethod(IxCode.ClassSymbol top, IxCode.MethodSymbol ref, String name, IxCode.TypeRef[] args, IxCode.TypeRef ret) {
      IxCode.IrMethod method = new IxCode.IrMethod(
        Modifier.STATIC | Modifier.PUBLIC, top, name, args, ret, null);
      LocalFrame frame = new LocalFrame(null);
      IxCode.IrExpression[] params = new IxCode.IrExpression[args.length];
      for(int i = 0; i < args.length; i++){
        int index = frame.addEntry("args" + i, args[i]);
        params[i] = new IxCode.IrLocalRef(0, index, args[i]);
      }
      method.setFrame(frame);
      IxCode.ConstructorSymbol c = top.findConstructor(new IxCode.IrExpression[0])[0];
      IxCode.IrExpression exp = new IxCode.IrNew(c, new IxCode.IrExpression[0]);
      exp = new IxCode.IrCall(exp, ref, params);
      IxCode.IrBlock block = new IxCode.IrBlock(new IxCode.IrExpStmt(exp));
      block = addReturnNode(block, IxCode.BasicTypeRef.VOID);
      method.setBlock(block);
      return method;
    }
    
    public Object visit(InterfaceDeclaration ast, LocalContext context) {
      setContextClass((IxCode.IrClass) lookupKernelNode(ast));
      return null;
    }
    
    public Object visit(ClassDeclaration ast, LocalContext context) {
      setContextClass((IxCode.IrClass) lookupKernelNode(ast));
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
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if(left.isBasicType() && right.isBasicType()){
        return checkNumExp(ADD, ast, left, right, context);
      }
      if(left.isBasicType()){
        if(left.type() == IxCode.BasicTypeRef.VOID){
          report(IS_NOT_BOXABLE_TYPE, ast.getLeft(), new Object[]{left.type()});
          return null;
        }else{
          left = Boxing.boxing(getTable(), left);
        }
      }
      if(right.isBasicType()){
        if(right.type() == IxCode.BasicTypeRef.VOID){
          report(IS_NOT_BOXABLE_TYPE, ast.getRight(), new Object[]{right.type()});
          return null;
        }else{
          right = Boxing.boxing(getTable(), right);
        }
      }
      IxCode.MethodSymbol toString;
      toString = findMethod(ast.getLeft(), (IxCode.ObjectTypeRef)left.type(), "toString");
      left = new IxCode.IrCall(left, toString, new IxCode.IrExpression[0]);
      toString = findMethod(ast.getRight(), (IxCode.ObjectTypeRef)right.type(), "toString");
      right = new IxCode.IrCall(right, toString, new IxCode.IrExpression[0]);
      IxCode.MethodSymbol concat =
        findMethod(ast, (IxCode.ObjectTypeRef)left.type(), "concat", new IxCode.IrExpression[]{right});
      return new IxCode.IrCall(left, concat, new IxCode.IrExpression[]{right});
    }
    
    public Object visit(PostIncrement node, LocalContext context) {
      LocalContext local = (LocalContext)context;
      Expression operand = node.getTarget();
      IxCode.IrExpression irOperand = typeCheck(operand, context);
      if(irOperand == null) return null;
      if((!irOperand.isBasicType()) || !hasNumericType(irOperand)){
        report(INCOMPATIBLE_OPERAND_TYPE, node, new Object[]{node.getSymbol(), new IxCode.TypeRef[]{irOperand.type()}});
        return null;
      }
      int varIndex = local.addEntry(local.newName(), irOperand.type());
      IxCode.IrExpression result = null;
      if(irOperand instanceof IxCode.IrLocalRef){
        IxCode.IrLocalRef ref = (IxCode.IrLocalRef)irOperand;
        result = new IxCode.IrBegin(
          new IxCode.IrLocalSet(0, varIndex, irOperand.type(), irOperand),
          new IxCode.IrLocalSet(
            ref.frame(), ref.index(), ref.type(),
            new IxCode.IrBinExp(
              ADD, irOperand.type(), 
              new IxCode.IrLocalRef(0, varIndex, irOperand.type()),
              new IxCode.IrInt(1)
            )
          ),
          new IxCode.IrLocalRef(0, varIndex, irOperand.type())
        );
      }else{
        report(UNIMPLEMENTED_FEATURE, node, new Object[0]);
      }
      return result;
    }
    
    public Object visit(PostDecrement node, LocalContext context) {
      LocalContext local = (LocalContext)context;
      Expression operand = node.getTarget();
      IxCode.IrExpression irOperand = typeCheck(operand, context);
      if(irOperand == null) return null;
      if((!irOperand.isBasicType()) || !hasNumericType(irOperand)){
        report(INCOMPATIBLE_OPERAND_TYPE, node, new Object[]{node.getSymbol(), new IxCode.TypeRef[]{irOperand.type()}});
        return null;
      }
      int varIndex = local.addEntry(local.newName(), irOperand.type());
      IxCode.IrExpression result = null;
      if(irOperand instanceof IxCode.IrLocalRef){
        IxCode.IrLocalRef ref = (IxCode.IrLocalRef)irOperand;
        result = new IxCode.IrBegin(
          new IxCode.IrLocalSet(0, varIndex, irOperand.type(), irOperand),
          new IxCode.IrLocalSet(
            ref.frame(), ref.index(), ref.type(),
            new IxCode.IrBinExp(
              SUBTRACT, irOperand.type(), 
              new IxCode.IrLocalRef(0, varIndex, irOperand.type()),
              new IxCode.IrInt(1)
            )
          ),
          new IxCode.IrLocalRef(0, varIndex, irOperand.type())
        );
      }else{
        report(UNIMPLEMENTED_FEATURE, node, new Object[0]);
      }
      return result;
    }
    
    @Override
    public Object visit(Elvis ast, LocalContext context) {
      IxCode.IrExpression l = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression r = typeCheck(ast.getRight(), context);
      if(l.isBasicType() || r.isBasicType() || !IxCode.TypeRules.isAssignable(l.type(), r.type())) {
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{l.type(), r.type()}});
        return null;
      }
      return new IxCode.IrBinExp(ELVIS, l.type(), l, r);
    }
    
    public Object visit(Subtraction ast, LocalContext context) {
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(SUBTRACT, ast, left, right, context);
    }
    
    public Object visit(Multiplication ast, LocalContext context) {
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(MULTIPLY,  ast, left, right, context);
    }
    
    public Object visit(Division ast, LocalContext context) {
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(DIVIDE, ast, left, right, context);
    }
    
    public Object visit(Modulo ast, LocalContext context) {
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
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
      IxCode.IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null){
        return null;
      }
      return new IxCode.IrBinExp(LESS_OR_EQUAL, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LessThan ast, LocalContext context) {
      IxCode.IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.IrBinExp(
        LESS_THAN, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(GreaterOrEqual ast, LocalContext context) {
      IxCode.IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;    
      return new IxCode.IrBinExp(
        GREATER_OR_EQUAL, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(GreaterThan ast, LocalContext context) {
      IxCode.IrExpression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.IrBinExp(
        GREATER_THAN, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LogicalAnd ast, LocalContext context) {
      IxCode.IrExpression[] ops = processLogicalExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.IrBinExp(
        LOGICAL_AND, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LogicalOr ast, LocalContext context) {
      IxCode.IrExpression[] ops = processLogicalExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.IrBinExp(
        LOGICAL_OR, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
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
    
    IxCode.IrExpression[] processLogicalExpression(BinaryExpression ast, LocalContext context){
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type(), rightType = right.type();
      if((leftType != IxCode.BasicTypeRef.BOOLEAN) || (rightType != IxCode.BasicTypeRef.BOOLEAN)){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()}});
        return null;
      }
      return new IxCode.IrExpression[]{left, right};
    }
    
    IxCode.IrExpression processShiftExpression(
      int kind, BinaryExpression ast, LocalContext context){
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if(!left.type().isBasicType()){
        IxCode.IrExpression[] params = new IxCode.IrExpression[]{right};
        Pair<Boolean, IxCode.MethodSymbol> result = tryFindMethod(ast, (IxCode.ObjectTypeRef)left.type(), "add", params);
        if(result._2 == null){
          report(METHOD_NOT_FOUND, ast, new Object[]{left.type(), "add", types(params)});
          return null;
        }
        return new IxCode.IrCall(left, result._2, params);
      }
      if(!right.type().isBasicType()){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()}});
        return null;
      }
      IxCode.BasicTypeRef leftType = (IxCode.BasicTypeRef)left.type();
      IxCode.BasicTypeRef rightType = (IxCode.BasicTypeRef)right.type();
      if((!leftType.isInteger()) || (!rightType.isInteger())){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()}});
        return null;
      }
      IxCode.TypeRef leftResultType = promoteInteger(leftType);
      if(leftResultType != leftType){
        left = new IxCode.IrCast(left, leftResultType);
      }
      if(rightType != IxCode.BasicTypeRef.INT){
        right = new IxCode.IrCast(right, IxCode.BasicTypeRef.INT);
      }
      return new IxCode.IrBinExp(kind, IxCode.BasicTypeRef.BOOLEAN, left, right);
    }
    
    IxCode.TypeRef promoteInteger(IxCode.TypeRef type){
      if(type == IxCode.BasicTypeRef.BYTE || type == IxCode.BasicTypeRef.SHORT ||
         type == IxCode.BasicTypeRef.CHAR || type == IxCode.BasicTypeRef.INT){
        return IxCode.BasicTypeRef.INT;
      }
      if(type == IxCode.BasicTypeRef.LONG){
        return IxCode.BasicTypeRef.LONG;
      }
      return null;
    }  
      
    IxCode.IrExpression checkBitExp(int kind, BinaryExpression ast, LocalContext context){
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if((!left.isBasicType()) || (!right.isBasicType())){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()}});
        return null;
      }
      IxCode.BasicTypeRef leftType = (IxCode.BasicTypeRef)left.type();
      IxCode.BasicTypeRef rightType = (IxCode.BasicTypeRef)right.type();
      IxCode.TypeRef resultType = null;
      if(leftType.isInteger() && rightType.isInteger()){
        resultType = promote(leftType, rightType);    
      }else if(leftType.isBoolean() && rightType.isBoolean()){
        resultType = IxCode.BasicTypeRef.BOOLEAN;
      }else{
        report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType}});
        return null;
      }
      if(left.type() != resultType){
        left = new IxCode.IrCast(left, resultType);
      }
      if(right.type() != resultType){
        right = new IxCode.IrCast(right, resultType);
      }
      return new IxCode.IrBinExp(kind, resultType, left, right);
    }
    
    IxCode.IrExpression checkNumExp(int kind, BinaryExpression ast, IxCode.IrExpression left, IxCode.IrExpression right, LocalContext context) {
      if((!hasNumericType(left)) || (!hasNumericType(right))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()}});
        return null;
      }
      IxCode.TypeRef resultType = promote(left.type(), right.type());
      if(left.type() != resultType){
        left = new IxCode.IrCast(left, resultType);
      }
      if(right.type() != resultType){
        right = new IxCode.IrCast(right, resultType);
      }
      return new IxCode.IrBinExp(kind, resultType, left, right);
    }
    
    IxCode.IrExpression checkRefEqualsExp(int kind, BinaryExpression ast, LocalContext context){
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type();
      IxCode.TypeRef rightType = right.type();
      if(
        (left.isBasicType() && (!right.isBasicType())) ||
        ((!left.isBasicType()) && (right.isBasicType()))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType}});
        return null;
      }
      if(left.isBasicType() && right.isBasicType()){
        if(hasNumericType(left) && hasNumericType(right)){
          IxCode.TypeRef resultType = promote(leftType, rightType);
          if(resultType != left.type()){
            left = new IxCode.IrCast(left, resultType);
          }
          if(resultType != right.type()){
            right = new IxCode.IrCast(right, resultType);
          }
        }else if(leftType != IxCode.BasicTypeRef.BOOLEAN || rightType != IxCode.BasicTypeRef.BOOLEAN){
          report(INCOMPATIBLE_OPERAND_TYPE, ast, new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType}});
          return null;
        }
      }
      return new IxCode.IrBinExp(kind, IxCode.BasicTypeRef.BOOLEAN, left, right);
    }
    
    IxCode.IrExpression checkEqualExp(int kind, BinaryExpression ast, LocalContext context){
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type(), rightType = right.type();
      if(
        (left.isBasicType() && (!right.isBasicType())) ||
        ((!left.isBasicType()) && (right.isBasicType()))
      ){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType}}
        );
        return null;
      }
      if(left.isBasicType() && right.isBasicType()){      
        if(hasNumericType(left) && hasNumericType(right)){
          IxCode.TypeRef resultType = promote(leftType, rightType);
          if(resultType != left.type()){
            left = new IxCode.IrCast(left, resultType);
          }
          if(resultType != right.type()){
            right = new IxCode.IrCast(right, resultType);
          }
        }else if(leftType != IxCode.BasicTypeRef.BOOLEAN || rightType != IxCode.BasicTypeRef.BOOLEAN){
          report(
            INCOMPATIBLE_OPERAND_TYPE, ast,
            new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType}}
          );
          return null;
        }
      }else if(left.isReferenceType() && right.isReferenceType()){
        return createEquals(kind, left, right);
      }
      return new IxCode.IrBinExp(kind, IxCode.BasicTypeRef.BOOLEAN, left, right);
    }
    
    IxCode.IrExpression createEquals(int kind, IxCode.IrExpression left, IxCode.IrExpression right){
      right = new IxCode.IrCast(right, rootClass());
      IxCode.IrExpression[] params = {right};
      IxCode.ObjectTypeRef target = (IxCode.ObjectTypeRef) left.type();
      IxCode.MethodSymbol[] methods = target.findMethod("equals", params);
      IxCode.IrExpression node = new IxCode.IrCall(left, methods[0], params);
      if(kind == IxCode.IrBinExp.Constants.NOT_EQUAL){
        node = new IxCode.IrUnaryExp(NOT, IxCode.BasicTypeRef.BOOLEAN, node);
      }
      return node;
    }
    
    IxCode.IrExpression[] processComparableExpression(BinaryExpression ast, LocalContext context) {
      IxCode.IrExpression left = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type(), rightType = right.type();
      if((!numeric(left.type())) || (!numeric(right.type()))){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()}});
        return null;
      }
      IxCode.TypeRef resultType = promote(leftType, rightType);
      if(leftType != resultType){
        left = new IxCode.IrCast(left, resultType);
      }
      if(rightType != resultType){
        right = new IxCode.IrCast(right, resultType);
      }
      return new IxCode.IrExpression[]{left, right};
    }
  //-------------------------------------------------------------------------------//  
  //------------------------- literals --------------------------------------------//
    public Object visit(FloatLiteral ast, LocalContext context) {
      return new IxCode.IrFloat(ast.getValue());
    }
    
    public Object visit(SuperMethodCall ast, LocalContext context) {
      IxCode.IrExpression[] params;
      params = typeCheckExps(ast.getParams(), context);
      if(params == null) return null;
      IxCode.ClassSymbol contextClass = getContextClass();
      Pair<Boolean, IxCode.MethodSymbol> result = tryFindMethod(ast, contextClass.getSuperClass(), ast.getName(), params);
      if(result._2 == null){
        if(result._1){
          report(
            METHOD_NOT_FOUND, ast, 
            new Object[]{contextClass, ast.getName(), types(params)});
        }
        return null;
      }
      return new IxCode.IrCallSuper(new IxCode.IrThis(contextClass), result._2, params);
    }
    
    public Object visit(DoubleLiteral ast, LocalContext context) {
      return new IxCode.IrDouble(ast.getValue());
    }
    
    public Object visit(IntegerLiteral node, LocalContext context) {
      return new IxCode.IrInt(node.getValue());
    }
    
    public Object visit(CharacterLiteral node, LocalContext context) {
      return new IxCode.IrChar(node.getValue());
    }
    
    public Object visit(LongLiteral ast, LocalContext context) {
      return new IxCode.IrLong(ast.getValue());
    }
    
    public Object visit(BooleanLiteral ast, LocalContext context) {
      return new IxCode.IrBool(ast.getValue());
    }
    
    public Object visit(ListLiteral ast, LocalContext context) {
      IxCode.IrExpression[] elements = new IxCode.IrExpression[ast.size()];
      for(int i = 0; i < ast.size(); i++){
        elements[i] = typeCheck(ast.getExpression(i), context);
      }
      IxCode.IrList node = new IxCode.IrList(elements, load("java.util.List"));
      return node;
    }
    
    public Object visit(StringLiteral ast, LocalContext context) {
      return new IxCode.IrString(ast.getValue(), load("java.lang.String"));
    }  
    
    public Object visit(NullLiteral ast, LocalContext context) {
      return new IxCode.IrNull();
    }
  //-----------------------------------------------------------------------------//
    
  //---------------------------- unary expressions ------------------------------//
    public Object visit(Posit ast, LocalContext context) {
      IxCode.IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(!hasNumericType(node)){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast, 
          new Object[]{"+", new IxCode.TypeRef[]{node.type()}});
        return null;
      }
      node = new IxCode.IrUnaryExp(PLUS, node.type(), node);
      return node;
    }
    
    public Object visit(Negate ast, LocalContext context) {
      IxCode.IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(!hasNumericType(node)){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{"-", new IxCode.TypeRef[]{node.type()}}
        );
        return null;
      }
      node = new IxCode.IrUnaryExp(MINUS, node.type(), node);
      return node;
    }
    
    public Object visit(Not ast, LocalContext context) {
      IxCode.IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(node.type() != IxCode.BasicTypeRef.BOOLEAN){
        report(
          INCOMPATIBLE_OPERAND_TYPE, ast,
          new Object[]{"!", new IxCode.TypeRef[]{node.type()}});
        return null;
      }
      node = new IxCode.IrUnaryExp(NOT, IxCode.BasicTypeRef.BOOLEAN, node);
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
    
    private IxCode.IrExpression processLocalAssign(Assignment ast, LocalContext context){
      IxCode.IrExpression value = typeCheck(ast.getRight(), context);
      if(value == null) return null;
      Id id = (Id) ast.getLeft();
      LocalContext local = ((LocalContext)context);
      ClosureLocalBinding bind = local.lookup(id.getName());
      int frame, index;
      IxCode.TypeRef leftType, rightType = value.type();
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
      return new IxCode.IrLocalSet(frame, index, leftType, value);
    }
    
    private Object processSelfFieldAssign(Assignment ast, LocalContext context){
      IxCode.IrExpression value = typeCheck(ast.getRight(), context);
      if(value == null) return null;
      SelfFieldReference ref = (SelfFieldReference) ast.getLeft();
      LocalContext local = context;
      IxCode.ClassSymbol selfClass;
      if(local.isGlobal()){
        selfClass = loadTopClass();
      }else {
        if(local.getMethod() != null){
          selfClass = local.getMethod().getClassType();
        }else{
          selfClass = local.getConstructor().getClassType();
        }
      }
      IxCode.FieldSymbol field = findField(selfClass, ref.getName());
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
      return new IxCode.IrFieldSet(new IxCode.IrThis(selfClass), field, value);
    }
    
    Object processArrayAssign(Assignment ast, LocalContext context){
      IxCode.IrExpression value = typeCheck(ast.getRight(), context);
      Indexing indexing = (Indexing) ast.getLeft();
      IxCode.IrExpression target = typeCheck(indexing.getLeft(), context);
      IxCode.IrExpression index = typeCheck(indexing.getRight(), context);
      if(value == null || target == null || index == null) return null;
      if(target.isBasicType()){
        report(
          INCOMPATIBLE_TYPE,
          indexing.getLeft(), new Object[]{rootClass(), target.type()});
        return null;
      }
      if(target.isArrayType()){
        IxCode.ArraySymbol targetType = ((IxCode.ArraySymbol)target.type());
        if(!(index.isBasicType() && ((IxCode.BasicTypeRef)index.type()).isInteger())){
          report(
            INCOMPATIBLE_TYPE, 
            indexing.getRight(), new Object[]{IxCode.BasicTypeRef.INT, index.type()});
          return null;
        }
        IxCode.TypeRef base = targetType.getBase();
        value = processAssignable(ast.getRight(), base, value);
        if(value == null) return null;
        return new IxCode.IrArraySet(target, index, value);
      }
      IxCode.IrExpression[] params;
      params = new IxCode.IrExpression[]{index, value};
      Pair<Boolean, IxCode.MethodSymbol> result = tryFindMethod(ast, (IxCode.ObjectTypeRef)target.type(), "set", new IxCode.IrExpression[]{index, value});
      if(result._2 == null){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{target.type(), "set", types(params)});
        return null;
      }
      return new IxCode.IrCall(target, result._2, params);
    }
    
    Object processFieldOrMethodAssign(Assignment ast, LocalContext context){
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(AdditionAssignment ast, LocalContext context) {
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(SubtractionAssignment ast, LocalContext context) {
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(MultiplicationAssignment ast, LocalContext context) {
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);    
      return null;
    }
    
    public Object visit(DivisionAssignment ast, LocalContext context) {
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return null;
    }
    
    public Object visit(ModuloAssignment ast, LocalContext context) {
      IxCode.IrExpression right = typeCheck(ast.getRight(), context);
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
      return new IxCode.IrLocalRef(bind);
    }
    
    IxCode.MethodSymbol findMethod(AstNode ast, IxCode.ObjectTypeRef type, String name) {
      IxCode.IrExpression[] params = new IxCode.IrExpression[0];
      IxCode.MethodSymbol[] methods = type.findMethod(name, params);
      if(methods.length == 0){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{type, name, types(params)});
        return null;
      }
      return methods[0];
    }
    
    IxCode.MethodSymbol findMethod(
      AstNode ast, IxCode.ObjectTypeRef type, String name, IxCode.IrExpression[] params
    ) {
      IxCode.MethodSymbol[] methods = type.findMethod(name, params);
      return methods[0];
    }
    
    public Object visit(CurrentInstance ast, LocalContext context) {
      LocalContext local = context;
      if(local.isStatic()) return null;
      IxCode.ClassSymbol selfClass = getContextClass();
      return new IxCode.IrThis(selfClass);
    }
    
    boolean hasSamePackage(IxCode.ClassSymbol a, IxCode.ClassSymbol b) {
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
    
    boolean isAccessible(IxCode.ClassSymbol target, IxCode.ClassSymbol context) {
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
    
    boolean isAccessible(IxCode.MemberSymbol member, IxCode.ClassSymbol context) {
      IxCode.ClassSymbol targetType = member.getClassType();
      if(targetType == context) return true;
      int modifier = member.getModifier();
      if(IxCode.TypeRules.isSuperType(targetType, context)){
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
    
    private IxCode.FieldSymbol findField(IxCode.ObjectTypeRef target, String name) {
      if(target == null) return null;
      IxCode.FieldSymbol[] fields = target.getFields();
      for (int i = 0; i < fields.length; i++) {
        if(fields[i].getName().equals(name)){
          return fields[i];
        }
      }
      IxCode.FieldSymbol field = findField(target.getSuperClass(), name);
      if(field != null) return field;
      IxCode.ClassSymbol[] interfaces = target.getInterfaces();
      for(int i = 0; i < interfaces.length; i++){
        field = findField(interfaces[i], name);
        if(field != null) return field;
      }
      return null;
    }
    
    private boolean checkAccessible(
      AstNode ast, IxCode.ObjectTypeRef target, IxCode.ClassSymbol context
    ) {
      if(target.isArrayType()){
        IxCode.TypeRef component = ((IxCode.ArraySymbol)target).getComponent();
        if(!component.isBasicType()){
          if(!isAccessible((IxCode.ClassSymbol)component, getContextClass())){
            report(CLASS_NOT_ACCESSIBLE, ast, new Object[]{target, context});
            return false;
          }
        }
      }else{
        if(!isAccessible((IxCode.ClassSymbol)target, context)){
          report(CLASS_NOT_ACCESSIBLE, ast, new Object[]{target, context});
          return false;
        }
      }
      return true;
    }
    
    public Object visit(FieldOrMethodRef ast, LocalContext context) {
      IxCode.IrClass contextClass = getContextClass();
      IxCode.IrExpression target = typeCheck(ast.getTarget(), context);
      if(target == null) return null;
      if(target.type().isBasicType() || target.type().isNullType()){
        report(
          INCOMPATIBLE_TYPE, ast.getTarget(),
          new IxCode.TypeRef[]{rootClass(), target.type()});
        return null;
      }
      IxCode.ObjectTypeRef targetType = (IxCode.ObjectTypeRef) target.type();
      if(!checkAccessible(ast, targetType, contextClass)) return null;
      String name = ast.getName();
      if(target.type().isArrayType()){
        if(name.equals("length") || name.equals("size")){
          return new IxCode.IrArrayLength(target);
        }else{
          return null;
        }
      }
      IxCode.FieldSymbol field = findField(targetType, name);
      if(field != null && isAccessible(field, getContextClass())){
        return new IxCode.IrFieldRef(target, field);
      }
      Pair<Boolean, IxCode.MethodSymbol> result;
      boolean continuable;
      
      result = tryFindMethod(ast, targetType, name, new IxCode.IrExpression[0]);
      if(result._2 != null){
        return new IxCode.IrCall(target, result._2, new IxCode.IrExpression[0]);
      }
      continuable = result._1;
      if(!continuable) return null;
      
      String getterName;
      getterName = getter(name);
      result = tryFindMethod(ast, targetType, getterName, new IxCode.IrExpression[0]);
      if(result._2 != null){
        return new IxCode.IrCall(target, result._2, new IxCode.IrExpression[0]);
      }
      continuable = result._1;
      if(!continuable) return null;
      
      getterName = getterBoolean(name);
      result = tryFindMethod(ast, targetType, getterName, new IxCode.IrExpression[0]);
      if(result._2 != null){
        return new IxCode.IrCall(target, result._2, new IxCode.IrExpression[0]);
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
    
    private Pair<Boolean, IxCode.MethodSymbol> tryFindMethod(
      AstNode ast, IxCode.ObjectTypeRef target, String name, IxCode.IrExpression[] params
    ) {
      IxCode.MethodSymbol[] methods;
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
      IxCode.TypeRef type = resolve(ast.getType(), getSolver());
      if(type == null) return null;
      local.addEntry(name, type);
      return type;
    }
    
    public Object visit(NewArray ast, LocalContext context) {
      IxCode.TypeRef type = resolve(ast.getType(), getSolver());
      IxCode.IrExpression[] parameters = typeCheckExps(ast.getArguments(), context);
      if(type == null || parameters == null) return null;
      IxCode.ArraySymbol resultType = loadArray(type, parameters.length);
      return new IxCode.IrNewArray(resultType, parameters);
    }
      
    public Object visit(Cast ast, LocalContext context) {
      IxCode.IrExpression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      IxCode.TypeRef conversion = resolve(ast.getType(), getSolver());
      if(conversion == null) return null;
      node = new IxCode.IrCast(node, conversion);
      return node;
    }
    
    public boolean equals(IxCode.TypeRef[] types1, IxCode.TypeRef[] types2) {
      if(types1.length != types2.length) return false;
      for(int i = 0; i < types1.length; i++){
        if(types1[i] != types2[i]) return false;
      }
      return true;
    }
    
    public Object visit(ClosureExpression ast, LocalContext context) {
      LocalContext local = ((LocalContext)context);
      IxCode.ClassSymbol type = (IxCode.ClassSymbol) CodeAnalysis.this.resolve(ast.getType());
      Argument[] args = ast.getArguments();
      IxCode.TypeRef[] argTypes = new IxCode.TypeRef[args.length];
      String name = ast.getName();
      try {
        local.openFrame();
        boolean error = false;
        for(int i = 0; i < args.length; i++){
          argTypes[i] = (IxCode.TypeRef)accept(args[i], context);
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
        IxCode.MethodSymbol[] methods = type.getMethods();
        IxCode.MethodSymbol method = null;
        for(int i = 0; i < methods.length; i++){
          IxCode.TypeRef[] types = methods[i].getArguments();
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
        IxCode.IrStatement block = translate(ast.getBlock(), context);
        block = addReturnNode(block, method.getReturnType());
        IxCode.IrClosure node = new IxCode.IrClosure(type, method, block);
        node.setFrame(local.getContextFrame());
        return node;
      }finally{
        local.closeFrame();
      }     
    }
    
    public Object visit(Indexing ast, LocalContext context) {
      IxCode.IrExpression target = typeCheck(ast.getLeft(), context);
      IxCode.IrExpression index = typeCheck(ast.getRight(), context);
      if(target == null || index == null) return null;
      if(target.isArrayType()){
        if(!(index.isBasicType() && ((IxCode.BasicTypeRef)index.type()).isInteger())){
          report(
            INCOMPATIBLE_TYPE, ast, new Object[]{IxCode.BasicTypeRef.INT, index.type()});
          return null;
        }
        return new IxCode.IrArrayRef(target, index);
      }    
      if(target.isBasicType()){
        report(
          INCOMPATIBLE_TYPE,
          ast.getLeft(), new Object[]{rootClass(), target.type()});
        return null;
      }
      if(target.isArrayType()){
        if(!(index.isBasicType() && ((IxCode.BasicTypeRef)index.type()).isInteger())){
          report(
            INCOMPATIBLE_TYPE, 
            ast.getRight(), new Object[]{IxCode.BasicTypeRef.INT, index.type()}
          );
          return null;
        }
        return new IxCode.IrArrayRef(target, index);
      }    
      IxCode.IrExpression[] params = {index};
      Pair<Boolean, IxCode.MethodSymbol> result = tryFindMethod(ast, (IxCode.ObjectTypeRef)target.type(), "get", new IxCode.IrExpression[]{index});
      if(result._2 == null){
        report(
          METHOD_NOT_FOUND, ast, 
          new Object[]{target.type(), "get", types(params)});
        return null;
      }
      return new IxCode.IrCall(target, result._2, params);
    }
    
    public Object visit(SelfFieldReference ast, LocalContext context) {
      LocalContext local = context;
      IxCode.ClassSymbol selfClass = null;
      if(local.isStatic()) return null;
      selfClass = getContextClass();
      IxCode.FieldSymbol field = findField(selfClass, ast.getName());
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
      return new IxCode.IrFieldRef(new IxCode.IrThis(selfClass), field);
    }
    
    public Object visit(NewObject ast, LocalContext context) {
      IxCode.ClassSymbol type = (IxCode.ClassSymbol) CodeAnalysis.this.resolve(ast.getType());
      IxCode.IrExpression[] parameters = typeCheckExps(ast.getArguments(), context);
      if(parameters == null || type == null) return null;
      IxCode.ConstructorSymbol[] constructors = type.findConstructor(parameters);
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
      return new IxCode.IrNew(constructors[0], parameters);
    }
        
    public Object visit(IsInstance ast, LocalContext context) {
      IxCode.IrExpression target = typeCheck(ast.getTarget(), context);
      IxCode.TypeRef checkType = resolve(ast.getType(), getSolver());
      if(target == null || checkType == null) return null;
      return new IxCode.IrInstanceOf(target, checkType);
    }
    
    private IxCode.TypeRef[] types(IxCode.IrExpression[] parameters){
      IxCode.TypeRef[] types = new IxCode.TypeRef[parameters.length];
      for(int i = 0; i < types.length; i++){
        types[i] = parameters[i].type();
      }
      return types;
    }
    
    public Object visit(SelfMethodCall ast, LocalContext context) {
      IxCode.IrExpression[] params = typeCheckExps(ast.getArguments(), context);
      if(params == null) return null;
      IxCode.IrClass targetType = getContextClass();
      String name = ast.getName();
      IxCode.MethodSymbol[] methods = targetType.findMethod(ast.getName(), params);
      
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
        return new IxCode.IrCallStatic(targetType, methods[0], params);
      }else {
        return new IxCode.IrCall(new IxCode.IrThis(targetType), methods[0], params);
      }
    }
    
    private IxCode.IrExpression[] convert(IxCode.TypeRef[] arguments, IxCode.IrExpression[] params){
      for(int i = 0; i < params.length; i++){
        if(arguments[i] != params[i].type()){
          params[i] = new IxCode.IrCast(params[i], arguments[i]);
        }
      }
      return params;
    }
    
    public Object visit(MethodCall ast, LocalContext context) {
      IxCode.IrExpression target = typeCheck(ast.getTarget(), context);
      if(target == null) return null;
      IxCode.IrExpression[] params = typeCheckExps(ast.getArguments(), context);
      if(params == null) return null;
      IxCode.ObjectTypeRef targetType = (IxCode.ObjectTypeRef) target.type();
      final String name = ast.getName();
      IxCode.MethodSymbol[] methods = targetType.findMethod(name, params);
      
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
      return new IxCode.IrCall(target, methods[0], params);
    }
    
    public Object visit(StaticIDExpression ast, LocalContext context) {
      IxCode.ClassSymbol type = (IxCode.ClassSymbol) CodeAnalysis.this.resolve(ast.getType());
      if(type == null) return null;
      IxCode.FieldSymbol field = findField(type, ast.getName());
      if(field == null){
        report(FIELD_NOT_FOUND, ast, new Object[]{type, ast.getName()});
        return null;
      }
      return new IxCode.IrStaticFieldRef(type, field);
    }
    
    public Object visit(StaticMethodCall ast, LocalContext context) {
      IxCode.ClassSymbol type = (IxCode.ClassSymbol) CodeAnalysis.this.resolve(ast.getTarget());
      IxCode.IrExpression[] params = typeCheckExps(ast.getArgs(), context);
      if(type == null) return null;
      if(params == null) return null;
      IxCode.MethodSymbol[] methods = type.findMethod(ast.getName(), params);
      
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
      return new IxCode.IrCallStatic(type, methods[0], params);
    }
      
    private String[] typeNames(IxCode.TypeRef[] types) {
      String[] names = new String[types.length];
      for(int i = 0; i < names.length; i++){
        names[i] = types[i].getName();
      }
      return names;
    }
    
    private IxCode.IrExpression[] typeCheckExps(Expression[] ast, LocalContext context){
      IxCode.IrExpression[] expressions = new IxCode.IrExpression[ast.length];
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
        IxCode.IrExpression collection = typeCheck(collectionAST, context);
        Argument arg = ast.getDeclaration();
        accept(arg, context);
        ClosureLocalBinding bind = local.lookupOnlyCurrentScope(arg.getName());
        IxCode.IrStatement block = translate(ast.getStatement(), context);
        
        if(collection.isBasicType()){
          report(
            INCOMPATIBLE_TYPE, collectionAST,
            new Object[]{load("java.util.Collection"), collection.type()});
          return null;
        }
        ClosureLocalBinding bind2 = new ClosureLocalBinding(
          0, local.addEntry(local.newName(), collection.type()), collection.type());
        IxCode.IrStatement init =
          new IxCode.IrExpStmt(new IxCode.IrLocalSet(bind2, collection));
        if(collection.isArrayType()){
          ClosureLocalBinding bind3 = new ClosureLocalBinding(
            0, local.addEntry(local.newName(), IxCode.BasicTypeRef.INT), IxCode.BasicTypeRef.INT
          );
          init = new IxCode.IrBlock(init, new IxCode.IrExpStmt(new IxCode.IrLocalSet(bind3, new IxCode.IrInt(0))));
          block = new IxCode.IrLoop(
            new IxCode.IrBinExp(
              LESS_THAN, IxCode.BasicTypeRef.BOOLEAN,
              ref(bind3),
              new IxCode.IrArrayLength(ref(bind2))
            ),
            new IxCode.IrBlock(
              assign(bind, indexref(bind2, ref(bind3))),
              block,
              assign(
                bind3, 
                new IxCode.IrBinExp(ADD, IxCode.BasicTypeRef.INT, ref(bind3), new IxCode.IrInt(1))
              )
            )
          );
          return new IxCode.IrBlock(init, block);
        }else{
          IxCode.ObjectTypeRef iterator = load("java.util.Iterator");
          ClosureLocalBinding bind3 = new ClosureLocalBinding(
            0, local.addEntry(local.newName(), iterator), iterator
          );
          IxCode.MethodSymbol mIterator, mNext, mHasNext;
          mIterator = findMethod(collectionAST, (IxCode.ObjectTypeRef) collection.type(), "iterator");
          mNext = findMethod(ast.getCollection(), iterator, "next");
          mHasNext = findMethod(ast.getCollection(), iterator, "hasNext");
          init = new IxCode.IrBlock(
            init,
            assign(bind3, new IxCode.IrCall(ref(bind2), mIterator, new IxCode.IrExpression[0]))
          );
          IxCode.IrExpression callNext = new IxCode.IrCall(
            ref(bind3), mNext, new IxCode.IrExpression[0]
          );
          if(bind.getType() != rootClass()){
            callNext = new IxCode.IrCast(callNext, bind.getType());
          }
          block = new IxCode.IrLoop(
            new IxCode.IrCall(ref(bind3), mHasNext, new IxCode.IrExpression[0]),
            new IxCode.IrBlock(assign(bind, callNext), block)
          );
          return new IxCode.IrBlock(init, block);
        }
      }finally{
        local.closeScope();
      }
    }
    
    private IxCode.IrExpression indexref(ClosureLocalBinding bind, IxCode.IrExpression value) {
      return new IxCode.IrArrayRef(new IxCode.IrLocalRef(bind), value);
    }
    
    private IxCode.IrStatement assign(ClosureLocalBinding bind, IxCode.IrExpression value) {
      return new IxCode.IrExpStmt(new IxCode.IrLocalSet(bind, value));
    }
    
    private IxCode.IrExpression ref(ClosureLocalBinding bind) {
      return new IxCode.IrLocalRef(bind);
    }
    
    public Object visit(ExpressionStatement ast, LocalContext context) {
      IxCode.IrExpression expression = typeCheck(ast.getExpression(), context);
      return new IxCode.IrExpStmt(expression);
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
          IxCode.IrExpression texpr = typeCheck(expr, context);
          if(texpr != null && texpr.type() != IxCode.BasicTypeRef.BOOLEAN){
            IxCode.TypeRef expect = IxCode.BasicTypeRef.BOOLEAN;
            IxCode.TypeRef actual = texpr.type();
            report(INCOMPATIBLE_TYPE, expr, new Object[]{expect, actual});
          }
          exprs.push(texpr);
          IxCode.IrStatement tstmt = translate(stmt, context);
          stmts.push(tstmt);
        }
        
        Statement elseStmt = node.getElseBlock();
        IxCode.IrStatement result = null;
        if(elseStmt != null){
          result = translate(elseStmt, context);
        }
        
        for(int i = 0; i < size; i++){
          IxCode.IrExpression expr = (IxCode.IrExpression)exprs.pop();
          IxCode.IrStatement stmt = (IxCode.IrStatement)stmts.pop();
          result = new IxCode.IrIf(expr, stmt, result);
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
        
        IxCode.IrStatement init = null;
        if(ast.getInit() != null){
          init = translate(ast.getInit(), context);
        }else{
          init = new IxCode.IrNOP();
        }
        IxCode.IrExpression condition;
        Expression astCondition = ast.getCondition();
        if(astCondition != null){
          condition = typeCheck(ast.getCondition(), context);
          IxCode.TypeRef expected = IxCode.BasicTypeRef.BOOLEAN;
          if(condition != null && condition.type() != expected){
            IxCode.TypeRef appeared = condition.type();
            report(INCOMPATIBLE_TYPE, astCondition, new Object[]{expected, appeared});
          }
        }else{
          condition = new IxCode.IrBool(true);
        }
        IxCode.IrExpression update = null;
        if(ast.getUpdate() != null){
          update = typeCheck(ast.getUpdate(), context);
        }
        IxCode.IrStatement loop = translate(
          ast.getBlock(), context);
        if(update != null){
          loop = new IxCode.IrBlock(loop, new IxCode.IrExpStmt(update));
        }
        IxCode.IrStatement result = new IxCode.IrLoop(condition, loop);
        result = new IxCode.IrBlock(init, result);
        return result;
      }finally{
        local.closeScope();
      }
    }
    
    public Object visit(BlockStatement ast, LocalContext context) {
      Statement[] astStatements = ast.getStatements();
      IxCode.IrStatement[] statements = new IxCode.IrStatement[astStatements.length];
      LocalContext local = context;
      try{
        local.openScope();
        for(int i = 0; i < astStatements.length; i++){
          statements[i] = translate(astStatements[i], context);
        }
        return new IxCode.IrBlock(statements);
      }finally{
        local.closeScope();
      }
    }
    
    public Object visit(IfStatement ast, LocalContext context) {
      LocalContext local = ((LocalContext)context);
      try{
        local.openScope();
        
        IxCode.IrExpression condition = typeCheck(ast.getCondition(), context);
        IxCode.TypeRef expected = IxCode.BasicTypeRef.BOOLEAN;
        if(condition != null && condition.type() != expected){
          report(INCOMPATIBLE_TYPE, ast.getCondition(), new Object[]{expected, condition.type()});
        }
        IxCode.IrStatement thenBlock = translate(ast.getThenBlock(), context);
        IxCode.IrStatement elseBlock = ast.getElseBlock() == null ? null : translate(ast.getElseBlock(), context);
        return new IxCode.IrIf(condition, thenBlock, elseBlock);
      }finally{     
        local.closeScope();
      }
    }
    
    public Object visit(WhileStatement ast, LocalContext context) {
      LocalContext local = ((LocalContext)context);
      try{
        local.openScope();
        
        IxCode.IrExpression condition = typeCheck(ast.getCondition(), context);
        IxCode.TypeRef expected = IxCode.BasicTypeRef.BOOLEAN;
        if(condition != null && condition.type() != expected){
          IxCode.TypeRef actual = condition.type();
          report(INCOMPATIBLE_TYPE, ast, new Object[]{expected, actual});
        }
        IxCode.IrStatement thenBlock = translate(ast.getBlock(), context);
        return new IxCode.IrLoop(condition, thenBlock);
      }finally{
        local.closeScope();
      }     
    }
    
    public Object visit(ReturnStatement ast, LocalContext context) {
      IxCode.TypeRef returnType = ((LocalContext)context).getReturnType();
      if(ast.getExpression() == null){
        IxCode.TypeRef expected = IxCode.BasicTypeRef.VOID;
        if(returnType != expected){
          report(CANNOT_RETURN_VALUE, ast, new Object[0]);
        }
        return new IxCode.IrReturn(null);
      }else{
        IxCode.IrExpression returned = typeCheck(ast.getExpression(), context);
        if(returned == null) return new IxCode.IrReturn(null);
        if(returned.type() == IxCode.BasicTypeRef.VOID){
          report(CANNOT_RETURN_VALUE, ast, new Object[0]);
        }else {
          returned = processAssignable(ast.getExpression(), returnType, returned);
          if(returned == null) return new IxCode.IrReturn(null);
        }
        return new IxCode.IrReturn(returned);
      }
    }
    
    IxCode.IrExpression processAssignable(AstNode ast, IxCode.TypeRef a, IxCode.IrExpression b){
      if(b == null) return null;
      if(a == b.type()) return b;
      if(!IxCode.TypeRules.isAssignable(a, b.type())){
        report(INCOMPATIBLE_TYPE, ast, new Object[]{ a, b.type() });
        return null;
      }
      b = new IxCode.IrCast(b, a);
      return b;
    }
    
    public Object visit(SelectStatement ast, LocalContext context) {
      LocalContext local = context;
      try{
        local.openScope();
        IxCode.IrExpression condition = typeCheck(ast.getCondition(), context);
        if(condition == null){
          return new IxCode.IrNOP();
        }
        String name = local.newName();
        int index = local.addEntry(name, condition.type());
        IxCode.IrStatement statement;
        if(ast.getCases().length == 0){
          if(ast.getElseBlock() != null){
            statement = translate(ast.getElseBlock(), context);
          }else{
            statement = new IxCode.IrNOP();
          }
        }else{
          statement = processCases(ast, condition, name, context);
        }
        IxCode.IrBlock block = new IxCode.IrBlock(
          new IxCode.IrExpStmt(new IxCode.IrLocalSet(0, index, condition.type(), condition)),
          statement
        );
        return block;
      }finally{
        local.closeScope();
      }
    }
    
    IxCode.IrStatement processCases(
      SelectStatement ast, IxCode.IrExpression cond, String var, LocalContext context
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
      IxCode.IrStatement statement;
      if(ast.getElseBlock() != null){
        statement = translate(ast.getElseBlock(), context);
      }else{
        statement = null;
      }
      for(int i = cases.length - 1; i >= 0; i--){
        IxCode.IrExpression value = (IxCode.IrExpression) nodes.get(i);
        IxCode.IrStatement then = (IxCode.IrStatement) thens.get(i);
        statement = new IxCode.IrIf(value, then, statement);
      }
      return statement;
    }
    
    IxCode.IrExpression processNodes(
      Expression[] asts, IxCode.TypeRef type, ClosureLocalBinding bind, LocalContext context
    ) {
      IxCode.IrExpression[] nodes = new IxCode.IrExpression[asts.length];
      boolean error = false;
      for(int i = 0; i < asts.length; i++){
        nodes[i] = typeCheck(asts[i], context);
        if(nodes[i] == null){
          error = true;
          continue;
        }
        if(!IxCode.TypeRules.isAssignable(type, nodes[i].type())){
          report(INCOMPATIBLE_TYPE, asts[i], new IxCode.TypeRef[]{type, nodes[i].type()});
          error = true;
          continue;
        }
        if(nodes[i].isBasicType() && nodes[i].type() != type){
          nodes[i] = new IxCode.IrCast(nodes[i], type);
        }
        if(nodes[i].isReferenceType() && nodes[i].type() != rootClass()){
          nodes[i] = new IxCode.IrCast(nodes[i], rootClass());
        }
      }
      if(!error){
        IxCode.IrExpression node;
        if(nodes[0].isReferenceType()){
          node = createEquals(IxCode.IrBinExp.Constants.EQUAL, new IxCode.IrLocalRef(bind), nodes[0]);
        }else{
          node = new IxCode.IrBinExp(EQUAL, IxCode.BasicTypeRef.BOOLEAN, new IxCode.IrLocalRef(bind), nodes[0]);
        }
        for(int i = 1; i < nodes.length; i++){
          node = new IxCode.IrBinExp(
            LOGICAL_OR, IxCode.BasicTypeRef.BOOLEAN, node,
            new IxCode.IrBinExp(EQUAL, IxCode.BasicTypeRef.BOOLEAN, new IxCode.IrLocalRef(bind), nodes[i])
          );
        }
        return node;
      }else{
        return null;
      }
    }
    
    public Object visit(ThrowStatement ast, LocalContext context) {
      IxCode.IrExpression expression = typeCheck(ast.getExpression(), context);
      if(expression != null){
        IxCode.TypeRef expected = load("java.lang.Throwable");
        IxCode.TypeRef detected = expression.type();
        if(!IxCode.TypeRules.isSuperType(expected, detected)){
          report(
            INCOMPATIBLE_TYPE, ast.getExpression(), 
            new Object[]{ expected, detected}
          );
        }
      }
      return new IxCode.IrThrow(expression);
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
        return new IxCode.IrNOP();
      }
      IxCode.TypeRef leftType = CodeAnalysis.this.resolve(ast.getType());
      if(leftType == null) return new IxCode.IrNOP();
      int index = localContext.addEntry(ast.getName(), leftType);
      IxCode.IrLocalSet node;
      if(initializer != null){
        IxCode.IrExpression valueNode = typeCheck(initializer, context);
        if(valueNode == null) return new IxCode.IrNOP();
        valueNode = processAssignable(initializer, leftType, valueNode);
        if(valueNode == null) return new IxCode.IrNOP();
        node = new IxCode.IrLocalSet(0, index, leftType, valueNode);
      }else{
        node = new IxCode.IrLocalSet(0, index, leftType, defaultValue(leftType));
      }
      return new IxCode.IrExpStmt(node);
    }
    
    public IxCode.IrExpression defaultValue(IxCode.TypeRef type) {
      return IxCode.IrExpression.defaultValue(type);
    }
    
    public Object visit(EmptyStatement ast, LocalContext context) {
      return new IxCode.IrNOP();
    }
    
    public Object visit(TryStatement ast, LocalContext context) {
      LocalContext local = context;   
      IxCode.IrStatement tryStatement = translate(ast.getTryBlock(), context);
      ClosureLocalBinding[] binds = new ClosureLocalBinding[ast.getArguments().length];
      IxCode.IrStatement[] catchBlocks = new IxCode.IrStatement[ast.getArguments().length];
      for(int i = 0; i < ast.getArguments().length; i++){
        local.openScope();
        IxCode.TypeRef arg = (IxCode.TypeRef)accept(ast.getArguments()[i], context);
        IxCode.TypeRef expected = load("java.lang.Throwable");
        if(!IxCode.TypeRules.isSuperType(expected, arg)){
          report(INCOMPATIBLE_TYPE, ast.getArguments()[i], new Object[]{expected, arg});
        }
        binds[i] = local.lookupOnlyCurrentScope(ast.getArguments()[i].getName());
        catchBlocks[i] = translate(ast.getRecBlocks()[i], context);
        local.closeScope();
      }
      return new IxCode.IrTry(tryStatement, binds, catchBlocks);
    }
    
    public Object visit(SynchronizedStatement ast, LocalContext context) {
      IxCode.IrExpression lock = typeCheck(ast.getTarget(), context);
      IxCode.IrStatement block = translate(ast.getBlock(), context);
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return new IxCode.IrSynchronized(lock, block);
    }
    
    public Object visit(BreakStatement ast, LocalContext context) {
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return new IxCode.IrBreak();
    }
    
    public Object visit(ContinueStatement ast, LocalContext context) {
      report(UNIMPLEMENTED_FEATURE, ast, new Object[0]);
      return new IxCode.IrContinue();
    }
  //-------------------------------------------------------------------------//
    
  //----------------------------- members ----------------------------------------//  
    public Object visit(FunctionDeclaration ast, LocalContext context) {
      IxCode.IrMethod function = (IxCode.IrMethod) lookupKernelNode(ast);
      if(function == null) return null;
      LocalContext local = new LocalContext();
      if(Modifier.isStatic(function.getModifier())){
        local.setStatic(true);
      }
      local.setMethod(function);
      IxCode.TypeRef[] arguments = function.getArguments();
      for(int i = 0; i < arguments.length; i++){
        local.addEntry(ast.getArguments()[i].getName(), arguments[i]);
      }
      IxCode.IrBlock block = (IxCode.IrBlock) accept(ast.getBlock(), local);
      block = addReturnNode(block, function.getReturnType());
      function.setBlock(block);
      function.setFrame(local.getContextFrame());
      return null;
    }
    
    public IxCode.IrBlock addReturnNode(IxCode.IrStatement node, IxCode.TypeRef returnType) {
      return new IxCode.IrBlock(node, new IxCode.IrReturn(defaultValue(returnType)));
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
      IxCode.IrMethod method = (IxCode.IrMethod) lookupKernelNode(ast);
      if(method == null) return null;
      if(ast.getBlock() == null) return null;
      LocalContext local = new LocalContext();
      if(Modifier.isStatic(method.getModifier())){
        local.setStatic(true);
      }
      local.setMethod(method);
      IxCode.TypeRef[] arguments = method.getArguments();
      for(int i = 0; i < arguments.length; i++){
        local.addEntry(ast.getArguments()[i].getName(), arguments[i]);
      }    
      IxCode.IrBlock block = (IxCode.IrBlock) accept(ast.getBlock(), local);
      block = addReturnNode(block, method.getReturnType());
      method.setBlock(block);
      method.setFrame(local.getContextFrame());
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, LocalContext context) {
      IxCode.IrConstructor constructor = (IxCode.IrConstructor) lookupKernelNode(ast);
      if(constructor == null) return null;
      LocalContext local = new LocalContext();
      local.setConstructor(constructor);
      IxCode.TypeRef[] args = constructor.getArgs();
      for(int i = 0; i < args.length; i++){
        local.addEntry(ast.getArguments()[i].getName(), args[i]);
      }
      IxCode.IrExpression[] params = typeCheckExps(ast.getInitializers(), local);
      IxCode.IrBlock block = (IxCode.IrBlock) accept(ast.getBody(), local);
      IxCode.IrClass currentClass = getContextClass();
      IxCode.ClassSymbol superClass = currentClass.getSuperClass();
      IxCode.ConstructorSymbol[] matched = superClass.findConstructor(params);
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
      IxCode.IrSuper init = new IxCode.IrSuper(
        superClass, matched[0].getArgs(), params
      );
      constructor.setSuperInitializer(init);
      block = addReturnNode(block, IxCode.BasicTypeRef.VOID);
      constructor.setBlock(block);
      constructor.setFrame(local.getContextFrame());
      return null;
    }
  //----------------------------------------------------------------------------// 
  //----------------------------------------------------------------------------//
  
    public IxCode.TypeRef resolve(TypeSpec type, NameResolution resolver) {
      IxCode.TypeRef resolvedType = (IxCode.TypeRef) resolver.resolve(type);
      if(resolvedType == null){
        report(CLASS_NOT_FOUND, type, new Object[]{type.getComponentName()});      
      }
      return resolvedType;
    }
    
    IxCode.TypeRef promote(IxCode.TypeRef left,  IxCode.TypeRef right) {
      if(!numeric(left) || !numeric(right)) return null;
      if(left == IxCode.BasicTypeRef.DOUBLE || right == IxCode.BasicTypeRef.DOUBLE){
        return IxCode.BasicTypeRef.DOUBLE;
      }
      if(left == IxCode.BasicTypeRef.FLOAT || right == IxCode.BasicTypeRef.FLOAT){
        return IxCode.BasicTypeRef.FLOAT;
      }
      if(left == IxCode.BasicTypeRef.LONG || right == IxCode.BasicTypeRef.LONG){
        return IxCode.BasicTypeRef.LONG;
      }
      return IxCode.BasicTypeRef.INT;
    }
    
    boolean hasNumericType(IxCode.IrExpression expression){
      return numeric(expression.type());
    }
    
    boolean numeric(IxCode.TypeRef symbol){
      return 
      (symbol.isBasicType()) &&
      (symbol == IxCode.BasicTypeRef.BYTE   ||
       symbol == IxCode.BasicTypeRef.SHORT  ||
       symbol == IxCode.BasicTypeRef.CHAR   ||
       symbol == IxCode.BasicTypeRef.INT    ||
       symbol == IxCode.BasicTypeRef.LONG   ||
       symbol == IxCode.BasicTypeRef.FLOAT  ||
       symbol == IxCode.BasicTypeRef.DOUBLE
      );
    }
    
    IxCode.IrExpression typeCheck(Expression expression, LocalContext context){
      return (IxCode.IrExpression) expression.accept(this, context);
    }
      
    IxCode.IrStatement translate(Statement statement, LocalContext context){
      return (IxCode.IrStatement) statement.accept(this, context);
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
  
  public IxCode.IrClass[] process(CompilationUnit[] units){
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
