/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.pass;

import java.util.*;

import onion.compiler.*;
import onion.compiler.env.*;
import onion.compiler.util.*;
import onion.lang.core.*;
import onion.lang.core.type.*;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.*;
import org.onion_lang.onion.lang.syntax.Modifier;

/**
 * @author Kota Mizushima Date: 2005/04/10
 */
public class CodeGeneration implements IrBinExp.Constants, IrUnaryExp.Constants {
  private CompilerConfig config;
  private List compiledClasses = new ArrayList();
  private SymbolGenerator generator;
  
  private static Map unboxingMethods = new HashMap(){{
    put("java.lang.Byte", "byteValue");
    put("java.lang.Short", "shortValue");
    put("java.lang.Character", "charValue");
    put("java.lang.Integer", "intValue");
    put("java.lang.Long", "longValue");
    put("java.lang.Float", "floatValue");
    put("java.lang.Double", "doubleValue");
    put("java.lang.Boolean", "booleanValue");
  }};
  
  private static final String FRAME_PREFIX = "frame";
  private static final String CLOSURE_CLASS_SUFFIX = "Closure";
  private VMTypeBridge bridge;
  
  public CodeGeneration(CompilerConfig config) {
    this.config = config;
    this.bridge = new VMTypeBridge();
  }

  public CompiledClass[] process(IrClass[] classes) {
    compiledClasses.clear();
    String base = config.getOutputDirectory();
    base = base != null ? base : ".";
    base += Systems.getFileSeparator();
    for (int i = 0; i < classes.length; i++) {
      codeClass(classes[i]);
    }
    CompiledClass[] classFiles = new CompiledClass[compiledClasses.size()];
    for(int i = 0; i < compiledClasses.size(); i++){
      JavaClass clazz = (JavaClass) compiledClasses.get(i);
      String outDir = getOutputDir(base, clazz.getClassName());
      classFiles[i] = new CompiledClass(clazz.getClassName(), outDir, clazz.getBytes());
    }
    return classFiles;
  }
  
  private String getOutputDir(String base, String fqcn){
    String packageName = getPackageName(fqcn);
    return base + packageName.replaceAll(".", Systems.getFileSeparator());
  }
  
  private String getPackageName(String fqcn){
    int index = fqcn.lastIndexOf("\\.");
    if(index < 0){
      return "";
    }else{
      return fqcn.substring(0, index);
    }
  }
  
  private int classModifier(IrClass node){
    int modifier = toJavaModifier(node.getModifier());
    modifier |= node.isInterface() ? Constants.ACC_INTERFACE : modifier;
    modifier |= (!Modifier.isInternal(modifier)) ? Constants.ACC_PUBLIC : modifier;
    return modifier;
  }

  public void codeClass(IrClass node) {
    int modifier = classModifier(node);
    String className = node.getName();
    generator = new SymbolGenerator(className + CLOSURE_CLASS_SUFFIX);
    String superClass = node.getSuperClass().getName();
    String[] interfaces = namesOf(node.getInterfaces());
    String file = node.getSourceFile();
    ClassGen gen = new ClassGen(className, superClass, file, modifier, interfaces);
    ConstructorSymbol[] constructors = node.getConstructors();
    for (int i = 0; i < constructors.length; i++) {
      codeConstructor(gen, ((IrConstructor) constructors[i]));
    }
    MethodSymbol[] methods = node.getMethods();
    for (int i = 0; i < methods.length; i++) {
      codeMethod(gen, ((IrMethod) methods[i]));
    }
    FieldSymbol[] fields = node.getFields();
    for (int i = 0; i < fields.length; i++) {
      codeField(gen, ((IrField) fields[i]));
    }
    compiledClasses.add(gen.getJavaClass());
  }
  
  public InstructionHandle codeExpressions(IrExpression[] nodes, CodeProxy code){
    InstructionHandle start;
    if(nodes.length > 0){
      start = codeExpression(nodes[0], code);
      for(int i = 1; i < nodes.length; i++){
        codeExpression(nodes[i], code);
      }
    }else{
      start = code.append(InstructionConstants.NOP);
    }
    return start;
  }

  public void codeConstructor(ClassGen gen, IrConstructor node) {
    CodeProxy code = new CodeProxy(gen.getConstantPool());
    LocalFrame frame = node.getFrame();
    code.setFrame(frame);
    String[] args = new String[node.getArgs().length];
    for (int i = 0; i < args.length; i++) {
      args[i] = "arg" + i;
    }
    ObjectType classType = (ObjectType) typeOf(node.getClassType());
    int modifier = toJavaModifier(node.getModifier());
    Type[] arguments = typesOf(node.getArgs());
    MethodGen method = new MethodGen(
      modifier, Type.VOID, arguments, args, "<init>",
      classType.getClassName(), code.getCode(), gen.getConstantPool());
    if(frame.isClosed()){
      int frameObjectIndex = frameObjectIndex(1, node.getArgs());
      code.setFrameObjectIndex(frameObjectIndex);
      code.setIndexTable(indexTableFor(frame));   
      appendInitialCode(code, frame, arguments, 1);
    }else{
      code.setIndexTable(indexTableFor(1, frame));
    }
    code.setMethod(method);
    IrSuper init = node.getSuperInitializer();
    classType = (ObjectType) typeOf(init.getClassType());
    arguments = typesOf(init.getArguments());    
    code.append(InstructionConstants.ALOAD_0);
    codeExpressions(init.getExpressions(), code);
    code.appendCallConstructor(classType, arguments);
    codeBlock(node.getBlock(), code);
    method.setMaxLocals();
    method.setMaxStack();
    code.appendReturn(typeOf(BasicTypeRef.VOID));
    gen.addMethod(method.getMethod());
  }

  public void codeMethod(ClassGen gen, IrMethod node) {
    CodeProxy code = new CodeProxy(gen.getConstantPool());
    LocalFrame frame = node.getFrame();
    code.setFrame(frame);
    
    int modifier = toJavaModifier(node.getModifier());
    Type returned = typeOf(node.getReturnType());
    Type[] arguments = typesOf(node.getArguments());
    String[] argNames = names(arguments.length);
    String name = node.getName();
    String className = node.getClassType().getName();
    MethodGen method = new MethodGen(
      modifier, returned, arguments, argNames, name, className,
      code.getCode(), gen.getConstantPool());
    code.setMethod(method);
    if (!Modifier.isAbstract(node.getModifier())) {   
      if(frame.isClosed()){
        int origin;
        if(Modifier.isStatic(node.getModifier())){
          code.setFrameObjectIndex(
            frameObjectIndex(0, node.getArguments()));
          origin = 0;
        }else{
          code.setFrameObjectIndex(
            frameObjectIndex(1, node.getArguments()));
          origin = 1;
        }
        code.setIndexTable(indexTableFor(frame));
        appendInitialCode(code, frame, arguments, origin);
      }else{
        if (Modifier.isStatic(node.getModifier())) {
          code.setIndexTable(indexTableFor(0, frame));
        } else {
          code.setIndexTable(indexTableFor(1, frame));
        }
      }
      codeBlock(node.getBlock(), code);
      method.setMaxLocals();
      method.setMaxStack();
    }
    gen.addMethod(method.getMethod());
  }
  
  private void appendInitialCode(CodeProxy code, LocalFrame frame, Type[] arguments, int origin) {
    int frameObjectIndex = code.getFrameObjectIndex();
    code.appendConstant(new Integer(frame.entries().length));
    code.appendNewArray(Type.OBJECT, (short)1);
    code.appendDup(1);
    code.appendStore(new ArrayType(Type.OBJECT, 1), frameObjectIndex);
    int index = origin;
    for(int i = 0; i < arguments.length;){
      Type arg = arguments[i];
      code.appendDup(1);
      code.appendConstant(new Integer(i));
      if(arguments[i] instanceof BasicType){
        ObjectType boxed = code.boxing(arg);
        code.appendNew(boxed);
        code.appendDup(1);
        code.appendLoad(arg, index + i);
        code.appendCallConstructor(boxed, new Type[]{arg});
      }else{
        code.appendLoad(arg, index + i);
      }
      code.appendArrayStore(Type.OBJECT);
      if(arg == Type.DOUBLE || arg == Type.LONG){
        i += 2;
      }else{
        i++;
      }
    }
  }

  private void implementsMethods(ClassGen gen, MethodSymbol[] methods){
    for(int i = 0; i < methods.length; i++){
      MethodSymbol method = methods[i];
      Type returnType = typeOf(method.getReturnType());
      String name = method.getName();
      Type[] args = typesOf(method.getArguments());
      String[] argNames = names(args.length);
      CodeProxy code = new CodeProxy(gen.getConstantPool());
      MethodGen mgen = new MethodGen(
        Constants.ACC_PUBLIC, returnType, args, argNames, name, 
        gen.getClassName(), code.getCode(), gen.getConstantPool());
      code.appendDefaultValue(returnType);
      code.appendReturn(returnType);
      mgen.setMaxLocals();
      mgen.setMaxStack();
      gen.addMethod(mgen.getMethod());
    }
  }
  
  public InstructionHandle codeClosure(IrClosure node, CodeProxy code){
    ClassSymbol classType = node.getClassType();
    String closureName = generator.generate();
    Type[] arguments = typesOf(node.getArguments());
    ClassGen gen = new ClassGen(
      closureName, "java.lang.Object", "<generated>", Constants.ACC_PUBLIC,
      new String[]{classType.getName()});
    
    Set methods = Classes.getInterfaceMethods(classType);
    methods.remove(node.getMethod());
    implementsMethods(
      gen, (MethodSymbol[]) methods.toArray(new MethodSymbol[0]));
    
    LocalFrame frame = node.getFrame();
    int depth = frame.depth();
    for(int i = 1; i <= depth; i++){
      FieldGen field = new FieldGen(
        Constants.ACC_PRIVATE, new ArrayType("java.lang.Object", 1),
        FRAME_PREFIX + i,
        gen.getConstantPool());
      gen.addField(field.getField());
    }
    Type[] types = closureArguments(depth);
    MethodGen method = 
      createClosureConstructor(closureName, types, gen.getConstantPool());
    gen.addMethod(method.getMethod());
    
    CodeProxy closureCode = new CodeProxy(gen.getConstantPool());
    method = new MethodGen(
      Constants.ACC_PUBLIC, typeOf(node.getReturnType()),
      arguments, names(arguments.length), node.getName(), 
      closureName, closureCode.getCode(), gen.getConstantPool());
    closureCode.setMethod(method);
    closureCode.setFrame(frame);
    if(frame.isClosed()){
      int frameObjectIndex = frameObjectIndex(1, node.getArguments());
      closureCode.setFrameObjectIndex(frameObjectIndex);
      closureCode.setIndexTable(indexTableFor(frame));      
      appendInitialCode(closureCode, frame, arguments, 1);
    }else{
      closureCode.setIndexTable(indexTableFor(1, frame));
    }
    codeStatement(node.getBlock(), closureCode);
    method.setMaxLocals();
    method.setMaxStack();
    gen.addMethod(method.getMethod());
    compiledClasses.add(gen.getJavaClass());
    
    InstructionHandle start = code.appendNew(new ObjectType(closureName));
    code.appendDup(1);
    String name = code.getMethod().getClassName();
    int index = code.getFrameObjectIndex();
    code.appendLoad(new ArrayType("java.lang.Object", 1), index);
    for(int i = 1; i < depth; i++){
      code.appendThis();
      code.appendGetField(
        name, FRAME_PREFIX + i, new ArrayType("java.lang.Object", 1));
    }
    code.appendCallConstructor(
      new ObjectType(closureName), closureArguments(depth));
    return start;
  }
  
  private Type[] closureArguments(int size){
    Type[] arguments = new Type[size];
    for(int i = 0; i < arguments.length; i++){
      arguments[i] = new ArrayType("java.lang.Object", 1);
    }
    return arguments;
  }
  
  private InstructionHandle codeList(IrList node, CodeProxy code){
    ObjectType listType = (ObjectType) typeOf(node.type());
    InstructionHandle start = code.appendNew("java.util.ArrayList");
    code.appendDup(1);
    code.appendCallConstructor(
      new ObjectType("java.util.ArrayList"), new Type[0]);
    IrExpression[] elements = node.getElements();
    for(int i = 0; i < elements.length; i++){
      code.appendDup(1);
      codeExpression(elements[i], code);
      code.appendInvoke(
        listType.getClassName(), "add", Type.BOOLEAN, new Type[]{Type.OBJECT}, 
        Constants.INVOKEINTERFACE);
      code.appendPop(1);
    }
    return start;
  }
  
  public InstructionHandle codeSuperCall(IrCallSuper node, CodeProxy code){
    InstructionHandle start = codeExpression(node.getTarget(), code);
    codeExpressions(node.getParams(), code);
    MethodSymbol method = node.getMethod();
    code.appendInvoke(
      method.getClassType().getName(),
      method.getName(),
      typeOf(method.getReturnType()),
      typesOf(method.getArguments()),
      Constants.INVOKESPECIAL);
    return start;
  }
  
  private String[] names(int size){
    String[] names = new String[size];
    for(int i = 0; i < names.length; i++){
      names[i] = "args" + size;
    }
    return names;
  }
  
  private MethodGen createClosureConstructor(
    String className, Type[] types, ConstantPoolGen pool){
    String[] argNames = new String[types.length];
    for(int i = 0; i < types.length; i++){
      argNames[i] = FRAME_PREFIX + i;
    }
    CodeProxy code = new CodeProxy(pool);
    MethodGen constructor = new MethodGen(
      Constants.ACC_PUBLIC, Type.VOID, types, argNames, "<init>",
      className, code.getCode(), pool);
    code.appendThis();
    code.appendCallConstructor(Type.OBJECT, new Type[0]);
    for(int i = 0; i < types.length; i++){
      code.appendThis();
      code.appendLoad(types[i], i + 1);
      code.appendPutField(className, FRAME_PREFIX + (i + 1), types[i]);
    }
    code.append(InstructionConstants.RETURN);
    constructor.setMaxLocals();
    constructor.setMaxStack();
    return constructor;
  }
  
  public void codeField(ClassGen gen, IrField node) {
    FieldGen field = new FieldGen(
      toJavaModifier(node.getModifier()), 
      typeOf(node.getType()), node.getName(), gen.getConstantPool());
    gen.addField(field.getField());
  }

  public InstructionHandle codeBlock(IrBlock node, CodeProxy code) {
    InstructionHandle start;
    if(node.getStatements().length > 0){
      start = codeStatement(node.getStatements()[0], code);
      for (int i = 1; i < node.getStatements().length; i++) {
        codeStatement(node.getStatements()[i], code);
      }
    }else{
      start = code.append(InstructionConstants.NOP);
    }
    return start;
  }

  public InstructionHandle codeExpressionStatement(IrExpStmt node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.expression, code);
    TypeRef type = node.expression.type();
    if (type != BasicTypeRef.VOID) {
      if (isWideType(type)) {
        code.append(InstructionConstants.POP2);
      } else {
        code.append(InstructionConstants.POP);
      }
    }
    return start;
  }

  public InstructionHandle codeStatement(IrStatement node, CodeProxy code) {
    InstructionHandle start;
    if (node instanceof IrBlock) {
      start = codeBlock((IrBlock) node, code);
    } else if (node instanceof IrExpStmt) {
      start = codeExpressionStatement((IrExpStmt) node, code);
    } else if (node instanceof IrIf) {
      start = codeIf((IrIf) node, code);
    } else if (node instanceof IrLoop) {
      start = codeLoop((IrLoop) node, code);
    } else if (node instanceof IrNOP) {
      start = codeEmpty((IrNOP) node, code);
    } else if (node instanceof IrReturn) {
      start = codeReturn((IrReturn) node, code);
    } else if (node instanceof IrSynchronized) {
      start = codeSynchronized((IrSynchronized) node, code);
    } else if (node instanceof IrThrow) {
      start = codeThrowNode((IrThrow)node, code);
    } else if (node instanceof IrTry){
      start = codeTry((IrTry)node, code);
    } else {
      start = code.append(InstructionConstants.NOP);
    }
    return start;
  }

  public InstructionHandle codeReturn(IrReturn node, CodeProxy code) {
    InstructionHandle start;
    if (node.expression != null) {
      start = codeExpression(node.expression, code);
      Type type = typeOf(node.expression.type());
      code.appendReturn(type);
    } else {
      start = code.append(InstructionConstants.RETURN);
    }
    return start;
  }

  public InstructionHandle codeSynchronized(IrSynchronized node, CodeProxy code) {
    return null;
  }
  
  public InstructionHandle codeThrowNode(IrThrow node, CodeProxy code){
    InstructionHandle start = codeExpression(node.expression, code);
    code.append(InstructionConstants.ATHROW);
    return start;
  }
  
  public InstructionHandle codeTry(IrTry node, CodeProxy code){
    InstructionHandle start = codeStatement(node.tryStatement, code);    
    BranchHandle to = code.append(new GOTO(null));
    int length = node.catchTypes.length;
    BranchHandle[] catchEnds = new BranchHandle[length];
    for(int i = 0; i < length; i++){
      ClosureLocalBinding bind = node.catchTypes[i];
      int index = code.getIndexTable()[bind.getIndex()];
      ObjectType type = (ObjectType) typeOf(bind.getType());
      InstructionHandle target = code.appendStore(type, index);
      code.addExceptionHandler(start, to, target, type);
      codeStatement(node.catchStatements[i], code);
      catchEnds[i] = code.append(new GOTO(null));
    }
    InstructionHandle end = code.append(InstructionConstants.NOP);
    to.setTarget(end);
    for(int i = 0; i < catchEnds.length; i++){
      catchEnds[i].setTarget(end);
    }
    return start;
  }

  public InstructionHandle codeEmpty(IrNOP node, CodeProxy code) {
    return code.append(InstructionConstants.NOP);
  }

  public InstructionHandle codeIf(IrIf node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getCondition(), code);
    BranchHandle toThen = code.append(new IFNE(null));
    if (node.getElseStatement() != null) {
      codeStatement(node.getElseStatement(), code);
    }
    BranchHandle toEnd = code.append(new GOTO(null));
    toThen.setTarget(codeStatement(node.getThenStatement(), code));
    toEnd.setTarget(code.append(new NOP()));
    return start;
  }

  public InstructionHandle codeLoop(IrLoop node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.condition, code);
    BranchHandle branch = code.append(new IFEQ(null));
    codeStatement(node.stmt, code);
    code.append(new GOTO(start));    
    InstructionHandle end = code.append(InstructionConstants.NOP);
    branch.setTarget(end);
    return start;
  }

  private static int toJavaModifier(int src) {
    int modifier = 0;
    modifier |= Modifier.isPrivate(src) ? Constants.ACC_PRIVATE : modifier;
    modifier |= Modifier.isProtected(src) ? Constants.ACC_PROTECTED : modifier;
    modifier |= Modifier.isPublic(src) ? Constants.ACC_PUBLIC : modifier;
    modifier |= Modifier.isStatic(src) ? Constants.ACC_STATIC : modifier;
    modifier |= Modifier.isSynchronized(src) ? Constants.ACC_SYNCHRONIZED : modifier;
    modifier |= Modifier.isAbstract(src) ? Constants.ACC_ABSTRACT : modifier;
    modifier |= Modifier.isFinal(src) ? Constants.ACC_FINAL : modifier;
    return modifier;
  }

  private String nameOf(ClassSymbol symbol) {
    return symbol.getName();
  }

  private String[] namesOf(ClassSymbol[] symbols) {
    String[] names = new String[symbols.length];
    for (int i = 0; i < names.length; i++) {
      names[i] = nameOf(symbols[i]);
    }
    return names;
  }

  public InstructionHandle codeExpression(
    IrExpression node, CodeProxy code) {
    InstructionHandle start;
    if (node instanceof IrBinExp) {
      start = codeBinaryExpression((IrBinExp) node, code);
    } else if(node instanceof IrUnaryExp) {
      start = codeUnaryExpression((IrUnaryExp) node, code);
    } else if(node instanceof IrBegin) {
      start = codeBegin((IrBegin) node, code);
    } else if(node instanceof IrLocalSet) {
      start = codeLocalAssign((IrLocalSet) node, code);
    } else if(node instanceof IrLocalRef) {
      start = codeLocalRef((IrLocalRef) node, code);
    } else if(node instanceof IrStaticFieldRef) {
      start = codeStaticFieldRef((IrStaticFieldRef) node, code);
    } else if(node instanceof IrFieldRef) {
      start = codeFieldRef((IrFieldRef) node, code);
    } else if(node instanceof IrFieldSet) {
      start = codeFieldAssign((IrFieldSet) node, code);
    } else if(node instanceof IrCall) {
      start = codeMethodCall((IrCall) node, code);
    } else if(node instanceof IrArrayRef){
      start = codeArrayRef((IrArrayRef)node, code);
    } else if(node instanceof IrArrayLength){
      start = codeArrayLengthNode((IrArrayLength)node, code);
    } else if(node instanceof IrArraySet){
      start = codeArrayAssignment((IrArraySet)node, code);
    } else if(node instanceof IrNew){
      start = codeNew((IrNew)node, code);
    } else if(node instanceof IrNewArray){
      start = codeNewArray((IrNewArray)node, code);
    } else if(node instanceof IrArrayRef){
      start = codeArrayRef((IrArrayRef)node, code);
    } else if(node instanceof IrCallStatic){
      start = codeStaticMethodCall((IrCallStatic)node, code);
    } else if(node instanceof IrChar){
      start = codeChar((IrChar)node, code);
    } else if(node instanceof IrString) {
      start = codeString((IrString) node, code);
    } else if(node instanceof IrInt) {
      start = codeInteger((IrInt)node, code);
    } else if(node instanceof IrLong){
      start = codeLong((IrLong)node, code);
  	} else if(node instanceof IrFloat) {
  	  start = codeFloat((IrFloat)node ,code);
  	} else if(node instanceof IrDouble) {
  	  start = codeDouble((IrDouble)node, code);
  	} else if(node instanceof IrBool) {
  	  start = codeBoolean((IrBool)node, code);
  	} else if(node instanceof IrNull) {
  	  start = codeNull((IrNull)node, code);
  	} else if(node instanceof IrCast) {
  	  start = codeCast((IrCast)node, code);
  	} else if(node instanceof IrThis) {
  	  start = codeSelf((IrThis)node, code);
  	} else if(node instanceof IrInstanceOf){
  	  start = codeIsInstance((IrInstanceOf)node, code);
  	} else if(node instanceof IrClosure){
  	  start = codeClosure((IrClosure)node, code);
  	} else if(node instanceof IrList){
  	  start = codeList((IrList)node, code);
  	} else if(node instanceof IrCallSuper){
  	  start = codeSuperCall((IrCallSuper)node, code);
  	} else {
  	  throw new RuntimeException();
    }
    return start;
  }
  
  public InstructionHandle codeBegin(IrBegin node, CodeProxy code) {
    InstructionHandle start;
    IrExpression[] expressions = node.getExpressions();
    if(expressions.length > 0){
      start = codeExpression(expressions[0], code);
      for (int i = 1; i < expressions.length; i++) {
        TypeRef type = expressions[i - 1].type();
        if (type != BasicTypeRef.VOID) {
          if (isWideType(type)) {
            code.append(InstructionConstants.POP2);
          } else {
            code.append(InstructionConstants.POP);
          }
        }
        codeExpression(expressions[i], code);
      }
    }else{
      start = code.append(InstructionConstants.NOP);
    }
    return start;
  }

  public InstructionHandle codeLocalAssign(IrLocalSet node, CodeProxy code) {
    InstructionHandle start = null;
    Type type = typeOf(node.type());
    if(node.getFrame() == 0 && !code.getFrame().isClosed()){
      start = codeExpression(node.getValue(), code);
      if(isWideType(node.type())){
        code.append(InstructionConstants.DUP2);
      }else{
        code.append(InstructionConstants.DUP);
      }
      code.appendStore(type, code.getIndexTable()[node.getIndex()]);
    }else{
      if(node.getFrame() == 0 && code.getFrame().isClosed()){
        int index = code.getFrameObjectIndex();
        start = code.appendLoad(new ArrayType("java.lang.Object", 1), index);
        code.appendConstant(new Integer(code.index(node.getIndex())));
      }else{
        start = code.appendThis();
        code.appendGetField(
          code.getMethod().getClassName(),
          FRAME_PREFIX + node.getFrame(), new ArrayType("java.lang.Object", 1));
        code.appendConstant(new Integer(node.getIndex()));
      }
      if(node.isBasicType()){
        ObjectType boxed = code.boxing(type);          
        code.appendNew(boxed);
        code.appendDup(1);
        codeExpression(node.getValue(), code);
        code.appendInvoke(boxed.getClassName(), "<init>", Type.VOID, new Type[]{type}, Constants.INVOKESPECIAL);
        code.appendDup_2(1);
        code.appendArrayStore(Type.OBJECT);
        String method = (String)unboxingMethods.get(boxed.getClassName());
        code.appendInvoke(boxed.getClassName(), method, type, new Type[0], Constants.INVOKEVIRTUAL);
      }else{
        codeExpression(node.getValue(), code);
        code.appendDup_2(1);
        code.appendArrayStore(Type.OBJECT);          
      }
    }
    return start;
  }
  


  public InstructionHandle codeLocalRef(IrLocalRef node, CodeProxy code) {
    InstructionHandle start = null;
    Type type = typeOf(node.type());
    if(node.frame() == 0 && !code.getFrame().isClosed()){
      start = code.appendLoad(type, code.index(node.index()));
    }else{
      if(node.frame() == 0 && code.getFrame().isClosed()){
        int index = code.getFrameObjectIndex();
        start = code.appendLoad(new ArrayType("java.lang.Object", 1), index);
        code.appendConstant(new Integer(code.index(node.index())));
      }else{
        start = code.appendThis();
        code.appendGetField(
          code.getMethod().getClassName(),
          FRAME_PREFIX + node.frame(), new ArrayType("java.lang.Object", 1));
        code.appendConstant(new Integer(node.index()));
      }
      code.appendArrayLoad(Type.OBJECT);
      if(node.isBasicType()){
        ObjectType boxed = code.boxing(type);      
        String method = (String)unboxingMethods.get(boxed.getClassName());
        code.appendCast(Type.OBJECT, boxed);
        code.appendInvoke(boxed.getClassName(), method, type, new Type[0], Constants.INVOKEVIRTUAL);
      }else{
        code.appendCast(Type.OBJECT, type);
      }
    }
    return start;
  }

  public InstructionHandle codeStaticFieldRef(IrStaticFieldRef node, CodeProxy code) {
    String classType = node.field.getClassType().getName();
    String name = node.field.getName();
    Type type = typeOf(node.type());
    return code.appendGetStatic(classType, name, type);
  }

  public InstructionHandle codeMethodCall(IrCall node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.target, code);
    for (int i = 0; i < node.parameters.length; i++) {
      codeExpression(node.parameters[i], code);
    }    
    ObjectTypeRef classType = (ObjectTypeRef) node.target.type();
    short kind;
    if(classType.isInterface()){
      kind = Constants.INVOKEINTERFACE;
    }else{
      kind = Constants.INVOKEVIRTUAL;
    }
    String className = classType.getName();
    String name = node.method.getName();
    Type ret = typeOf(node.type());
    Type[] args = typesOf(node.method.getArguments());
    code.appendInvoke(className, name, ret, args, kind);
    return start;
  }
  
  public InstructionHandle codeArrayRef(IrArrayRef node, CodeProxy code){
    ArraySymbol targetType = (ArraySymbol)node.getObject().type();
    InstructionHandle start = codeExpression(node.getObject(), code);
    codeExpression(node.getIndex(), code);
    code.appendArrayLoad(typeOf(targetType.getBase()));
    return start;
  }
  
  public InstructionHandle codeArrayLengthNode(IrArrayLength node, CodeProxy code){
    InstructionHandle start = codeExpression(node.getTarget(), code);
    code.append(InstructionConstants.ARRAYLENGTH);
    return start;
  }
  
  public InstructionHandle codeArrayAssignment(
    IrArraySet node, CodeProxy code){
    ArraySymbol targetType = (ArraySymbol)node.getObject().type();
    InstructionHandle start = codeExpression(node.getObject(), code);
    code.appendDup(1);
    codeExpression(node.getIndex(), code);
    codeExpression(node.getValue(), code);
    code.appendArrayStore(typeOf(targetType.getBase()));
    return start;
  }
  
  public InstructionHandle codeNew(IrNew node, CodeProxy code) {
    ClassSymbol type = node.constructor.getClassType();
    InstructionHandle start = code.appendNew((ObjectType)typeOf(type));
    code.append(InstructionConstants.DUP);
    for (int i = 0; i < node.parameters.length; i++) {
      codeExpression(node.parameters[i], code);
    }
    String className = type.getName();
    Type[] arguments = typesOf(node.constructor.getArgs());
    short kind = Constants.INVOKESPECIAL;
    code.appendInvoke(className, "<init>", Type.VOID, arguments, kind);
    return start;
  }
  
  public InstructionHandle codeNewArray(IrNewArray node, CodeProxy code){
    InstructionHandle start = codeExpressions(node.parameters, code);
    ArraySymbol type = node.arrayType;
    code.appendNewArray(typeOf(type.getComponent()), (short)node.parameters.length);
    return start;
  }
  
  public InstructionHandle codeStaticMethodCall(
    IrCallStatic node, CodeProxy code
  ){
    InstructionHandle start;
    if(node.parameters.length > 0){
      start = codeExpression(node.parameters[0], code);
      for (int i = 1; i < node.parameters.length; i++) {
        codeExpression(node.parameters[i], code);
      }      
    }else{
      start = code.append(InstructionConstants.NOP);
    }
    String className = node.target.getName();
    String name = node.method.getName();
    Type returnType = typeOf(node.type());
    Type[] arguments = typesOf(node.method.getArguments());
    short kind = Constants.INVOKESTATIC;
    code.appendInvoke(className, name, returnType, arguments, kind);
    return start;
  }

  public InstructionHandle codeBinaryExpression(
    IrBinExp node, CodeProxy code
  ){
    if(node.getKind() == LOGICAL_AND){
      return codeLogicalAnd(node, code);
    }else if(node.getKind() == LOGICAL_OR){
      return codeLogicalOr(node, code);
    }else if(node.getKind() == ELVIS){
      return codeElvis(node, code);
    }
    IrExpression left = node.getLeft();
    IrExpression right = node.getRight();
    InstructionHandle start = codeExpression(left, code);
    codeExpression(right, code);
    switch (node.getKind()) {
      case ADD: add(code, left.type()); break;
      case SUBTRACT: sub(code, left.type()); break;
      case MULTIPLY: mul(code, left.type()); break;
      case DIVIDE: div(code, left.type()); break;
      case MOD: mod(code, left.type()); break;
      case EQUAL: eq(code, left.type()); break;
      case NOT_EQUAL: noteq(code, left.type()); break;
      case LESS_OR_EQUAL: lte(code, left.type()); break;
      case GREATER_OR_EQUAL: gte(code, left.type()); break;
      case LESS_THAN: lt(code, left.type()); break;
      case GREATER_THAN: gt(code, left.type()); break;
      case BIT_AND: bitAnd(code, left.type()); break;
      case BIT_OR: bitOr(code, right.type()); break;
      case XOR: xor(code, right.type()); break;
      case BIT_SHIFT_L2: bitShiftL2(code, left.type()); break;
      case BIT_SHIFT_R2: bitShiftR2(code, left.type()); break;
      case BIT_SHIFT_R3: bitShiftR3(code, left.type()); break;
      default: break;
    }
    return start;
  }
  
  public void bitShiftR2(CodeProxy code, TypeRef type){
    if(type == BasicTypeRef.INT){
      code.append(InstructionConstants.ISHR);
    }else if(type == BasicTypeRef.LONG){
      code.append(InstructionConstants.LSHR);
    }else{
      throw new RuntimeException();
    }
  }
  
  public void bitShiftL2(CodeProxy code, TypeRef type){
    if(type == BasicTypeRef.INT){
      code.append(InstructionConstants.ISHL);
    }else if(type == BasicTypeRef.LONG){
      code.append(InstructionConstants.LSHL);
    }else{
      throw new RuntimeException();
    }
  }
  
  public void bitShiftR3(CodeProxy code, TypeRef type){
    if(type == BasicTypeRef.INT){
      code.append(InstructionConstants.IUSHR);
    }else if(type == BasicTypeRef.LONG){
      code.append(InstructionConstants.LUSHR);
    }else{
      throw new RuntimeException();
    }
  }
  
  public InstructionHandle codeLogicalAnd(IrBinExp node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getLeft(), code);
    BranchHandle b1 = null, b2 = null, b3 = null;
    
    b1 = code.append(new IFEQ(null));
    codeExpression(node.getRight(), code);
    b2 = code.append(new IFEQ(null));
    code.append(InstructionConstants.ICONST_1);
    b3 = code.append(new GOTO(null));
    InstructionHandle failure = code.append(InstructionConstants.ICONST_0);
    b1.setTarget(failure);
    b2.setTarget(failure);
    b3.setTarget(code.append(InstructionConstants.NOP));      
    return start;
  }

  public InstructionHandle codeLogicalOr(IrBinExp node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getLeft(), code);
    BranchHandle b1 = null, b2 = null, b3 = null;
    b1 = code.append(new IFNE(null));
    codeExpression(node.getRight(), code);
    b2 = code.append(new IFNE(null));
    code.append(InstructionConstants.ICONST_0);
    b3 = code.append(new GOTO(null));
    InstructionHandle success = code.append(InstructionConstants.ICONST_1);
    b1.setTarget(success);
    b2.setTarget(success);
    b3.setTarget(code.append(new NOP()));
    return start;
  }
  
  public InstructionHandle codeElvis(IrBinExp node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getLeft(), code);
    code.appendDup(1);
    code.appendNull(typeOf(node.type()));
    BranchHandle b1 = code.append(new IF_ACMPEQ(null));
    BranchHandle b2 = code.append(new GOTO(null));
    b1.setTarget(code.appendPop(1));
    codeExpression(node.getRight(), code);
    b2.setTarget(code.append(new NOP()));
    return start;
  }
  
  public void bitAnd(CodeProxy code, TypeRef type) {
    if (type == BasicTypeRef.INT || type == BasicTypeRef.BOOLEAN) {
      code.append(new IAND());
    } else if(type == BasicTypeRef.LONG) {
      code.append(new LAND());
    } else {
      throw new RuntimeException();
    }
  }
  
  public void bitOr(CodeProxy code, TypeRef type) {
    if(type == BasicTypeRef.INT || type == BasicTypeRef.BOOLEAN) {
      code.append(new IOR());
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LOR());
    } else {
      throw new RuntimeException();
    }
  }
  
  public void xor(CodeProxy code, TypeRef type) {
    if(type == BasicTypeRef.INT || type == BasicTypeRef.BOOLEAN) {
      code.append(new IXOR());
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LXOR());
    } else {
      throw new RuntimeException();
    }
  }

  public void eq(CodeProxy code, TypeRef type) {
    BranchHandle b1 = null;
    if(type == BasicTypeRef.INT || type == BasicTypeRef.CHAR ||
       type == BasicTypeRef.BOOLEAN) {
      b1 = code.append(new IF_ICMPEQ(null));
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LCMP());
      b1 = code.append(new IFEQ(null));
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      b1 = code.append(new IFEQ(null));
    } else if (type == BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      b1 = code.append(new IFEQ(null));
    } else {
      b1 = code.append(new IF_ACMPEQ(null));
    }
    processBranch(code, b1);
  }
  
  public void noteq(CodeProxy code, TypeRef type) {
    BranchHandle b1 = null;
    if(type == BasicTypeRef.INT || type == BasicTypeRef.CHAR ||
       type == BasicTypeRef.BOOLEAN) {
      b1 = code.append(new IF_ICMPNE(null));
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LCMP());
      b1 = code.append(new IFNE(null));
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      b1 = code.append(new IFNE(null));
    } else if (type == BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      b1 = code.append(new IFNE(null));
    } else {
      b1 = code.append(new IF_ACMPNE(null));
    }
    processBranch(code, b1);
  }
  
  public void gt(CodeProxy code, TypeRef type) {
    BranchHandle b1 = null;
    if (type == BasicTypeRef.INT){
      b1 = code.append(new IF_ICMPGT(null));
    } else if (type == BasicTypeRef.LONG){
      code.append(new LCMP());
      b1 = code.append(new IFGT(null));
    } else if (type == BasicTypeRef.FLOAT){
      code.append(new FCMPL());
      b1 = code.append(new IFGT(null));
    } else if (type == BasicTypeRef.DOUBLE){
      code.append(new DCMPL());
      b1 = code.append(new IFGT(null));
    } else {
      throw new RuntimeException("");
    }
    processBranch(code, b1);
  }
  
  public void gte(CodeProxy code, TypeRef type) {
    BranchHandle comparation = null;
    if(type == BasicTypeRef.INT) {
      comparation = code.append(new IF_ICMPGE(null));
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LCMP());
      comparation = code.append(new IFGE(null));
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      comparation = code.append(new IFGE(null));
    } else if (type == BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      comparation = code.append(new IFGE(null));
    } else {
      throw new RuntimeException("");
    }
    processBranch(code, comparation);
  }

  public void lte(CodeProxy code, TypeRef type) {
    BranchHandle b1 = null;
    if (type == BasicTypeRef.INT) {
      b1 = code.append(new IF_ICMPLE(null));
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LCMP());
      b1 = code.append(new IFLT(null));
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      b1 = code.append(new IFLE(null));
    } else if (type == BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      b1 = code.append(new IFLE(null));
    } else {
      throw new RuntimeException("");
    }
    processBranch(code, b1);
  }

  
  public void lt(CodeProxy code, TypeRef type) {
    BranchHandle comparation = null;
    if(type == BasicTypeRef.INT) {
      comparation = code.append(new IF_ICMPLT(null));
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LCMP());
      comparation = code.append(new IFLT(null));
    } else if (type == BasicTypeRef.FLOAT){
      code.append(new FCMPL());
      comparation = code.append(new IFLT(null));
    } else if (type == BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      comparation = code.append(new IFLT(null));
    } else {
      throw new RuntimeException("");
    }
    processBranch(code, comparation);
  }

  
  private void processBranch(CodeProxy code, BranchHandle b1) {
    code.append(InstructionConstants.ICONST_0);
    BranchHandle b2 = code.append(new GOTO(null));
    b1.setTarget(code.append(InstructionConstants.ICONST_1));
    b2.setTarget(code.append(InstructionConstants.NOP));
  }
  
  public InstructionHandle codeChar(IrChar node, CodeProxy code) {
    return code.appendConstant(new Character(node.getValue()));
  }

  public InstructionHandle codeString(IrString node, CodeProxy code) {
    return code.appendConstant(node.getValue());
  }
  
  public InstructionHandle codeInteger(IrInt node, CodeProxy code){
    return code.appendConstant(new Integer(node.getValue()));
  }
  
  public InstructionHandle codeLong(IrLong node, CodeProxy code){
    return code.appendConstant(new Long(node.getValue()));
  }
  
  public InstructionHandle codeFloat(IrFloat node, CodeProxy code){
    return code.appendConstant(new Float(node.getValue()));
  }
  
  public InstructionHandle codeDouble(IrDouble node, CodeProxy code){
    return code.appendConstant(new Double(node.getValue()));
  }
  
  public InstructionHandle codeBoolean(IrBool node, CodeProxy code){
    return code.appendConstant(Boolean.valueOf(node.getValue()));
  }
  
  public InstructionHandle codeNull(IrNull node, CodeProxy code){
    return code.append(InstructionConstants.ACONST_NULL);
  }
  
  public InstructionHandle codeUnaryExpression(
    IrUnaryExp node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getOperand(), code);
    TypeRef type = node.getOperand().type();
    switch(node.getKind()){
    	case PLUS: plus(code, type); break;
    	case MINUS: minus(code, type); break;
    	case NOT: not(code, type); break;
    	case BIT_NOT: bitNot(code, type); break;
    	default: throw new RuntimeException();
    }
    return start;
  }
  
  private void plus(CodeProxy code, TypeRef type){
    if(
     type != BasicTypeRef.INT && type != BasicTypeRef.LONG &&
     type != BasicTypeRef.FLOAT && type != BasicTypeRef.DOUBLE){
      throw new RuntimeException();
    }
    /*nothing to do*/
  }

  private void minus(CodeProxy code, TypeRef type){
    if(type == BasicTypeRef.INT){
      code.append(InstructionConstants.INEG);
    }else if(type == BasicTypeRef.LONG){
      code.append(InstructionConstants.LNEG);
    }else if(type == BasicTypeRef.FLOAT){
      code.append(InstructionConstants.FNEG);
    }else if(type == BasicTypeRef.DOUBLE){
      code.append(InstructionConstants.DNEG);
    }else{
      throw new RuntimeException();
    }
  }

  private void not(CodeProxy code, TypeRef type){
    if(type == BasicTypeRef.BOOLEAN){
      BranchHandle b1 = code.append(new IFNE(null));
      BranchHandle b2;
      code.append(new ICONST(1));
      b2 = code.append(new GOTO(null));
      b1.setTarget(code.append(new ICONST(0)));
      b2.setTarget(code.append(new NOP()));
    }else{
      throw new RuntimeException();
    }
  }

  private void bitNot(CodeProxy code, TypeRef type){
    if(type == BasicTypeRef.INT){
      code.append(new ICONST(-1));
      code.append(new IXOR());
    }else if(type == BasicTypeRef.LONG){
      code.append(new LCONST(-1));
      code.append(new LXOR());
    }else{
      throw new RuntimeException();
    }
  }

  public InstructionHandle codeCast(IrCast node, CodeProxy code) {
    IrExpression target = node.getTarget();
    InstructionHandle start = codeExpression(target, code);
    code.appendCast(typeOf(target.type()), typeOf(node.getConversion()));
    return start;
  }
  
  public InstructionHandle codeIsInstance(IrInstanceOf node, CodeProxy code){
    InstructionHandle start = codeExpression(node.target, code);
    code.appendInstanceOf((ReferenceType)typeOf(node.getCheckType()));
    return start;
  }
  
  public InstructionHandle codeSelf(IrThis node, CodeProxy code){
    return code.append(InstructionConstants.ALOAD_0);
  }

  public InstructionHandle codeFieldRef(IrFieldRef node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.target, code);
    ClassSymbol symbol = (ClassSymbol) node.target.type();
    code.appendGetField(symbol.getName(), node.field.getName(), typeOf(node.type()));
    return start;
  }

  public InstructionHandle codeFieldAssign(IrFieldSet node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getObject(), code);    
    codeExpression(node.getValue(), code);
    if(isWideType(node.getValue().type())){
      code.append(InstructionConstants.DUP2_X1);
    }else{
      code.append(InstructionConstants.DUP_X1);
    }
    ClassSymbol symbol = (ClassSymbol) node.getObject().type();
    code.appendPutField(symbol.getName(), node.getField().getName(), typeOf(node.type()));
    return start;
  }

  private void add(CodeProxy code, TypeRef type) {
    if (type == BasicTypeRef.INT) {
      code.append(new IADD());
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LADD());
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FADD());
    } else {
      code.append(new DADD());
    }
  }

  private void sub(CodeProxy code, TypeRef type) {
    if (type == BasicTypeRef.INT) {
      code.append(new ISUB());
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LSUB());
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FSUB());
    } else {
      code.append(new DSUB());
    }
  }
  
  private void mul(CodeProxy code, TypeRef type) {
    if (type == BasicTypeRef.INT) {
      code.append(new IMUL());
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LMUL());
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FMUL());
    } else {
      code.append(new DMUL());
    }
  }

  private void div(CodeProxy code, TypeRef type) {
    if (type == BasicTypeRef.INT) {
      code.append(new IDIV());
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LDIV());
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FDIV());
    } else {
      code.append(new DDIV());
    }
  }

  private void mod(CodeProxy code, TypeRef type) {
    if (type == BasicTypeRef.INT) {
      code.append(new IREM());
    } else if (type == BasicTypeRef.LONG) {
      code.append(new LREM());
    } else if (type == BasicTypeRef.FLOAT) {
      code.append(new FREM());
    } else {
      code.append(new DREM());
    }
  }
  
  private int frameObjectIndex(int origin, TypeRef[] arguments){
    int maxIndex = origin;
    for(int i = 0; i < arguments.length; i++) {
      if (isWideType(arguments[i])) {
        maxIndex += 2;
      } else {
        maxIndex++;
      }
    }
    return maxIndex;
  }

  private int[] indexTableFor(int origin, LocalFrame frame) {
    LocalBinding[] bindings = frame.entries();
    int[] indexTable = new int[bindings.length];
    int maxIndex = origin;
    for(int i = 0; i < bindings.length; i++) {
      indexTable[i] = maxIndex;
      if (isWideType(bindings[i].getType())) {
        maxIndex += 2;
      } else {
        maxIndex++;
      }
    }
    return indexTable;
  }
  
  private int[] indexTableFor(LocalFrame frame) {
    LocalBinding[] bindings = frame.entries();
    int[] indexTable = new int[bindings.length];
    int maxIndex = 0;
    for(int i = 0; i < bindings.length; i++) {
      indexTable[i] = maxIndex;
      maxIndex++;
    }
    return indexTable;
  }

  private boolean isWideType(TypeRef symbol) {
    if (symbol == BasicTypeRef.DOUBLE || symbol == BasicTypeRef.LONG)
      return true;
    return false;
  }

  private Type typeOf(TypeRef type) {
    return bridge.toVMType(type);
  }

  private Type[] typesOf(TypeRef[] types) {
    Type[] destinationTypes = new Type[types.length];
    for (int i = 0; i < destinationTypes.length; i++) {
      destinationTypes[i] = bridge.toVMType(types[i]);
    }
    return destinationTypes;
  }
}

class CodeProxy {
  private InstructionList code;
  private InstructionFactory factory;
  private LocalFrame frame;
  private int frameObjectIndex;
  private int[] indexTable;
  private MethodGen method;  
  
  public CodeProxy(ConstantPoolGen pool){
    this.code = new InstructionList();
    this.factory = new InstructionFactory(pool);
  }
  
  public void setFrame(LocalFrame frame){
    this.frame = frame;
  }
  
  public LocalFrame getFrame(){
    return frame;
  }
  
  public int getFrameObjectIndex(){
    return frameObjectIndex;
  }
  
  public void setFrameObjectIndex(int frameObjectIndex){
    this.frameObjectIndex = frameObjectIndex;
  }
  
  public void setIndexTable(int[] indexTable){
    this.indexTable = (int[]) indexTable.clone();
  }
  
  public int index(int index){
    return indexTable[index];
  }
  
  public int[] getIndexTable(){
    return (int[]) indexTable.clone();
  }
  
  public void setMethod(MethodGen method){
    this.method = method;
  }
  
  public MethodGen getMethod(){
    return method;
  }
  
  public InstructionList getCode(){
    return code;
  }
  
  public CodeExceptionGen addExceptionHandler(InstructionHandle start_pc, InstructionHandle end_pc, InstructionHandle handler_pc, ObjectType catch_type) {
    return method.addExceptionHandler(start_pc, end_pc, handler_pc, catch_type);
  }
  
  public LineNumberGen addLineNumber(InstructionHandle ih, int src_line) {
    return method.addLineNumber(ih, src_line);
  }
  
  public InstructionHandle appendCallConstructor(ObjectType type, Type[] params){
    return appendInvoke(type.getClassName(), "<init>", Type.VOID, params, Constants.INVOKESPECIAL);
  }
  
  public InstructionHandle appendDefaultValue(Type type){
    InstructionHandle start;
    if(type instanceof BasicType){
      if(type == BasicType.BOOLEAN){
        start = appendConstant(Boolean.valueOf(false));
      }else if(type == BasicType.BYTE){
        start = appendConstant(new Byte((byte)0));
      }else if(type == BasicType.SHORT){
        start = appendConstant(new Short((short)0));
      }else if(type == BasicType.CHAR){
        start = appendConstant(new Character((char)0));
      }else if(type == BasicType.INT){
        start = appendConstant(new Integer(0));
      }else if(type == BasicType.LONG){
        start = appendConstant(new Long(0));
      }else if(type == BasicType.FLOAT){
        start = appendConstant(new Float(0.0f));
      }else if(type == BasicType.DOUBLE){
        start = appendConstant(new Double(0.0));
      }else{
        start = append(InstructionConstants.NOP);
      }
    }else{
      start = appendNull(type);
    }
    return start;
  }
  
  private final Map BOXING_TABLE = new HashMap(){{
    put(Type.BOOLEAN,  new ObjectType("java.lang.Boolean"));
    put(Type.BYTE,     new ObjectType("java.lang.Byte"));
    put(Type.SHORT,    new ObjectType("java.lang.Short"));
    put(Type.CHAR,     new ObjectType("java.lang.Character"));
    put(Type.INT,      new ObjectType("java.lang.Integer"));
    put(Type.LONG,     new ObjectType("java.lang.Long"));
    put(Type.FLOAT,    new ObjectType("java.lang.Float"));
    put(Type.DOUBLE,   new ObjectType("java.lang.Double"));
  }};
  
  public ObjectType boxing(Type type){
    ObjectType boxedType = (ObjectType)BOXING_TABLE.get(type);
    if(boxedType == null) throw new RuntimeException("type " + type + "cannot be boxed");
    return boxedType;
  }
  
  public InstructionHandle appendArrayLoad(Type type) {
    return code.append(InstructionFactory.createArrayLoad(type));
  }
  
  public InstructionHandle appendArrayStore(Type type) {
    return code.append(InstructionFactory.createArrayStore(type));
  }
  
  public InstructionHandle appendBinaryOperation(String op, Type type) {
    return code.append(InstructionFactory.createBinaryOperation(op, type));
  }
  
  public BranchHandle appendBranchInstruction(short opcode, InstructionHandle target) {
    return code.append(InstructionFactory.createBranchInstruction(opcode, target));
  }
  
  public InstructionHandle appendDup(int size) {
    return code.append(InstructionFactory.createDup(size));
  }
  
  public InstructionHandle appendDup_1(int size) {
    return code.append(InstructionFactory.createDup_1(size));
  }
  
  public InstructionHandle appendDup_2(int size) {
    return code.append(InstructionFactory.createDup_2(size));
  }
  
  public InstructionHandle appendLoad(Type type, int index) {
    return code.append(InstructionFactory.createLoad(type, index));
  }
  
  public InstructionHandle appendNull(Type type) {
    return code.append(InstructionFactory.createNull(type));
  }
  
  public InstructionHandle appendPop(int size) {
    return code.append(InstructionFactory.createPop(size));
  }
  
  public InstructionHandle appendReturn(Type type) {
    return code.append(InstructionFactory.createReturn(type));
  }
  
  public InstructionHandle appendStore(Type type, int index) {
    return code.append(InstructionFactory.createStore(type, index));
  }
  
  public InstructionHandle appendThis() {
    return code.append(InstructionFactory.createThis());
  }
  
  public InstructionHandle appendAppend(Type type) {
    return code.append(factory.createAppend(type));
  }
  
  public InstructionHandle appendCast(Type src_type, Type dest_type) {
    return code.append(factory.createCast(src_type, dest_type));
  }
  
  public InstructionHandle appendCheckCast(ReferenceType t) {
    return code.append(factory.createCheckCast(t));
  }
  
  public InstructionHandle appendConstant(Object value) {
    return code.append(factory.createConstant(value));
  }
  
  public InstructionHandle appendFieldAccess(String class_name, String name, Type type, short kind) {
    return code.append(factory.createFieldAccess(class_name, name, type, kind));
  }
  
  public InstructionHandle appendGetField(String class_name, String name, Type t) {
    return code.append(factory.createGetField(class_name, name, t));
  }
  
  public InstructionHandle appendGetStatic(String class_name, String name, Type t) {
    return code.append(factory.createGetStatic(class_name, name, t));
  }
  
  public InstructionHandle appendInstanceOf(ReferenceType t) {
    return code.append(factory.createInstanceOf(t));
  }
  
  public InstructionHandle appendInvoke(String class_name, String name, Type ret_type, Type[] arg_types, short kind) {
    return code.append(factory.createInvoke(class_name, name, ret_type, arg_types, kind));
  }
  
  public InstructionHandle appendNew(String s) {
    return code.append(factory.createNew(s));
  }
  
  public InstructionHandle appendNew(ObjectType t) {
    return code.append(factory.createNew(t));
  }
  
  public InstructionHandle appendNewArray(Type t, short dim) {
    return code.append(factory.createNewArray(t, dim));
  }
  
  public InstructionHandle appendPutField(String class_name, String name, Type t) {
    return code.append(factory.createPutField(class_name, name, t));
  }
  public InstructionHandle appendPutStatic(String class_name, String name, Type t) {
    return code.append(factory.createPutStatic(class_name, name, t));
  }
  
  public BranchHandle append(BranchInstruction i) {
    return code.append(i);
  }
  
  public InstructionHandle append(CompoundInstruction c) {
    return code.append(c);
  }
  
  public InstructionHandle append(Instruction i) {
    return code.append(i);
  }
  
  public InstructionHandle append(Instruction i, CompoundInstruction c) {
    return code.append(i, c);
  }
  
  public InstructionHandle append(Instruction i, Instruction j) {
    return code.append(i, j);
  }
  
  public InstructionHandle append(Instruction i, InstructionList il) {
    return code.append(i, il);
  }
  
  public BranchHandle append(InstructionHandle ih, BranchInstruction i) {
    return code.append(ih, i);
  }
  
  public InstructionHandle append(InstructionHandle ih, CompoundInstruction c) {
    return code.append(ih, c);
  }
  
  public InstructionHandle append(InstructionHandle ih, Instruction i) {
    return code.append(ih, i);
  }
  
  public InstructionHandle append(InstructionHandle ih, InstructionList il) {
    return code.append(ih, il);
  }
  
  public InstructionHandle append(InstructionList il) {
    return code.append(il);
  }
  
  public InstructionHandle getEnd() {
    return code.getEnd();
  }
  
  public InstructionHandle[] getInstructionHandles() {
    return code.getInstructionHandles();
  }
  
  public int[] getInstructionPositions() {
    return code.getInstructionPositions();
  }
  
  public Instruction[] getInstructions() {
    return code.getInstructions();
  }
  
  public int getLength() {
    return code.getLength();
  }
  
  public InstructionHandle getStart() {
    return code.getStart();
  }
  
  public BranchHandle insert(BranchInstruction i) {
    return code.insert(i);
  }
  
  public InstructionHandle insert(CompoundInstruction c) {
    return code.insert(c);
  }
  
  public InstructionHandle insert(Instruction i) {
    return code.insert(i);
  }
  
  public InstructionHandle insert(Instruction i, CompoundInstruction c) {
    return code.insert(i, c);
  }
  
  public InstructionHandle insert(Instruction i, Instruction j) {
    return code.insert(i, j);
  }
  
  public InstructionHandle insert(Instruction i, InstructionList il) {
    return code.insert(i, il);
  }
  
  public BranchHandle insert(InstructionHandle ih, BranchInstruction i) {
    return code.insert(ih, i);
  }
  
  public InstructionHandle insert(InstructionHandle ih, CompoundInstruction c) {
    return code.insert(ih, c);
  }
  
  public InstructionHandle insert(InstructionHandle ih, Instruction i) {
    return code.insert(ih, i);
  }
  
  public InstructionHandle insert(InstructionHandle ih, InstructionList il) {
    return code.insert(ih, il);
  }
  
  public InstructionHandle insert(InstructionList il) {
    return code.insert(il);
  }
  
  public boolean isEmpty() {
    return code.isEmpty();
  }
  
  public Iterator iterator() {
    return code.iterator();
  }
  
  public void move(InstructionHandle ih, InstructionHandle target) {
    code.move(ih, target);
  }
  
  public void move(InstructionHandle start, InstructionHandle end, InstructionHandle target) {
    code.move(start, end, target);
  }
  
  public void redirectBranches(InstructionHandle old_target, InstructionHandle new_target) {
    code.redirectBranches(old_target, new_target);
  }
  
  public void redirectExceptionHandlers(CodeExceptionGen[] exceptions, InstructionHandle old_target, InstructionHandle new_target) {
    code.redirectExceptionHandlers(exceptions, old_target, new_target);
  }
  
  public void redirectLocalVariables(LocalVariableGen[] lg, InstructionHandle old_target, InstructionHandle new_target) {
    code.redirectLocalVariables(lg, old_target, new_target);
  }
  
  public void update() {
    code.update();
  }
}