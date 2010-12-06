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

import onion.compiler.*;
import onion.compiler.ClassTable;
import onion.compiler.ClosureLocalBinding;
import onion.compiler.ImportItem;
import onion.compiler.ImportList;
import onion.compiler.LocalContext;
import onion.compiler.LocalFrame;
import onion.compiler.StaticImportItem;
import onion.compiler.StaticImportList;
import onion.compiler.CompilationException;
import onion.compiler.util.Boxing;
import onion.compiler.util.Classes;
import onion.compiler.util.Paths;
import onion.compiler.util.Systems;
import onion.lang.syntax.*;
import onion.lang.syntax.visitor.ASTVisitor;
import static onion.compiler.SemanticErrorReporter.Constants.*;
import static onion.compiler.IxCode.BinaryExpression.Constants.*;
import static onion.compiler.IxCode.UnaryExpression.Constants.*;

public class CodeAnalysis {
  private SemanticErrorReporter reporter;
  private CompilerConfig config;
  private ClassTable table;
  private Map<IxCode.Node, AstNode> irt2ast;
  private Map<AstNode, IxCode.Node> ast2irt;
  private Map<String, NameResolution> solvers;
  
  private CompilationUnit unit;
  private StaticImportList staticImportedList;
  private ImportList importedList;
  private IxCode.ClassDefinition contextClass;
  private NameResolution solver;
  private int access;
  
  public String topClass(){
    ModuleDeclaration module = unit.getModuleDeclaration();
    String moduleName = module != null ? module.getName() : null;
    return createName(moduleName, Paths.cutExtension(unit.getSourceFileName()) + "Main");
  }

  //---------------------------------------------------------------------------//
  
  public void put(AstNode astNode, IxCode.Node kernelNode){
    ast2irt.put(astNode, kernelNode);
    irt2ast.put(kernelNode, astNode);
  }
  
  public AstNode lookupAST(IxCode.Node kernelNode){
    return irt2ast.get(kernelNode);
  }
  
  public IxCode.Node lookupKernelNode(AstNode astNode){
    return ast2irt.get(astNode);
  }
  
  public void addSolver(String className, NameResolution solver) {
    solvers.put(className, solver);
  }
  
  public NameResolution findSolver(String className){
    return solvers.get(className);
  }
  
  private String createName(String moduleName, String simpleName){
    return (moduleName != null ? moduleName + "." : "") + simpleName;
  }
    
  private String classpath(String[] classPaths){
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
    IxCode.TypeRef resolvedType = resolver.resolve(type);
    if(resolvedType == null) report(CLASS_NOT_FOUND, type, type.getComponentName());
    return resolvedType;
  }
  
  public IxCode.TypeRef resolve(TypeSpec type) {
    return resolve(type, solver);
  }

  public void report(int error, AstNode node, Object... items) {
    reporter.setSourceFile(unit.getSourceFileName());
    reporter.report(error, node.getLocation(), items);
  }

  public IxCode.ClassTypeRef load(String name) {
    return table.load(name);
  }
  
  public IxCode.ClassTypeRef loadTopClass() {
    return table.load(topClass());
  }
  
  public IxCode.ArrayTypeRef loadArray(IxCode.TypeRef type, int dimension) {
    return table.loadArray(type, dimension);
  }
  
  public IxCode.ClassTypeRef rootClass() {
    return table.rootClass();
  }
  
  public CompileError[] getProblems() {
    return reporter.getProblems();
  }
  
  public IxCode.ClassDefinition[] getSourceClasses() {
    return table.classes().values().toArray(new IxCode.ClassDefinition[0]);
  }
  
  private class ClassTableBuilder extends ASTVisitor<String> {
    public ClassTableBuilder() {
    }
    
    public void process(CompilationUnit compilationUnit){
      unit = compilationUnit;
      ModuleDeclaration module = compilationUnit.getModuleDeclaration();
      ImportListDeclaration imports = compilationUnit.getImportListDeclaration();
      String moduleName = module != null ? module.getName() : null;
      ImportList list = new ImportList();
      list.add(new ImportItem("*", "java.lang.*"));
      list.add(new ImportItem("*", "java.io.*"));
      list.add(new ImportItem("*", "java.util.*"));
      list.add(new ImportItem("*", "javax.swing.*"));
      list.add(new ImportItem("*", "java.awt.event.*"));
      list.add(new ImportItem("*", "onion.*"));
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
      importedList = list;
      staticImportedList = staticList;
      int count = 0;
      for(TopLevelElement top:compilationUnit.getTopLevels()) {
        if(top instanceof TypeDeclaration){
          accept(top, moduleName);
        }else {
          count++;
        }
      }
      if(count > 0){
        IxCode.ClassDefinition node = IxCode.ClassDefinition.newClass(0, topClass(), table.rootClass(), new IxCode.ClassTypeRef[0]);
        node.setSourceFile(Paths.nameOf(compilationUnit.getSourceFileName()));
        node.setResolutionComplete(true);
        table.classes().add(node);
        node.addDefaultConstructor();
        put(compilationUnit, node);
        addSolver(node.name(), new NameResolution(list));
      }
    }
    
    public Object visit(ClassDeclaration ast, String context) {
      String module = context;
      IxCode.ClassDefinition node = IxCode.ClassDefinition.newClass(ast.getModifier(), createFQCN(module, ast.getName()));
      node.setSourceFile(Paths.nameOf(unit.getSourceFileName()));
      if(table.lookup(node.name()) != null){
        report(DUPLICATE_CLASS,  ast, node.name());
        return null;
      }
      table.classes().add(node);
      put(ast, node);
      addSolver(node.name(), new NameResolution(importedList));
      return null;    
    }
    
    public Object visit(InterfaceDeclaration ast, String context) {
      String module = context;
      IxCode.ClassDefinition node = IxCode.ClassDefinition.newInterface(ast.getModifier(), createFQCN(module, ast.getName()), null);
      node.setSourceFile(Paths.nameOf(unit.getSourceFileName()));
      ClassTable table = CodeAnalysis.this.table;
      if(table.lookup(node.name()) != null){
        report(DUPLICATE_CLASS,  ast, node.name());
        return null;
      }
      table.classes().add(node);
      put(ast, node);
      addSolver(node.name(), new NameResolution(importedList) );
      return null;
    }
    
    private String createFQCN(String moduleName, String simpleName) {
      return (moduleName != null ? moduleName + "." : "") + simpleName;
    }
  }
  
  private class TypeHeaderAnalysis extends ASTVisitor<Void> {
    private int nconstructor;
  
    public TypeHeaderAnalysis() {
    }
    
    public void process(CompilationUnit compilationUnit){
      unit = compilationUnit;
      for(TopLevelElement top:compilationUnit.getTopLevels()){
        solver = findSolver(topClass());
        accept(top);
      }
    }
    
    public Object visit(ClassDeclaration ast, Void context) {
      nconstructor = 0;
      IxCode.ClassDefinition node = (IxCode.ClassDefinition) lookupKernelNode(ast);
      contextClass = node;
      solver = findSolver(node.name());
      constructTypeHierarchy(node, new ArrayList());
      if(hasCyclicity(node)) report(CYCLIC_INHERITANCE, ast, node.name());
      if(ast.getDefaultSection() != null){
        accept(ast.getDefaultSection());
      }
      AccessSection[] sections = ast.getSections();
      for(int i = 0; i < sections.length; i++){
        accept(sections[i]);
      }
      if(nconstructor == 0){
        node.addDefaultConstructor();
      }
      return null;
    }
      
    public Object visit(InterfaceDeclaration ast, Void context) {
      IxCode.ClassDefinition node = (IxCode.ClassDefinition) lookupKernelNode(ast);
      contextClass = node;
      solver = findSolver(node.name());
      constructTypeHierarchy(node, new ArrayList());
      if(hasCyclicity(node)){
        report(CYCLIC_INHERITANCE, ast, node.name());
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
        report(INTERFACE_REQUIRED, ast.getType(), type);
        return null;
      }
      int modifier = ast.getModifier() | access | Modifier.FORWARDED;
      String name = ast.getName();
      IxCode.FieldDefinition node = new IxCode.FieldDefinition(modifier, contextClass, name, type);
      put(ast, node);
      contextClass.add(node);
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, Void context) {
      nconstructor++;
      IxCode.TypeRef[] args = typesOf(ast.getArguments());
      IxCode.ClassDefinition contextClass = CodeAnalysis.this.contextClass;
      if(args == null) return null;
      int modifier = ast.getModifier() | access;
      IxCode.ConstructorDefinition node = new IxCode.ConstructorDefinition(modifier, contextClass, args, null, null);
      put(ast, node);
      contextClass.add(node);
      return null;
    }

    public Object visit(MethodDeclaration ast, Void context) {
      IxCode.TypeRef[] args = typesOf(ast.getArguments());
      IxCode.TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = IxCode.BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;

      IxCode.ClassDefinition contextClass = CodeAnalysis.this.contextClass;
      int modifier = ast.getModifier() | access;
      if(ast.getBlock() == null) modifier |= Modifier.ABSTRACT;
      String name = ast.getName();    
      IxCode.MethodDefinition node = new IxCode.MethodDefinition(modifier, contextClass, name, args, returnType, null);
      put(ast, node);
      contextClass.add(node);
      return null;
    }
    
    public Object visit(FieldDeclaration ast, Void context) {
      IxCode.TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      IxCode.ClassDefinition contextClass = CodeAnalysis.this.contextClass;
      int modifier = ast.getModifier() | access;
      String name = ast.getName();    
      IxCode.FieldDefinition node = new IxCode.FieldDefinition(modifier, contextClass, name, type);
      put(ast, node);
      contextClass.add(node);
      return node;
    }
    
    private IxCode.TypeRef[] typesOf(Argument[] ast){
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
    
    private IxCode.FieldDefinition createFieldNode(FieldDeclaration ast){
      IxCode.TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      IxCode.FieldDefinition node = new IxCode.FieldDefinition(
        ast.getModifier() | access, contextClass,
        ast.getName(), type);
      return node;
    }
      
    public Object visit(InterfaceMethodDeclaration ast, Void context) {
      IxCode.TypeRef[] args = typesOf(ast.getArguments());
      IxCode.TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = IxCode.BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      int modifier = Modifier.PUBLIC | Modifier.ABSTRACT;
      IxCode.ClassDefinition classType = contextClass;
      String name = ast.getName();    
      IxCode.MethodDefinition node =
        new IxCode.MethodDefinition(modifier, classType, name, args, returnType, null);
      put(ast, node);
      classType.add(node);
      return null;
    }
    
    public Object visit(FunctionDeclaration ast, Void context) {
      IxCode.TypeRef[] args = typesOf(ast.getArguments());
      IxCode.TypeRef returnType;
      if(ast.getReturnType() != null){
        returnType = resolve(ast.getReturnType());
      }else{
        returnType = IxCode.BasicTypeRef.VOID;
      }
      if(args == null || returnType == null) return null;
      
      IxCode.ClassDefinition classType = (IxCode.ClassDefinition) loadTopClass();
      int modifier = ast.getModifier() | Modifier.PUBLIC;
      String name = ast.getName();
      
      IxCode.MethodDefinition node =
        new IxCode.MethodDefinition(modifier, classType, name, args, returnType, null);
      put(ast, node);
      classType.add(node);
      return null;
    }
    
    public Object visit(GlobalVariableDeclaration ast, Void context) {
      IxCode.TypeRef type = resolve(ast.getType());
      if(type == null) return null;
      
      int modifier = ast.getModifier() | Modifier.PUBLIC;
      IxCode.ClassDefinition classType = (IxCode.ClassDefinition)loadTopClass();
      String name = ast.getName();
      
      IxCode.FieldDefinition node = new IxCode.FieldDefinition(modifier, classType, name, type);
      put(ast, node);
      classType.add(node);
      return null;
    }
      
    public Object visit(AccessSection section, Void context){
      if(section == null) return null;

      CodeAnalysis.this.access = section.getID();
      MemberDeclaration[] members = section.getMembers();
      for(int i = 0; i < members.length; i++){
        accept(members[i], context);
      }
      return null;
    }
    
    public boolean hasCyclicity(IxCode.ClassDefinition start){
      return hasCylicitySub(start, new HashSet());
    }
    
    private boolean hasCylicitySub(IxCode.ClassTypeRef symbol, HashSet visit){
      if(symbol == null) return false;
      if(visit.contains(symbol)){
        return true;      
      }
      visit.add(symbol);
      if(hasCylicitySub(symbol.getSuperClass(), (HashSet)visit.clone())){
        return true;      
      }
      IxCode.ClassTypeRef[] interfaces = symbol.getInterfaces();
      for (int i = 0; i < interfaces.length; i++) {
        if(hasCylicitySub(interfaces[i], (HashSet)visit.clone())){
          return true;        
        }
      }
      return false;
    }
  
    private void constructTypeHierarchy(IxCode.ClassTypeRef ref, List<IxCode.ClassTypeRef> visit) {
      if(ref == null || visit.indexOf(ref) >= 0) return;
      visit.add(ref);
      if(ref instanceof IxCode.ClassDefinition){
        IxCode.ClassDefinition node = (IxCode.ClassDefinition) ref;
        if(node.isResolutionComplete()) return;
        IxCode.ClassTypeRef superClass = null;
        List<IxCode.ClassTypeRef> interfaces = new ArrayList<IxCode.ClassTypeRef>();
        NameResolution resolver = findSolver(node.name());
        if(node.isInterface()){
          InterfaceDeclaration ast = (InterfaceDeclaration) lookupAST(node);
          superClass = rootClass();
          TypeSpec[] typeSpecifiers = ast.getInterfaces();
          for(TypeSpec typeSpec:ast.getInterfaces()) {
            IxCode.ClassTypeRef superType = validateSuperType(typeSpec, true, resolver);
            if(superType != null) interfaces.add(superType);
          }
        }else{
          ClassDeclaration ast = (ClassDeclaration) lookupAST(node);
          superClass = validateSuperType(ast.getSuperClass(), false, resolver);
          for(TypeSpec typeSpec:ast.getInterfaces()) {
            IxCode.ClassTypeRef superType = validateSuperType(typeSpec, true, resolver);
            if(superType != null) interfaces.add(superType);
          }
        }
        constructTypeHierarchy(superClass, visit);
        for(IxCode.ClassTypeRef superType:interfaces) {
          constructTypeHierarchy(superType, visit);
        }
        node.setSuperClass(superClass);
        node.setInterfaces(interfaces.toArray(new IxCode.ClassTypeRef[0]));
        node.setResolutionComplete(true);
      }else{
        constructTypeHierarchy(ref.getSuperClass(), visit);
        IxCode.ClassTypeRef[] interfaces = ref.getInterfaces();
        for(int i = 0; i < interfaces.length; i++){
          constructTypeHierarchy(interfaces[i], visit);
        }
      }
    }
    
    private IxCode.ClassTypeRef validateSuperType(
      TypeSpec ast, boolean shouldInterface, NameResolution resolver){
      
      IxCode.ClassTypeRef symbol = null;
      if(ast == null){
        symbol = table.rootClass();
      }else{
        symbol = (IxCode.ClassTypeRef) resolve(ast, resolver);
      }
      if(symbol == null) return null;
      boolean isInterface = symbol.isInterface();
      if(((!isInterface) && shouldInterface) || (isInterface && (!shouldInterface))){
        AstNode astNode = null;
        if(symbol instanceof IxCode.ClassDefinition){
          astNode = lookupAST((IxCode.ClassDefinition)symbol);
        }
        report(ILLEGAL_INHERITANCE, astNode, symbol.name());
      }
      return symbol;
    }
  }
  
  private class DuplicationChecker extends ASTVisitor<Void> {
    private Set methods;
    private Set constructors;
    private Set fields;
    private Set variables;
    private Set functions;
    
    public DuplicationChecker() {
      this.methods      = new TreeSet(new IxCode.MethodRefComparator());
      this.fields       = new TreeSet(new IxCode.FieldRefComparator());
      this.constructors = new TreeSet(new IxCode.ConstructorRefComparator());
      this.variables    = new TreeSet(new IxCode.FieldRefComparator());
      this.functions    = new TreeSet(new IxCode.MethodRefComparator());
    }
    
    public void process(CompilationUnit unit){
      CodeAnalysis.this.unit = unit;
      variables.clear();
      functions.clear();
      TopLevelElement[] toplevels = unit.getTopLevels();    
      for(int i = 0; i < toplevels.length; i++){
        CodeAnalysis.this.solver = findSolver(topClass());
        accept(toplevels[i]);
      }
    }
    
    public Object visit(ClassDeclaration ast, Void context) {
      IxCode.ClassDefinition node = (IxCode.ClassDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      methods.clear();
      fields.clear();
      constructors.clear();
      CodeAnalysis.this.contextClass = node;
      CodeAnalysis.this.solver = findSolver(node.name());
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
      Set generated = new TreeSet(new IxCode.MethodRefComparator());
      Set methodSet = new TreeSet(new IxCode.MethodRefComparator());
      for(Iterator i = fields.iterator(); i.hasNext();){
        IxCode.FieldDefinition node = (IxCode.FieldDefinition)i.next();
        if(Modifier.isForwarded(node.modifier())){
          generateDelegationMethods(node ,generated, methodSet);
        }
      }
    }
    
    private void generateDelegationMethods(IxCode.FieldDefinition node, Set generated, Set methodSet){
      IxCode.ClassTypeRef type = (IxCode.ClassTypeRef) node.getType();
      Set src = Classes.getInterfaceMethods(type);
      for (Iterator i = src.iterator(); i.hasNext();) {
        IxCode.MethodRef method = (IxCode.MethodRef) i.next();
        if(!methodSet.contains(method)) {
          if(generated.contains(method)){
            report(DUPLICATE_GENERATED_METHOD, lookupAST(node), method.affiliation(), method.name(), method.arguments());
          }else {
            IxCode.MethodDefinition generatedMethod = createEmptyMethod(node, method);
            generated.add(generatedMethod);
            contextClass.add(generatedMethod);
          }
        }
      }
    }
    
    private IxCode.MethodDefinition createEmptyMethod(IxCode.FieldRef field, IxCode.MethodRef method){
      IxCode.Expression target;
      target = new IxCode.RefField(new IxCode.This(contextClass), field);
      IxCode.TypeRef[] args = method.arguments();
      IxCode.Expression[] params = new IxCode.Expression[args.length];
      LocalFrame frame = new LocalFrame(null);
      for(int i = 0; i < params.length; i++){
        int index = frame.add("arg" + i, args[i]);
        params[i] = new IxCode.RefLocal(new ClosureLocalBinding(0, index, args[i]));
      }
      target = new IxCode.Call(target, method, params);
      IxCode.StatementBlock statement;
      if(method.returnType() != IxCode.BasicTypeRef.VOID){
        statement = new IxCode.StatementBlock(new IxCode.Return(target));
      }else{
        statement = new IxCode.StatementBlock(new IxCode.ExpressionStatement(target), new IxCode.Return(null));
      }
      IxCode.MethodDefinition node = new IxCode.MethodDefinition(
        Modifier.PUBLIC, contextClass, method.name(),
        method.arguments(), method.returnType(), statement
      );
      node.setFrame(frame);
      return node;
    }
      
    public Object visit(InterfaceDeclaration ast, Void context) {
      IxCode.ClassDefinition node = (IxCode.ClassDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      methods.clear();
      fields.clear();
      constructors.clear();
      CodeAnalysis.this.contextClass = node;
      CodeAnalysis.this.solver = findSolver(node.name());
      InterfaceMethodDeclaration[] members = ast.getDeclarations();
      for(int i = 0; i < members.length; i++){
        accept(members[i], context);
      }
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, Void context) {
      IxCode.ConstructorDefinition node = (IxCode.ConstructorDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      if(constructors.contains(node)){
        IxCode.ClassTypeRef classType = node.affiliation();
        IxCode.TypeRef[] args = node.getArgs();
        report(DUPLICATE_CONSTRUCTOR, ast, classType, args);
      }else{
        constructors.add(node);
      }
      return null;
    }
    
    public Object visit(DelegationDeclaration ast, Void context) {
      IxCode.FieldDefinition node = (IxCode.FieldDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      if(fields.contains(node)){
        IxCode.ClassTypeRef classType = node.affiliation();
        String name = node.name();
        report(DUPLICATE_FIELD, ast, classType, name);
      }else{
        fields.add(node);
      }
      return null;
    }
    
    public Object visit(MethodDeclaration ast, Void context) {
      IxCode.MethodDefinition node = (IxCode.MethodDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      if(methods.contains(node)){
        IxCode.ClassTypeRef classType = node.affiliation();
        String name = node.name();
        IxCode.TypeRef[] args = node.arguments();
        report(DUPLICATE_METHOD, ast, classType, name, args);
      }else{
        methods.add(node);
      }
      return null;
    }
    
    public Object visit(FieldDeclaration ast, Void context) {
      IxCode.FieldDefinition node = (IxCode.FieldDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      if(fields.contains(node)){
        IxCode.ClassTypeRef classType = node.affiliation();
        String name = node.name();
        report(DUPLICATE_FIELD, ast, classType, name);
      }else{
        fields.add(node);
      }
      return null;
    }
    
    public Object visit(InterfaceMethodDeclaration ast, Void context) {
      IxCode.MethodDefinition node = (IxCode.MethodDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      if(methods.contains(node)){
        IxCode.ClassTypeRef classType = node.affiliation();
        String name = node.name();
        IxCode.TypeRef[] args = node.arguments();
        report(DUPLICATE_METHOD, ast, classType, name, args);
      }else{
        methods.add(node);
      }
      return null;
    }
    
    public Object visit(FunctionDeclaration ast, Void context) {
      IxCode.MethodDefinition node = (IxCode.MethodDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      if(functions.contains(node)){
        String name = node.name();
        IxCode.TypeRef[] args = node.arguments();
        report(DUPLICATE_FUNCTION, ast, name, args);
      }else{
        functions.add(node);
      }
      return null;
    }
    
    public Object visit(GlobalVariableDeclaration ast, Void context) {
      IxCode.FieldDefinition node = (IxCode.FieldDefinition) lookupKernelNode(ast);
      if(node == null) return null;
      if(variables.contains(node)){
        String name = node.name();
        report(DUPLICATE_GLOBAL_VARIABLE, ast, name);
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
  }
  
  private class TypeChecker extends ASTVisitor<LocalContext>  {
    public TypeChecker(){
    }
    
    public void process(CompilationUnit unit){
      accept(unit);
    }
    
  //------------------------------ top level --------------------------------------//
    public Object visit(CompilationUnit unit, LocalContext object) {
      CodeAnalysis.this.unit = unit;
      TopLevelElement[] toplevels = unit.getTopLevels();
      LocalContext context = new LocalContext();
      List<IxCode.ActionStatement> statements = new ArrayList<IxCode.ActionStatement>();
      String className = topClass();
      CodeAnalysis.this.solver = findSolver(topClass());
      IxCode.ClassDefinition klass = (IxCode.ClassDefinition) loadTopClass();
      IxCode.ArrayTypeRef argsType = loadArray(load("java.lang.String"), 1);
      IxCode.MethodDefinition method = new IxCode.MethodDefinition(Modifier.PUBLIC, klass,  "start", new IxCode.TypeRef[]{argsType}, IxCode.BasicTypeRef.VOID, null);
      context.add("args", argsType);
      for(TopLevelElement element:toplevels) {
        if(!(element instanceof TypeDeclaration)){
          CodeAnalysis.this.contextClass = klass;
        }
        if(element instanceof Statement){
          context.setMethod(method);
          IxCode.ActionStatement statement = (IxCode.ActionStatement) accept(element, context);
          statements.add(statement);
        }else{
          accept(element, null);
        }
      }    
      if(klass != null){
        statements.add(new IxCode.Return(null));
        method.setBlock(new IxCode.StatementBlock(statements));
        method.setFrame(context.getContextFrame());
        klass.add(method);
        klass.add(createMain(klass, method, "main", new IxCode.TypeRef[]{argsType}, IxCode.BasicTypeRef.VOID));
      }
      return null;
    }
    
    private IxCode.MethodDefinition createMain(IxCode.ClassTypeRef top, IxCode.MethodRef ref, String name, IxCode.TypeRef[] args, IxCode.TypeRef ret) {
      IxCode.MethodDefinition method = new IxCode.MethodDefinition(Modifier.STATIC | Modifier.PUBLIC, top, name, args, ret, null);
      LocalFrame frame = new LocalFrame(null);
      IxCode.Expression[] params = new IxCode.Expression[args.length];
      for(int i = 0; i < args.length; i++){
        int index = frame.add("args" + i, args[i]);
        params[i] = new IxCode.RefLocal(0, index, args[i]);
      }
      method.setFrame(frame);
      IxCode.ConstructorRef cref = top.findConstructor(new IxCode.Expression[0])[0];
      IxCode.StatementBlock block = new IxCode.StatementBlock(
        new IxCode.ExpressionStatement(new IxCode.Call(new IxCode.NewObject(cref, new IxCode.Expression[0]), ref, params))
      );
      block = addReturnNode(block, IxCode.BasicTypeRef.VOID);
      method.setBlock(block);
      return method;
    }
    
    public Object visit(InterfaceDeclaration ast, LocalContext context) {
      CodeAnalysis.this.contextClass = (IxCode.ClassDefinition) lookupKernelNode(ast);
      return null;
    }
    
    public Object visit(ClassDeclaration ast, LocalContext context) {
      CodeAnalysis.this.contextClass = (IxCode.ClassDefinition) lookupKernelNode(ast);
      CodeAnalysis.this.solver = findSolver(contextClass.name());
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
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if(left.isBasicType() && right.isBasicType()){
        return checkNumExp(ADD, ast, left, right, context);
      }
      if(left.isBasicType()){
        if(left.type() == IxCode.BasicTypeRef.VOID){
          report(IS_NOT_BOXABLE_TYPE, ast.getLeft(), left.type());
          return null;
        }else{
          left = Boxing.boxing(table, left);
        }
      }
      if(right.isBasicType()){
        if(right.type() == IxCode.BasicTypeRef.VOID){
          report(IS_NOT_BOXABLE_TYPE, ast.getRight(), right.type());
          return null;
        }else{
          right = Boxing.boxing(table, right);
        }
      }
      IxCode.MethodRef toString;
      toString = findMethod(ast.getLeft(), (IxCode.ObjectTypeRef)left.type(), "toString");
      left = new IxCode.Call(left, toString, new IxCode.Expression[0]);
      toString = findMethod(ast.getRight(), (IxCode.ObjectTypeRef)right.type(), "toString");
      right = new IxCode.Call(right, toString, new IxCode.Expression[0]);
      IxCode.MethodRef concat =
        findMethod(ast, (IxCode.ObjectTypeRef)left.type(), "concat", new IxCode.Expression[]{right});
      return new IxCode.Call(left, concat, new IxCode.Expression[]{right});
    }
    
    public Object visit(PostIncrement node, LocalContext context) {
      IxCode.Expression operand = typeCheck(node.getTarget(), context);
      if(operand == null) return null;
      if((!operand.isBasicType()) || !hasNumericType(operand)){
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.getSymbol(), new IxCode.TypeRef[]{operand.type()});
        return null;
      }
      IxCode.Expression result = null;
      if(operand instanceof IxCode.RefLocal){
        int varIndex = context.add(context.newName(), operand.type());
        IxCode.RefLocal ref = (IxCode.RefLocal)operand;
        result = new IxCode.Begin(
          new IxCode.SetLocal(0, varIndex, operand.type(), operand),
          new IxCode.SetLocal(
            ref.frame(), ref.index(), ref.type(),
            new IxCode.BinaryExpression(
              ADD, operand.type(),
              new IxCode.RefLocal(0, varIndex, operand.type()),
              new IxCode.IntLiteral(1)
            )
          ),
          new IxCode.RefLocal(0, varIndex, operand.type())
        );
      }else if(operand instanceof IxCode.RefField){
        IxCode.RefField ref = (IxCode.RefField)operand;
        int varIndex = context.add(context.newName(), ref.target.type());
        result = new IxCode.Begin(
          new IxCode.SetLocal(0, varIndex, ref.target.type(), ref.target),
          new IxCode.SetField(
            new IxCode.RefLocal(0, varIndex, ref.target.type()),
            ref.field,
            new IxCode.BinaryExpression(
              ADD, operand.type(),
              new IxCode.RefField(new IxCode.RefLocal(0, varIndex, ref.target.type()), ref.field),
              new IxCode.IntLiteral(1)
            )
          )
        );
      }else {
        report(UNIMPLEMENTED_FEATURE, node);
      }
      return result;
    }

    public Object visit(PostDecrement node, LocalContext context) {
      IxCode.Expression operand = typeCheck(node.getTarget(), context);
      if(operand == null) return null;
      if((!operand.isBasicType()) || !hasNumericType(operand)){
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.getSymbol(), new IxCode.TypeRef[]{operand.type()});
        return null;
      }
      IxCode.Expression result = null;
      if(operand instanceof IxCode.RefLocal){
        int varIndex = context.add(context.newName(), operand.type());
        IxCode.RefLocal ref = (IxCode.RefLocal)operand;
        result = new IxCode.Begin(
          new IxCode.SetLocal(0, varIndex, operand.type(), operand),
          new IxCode.SetLocal(
            ref.frame(), ref.index(), ref.type(),
            new IxCode.BinaryExpression(
              SUBTRACT, operand.type(),
              new IxCode.RefLocal(0, varIndex, operand.type()),
              new IxCode.IntLiteral(1)
            )
          ),
          new IxCode.RefLocal(0, varIndex, operand.type())
        );
      }else if(operand instanceof IxCode.RefField){
        IxCode.RefField ref = (IxCode.RefField)operand;
        int varIndex = context.add(context.newName(), ref.target.type());
        result = new IxCode.Begin(
          new IxCode.SetLocal(0, varIndex, ref.target.type(), ref.target),
          new IxCode.SetField(
            new IxCode.RefLocal(0, varIndex, ref.target.type()),
            ref.field,
            new IxCode.BinaryExpression(
              SUBTRACT, operand.type(),
              new IxCode.RefField(new IxCode.RefLocal(0, varIndex, ref.target.type()), ref.field),
              new IxCode.IntLiteral(1)
            )
          )
        );
      }else {
        report(UNIMPLEMENTED_FEATURE, node);
      }
      return result;
    }
    
    @Override
    public Object visit(Elvis ast, LocalContext context) {
      IxCode.Expression l = typeCheck(ast.getLeft(), context);
      IxCode.Expression r = typeCheck(ast.getRight(), context);
      if(l.isBasicType() || r.isBasicType() || !IxCode.TypeRules.isAssignable(l.type(), r.type())) {
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{l.type(), r.type()});
        return null;
      }
      return new IxCode.BinaryExpression(ELVIS, l.type(), l, r);
    }
    
    public Object visit(Subtraction ast, LocalContext context) {
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(SUBTRACT, ast, left, right, context);
    }
    
    public Object visit(Multiplication ast, LocalContext context) {
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(MULTIPLY,  ast, left, right, context);
    }
    
    public Object visit(Division ast, LocalContext context) {
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      return checkNumExp(DIVIDE, ast, left, right, context);
    }
    
    public Object visit(Modulo ast, LocalContext context) {
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
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
      IxCode.Expression[] ops = processComparableExpression(ast, context);
      if(ops == null){
        return null;
      }
      return new IxCode.BinaryExpression(LESS_OR_EQUAL, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LessThan ast, LocalContext context) {
      IxCode.Expression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.BinaryExpression(
        LESS_THAN, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(GreaterOrEqual ast, LocalContext context) {
      IxCode.Expression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;    
      return new IxCode.BinaryExpression(
        GREATER_OR_EQUAL, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(GreaterThan ast, LocalContext context) {
      IxCode.Expression[] ops = processComparableExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.BinaryExpression(
        GREATER_THAN, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LogicalAnd ast, LocalContext context) {
      IxCode.Expression[] ops = processLogicalExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.BinaryExpression(
        LOGICAL_AND, IxCode.BasicTypeRef.BOOLEAN, ops[0], ops[1]);
    }
    
    public Object visit(LogicalOr ast, LocalContext context) {
      IxCode.Expression[] ops = processLogicalExpression(ast, context);
      if(ops == null) return null;
      return new IxCode.BinaryExpression(
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
    
    IxCode.Expression[] processLogicalExpression(onion.lang.syntax.BinaryExpression ast, LocalContext context){
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type(), rightType = right.type();
      if((leftType != IxCode.BasicTypeRef.BOOLEAN) || (rightType != IxCode.BasicTypeRef.BOOLEAN)){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()});
        return null;
      }
      return new IxCode.Expression[]{left, right};
    }
    
    IxCode.Expression processShiftExpression(
      int kind, onion.lang.syntax.BinaryExpression ast, LocalContext context){
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if(!left.type().isBasicType()){
        IxCode.Expression[] params = new IxCode.Expression[]{right};
        Pair<Boolean, IxCode.MethodRef> result = tryFindMethod(ast, (IxCode.ObjectTypeRef)left.type(), "add", params);
        if(result._2 == null){
          report(METHOD_NOT_FOUND, ast, left.type(), "add", types(params));
          return null;
        }
        return new IxCode.Call(left, result._2, params);
      }
      if(!right.type().isBasicType()){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()});
        return null;
      }
      IxCode.BasicTypeRef leftType = (IxCode.BasicTypeRef)left.type();
      IxCode.BasicTypeRef rightType = (IxCode.BasicTypeRef)right.type();
      if((!leftType.isInteger()) || (!rightType.isInteger())){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()});
        return null;
      }
      IxCode.TypeRef leftResultType = promoteInteger(leftType);
      if(leftResultType != leftType){
        left = new IxCode.AsInstanceOf(left, leftResultType);
      }
      if(rightType != IxCode.BasicTypeRef.INT){
        right = new IxCode.AsInstanceOf(right, IxCode.BasicTypeRef.INT);
      }
      return new IxCode.BinaryExpression(kind, IxCode.BasicTypeRef.BOOLEAN, left, right);
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
      
    IxCode.Expression checkBitExp(int kind, onion.lang.syntax.BinaryExpression ast, LocalContext context){
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      if((!left.isBasicType()) || (!right.isBasicType())){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()});
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
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType});
        return null;
      }
      if(left.type() != resultType){
        left = new IxCode.AsInstanceOf(left, resultType);
      }
      if(right.type() != resultType){
        right = new IxCode.AsInstanceOf(right, resultType);
      }
      return new IxCode.BinaryExpression(kind, resultType, left, right);
    }
    
    IxCode.Expression checkNumExp(int kind, onion.lang.syntax.BinaryExpression ast, IxCode.Expression left, IxCode.Expression right, LocalContext context) {
      if((!hasNumericType(left)) || (!hasNumericType(right))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()});
        return null;
      }
      IxCode.TypeRef resultType = promote(left.type(), right.type());
      if(left.type() != resultType){
        left = new IxCode.AsInstanceOf(left, resultType);
      }
      if(right.type() != resultType){
        right = new IxCode.AsInstanceOf(right, resultType);
      }
      return new IxCode.BinaryExpression(kind, resultType, left, right);
    }
    
    IxCode.Expression checkRefEqualsExp(int kind, onion.lang.syntax.BinaryExpression ast, LocalContext context){
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type();
      IxCode.TypeRef rightType = right.type();
      if(
        (left.isBasicType() && (!right.isBasicType())) ||
        ((!left.isBasicType()) && (right.isBasicType()))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType});
        return null;
      }
      if(left.isBasicType() && right.isBasicType()){
        if(hasNumericType(left) && hasNumericType(right)){
          IxCode.TypeRef resultType = promote(leftType, rightType);
          if(resultType != left.type()){
            left = new IxCode.AsInstanceOf(left, resultType);
          }
          if(resultType != right.type()){
            right = new IxCode.AsInstanceOf(right, resultType);
          }
        }else if(leftType != IxCode.BasicTypeRef.BOOLEAN || rightType != IxCode.BasicTypeRef.BOOLEAN){
          report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType});
          return null;
        }
      }
      return new IxCode.BinaryExpression(kind, IxCode.BasicTypeRef.BOOLEAN, left, right);
    }
    
    IxCode.Expression checkEqualExp(int kind, onion.lang.syntax.BinaryExpression ast, LocalContext context){
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type(), rightType = right.type();
      if((left.isBasicType() && (!right.isBasicType())) || ((!left.isBasicType()) && (right.isBasicType()))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType});
        return null;
      }
      if(left.isBasicType() && right.isBasicType()){      
        if(hasNumericType(left) && hasNumericType(right)){
          IxCode.TypeRef resultType = promote(leftType, rightType);
          if(resultType != left.type()){
            left = new IxCode.AsInstanceOf(left, resultType);
          }
          if(resultType != right.type()){
            right = new IxCode.AsInstanceOf(right, resultType);
          }
        }else if(leftType != IxCode.BasicTypeRef.BOOLEAN || rightType != IxCode.BasicTypeRef.BOOLEAN){
          report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{leftType, rightType});
          return null;
        }
      }else if(left.isReferenceType() && right.isReferenceType()){
        return createEquals(kind, left, right);
      }
      return new IxCode.BinaryExpression(kind, IxCode.BasicTypeRef.BOOLEAN, left, right);
    }
    
    IxCode.Expression createEquals(int kind, IxCode.Expression left, IxCode.Expression right){
      right = new IxCode.AsInstanceOf(right, rootClass());
      IxCode.Expression[] params = {right};
      IxCode.ObjectTypeRef target = (IxCode.ObjectTypeRef) left.type();
      IxCode.MethodRef[] methods = target.findMethod("equals", params);
      IxCode.Expression node = new IxCode.Call(left, methods[0], params);
      if(kind == IxCode.BinaryExpression.Constants.NOT_EQUAL){
        node = new IxCode.UnaryExpression(NOT, IxCode.BasicTypeRef.BOOLEAN, node);
      }
      return node;
    }
    
    IxCode.Expression[] processComparableExpression(onion.lang.syntax.BinaryExpression ast, LocalContext context) {
      IxCode.Expression left = typeCheck(ast.getLeft(), context);
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      if(left == null || right == null) return null;
      IxCode.TypeRef leftType = left.type(), rightType = right.type();
      if((!numeric(left.type())) || (!numeric(right.type()))){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, ast.getSymbol(), new IxCode.TypeRef[]{left.type(), right.type()});
        return null;
      }
      IxCode.TypeRef resultType = promote(leftType, rightType);
      if(leftType != resultType){
        left = new IxCode.AsInstanceOf(left, resultType);
      }
      if(rightType != resultType){
        right = new IxCode.AsInstanceOf(right, resultType);
      }
      return new IxCode.Expression[]{left, right};
    }
  //-------------------------------------------------------------------------------//  
  //------------------------- literals --------------------------------------------//
    public Object visit(onion.lang.syntax.FloatLiteral ast, LocalContext context) {
      return new IxCode.FloatLiteral(ast.getValue());
    }
    
    public Object visit(SuperMethodCall ast, LocalContext context) {
      IxCode.Expression[] params;
      params = typeCheckExps(ast.getParams(), context);
      if(params == null) return null;
      IxCode.ClassTypeRef contextClass = CodeAnalysis.this.contextClass;
      Pair<Boolean, IxCode.MethodRef> result = tryFindMethod(ast, contextClass.getSuperClass(), ast.getName(), params);
      if(result._2 == null){
        if(result._1) report(METHOD_NOT_FOUND, ast, contextClass, ast.getName(), types(params));
        return null;
      }
      return new IxCode.CallSuper(new IxCode.This(contextClass), result._2, params);
    }
    
    public Object visit(onion.lang.syntax.DoubleLiteral ast, LocalContext context) {
      return new IxCode.DoubleLiteral(ast.getValue());
    }
    
    public Object visit(IntegerLiteral node, LocalContext context) {
      return new IxCode.IntLiteral(node.getValue());
    }
    
    public Object visit(onion.lang.syntax.CharacterLiteral node, LocalContext context) {
      return new IxCode.CharacterLiteral(node.getValue());
    }
    
    public Object visit(onion.lang.syntax.LongLiteral ast, LocalContext context) {
      return new IxCode.LongLiteral(ast.getValue());
    }
    
    public Object visit(BooleanLiteral ast, LocalContext context) {
      return new IxCode.BoolLiteral(ast.getValue());
    }
    
    public Object visit(onion.lang.syntax.ListLiteral ast, LocalContext context) {
      IxCode.Expression[] elements = new IxCode.Expression[ast.size()];
      for(int i = 0; i < ast.size(); i++){
        elements[i] = typeCheck(ast.getExpression(i), context);
      }
      IxCode.ListLiteral node = new IxCode.ListLiteral(elements, load("java.util.List"));
      return node;
    }
    
    public Object visit(onion.lang.syntax.StringLiteral ast, LocalContext context) {
      return new IxCode.StringLiteral(ast.getValue(), load("java.lang.String"));
    }  
    
    public Object visit(onion.lang.syntax.NullLiteral ast, LocalContext context) {
      return new IxCode.NullLiteral();
    }
  //-----------------------------------------------------------------------------//
    
  //---------------------------- unary expressions ------------------------------//
    public Object visit(Posit ast, LocalContext context) {
      IxCode.Expression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(!hasNumericType(node)){
        report(INCOMPATIBLE_OPERAND_TYPE, ast,  "+", new IxCode.TypeRef[]{node.type()});
        return null;
      }
      node = new IxCode.UnaryExpression(PLUS, node.type(), node);
      return node;
    }
    
    public Object visit(Negate ast, LocalContext context) {
      IxCode.Expression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(!hasNumericType(node)){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, "-", new IxCode.TypeRef[]{node.type()});
        return null;
      }
      node = new IxCode.UnaryExpression(MINUS, node.type(), node);
      return node;
    }
    
    public Object visit(Not ast, LocalContext context) {
      IxCode.Expression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      if(node.type() != IxCode.BasicTypeRef.BOOLEAN){
        report(INCOMPATIBLE_OPERAND_TYPE, ast, "!", new IxCode.TypeRef[]{node.type()});
        return null;
      }
      node = new IxCode.UnaryExpression(NOT, IxCode.BasicTypeRef.BOOLEAN, node);
      return node;
    }
  //-----------------------------------------------------------------------------//
    
  //---------------------------- assignment operators ---------------------------//
    public Object visit(Assignment ast, LocalContext context) {
      onion.lang.syntax.Expression left = ast.getLeft();
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
    
    private IxCode.Expression processLocalAssign(Assignment ast, LocalContext context){
      IxCode.Expression value = typeCheck(ast.getRight(), context);
      if(value == null) return null;
      Id id = (Id) ast.getLeft();
      ClosureLocalBinding bind = context.lookup(id.getName());
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
        index = context.add(id.getName(), leftType);
      }
      value = processAssignable(ast.getRight(), leftType, value);
      if(value == null) return null;
      return new IxCode.SetLocal(frame, index, leftType, value);
    }
    
    private Object processSelfFieldAssign(Assignment ast, LocalContext context){
      IxCode.Expression value = typeCheck(ast.getRight(), context);
      if(value == null) return null;
      SelfFieldReference ref = (SelfFieldReference) ast.getLeft();
      IxCode.ClassTypeRef selfClass;
      if(context.isGlobal()){
        selfClass = loadTopClass();
      }else {
        if(context.method() != null){
          selfClass = context.method().affiliation();
        }else{
          selfClass = context.constructor().affiliation();
        }
      }
      IxCode.FieldRef field = findField(selfClass, ref.getName());
      if(field == null){
        report(FIELD_NOT_FOUND, ref, selfClass, ref.getName());
        return null;
      }
      if(!isAccessible(field, selfClass)){
        report(FIELD_NOT_ACCESSIBLE, ast, field.affiliation(), field.name(), selfClass);
        return null;
      }
      value = processAssignable(ast.getRight(), field.getType(), value);
      if(value == null) return null;
      return new IxCode.SetField(new IxCode.This(selfClass), field, value);
    }
    
    Object processArrayAssign(Assignment ast, LocalContext context){
      IxCode.Expression value = typeCheck(ast.getRight(), context);
      Indexing indexing = (Indexing) ast.getLeft();
      IxCode.Expression target = typeCheck(indexing.getLeft(), context);
      IxCode.Expression index = typeCheck(indexing.getRight(), context);
      if(value == null || target == null || index == null) return null;
      if(target.isBasicType()){
        report(INCOMPATIBLE_TYPE, indexing.getLeft(), rootClass(), target.type());
        return null;
      }
      if(target.isArrayType()){
        IxCode.ArrayTypeRef targetType = ((IxCode.ArrayTypeRef)target.type());
        if(!(index.isBasicType() && ((IxCode.BasicTypeRef)index.type()).isInteger())){
          report(INCOMPATIBLE_TYPE,  indexing.getRight(), IxCode.BasicTypeRef.INT, index.type());
          return null;
        }
        IxCode.TypeRef base = targetType.getBase();
        value = processAssignable(ast.getRight(), base, value);
        if(value == null) return null;
        return new IxCode.ArraySet(target, index, value);
      }
      IxCode.Expression[] params;
      params = new IxCode.Expression[]{index, value};
      Pair<Boolean, IxCode.MethodRef> result = tryFindMethod(ast, (IxCode.ObjectTypeRef)target.type(), "set", new IxCode.Expression[]{index, value});
      if(result._2 == null){
        report(METHOD_NOT_FOUND, ast,  target.type(), "set", types(params));
        return null;
      }
      return new IxCode.Call(target, result._2, params);
    }
    
    Object processFieldOrMethodAssign(Assignment ast, LocalContext context){
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast);
      return null;
    }
    
    public Object visit(AdditionAssignment ast, LocalContext context) {
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast);
      return null;
    }
    
    public Object visit(SubtractionAssignment ast, LocalContext context) {
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast);
      return null;
    }
    
    public Object visit(MultiplicationAssignment ast, LocalContext context) {
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast);
      return null;
    }
    
    public Object visit(DivisionAssignment ast, LocalContext context) {
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast);
      return null;
    }
    
    public Object visit(ModuloAssignment ast, LocalContext context) {
      IxCode.Expression right = typeCheck(ast.getRight(), context);
      report(UNIMPLEMENTED_FEATURE, ast);
      return null;
    }
  //-----------------------------------------------------------------------------//
  
  //---------------------------- other expressions ------------------------------//
    public Object visit(Id ast, LocalContext context) {
      ClosureLocalBinding bind = context.lookup(ast.getName());
      if(bind == null){
        report(VARIABLE_NOT_FOUND, ast, ast.getName());
        return null;
      }
      return new IxCode.RefLocal(bind);
    }
    
    IxCode.MethodRef findMethod(AstNode ast, IxCode.ObjectTypeRef type, String name) {
      return findMethod(ast, type, name, new IxCode.Expression[0]);
    }
    
    IxCode.MethodRef findMethod(AstNode ast, IxCode.ObjectTypeRef type, String name, IxCode.Expression[] params) {
      IxCode.MethodRef[] methods = type.findMethod(name, params);
      if(methods.length == 0){
        report(METHOD_NOT_FOUND, ast,  type, name, types(params));
        return null;
      }
      return methods[0];
    }
    
    public Object visit(CurrentInstance ast, LocalContext context) {
      if(context.isStatic()) {
        return null;
      }else {
        return new IxCode.This(contextClass);
      }
    }
    
    boolean hasSamePackage(IxCode.ClassTypeRef a, IxCode.ClassTypeRef b) {
      String name1 = a.name();
      String name2 = b.name();
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
    
    boolean isAccessible(IxCode.ClassTypeRef target, IxCode.ClassTypeRef context) {
      if(hasSamePackage(target, context)){
        return true;
      }else{
        if(Modifier.isInternal(target.modifier())){
          return false;
        }else{
          return true;
        }
      }
    }
    
    boolean isAccessible(IxCode.MemberRef member, IxCode.ClassTypeRef context) {
      IxCode.ClassTypeRef targetType = member.affiliation();
      if(targetType == context) return true;
      int modifier = member.modifier();
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
    
    private IxCode.FieldRef findField(IxCode.ObjectTypeRef target, String name) {
      if(target == null) return null;
      IxCode.FieldRef[] fields = target.fields();
      for (int i = 0; i < fields.length; i++) {
        if(fields[i].name().equals(name)){
          return fields[i];
        }
      }
      IxCode.FieldRef field = findField(target.getSuperClass(), name);
      if(field != null) return field;
      IxCode.ClassTypeRef[] interfaces = target.getInterfaces();
      for(int i = 0; i < interfaces.length; i++){
        field = findField(interfaces[i], name);
        if(field != null) return field;
      }
      return null;
    }
    
    private boolean isAccessible(
      AstNode ast, IxCode.ObjectTypeRef target, IxCode.ClassTypeRef context
    ) {
      if(target.isArrayType()){
        IxCode.TypeRef component = ((IxCode.ArrayTypeRef)target).getComponent();
        if(!component.isBasicType()){
          if(!isAccessible((IxCode.ClassTypeRef)component, contextClass)){
            report(CLASS_NOT_ACCESSIBLE, ast, target, context);
            return false;
          }
        }
      }else{
        if(!isAccessible((IxCode.ClassTypeRef)target, context)){
          report(CLASS_NOT_ACCESSIBLE, ast, target, context);
          return false;
        }
      }
      return true;
    }
    
    public Object visit(FieldOrMethodRef ast, LocalContext context) {
      IxCode.ClassDefinition contextClass = CodeAnalysis.this.contextClass;
      IxCode.Expression target = typeCheck(ast.getTarget(), context);
      if(target == null) return null;
      if(target.type().isBasicType() || target.type().isNullType()){
        report(INCOMPATIBLE_TYPE, ast.getTarget(), rootClass(), target.type());
        return null;
      }
      IxCode.ObjectTypeRef targetType = (IxCode.ObjectTypeRef) target.type();
      if(!isAccessible(ast, targetType, contextClass)) return null;
      String name = ast.getName();
      if(target.type().isArrayType()){
        if(name.equals("length") || name.equals("size")){
          return new IxCode.ArrayLength(target);
        }else{
          return null;
        }
      }
      IxCode.FieldRef field = findField(targetType, name);
      if(field != null && isAccessible(field, CodeAnalysis.this.contextClass)){
        return new IxCode.RefField(target, field);
      }
      Pair<Boolean, IxCode.MethodRef> result = tryFindMethod(ast, targetType, name, new IxCode.Expression[0]);
      if(result._2 != null){
        return new IxCode.Call(target, result._2, new IxCode.Expression[0]);
      }
      boolean continuable = result._1;
      if(!continuable) return null;
      
      String getterName;
      getterName = getter(name);
      result = tryFindMethod(ast, targetType, getterName, new IxCode.Expression[0]);
      if(result._2 != null){
        return new IxCode.Call(target, result._2, new IxCode.Expression[0]);
      }
      continuable = result._1;
      if(!continuable) return null;
      
      getterName = getterBoolean(name);
      result = tryFindMethod(ast, targetType, getterName, new IxCode.Expression[0]);
      if(result._2 != null){
        return new IxCode.Call(target, result._2, new IxCode.Expression[0]);
      }
      
      if(field == null){
        report(FIELD_NOT_FOUND, ast, targetType, ast.getName());
      }else{
        report(FIELD_NOT_ACCESSIBLE, ast,  targetType, ast.getName(), CodeAnalysis.this.contextClass);
      }
      return null;
    }
    
    private Pair<Boolean, IxCode.MethodRef> tryFindMethod(
      AstNode ast, IxCode.ObjectTypeRef target, String name, IxCode.Expression[] params
    ) {
      IxCode.MethodRef[] methods;
      methods = target.findMethod(name, params);
      if(methods.length > 0){
        if(methods.length > 1){
          report(AMBIGUOUS_METHOD, ast, new Object[]{methods[0].affiliation(), name, methods[0].arguments()}, new Object[]{methods[1].affiliation(), name, methods[1].arguments()});
          return Pair.make(false, null);
        }
        if(!isAccessible(methods[0], contextClass)){
          report(METHOD_NOT_ACCESSIBLE, ast, methods[0].affiliation(), name, methods[0].arguments(), contextClass);
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
      String name = ast.getName();
      ClosureLocalBinding binding = context.lookupOnlyCurrentScope(name);
      if(binding != null){
        report(DUPLICATE_LOCAL_VARIABLE, ast, name);
        return null;
      }
      IxCode.TypeRef type = resolve(ast.getType(), solver);
      if(type == null) return null;
      context.add(name, type);
      return type;
    }
    
    public Object visit(onion.lang.syntax.NewArray ast, LocalContext context) {
      IxCode.TypeRef type = resolve(ast.getType(), solver);
      IxCode.Expression[] parameters = typeCheckExps(ast.getArguments(), context);
      if(type == null || parameters == null) return null;
      IxCode.ArrayTypeRef resultType = loadArray(type, parameters.length);
      return new IxCode.NewArray(resultType, parameters);
    }
      
    public Object visit(Cast ast, LocalContext context) {
      IxCode.Expression node = typeCheck(ast.getTarget(), context);
      if(node == null) return null;
      IxCode.TypeRef conversion = resolve(ast.getType(), solver);
      if(conversion == null) return null;
      node = new IxCode.AsInstanceOf(node, conversion);
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
      IxCode.ClassTypeRef type = (IxCode.ClassTypeRef) CodeAnalysis.this.resolve(ast.getType());
      Argument[] args = ast.getArguments();
      IxCode.TypeRef[] argTypes = new IxCode.TypeRef[args.length];
      String name = ast.getName();
      try {
        context.openFrame();
        boolean error = false;
        for(int i = 0; i < args.length; i++){
          argTypes[i] = (IxCode.TypeRef)accept(args[i], context);
          if(argTypes[i] == null){
            error = true;
          }
        }     
        if(type == null) return null;
        if(!type.isInterface()){
          report(INTERFACE_REQUIRED, ast.getType(), type);
          return null;
        }
        if(error) return null;
        IxCode.MethodRef[] methods = type.methods();
        IxCode.MethodRef method = matches(argTypes, name, methods);
        if(method == null){
          report(METHOD_NOT_FOUND, ast, type, name, argTypes);
          return null;
        }
        context.setMethod(method);
        context.getContextFrame().parent().setAllClosed(true);
        IxCode.ActionStatement block = translate(ast.getBlock(), context);
        block = addReturnNode(block, method.returnType());
        IxCode.NewClosure node = new IxCode.NewClosure(type, method, block);
        node.setFrame(context.getContextFrame());
        return node;
      }finally{
        context.closeFrame();
      }     
    }

    private IxCode.MethodRef matches(IxCode.TypeRef[] argTypes, String name, IxCode.MethodRef[] methods) {
      for(int i = 0; i < methods.length; i++){
        IxCode.TypeRef[] types = methods[i].arguments();
        if(name.equals(methods[i].name()) && equals(argTypes, types)){
          return methods[i];
        }
      }
      return null;
    }

    public Object visit(Indexing ast, LocalContext context) {
      IxCode.Expression target = typeCheck(ast.getLeft(), context);
      IxCode.Expression index = typeCheck(ast.getRight(), context);
      if(target == null || index == null) return null;
      if(target.isArrayType()){
        if(!(index.isBasicType() && ((IxCode.BasicTypeRef)index.type()).isInteger())){
          report(INCOMPATIBLE_TYPE, ast, IxCode.BasicTypeRef.INT, index.type());
          return null;
        }
        return new IxCode.ArrayRef(target, index);
      }    
      if(target.isBasicType()){
        report(INCOMPATIBLE_TYPE, ast.getLeft(), rootClass(), target.type());
        return null;
      }
      if(target.isArrayType()){
        if(!(index.isBasicType() && ((IxCode.BasicTypeRef)index.type()).isInteger())){
          report(INCOMPATIBLE_TYPE,  ast.getRight(), IxCode.BasicTypeRef.INT, index.type());
          return null;
        }
        return new IxCode.ArrayRef(target, index);
      }    
      IxCode.Expression[] params = {index};
      Pair<Boolean, IxCode.MethodRef> result = tryFindMethod(ast, (IxCode.ObjectTypeRef)target.type(), "get", new IxCode.Expression[]{index});
      if(result._2 == null){
        report(METHOD_NOT_FOUND, ast,  target.type(), "get", types(params));
        return null;
      }
      return new IxCode.Call(target, result._2, params);
    }
    
    public Object visit(SelfFieldReference ast, LocalContext context) {
      IxCode.ClassTypeRef selfClass = null;
      if(context.isStatic()) return null;
      selfClass = contextClass;
      IxCode.FieldRef field = findField(selfClass, ast.getName());
      if(field == null){
        report(FIELD_NOT_FOUND, ast, selfClass, ast.getName());
        return null;
      }
      if(!isAccessible(field, selfClass)){
        report(FIELD_NOT_ACCESSIBLE, ast,  field.affiliation(), ast.getName(), selfClass);
        return null;
      }    
      return new IxCode.RefField(new IxCode.This(selfClass), field);
    }
    
    public Object visit(onion.lang.syntax.NewObject ast, LocalContext context) {
      IxCode.ClassTypeRef type = (IxCode.ClassTypeRef) CodeAnalysis.this.resolve(ast.getType());
      IxCode.Expression[] parameters = typeCheckExps(ast.getArguments(), context);
      if(parameters == null || type == null) return null;
      IxCode.ConstructorRef[] constructors = type.findConstructor(parameters);
      if(constructors.length == 0){
        report(CONSTRUCTOR_NOT_FOUND, ast, type, types(parameters));
        return null;
      }
      if(constructors.length > 1){
        report(AMBIGUOUS_CONSTRUCTOR, ast, new Object[]{constructors[0].affiliation(), constructors[0].getArgs()}, new Object[]{constructors[1].affiliation(), constructors[1].getArgs()});
        return null;
      }
      return new IxCode.NewObject(constructors[0], parameters);
    }
        
    public Object visit(IsInstance ast, LocalContext context) {
      IxCode.Expression target = typeCheck(ast.getTarget(), context);
      IxCode.TypeRef checkType = resolve(ast.getType(), solver);
      if(target == null || checkType == null) return null;
      return new IxCode.InstanceOf(target, checkType);
    }
    
    private IxCode.TypeRef[] types(IxCode.Expression[] parameters){
      IxCode.TypeRef[] types = new IxCode.TypeRef[parameters.length];
      for(int i = 0; i < types.length; i++){
        types[i] = parameters[i].type();
      }
      return types;
    }
    
    public Object visit(SelfMethodCall ast, LocalContext context) {
      IxCode.Expression[] params = typeCheckExps(ast.getArguments(), context);
      if(params == null) return null;
      IxCode.ClassDefinition targetType = contextClass;
      IxCode.MethodRef[] methods = targetType.findMethod(ast.getName(), params);
      if(methods.length == 0){
        report(METHOD_NOT_FOUND, ast,  targetType, ast.getName(), types(params));
        return null;
      }else if(methods.length > 1){
        report(AMBIGUOUS_METHOD, ast, new Object[]{methods[0].affiliation(), ast.getName(), methods[0].arguments()}, new Object[]{methods[1].affiliation(), ast.getName(), methods[1].arguments()});
        return null;
      }else {
        /*
        * TODO check illegal method call
        * ex. instance method call in the static context
        */
        params = doCastInsertion(methods[0].arguments(), params);
        if((methods[0].modifier() & Modifier.STATIC) != 0){
          return new IxCode.CallStatic(targetType, methods[0], params);
        }else {
          return new IxCode.Call(new IxCode.This(targetType), methods[0], params);
        }
      }
    }
    
    private IxCode.Expression[] doCastInsertion(IxCode.TypeRef[] arguments, IxCode.Expression[] params){
      for(int i = 0; i < params.length; i++){
        if(arguments[i] != params[i].type()){
          params[i] = new IxCode.AsInstanceOf(params[i], arguments[i]);
        }
      }
      return params;
    }
    
    public Object visit(MethodCall ast, LocalContext context) {
      IxCode.Expression target = typeCheck(ast.getTarget(), context);
      if(target == null) return null;
      IxCode.Expression[] params = typeCheckExps(ast.getArguments(), context);
      if(params == null) return null;
      IxCode.ObjectTypeRef targetType = (IxCode.ObjectTypeRef) target.type();
      final String name = ast.getName();
      IxCode.MethodRef[] methods = targetType.findMethod(name, params);
      
      if(methods.length == 0){
        report(METHOD_NOT_FOUND, ast, targetType, name, types(params));
        return null;
      }else if(methods.length > 1){
        report(AMBIGUOUS_METHOD, ast, new Object[]{methods[0].affiliation(), name, methods[0].arguments()}, new Object[]{methods[1].affiliation(), name, methods[1].arguments()});
        return null;
      }else if((methods[0].modifier() & Modifier.STATIC) != 0){
        report(ILLEGAL_METHOD_CALL, ast,  methods[0].affiliation(), name, methods[0].arguments());
        return null;
      }else {
        return new IxCode.Call(target, methods[0], doCastInsertion(methods[0].arguments(), params));
      }
    }
    
    public Object visit(StaticIDExpression ast, LocalContext context) {
      IxCode.ClassTypeRef type = (IxCode.ClassTypeRef) CodeAnalysis.this.resolve(ast.getType());
      if(type == null) return null;
      IxCode.FieldRef field = findField(type, ast.getName());
      if(field == null){
        report(FIELD_NOT_FOUND, ast, type, ast.getName());
        return null;
      }
      return new IxCode.StaticFieldRef(type, field);
    }
    
    public Object visit(StaticMethodCall ast, LocalContext context) {
      IxCode.ClassTypeRef type = (IxCode.ClassTypeRef) CodeAnalysis.this.resolve(ast.getTarget());
      IxCode.Expression[] params = typeCheckExps(ast.getArgs(), context);
      if(type == null || params == null) {
        return null;
      }else {
        IxCode.MethodRef[] methods = type.findMethod(ast.getName(), params);
        if(methods.length == 0){
          report(METHOD_NOT_FOUND, ast,  type, ast.getName(), types(params));
          return null;
        }else if(methods.length > 1){
          report(AMBIGUOUS_METHOD, ast,  ast.getName(), typeNames(methods[0].arguments()), typeNames(methods[1].arguments()));
          return null;
        }else {
          return new IxCode.CallStatic(type, methods[0], doCastInsertion(methods[0].arguments(), params));
        }
      }
    }
      
    private String[] typeNames(IxCode.TypeRef[] types) {
      String[] names = new String[types.length];
      for(int i = 0; i < names.length; i++){
        names[i] = types[i].name();
      }
      return names;
    }
    
    private IxCode.Expression[] typeCheckExps(onion.lang.syntax.Expression[] ast, LocalContext context){
      IxCode.Expression[] expressions = new IxCode.Expression[ast.length];
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
      onion.lang.syntax.Expression collectionAST = ast.getCollection();
      try {
        context.openScope();
        IxCode.Expression collection = typeCheck(collectionAST, context);
        Argument arg = ast.getDeclaration();
        accept(arg, context);
        IxCode.ActionStatement block = translate(ast.getStatement(), context);
        if(collection.isBasicType()){
          report(INCOMPATIBLE_TYPE, collectionAST, load("java.util.Collection"), collection.type());
          return null;
        }
        ClosureLocalBinding elementVar = context.lookupOnlyCurrentScope(arg.getName());
        ClosureLocalBinding collectionVar = new ClosureLocalBinding(0, context.add(context.newName(), collection.type()), collection.type());
        IxCode.ActionStatement init;
        if(collection.isArrayType()){
          ClosureLocalBinding counterVariable = new ClosureLocalBinding(0, context.add(context.newName(), IxCode.BasicTypeRef.INT), IxCode.BasicTypeRef.INT);
          init = new IxCode.StatementBlock(
            new IxCode.ExpressionStatement(new IxCode.SetLocal(collectionVar, collection)),
            new IxCode.ExpressionStatement(new IxCode.SetLocal(counterVariable, new IxCode.IntLiteral(0)))
          );
          block = new IxCode.ConditionalLoop(
            new IxCode.BinaryExpression(
              LESS_THAN, IxCode.BasicTypeRef.BOOLEAN,
              ref(counterVariable),
              new IxCode.ArrayLength(ref(collectionVar))
            ),
            new IxCode.StatementBlock(
              assign(elementVar, indexref(collectionVar, ref(counterVariable))),
              block,
              assign(counterVariable, new IxCode.BinaryExpression(ADD, IxCode.BasicTypeRef.INT, ref(counterVariable), new IxCode.IntLiteral(1)))
            )
          );
          return new IxCode.StatementBlock(init, block);
        }else{
          IxCode.ObjectTypeRef iteratorType = load("java.util.Iterator");
          ClosureLocalBinding iteratorVar = new ClosureLocalBinding(0, context.add(context.newName(), iteratorType), iteratorType);
          IxCode.MethodRef mIterator = findMethod(collectionAST, (IxCode.ObjectTypeRef) collection.type(), "iterator");
          IxCode.MethodRef mNext = findMethod(ast.getCollection(), iteratorType, "next");
          IxCode.MethodRef mHasNext = findMethod(ast.getCollection(), iteratorType, "hasNext");
          init = new IxCode.StatementBlock(
            new IxCode.ExpressionStatement(new IxCode.SetLocal(collectionVar, collection)),
            assign(iteratorVar, new IxCode.Call(ref(collectionVar), mIterator, new IxCode.Expression[0]))
          );
          IxCode.Expression next = new IxCode.Call(ref(iteratorVar), mNext, new IxCode.Expression[0]);
          if(elementVar.getType() != rootClass()){
            next = new IxCode.AsInstanceOf(next, elementVar.getType());
          }
          block = new IxCode.ConditionalLoop(
            new IxCode.Call(ref(iteratorVar), mHasNext, new IxCode.Expression[0]),
            new IxCode.StatementBlock(assign(elementVar, next), block)
          );
          return new IxCode.StatementBlock(init, block);
        }
      }finally{
        context.closeScope();
      }
    }
    
    private IxCode.Expression indexref(ClosureLocalBinding bind, IxCode.Expression value) {
      return new IxCode.ArrayRef(new IxCode.RefLocal(bind), value);
    }
    
    private IxCode.ActionStatement assign(ClosureLocalBinding bind, IxCode.Expression value) {
      return new IxCode.ExpressionStatement(new IxCode.SetLocal(bind, value));
    }
    
    private IxCode.Expression ref(ClosureLocalBinding bind) {
      return new IxCode.RefLocal(bind);
    }
    
    public Object visit(onion.lang.syntax.ExpressionStatement ast, LocalContext context) {
      IxCode.Expression expression = typeCheck(ast.getExpression(), context);
      return new IxCode.ExpressionStatement(expression);
    }
    
    public Object visit(CondStatement node, LocalContext context) {
      try {
        context.openScope();
        
        int size = node.size();
        Stack exprs = new Stack();
        Stack stmts = new Stack();
        for(int i = 0; i < size; i++){        
          onion.lang.syntax.Expression expr = node.getCondition(i);
          Statement  stmt = node.getBlock(i);
          IxCode.Expression texpr = typeCheck(expr, context);
          if(texpr != null && texpr.type() != IxCode.BasicTypeRef.BOOLEAN){
            IxCode.TypeRef expect = IxCode.BasicTypeRef.BOOLEAN;
            IxCode.TypeRef actual = texpr.type();
            report(INCOMPATIBLE_TYPE, expr, expect, actual);
          }
          exprs.push(texpr);
          IxCode.ActionStatement tstmt = translate(stmt, context);
          stmts.push(tstmt);
        }
        
        Statement elseStmt = node.getElseBlock();
        IxCode.ActionStatement result = null;
        if(elseStmt != null){
          result = translate(elseStmt, context);
        }
        
        for(int i = 0; i < size; i++){
          IxCode.Expression expr = (IxCode.Expression)exprs.pop();
          IxCode.ActionStatement stmt = (IxCode.ActionStatement)stmts.pop();
          result = new IxCode.IfStatement(expr, stmt, result);
        }
        
        return result;
      }finally{
        context.closeScope();
      }
    }
    
    public Object visit(ForStatement ast, LocalContext context) {
      try{
        context.openScope();
        
        IxCode.ActionStatement init = null;
        if(ast.getInit() != null){
          init = translate(ast.getInit(), context);
        }else{
          init = new IxCode.NOP();
        }
        IxCode.Expression condition;
        onion.lang.syntax.Expression astCondition = ast.getCondition();
        if(astCondition != null){
          condition = typeCheck(ast.getCondition(), context);
          IxCode.TypeRef expected = IxCode.BasicTypeRef.BOOLEAN;
          if(condition != null && condition.type() != expected){
            IxCode.TypeRef appeared = condition.type();
            report(INCOMPATIBLE_TYPE, astCondition, expected, appeared);
          }
        }else{
          condition = new IxCode.BoolLiteral(true);
        }
        IxCode.Expression update = null;
        if(ast.getUpdate() != null){
          update = typeCheck(ast.getUpdate(), context);
        }
        IxCode.ActionStatement loop = translate(
          ast.getBlock(), context);
        if(update != null){
          loop = new IxCode.StatementBlock(loop, new IxCode.ExpressionStatement(update));
        }
        IxCode.ActionStatement result = new IxCode.ConditionalLoop(condition, loop);
        result = new IxCode.StatementBlock(init, result);
        return result;
      }finally{
        context.closeScope();
      }
    }
    
    public Object visit(BlockStatement ast, LocalContext context) {
      Statement[] astStatements = ast.getStatements();
      IxCode.ActionStatement[] statements = new IxCode.ActionStatement[astStatements.length];
      try{
        context.openScope();
        for(int i = 0; i < astStatements.length; i++){
          statements[i] = translate(astStatements[i], context);
        }
        return new IxCode.StatementBlock(statements);
      }finally{
        context.closeScope();
      }
    }
    
    public Object visit(onion.lang.syntax.IfStatement ast, LocalContext context) {
      try{
        context.openScope();
        
        IxCode.Expression condition = typeCheck(ast.getCondition(), context);
        IxCode.TypeRef expected = IxCode.BasicTypeRef.BOOLEAN;
        if(condition != null && condition.type() != expected){
          report(INCOMPATIBLE_TYPE, ast.getCondition(), expected, condition.type());
        }
        IxCode.ActionStatement thenBlock = translate(ast.getThenBlock(), context);
        IxCode.ActionStatement elseBlock = ast.getElseBlock() == null ? null : translate(ast.getElseBlock(), context);
        return new IxCode.IfStatement(condition, thenBlock, elseBlock);
      }finally{     
        context.closeScope();
      }
    }
    
    public Object visit(WhileStatement ast, LocalContext context) {
      try{
        context.openScope();
        
        IxCode.Expression condition = typeCheck(ast.getCondition(), context);
        IxCode.TypeRef expected = IxCode.BasicTypeRef.BOOLEAN;
        if(condition != null && condition.type() != expected){
          IxCode.TypeRef actual = condition.type();
          report(INCOMPATIBLE_TYPE, ast, expected, actual);
        }
        IxCode.ActionStatement thenBlock = translate(ast.getBlock(), context);
        return new IxCode.ConditionalLoop(condition, thenBlock);
      }finally{
        context.closeScope();
      }     
    }
    
    public Object visit(ReturnStatement ast, LocalContext context) {
      IxCode.TypeRef returnType = ((LocalContext)context).returnType();
      if(ast.getExpression() == null){
        IxCode.TypeRef expected = IxCode.BasicTypeRef.VOID;
        if(returnType != expected){
          report(CANNOT_RETURN_VALUE, ast);
        }
        return new IxCode.Return(null);
      }else{
        IxCode.Expression returned = typeCheck(ast.getExpression(), context);
        if(returned == null) return new IxCode.Return(null);
        if(returned.type() == IxCode.BasicTypeRef.VOID){
          report(CANNOT_RETURN_VALUE, ast);
        }else {
          returned = processAssignable(ast.getExpression(), returnType, returned);
          if(returned == null) return new IxCode.Return(null);
        }
        return new IxCode.Return(returned);
      }
    }
    
    IxCode.Expression processAssignable(AstNode ast, IxCode.TypeRef a, IxCode.Expression b){
      if(b == null) return null;
      if(a == b.type()) return b;
      if(!IxCode.TypeRules.isAssignable(a, b.type())){
        report(INCOMPATIBLE_TYPE, ast, a, b.type());
        return null;
      }
      b = new IxCode.AsInstanceOf(b, a);
      return b;
    }
    
    public Object visit(SelectStatement ast, LocalContext context) {
      try{
        context.openScope();
        IxCode.Expression condition = typeCheck(ast.getCondition(), context);
        if(condition == null){
          return new IxCode.NOP();
        }
        String name = context.newName();
        int index = context.add(name, condition.type());
        IxCode.ActionStatement statement;
        if(ast.getCases().length == 0){
          if(ast.getElseBlock() != null){
            statement = translate(ast.getElseBlock(), context);
          }else{
            statement = new IxCode.NOP();
          }
        }else{
          statement = processCases(ast, condition, name, context);
        }
        IxCode.StatementBlock block = new IxCode.StatementBlock(
          new IxCode.ExpressionStatement(new IxCode.SetLocal(0, index, condition.type(), condition)),
          statement
        );
        return block;
      }finally{
        context.closeScope();
      }
    }
    
    IxCode.ActionStatement processCases(
      SelectStatement ast, IxCode.Expression cond, String var, LocalContext context
    ) {
      CaseBranch[] cases = ast.getCases();
      List nodes = new ArrayList();
      List thens = new ArrayList();
      for(int i = 0; i < cases.length; i++){
        onion.lang.syntax.Expression[] astExpressions = cases[i].getExpressions();
        ClosureLocalBinding bind = context.lookup(var);
        nodes.add(processNodes(astExpressions, cond.type(), bind, context));
        thens.add(translate(cases[i].getBlock(), context));
      }
      IxCode.ActionStatement statement;
      if(ast.getElseBlock() != null){
        statement = translate(ast.getElseBlock(), context);
      }else{
        statement = null;
      }
      for(int i = cases.length - 1; i >= 0; i--){
        IxCode.Expression value = (IxCode.Expression) nodes.get(i);
        IxCode.ActionStatement then = (IxCode.ActionStatement) thens.get(i);
        statement = new IxCode.IfStatement(value, then, statement);
      }
      return statement;
    }
    
    IxCode.Expression processNodes(Expression[] asts, IxCode.TypeRef type, ClosureLocalBinding bind, LocalContext context) {
      IxCode.Expression[] nodes = new IxCode.Expression[asts.length];
      boolean error = false;
      for(int i = 0; i < asts.length; i++){
        nodes[i] = typeCheck(asts[i], context);
        if(nodes[i] == null){
          error = true;
        }else if(!IxCode.TypeRules.isAssignable(type, nodes[i].type())){
          report(INCOMPATIBLE_TYPE, asts[i], type, nodes[i].type());
          error = true;
        }else {
          if(nodes[i].isBasicType() && nodes[i].type() != type) nodes[i] = new IxCode.AsInstanceOf(nodes[i], type);
          if(nodes[i].isReferenceType() && nodes[i].type() != rootClass()) nodes[i] = new IxCode.AsInstanceOf(nodes[i], rootClass());
        }
      }
      if(!error){
        IxCode.Expression node;
        if(nodes[0].isReferenceType()){
          node = createEquals(IxCode.BinaryExpression.Constants.EQUAL, new IxCode.RefLocal(bind), nodes[0]);
        }else{
          node = new IxCode.BinaryExpression(EQUAL, IxCode.BasicTypeRef.BOOLEAN, new IxCode.RefLocal(bind), nodes[0]);
        }
        for(int i = 1; i < nodes.length; i++){
          node = new IxCode.BinaryExpression(
            LOGICAL_OR, IxCode.BasicTypeRef.BOOLEAN, node,
            new IxCode.BinaryExpression(EQUAL, IxCode.BasicTypeRef.BOOLEAN, new IxCode.RefLocal(bind), nodes[i])
          );
        }
        return node;
      }else{
        return null;
      }
    }
    
    public Object visit(ThrowStatement ast, LocalContext context) {
      IxCode.Expression expression = typeCheck(ast.getExpression(), context);
      if(expression != null){
        IxCode.TypeRef expected = load("java.lang.Throwable");
        IxCode.TypeRef detected = expression.type();
        if(!IxCode.TypeRules.isSuperType(expected, detected)){
          report(INCOMPATIBLE_TYPE, ast.getExpression(),  expected, detected);
        }
      }
      return new IxCode.Throw(expression);
    }
    
    public Object visit(LocalVariableDeclaration ast, LocalContext context) {
      onion.lang.syntax.Expression initializer = ast.getInit();
      ClosureLocalBinding  binding = context.lookupOnlyCurrentScope(ast.getName());
      if(binding != null){
        report(DUPLICATE_LOCAL_VARIABLE, ast, ast.getName());
        return new IxCode.NOP();
      }
      IxCode.TypeRef leftType = CodeAnalysis.this.resolve(ast.getType());
      if(leftType == null) return new IxCode.NOP();
      int index = context.add(ast.getName(), leftType);
      IxCode.SetLocal node;
      if(initializer != null){
        IxCode.Expression valueNode = typeCheck(initializer, context);
        if(valueNode == null) return new IxCode.NOP();
        valueNode = processAssignable(initializer, leftType, valueNode);
        if(valueNode == null) return new IxCode.NOP();
        node = new IxCode.SetLocal(0, index, leftType, valueNode);
      }else{
        node = new IxCode.SetLocal(0, index, leftType, defaultValue(leftType));
      }
      return new IxCode.ExpressionStatement(node);
    }
    
    public IxCode.Expression defaultValue(IxCode.TypeRef type) {
      return IxCode.Expression.defaultValue(type);
    }
    
    public Object visit(EmptyStatement ast, LocalContext context) {
      return new IxCode.NOP();
    }
    
    public Object visit(TryStatement ast, LocalContext context) {
      IxCode.ActionStatement tryStatement = translate(ast.getTryBlock(), context);
      ClosureLocalBinding[] binds = new ClosureLocalBinding[ast.getArguments().length];
      IxCode.ActionStatement[] catchBlocks = new IxCode.ActionStatement[ast.getArguments().length];
      for(int i = 0; i < ast.getArguments().length; i++){
        context.openScope();
        IxCode.TypeRef arg = (IxCode.TypeRef)accept(ast.getArguments()[i], context);
        IxCode.TypeRef expected = load("java.lang.Throwable");
        if(!IxCode.TypeRules.isSuperType(expected, arg)){
          report(INCOMPATIBLE_TYPE, ast.getArguments()[i], expected, arg);
        }
        binds[i] = context.lookupOnlyCurrentScope(ast.getArguments()[i].getName());
        catchBlocks[i] = translate(ast.getRecBlocks()[i], context);
        context.closeScope();
      }
      return new IxCode.Try(tryStatement, binds, catchBlocks);
    }
    
    public Object visit(SynchronizedStatement ast, LocalContext context) {
      IxCode.Expression lock = typeCheck(ast.getTarget(), context);
      IxCode.ActionStatement block = translate(ast.getBlock(), context);
      report(UNIMPLEMENTED_FEATURE, ast);
      return new IxCode.Synchronized(lock, block);
    }
    
    public Object visit(BreakStatement ast, LocalContext context) {
      report(UNIMPLEMENTED_FEATURE, ast);
      return new IxCode.Break();
    }
    
    public Object visit(ContinueStatement ast, LocalContext context) {
      report(UNIMPLEMENTED_FEATURE, ast);
      return new IxCode.Continue();
    }
  //-------------------------------------------------------------------------//
    
  //----------------------------- members ----------------------------------------//  
    public Object visit(FunctionDeclaration ast, LocalContext local) {
      IxCode.MethodDefinition function = (IxCode.MethodDefinition) lookupKernelNode(ast);
      if(function == null) return null;
      LocalContext context = new LocalContext();
      if(Modifier.isStatic(function.modifier())){
        context.setStatic(true);
      }
      context.setMethod(function);
      IxCode.TypeRef[] arguments = function.arguments();
      for(int i = 0; i < arguments.length; i++){
        context.add(ast.getArguments()[i].getName(), arguments[i]);
      }
      IxCode.StatementBlock block = (IxCode.StatementBlock) accept(ast.getBlock(), context);
      block = addReturnNode(block, function.returnType());
      function.setBlock(block);
      function.setFrame(context.getContextFrame());
      return null;
    }
    
    public IxCode.StatementBlock addReturnNode(IxCode.ActionStatement node, IxCode.TypeRef returnType) {
      return new IxCode.StatementBlock(node, new IxCode.Return(defaultValue(returnType)));
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
    
    public Object visit(MethodDeclaration ast, LocalContext local) {
      IxCode.MethodDefinition method = (IxCode.MethodDefinition) lookupKernelNode(ast);
      if(method == null) return null;
      if(ast.getBlock() == null) return null;
      LocalContext context = new LocalContext();
      if(Modifier.isStatic(method.modifier())){
        context.setStatic(true);
      }
      context.setMethod(method);
      IxCode.TypeRef[] arguments = method.arguments();
      for(int i = 0; i < arguments.length; i++){
        context.add(ast.getArguments()[i].getName(), arguments[i]);
      }    
      IxCode.StatementBlock block = (IxCode.StatementBlock) accept(ast.getBlock(), context);
      block = addReturnNode(block, method.returnType());
      method.setBlock(block);
      method.setFrame(context.getContextFrame());
      return null;
    }
    
    public Object visit(ConstructorDeclaration ast, LocalContext local) {
      IxCode.ConstructorDefinition constructor = (IxCode.ConstructorDefinition) lookupKernelNode(ast);
      if(constructor == null) return null;
      LocalContext context = new LocalContext();
      context.setConstructor(constructor);
      IxCode.TypeRef[] args = constructor.getArgs();
      for(int i = 0; i < args.length; i++){
        context.add(ast.getArguments()[i].getName(), args[i]);
      }
      IxCode.Expression[] params = typeCheckExps(ast.getInitializers(), context);
      IxCode.StatementBlock block = (IxCode.StatementBlock) accept(ast.getBody(), context);
      IxCode.ClassDefinition currentClass = contextClass;
      IxCode.ClassTypeRef superClass = currentClass.getSuperClass();
      IxCode.ConstructorRef[] matched = superClass.findConstructor(params);
      if(matched.length == 0){
        report(CONSTRUCTOR_NOT_FOUND, ast, superClass, types(params));
        return null;
      }
      if(matched.length > 1){
        report(AMBIGUOUS_CONSTRUCTOR, ast, new Object[]{superClass, types(params)}, new Object[]{superClass, types(params)});
        return null;
      }
      IxCode.Super init = new IxCode.Super(
        superClass, matched[0].getArgs(), params
      );
      constructor.setSuperInitializer(init);
      block = addReturnNode(block, IxCode.BasicTypeRef.VOID);
      constructor.setBlock(block);
      constructor.setFrame(context.getContextFrame());
      return null;
    }
  //----------------------------------------------------------------------------// 
  //----------------------------------------------------------------------------//
  
    public IxCode.TypeRef resolve(TypeSpec type, NameResolution resolver) {
      IxCode.TypeRef resolvedType = (IxCode.TypeRef) resolver.resolve(type);
      if(resolvedType == null){
        report(CLASS_NOT_FOUND, type, type.getComponentName());
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
    
    boolean hasNumericType(IxCode.Expression expression){
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
    
    IxCode.Expression typeCheck(onion.lang.syntax.Expression expression, LocalContext context){
      return (IxCode.Expression) expression.accept(this, context);
    }
      
    IxCode.ActionStatement translate(Statement statement, LocalContext context){
      return (IxCode.ActionStatement) statement.accept(this, context);
    }
  }
  
  public CodeAnalysis(CompilerConfig config) {
    this.config   = config;
    this.table    = new ClassTable(classpath(config.getClassPath()));
    this.irt2ast  = new HashMap<IxCode.Node, AstNode>();
    this.ast2irt  = new HashMap<AstNode, IxCode.Node>();
    this.solvers  = new HashMap<String, NameResolution>();
    this.reporter = new SemanticErrorReporter(this.config.getMaxErrorReports());
  }
  
  public IxCode.ClassDefinition[] process(CompilationUnit[] units){
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

  /**
   * @author Kota Mizushima
   * Date: 2005/06/13
   */
  public class NameResolution {
    private ImportList imports;

    public NameResolution(ImportList imports) {
      this.imports = imports;
    }

    public IxCode.TypeRef resolve(TypeSpec specifier) {
      RawTypeNode component = specifier.getComponent();
      String name = component.name();
      IxCode.TypeRef resolvedType;
      if(component.kind() == RawTypeNode.BASIC){
        resolvedType =
          name.equals("char") ? IxCode.BasicTypeRef.CHAR :
          name.equals("byte") ? IxCode.BasicTypeRef.BYTE :
          name.equals("short") ? IxCode.BasicTypeRef.SHORT :
          name.equals("int") ? IxCode.BasicTypeRef.INT :
          name.equals("long") ? IxCode.BasicTypeRef.LONG :
          name.equals("float") ? IxCode.BasicTypeRef.FLOAT :
          name.equals("double") ? IxCode.BasicTypeRef.DOUBLE :
          name.equals("boolean") ? IxCode.BasicTypeRef.BOOLEAN :
                                    IxCode.BasicTypeRef.VOID;
      }else if(component.kind() == RawTypeNode.NOT_QUALIFIED){
        resolvedType = forName(name, false);
      }else{
        resolvedType = forName(name, true);
      }
      IxCode.TypeRef componentType = resolvedType;
      if(specifier.getDimension() > 0){
        return table.loadArray(componentType, specifier.getDimension());
      }else{
        return componentType;
      }
    }

    private IxCode.ClassTypeRef forName(String name, boolean qualified) {
      if(qualified) {
        return table.load(name);
      }else {
        for(int i = 0; i < imports.size(); i++){
          String qualifiedName = imports.get(i).match(name);
          if(qualifiedName != null){
            IxCode.ClassTypeRef resolvedSymbol = forName(qualifiedName, true);
            if(resolvedSymbol != null) return resolvedSymbol;
          }
        }
        return null;
      }
    }
  }
}
