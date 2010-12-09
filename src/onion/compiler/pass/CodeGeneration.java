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
import onion.compiler.util.*;
import onion.compiler.Modifier;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.*;
import static onion.compiler.IxCode.BinaryTerm.Constants.*;
import static onion.compiler.IxCode.UnaryTerm.Constants.*;
/**
 * @author Kota Mizushima Date: 2005/04/10
 */
public class CodeGeneration  {
  private CompilerConfig config;
  private List compiledClasses = new ArrayList();
  private SymbolGenerator generator;
  private boolean isStatic;
  private boolean isClosure;
  private String currentClosureName;
  
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
  private static final String OUTER_THIS = "outer$";
  private static final String CLOSURE_CLASS_SUFFIX = "Closure";

  private static final Map<IxCode.BasicTypeRef, Type> BASIC_TYPE_MAPPING = new HashMap(){{
    put(IxCode.BasicTypeRef.BYTE,			BasicType.BYTE);
    put(IxCode.BasicTypeRef.SHORT,		BasicType.SHORT);
    put(IxCode.BasicTypeRef.CHAR,			BasicType.CHAR);
    put(IxCode.BasicTypeRef.INT,			BasicType.INT);
    put(IxCode.BasicTypeRef.LONG, 		BasicType.LONG);
    put(IxCode.BasicTypeRef.FLOAT,		BasicType.FLOAT);
    put(IxCode.BasicTypeRef.DOUBLE, 	BasicType.DOUBLE);
    put(IxCode.BasicTypeRef.BOOLEAN,	BasicType.BOOLEAN);
    put(IxCode.BasicTypeRef.VOID,			BasicType.VOID);
  }};

  public static Type translateIxTypeToVmType(IxCode.TypeRef type){
    if(type.isBasicType()){
      return BASIC_TYPE_MAPPING.get(type);
    }else if(type.isArrayType()){
      IxCode.ArrayTypeRef arrayType = (IxCode.ArrayTypeRef)type;
      return new ArrayType(translateIxTypeToVmType(arrayType.component()), arrayType.dimension());
    }else if(type.isClassType()){
      return new ObjectType(type.name());
    }else{
      return Type.NULL;
    }
  }

  public CodeGeneration(CompilerConfig config) {
    this.config = config;
  }

  public CompiledClass[] process(IxCode.ClassDefinition[] classes) {
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
  
  private int classModifier(IxCode.ClassDefinition node){
    int modifier = toJavaModifier(node.modifier());
    modifier |= node.isInterface() ? Constants.ACC_INTERFACE : modifier;
    modifier |= (!Modifier.isInternal(modifier)) ? Constants.ACC_PUBLIC : modifier;
    return modifier;
  }

  public void codeClass(IxCode.ClassDefinition node) {
    int modifier = classModifier(node);
    String className = node.name();
    generator = new SymbolGenerator(className + CLOSURE_CLASS_SUFFIX);
    String superClass = node.superClass().name();
    String[] interfaces = namesOf(node.interfaces());
    String file = node.getSourceFile();
    ClassGen gen = new ClassGen(className, superClass, file, modifier, interfaces);
    IxCode.ConstructorRef[] constructors = node.constructors();
    for (int i = 0; i < constructors.length; i++) {
      codeConstructor(gen, ((IxCode.ConstructorDefinition) constructors[i]));
    }
    IxCode.MethodRef[] methods = node.methods();
    for (int i = 0; i < methods.length; i++) {
      codeMethod(gen, ((IxCode.MethodDefinition) methods[i]));
    }
    IxCode.FieldRef[] fields = node.fields();
    for (int i = 0; i < fields.length; i++) {
      codeField(gen, ((IxCode.FieldDefinition) fields[i]));
    }
    compiledClasses.add(gen.getJavaClass());
  }
  
  public InstructionHandle codeExpressions(IxCode.Term[] nodes, CodeProxy code){
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

  public void codeConstructor(ClassGen gen, IxCode.ConstructorDefinition node) {
    boolean isStaticOld = isStatic;
    isStatic = false;
    CodeProxy code = new CodeProxy(gen.getConstantPool());
    LocalFrame frame = node.getFrame();
    code.setFrame(frame);
    String[] args = new String[node.getArgs().length];
    for (int i = 0; i < args.length; i++) {
      args[i] = "arg" + i;
    }
    ObjectType classType = (ObjectType) typeOf(node.affiliation());
    int modifier = toJavaModifier(node.modifier());
    Type[] arguments = typesOf(node.getArgs());
    MethodGen method = new MethodGen(
      modifier, Type.VOID, arguments, args, "<init>",
      classType.getClassName(), code.getCode(), gen.getConstantPool()
    );
    if(frame.isClosed()){
      int frameObjectIndex = frameObjectIndex(1, node.getArgs());
      code.setFrameObjectIndex(frameObjectIndex);
      code.setIndexTable(makeIndexTableForClosureFrame(frame));
      appendInitialCode(code, frame, arguments, 1);
    }else{
      code.setIndexTable(makeIndexTableFor(1, frame));
    }
    code.setMethod(method);
    IxCode.Super init = node.getSuperInitializer();
    classType = (ObjectType) typeOf(init.getClassType());
    arguments = typesOf(init.getArguments());    
    code.append(InstructionConstants.ALOAD_0);
    codeExpressions(init.getExpressions(), code);
    code.appendCallConstructor(classType, arguments);
    codeBlock(node.getBlock(), code);
    method.setMaxLocals();
    method.setMaxStack();
    code.appendReturn(typeOf(IxCode.BasicTypeRef.VOID));
    gen.addMethod(method.getMethod());
    isStatic = isStaticOld;
  }

  public void codeMethod(ClassGen gen, IxCode.MethodDefinition node) {
    boolean isStaticOld = isStatic;
    isStatic = Modifier.isStatic(node.modifier());
    CodeProxy code = new CodeProxy(gen.getConstantPool());
    LocalFrame frame = node.getFrame();
    code.setFrame(frame);
    
    int modifier = toJavaModifier(node.modifier());
    Type returned = typeOf(node.returnType());
    Type[] arguments = typesOf(node.arguments());
    String[] argNames = names(arguments.length);
    String name = node.name();
    String className = node.affiliation().name();
    MethodGen method = new MethodGen(modifier, returned, arguments, argNames, name, className, code.getCode(), gen.getConstantPool());
    code.setMethod(method);
    if (!Modifier.isAbstract(node.modifier())) {
      if(frame.isClosed()){
        int origin;
        if(Modifier.isStatic(node.modifier())){
          code.setFrameObjectIndex(frameObjectIndex(0, node.arguments()));
          origin = 0;
        }else{
          code.setFrameObjectIndex(frameObjectIndex(1, node.arguments()));
          origin = 1;
        }
        code.setIndexTable(makeIndexTableForClosureFrame(frame));
        appendInitialCode(code, frame, arguments, origin);
      }else{
        if (Modifier.isStatic(node.modifier())) {
          code.setIndexTable(makeIndexTableFor(0, frame));
        } else {
          code.setIndexTable(makeIndexTableFor(1, frame));
        }
      }
      codeBlock(node.getBlock(), code);
      method.setMaxLocals();
      method.setMaxStack();
    }
    gen.addMethod(method.getMethod());
    isStatic = isStaticOld;
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

  private void implementsMethods(ClassGen gen, IxCode.MethodRef[] methods){
    for(int i = 0; i < methods.length; i++){
      IxCode.MethodRef method = methods[i];
      Type returnType = typeOf(method.returnType());
      String name = method.name();
      Type[] args = typesOf(method.arguments());
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
  
  public InstructionHandle codeClosure(IxCode.NewClosure node, CodeProxy code){
    IxCode.ClassTypeRef classType = node.getClassType();
    String closureName = generator.generate();
    Type[] arguments = typesOf(node.getArguments());
    ClassGen gen = new ClassGen(closureName, "java.lang.Object", "<generated>", Constants.ACC_PUBLIC, new String[]{classType.name()});
    Set methods = Classes.getInterfaceMethods(classType);
    methods.remove(node.getMethod());
    implementsMethods(gen, (IxCode.MethodRef[]) methods.toArray(new IxCode.MethodRef[0]));
    LocalFrame frame = node.getFrame();
    int depth = frame.depth();
    for(int i = 1; i <= depth; i++){
      FieldGen field = new FieldGen(
        Constants.ACC_PRIVATE, new ArrayType("java.lang.Object", 1),
        FRAME_PREFIX + i, gen.getConstantPool()
      );
      gen.addField(field.getField());
    }
    gen.addField(
      new FieldGen(
        Constants.ACC_PUBLIC,
        new ObjectType("java.lang.Object"),
        OUTER_THIS,
        gen.getConstantPool()
      ).getField()
    );
    Type[] types = closureArguments(depth);
    MethodGen method = createClosureConstructor(closureName, types, gen.getConstantPool());
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
      closureCode.setIndexTable(makeIndexTableForClosureFrame(frame));
      appendInitialCode(closureCode, frame, arguments, 1);
    }else{
      closureCode.setIndexTable(makeIndexTableFor(1, frame));
    }
    boolean isClosureOld = isClosure;
    String currentClosureNameOld = currentClosureName;
    isClosure = true;
    currentClosureName = closureName;
    codeStatement(node.getBlock(), closureCode);
    isClosure = isClosureOld;
    currentClosureName = currentClosureNameOld;
    method.setMaxLocals();
    method.setMaxStack();
    gen.addMethod(method.getMethod());
    compiledClasses.add(gen.getJavaClass());
    InstructionHandle start = code.appendNew(new ObjectType(closureName));
    code.appendDup(1);
    String name = code.getMethod().getClassName();
    int index = code.getFrameObjectIndex();
    if(!isStatic) {
      if(isClosure) {
        code.appendThis();
        code.appendGetField(currentClosureName, OUTER_THIS, new ObjectType("java.lang.Object"));
      }else {
        code.appendThis();
      }
    }
    code.appendLoad(new ArrayType("java.lang.Object", 1), index);
    for(int i = 1; i < depth; i++){
      code.appendThis();
      code.appendGetField(name, FRAME_PREFIX + i, new ArrayType("java.lang.Object", 1));
    }
    code.appendCallConstructor(new ObjectType(closureName), complementOuterThis(closureArguments(depth)));
    return start;
  }

  private Type[] complementOuterThis(Type[] types) {
    if(!isStatic) {
      Type[] newTypes = new Type[types.length + 1];
      newTypes[0] = new ObjectType("java.lang.Object");
      for(int i = 0; i < types.length; i++) {
        newTypes[i + 1] = types[i];
      }
      return newTypes;
    }else {
      return types;
    }
  }
  
  private Type[] closureArguments(int size){
    Type[] arguments = new Type[size];
    for(int i = 0; i < arguments.length; i++){
      arguments[i] = new ArrayType("java.lang.Object", 1);
    }
    return arguments;
  }
  
  private InstructionHandle codeList(IxCode.ListLiteral node, CodeProxy code){
    ObjectType listType = (ObjectType) typeOf(node.type());
    InstructionHandle start = code.appendNew("java.util.ArrayList");
    code.appendDup(1);
    code.appendCallConstructor(new ObjectType("java.util.ArrayList"), new Type[0]);
    IxCode.Term[] elements = node.getElements();
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
  
  public InstructionHandle codeSuperCall(IxCode.CallSuper node, CodeProxy code){
    InstructionHandle start = codeExpression(node.getTarget(), code);
    codeExpressions(node.getParams(), code);
    IxCode.MethodRef method = node.getMethod();
    code.appendInvoke(
      method.affiliation().name(),
      method.name(),
      typeOf(method.returnType()),
      typesOf(method.arguments()),
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
  
  private MethodGen createClosureConstructor(String className, Type[] types, ConstantPoolGen pool){
    String[] argNames;
    if(isStatic) {
      argNames = new String[types.length];
      for(int i = 0; i < types.length; i++){
        argNames[i] = FRAME_PREFIX + (i + 1);
      }
    }else{
      argNames = new String[types.length + 1];
      argNames[0] = OUTER_THIS;
      for(int i = 0; i < types.length; i++){
        argNames[i + 1] = FRAME_PREFIX + (i + 1);
      }
    }
    CodeProxy code = new CodeProxy(pool);
    MethodGen constructor = new MethodGen(
      Constants.ACC_PUBLIC, Type.VOID, complementOuterThis(types), argNames, "<init>",
      className, code.getCode(), pool);
    code.appendThis();
    code.appendCallConstructor(Type.OBJECT, new Type[0]);
    if(!isStatic) {
      code.appendThis();
      code.appendLoad(Type.OBJECT, 1);
      code.appendPutField(className, OUTER_THIS, Type.OBJECT);
    }
    int origin = isStatic ? 1 : 2;
    for(int i = 0; i < types.length; i++){
      code.appendThis();
      code.appendLoad(types[i], i + origin);
      code.appendPutField(className, FRAME_PREFIX + (i + 1), types[i]);
    }
    code.append(InstructionConstants.RETURN);
    constructor.setMaxLocals();
    constructor.setMaxStack();
    return constructor;
  }
  
  public void codeField(ClassGen gen, IxCode.FieldDefinition node) {
    FieldGen field = new FieldGen(
      toJavaModifier(node.modifier()),
      typeOf(node.type()), node.name(), gen.getConstantPool());
    gen.addField(field.getField());
  }

  public InstructionHandle codeBlock(IxCode.StatementBlock node, CodeProxy code) {
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

  public InstructionHandle codeExpressionStatement(IxCode.ExpressionActionStatement node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.term, code);
    IxCode.TypeRef type = node.term.type();
    if (type != IxCode.BasicTypeRef.VOID) {
      if (isWideType(type)) {
        code.append(InstructionConstants.POP2);
      } else {
        code.append(InstructionConstants.POP);
      }
    }
    return start;
  }

  public InstructionHandle codeStatement(IxCode.ActionStatement node, CodeProxy code) {
    InstructionHandle start;
    if (node instanceof IxCode.StatementBlock) {
      start = codeBlock((IxCode.StatementBlock) node, code);
    } else if (node instanceof IxCode.ExpressionActionStatement) {
      start = codeExpressionStatement((IxCode.ExpressionActionStatement) node, code);
    } else if (node instanceof IxCode.IfStatement) {
      start = codeIf((IxCode.IfStatement) node, code);
    } else if (node instanceof IxCode.ConditionalLoop) {
      start = codeLoop((IxCode.ConditionalLoop) node, code);
    } else if (node instanceof IxCode.NOP) {
      start = codeEmpty((IxCode.NOP) node, code);
    } else if (node instanceof IxCode.Return) {
      start = codeReturn((IxCode.Return) node, code);
    } else if (node instanceof IxCode.Synchronized) {
      start = codeSynchronized((IxCode.Synchronized) node, code);
    } else if (node instanceof IxCode.Throw) {
      start = codeThrowNode((IxCode.Throw)node, code);
    } else if (node instanceof IxCode.Try){
      start = codeTry((IxCode.Try)node, code);
    } else {
      start = code.append(InstructionConstants.NOP);
    }
    return start;
  }

  public InstructionHandle codeReturn(IxCode.Return node, CodeProxy code) {
    InstructionHandle start;
    if (node.term != null) {
      start = codeExpression(node.term, code);
      Type type = typeOf(node.term.type());
      code.appendReturn(type);
    } else {
      start = code.append(InstructionConstants.RETURN);
    }
    return start;
  }

  public InstructionHandle codeSynchronized(IxCode.Synchronized node, CodeProxy code) {
    return null;
  }
  
  public InstructionHandle codeThrowNode(IxCode.Throw node, CodeProxy code){
    InstructionHandle start = codeExpression(node.term, code);
    code.append(InstructionConstants.ATHROW);
    return start;
  }
  
  public InstructionHandle codeTry(IxCode.Try node, CodeProxy code){
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

  public InstructionHandle codeEmpty(IxCode.NOP node, CodeProxy code) {
    return code.append(InstructionConstants.NOP);
  }

  public InstructionHandle codeIf(IxCode.IfStatement node, CodeProxy code) {
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

  public InstructionHandle codeLoop(IxCode.ConditionalLoop node, CodeProxy code) {
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

  private String nameOf(IxCode.ClassTypeRef symbol) {
    return symbol.name();
  }

  private String[] namesOf(IxCode.ClassTypeRef[] symbols) {
    String[] names = new String[symbols.length];
    for (int i = 0; i < names.length; i++) {
      names[i] = nameOf(symbols[i]);
    }
    return names;
  }

  public InstructionHandle codeExpression(
    IxCode.Term node, CodeProxy code) {
    InstructionHandle start;
    if (node instanceof IxCode.BinaryTerm) {
      start = codeBinaryExpression((IxCode.BinaryTerm) node, code);
    } else if(node instanceof IxCode.UnaryTerm) {
      start = codeUnaryExpression((IxCode.UnaryTerm) node, code);
    } else if(node instanceof IxCode.Begin) {
      start = codeBegin((IxCode.Begin) node, code);
    } else if(node instanceof IxCode.SetLocal) {
      start = codeLocalAssign((IxCode.SetLocal) node, code);
    } else if(node instanceof IxCode.RefLocal) {
      start = codeLocalRef((IxCode.RefLocal) node, code);
    } else if(node instanceof IxCode.RefStaticField) {
      start = codeStaticFieldRef((IxCode.RefStaticField) node, code);
    } else if(node instanceof IxCode.RefField) {
      start = codeFieldRef((IxCode.RefField) node, code);
    } else if(node instanceof IxCode.SetField) {
      start = codeFieldAssign((IxCode.SetField) node, code);
    } else if(node instanceof IxCode.Call) {
      start = codeMethodCall((IxCode.Call) node, code);
    } else if(node instanceof IxCode.RefArray){
      start = codeArrayRef((IxCode.RefArray)node, code);
    } else if(node instanceof IxCode.ArrayLength){
      start = codeArrayLengthNode((IxCode.ArrayLength)node, code);
    } else if(node instanceof IxCode.SetArray){
      start = codeArrayAssignment((IxCode.SetArray)node, code);
    } else if(node instanceof IxCode.NewObject){
      start = codeNew((IxCode.NewObject)node, code);
    } else if(node instanceof IxCode.NewArray){
      start = codeNewArray((IxCode.NewArray)node, code);
    } else if(node instanceof IxCode.RefArray){
      start = codeArrayRef((IxCode.RefArray)node, code);
    } else if(node instanceof IxCode.CallStatic){
      start = codeStaticMethodCall((IxCode.CallStatic)node, code);
    } else if(node instanceof IxCode.CharacterValue){
      start = codeChar((IxCode.CharacterValue)node, code);
    } else if(node instanceof IxCode.StringValue) {
      start = codeString((IxCode.StringValue) node, code);
    } else if(node instanceof IxCode.IntValue) {
      start = codeInteger((IxCode.IntValue)node, code);
    } else if(node instanceof IxCode.LongValue){
      start = codeLong((IxCode.LongValue)node, code);
  	} else if(node instanceof IxCode.FloatValue) {
  	  start = codeFloat((IxCode.FloatValue)node ,code);
  	} else if(node instanceof IxCode.DoubleValue) {
  	  start = codeDouble((IxCode.DoubleValue)node, code);
  	} else if(node instanceof IxCode.BoolValue) {
  	  start = codeBoolean((IxCode.BoolValue)node, code);
  	} else if(node instanceof IxCode.NullValue) {
  	  start = codeNull((IxCode.NullValue)node, code);
  	} else if(node instanceof IxCode.AsInstanceOf) {
  	  start = codeCast((IxCode.AsInstanceOf)node, code);
  	} else if(node instanceof IxCode.This) {
  	  start = codeSelf((IxCode.This)node, code);
    } else if(node instanceof IxCode.OuterThis) {
      start = codeOuterThis((IxCode.OuterThis)node, code);
  	} else if(node instanceof IxCode.InstanceOf){
  	  start = codeIsInstance((IxCode.InstanceOf)node, code);
  	} else if(node instanceof IxCode.NewClosure){
  	  start = codeClosure((IxCode.NewClosure)node, code);
  	} else if(node instanceof IxCode.ListLiteral){
  	  start = codeList((IxCode.ListLiteral)node, code);
  	} else if(node instanceof IxCode.CallSuper){
  	  start = codeSuperCall((IxCode.CallSuper)node, code);
  	} else {
  	  throw new RuntimeException();
    }
    return start;
  }
  
  public InstructionHandle codeBegin(IxCode.Begin node, CodeProxy code) {
    InstructionHandle start;
    IxCode.Term[] terms = node.getExpressions();
    if(terms.length > 0){
      start = codeExpression(terms[0], code);
      for (int i = 1; i < terms.length; i++) {
        IxCode.TypeRef type = terms[i - 1].type();
        if (type != IxCode.BasicTypeRef.VOID) {
          if (isWideType(type)) {
            code.append(InstructionConstants.POP2);
          } else {
            code.append(InstructionConstants.POP);
          }
        }
        codeExpression(terms[i], code);
      }
    }else{
      start = code.append(InstructionConstants.NOP);
    }
    return start;
  }

  public InstructionHandle codeLocalAssign(IxCode.SetLocal node, CodeProxy code) {
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
  


  public InstructionHandle codeLocalRef(IxCode.RefLocal node, CodeProxy code) {
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

  public InstructionHandle codeStaticFieldRef(IxCode.RefStaticField node, CodeProxy code) {
    String classType = node.field.affiliation().name();
    String name = node.field.name();
    Type type = typeOf(node.type());
    return code.appendGetStatic(classType, name, type);
  }

  public InstructionHandle codeMethodCall(IxCode.Call node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.target, code);
    for (int i = 0; i < node.parameters.length; i++) {
      codeExpression(node.parameters[i], code);
    }    
    IxCode.ObjectTypeRef classType = (IxCode.ObjectTypeRef) node.target.type();
    short kind;
    if(classType.isInterface()){
      kind = Constants.INVOKEINTERFACE;
    }else{
      kind = Constants.INVOKEVIRTUAL;
    }
    String className = classType.name();
    String name = node.method.name();
    Type ret = typeOf(node.type());
    Type[] args = typesOf(node.method.arguments());
    code.appendInvoke(className, name, ret, args, kind);
    return start;
  }
  
  public InstructionHandle codeArrayRef(IxCode.RefArray node, CodeProxy code){
    IxCode.ArrayTypeRef targetType = (IxCode.ArrayTypeRef)node.getObject().type();
    InstructionHandle start = codeExpression(node.getObject(), code);
    codeExpression(node.getIndex(), code);
    code.appendArrayLoad(typeOf(targetType.base()));
    return start;
  }
  
  public InstructionHandle codeArrayLengthNode(IxCode.ArrayLength node, CodeProxy code){
    InstructionHandle start = codeExpression(node.getTarget(), code);
    code.append(InstructionConstants.ARRAYLENGTH);
    return start;
  }
  
  public InstructionHandle codeArrayAssignment(
    IxCode.SetArray node, CodeProxy code){
    IxCode.ArrayTypeRef targetType = (IxCode.ArrayTypeRef)node.object().type();
    InstructionHandle start = codeExpression(node.object(), code);
    code.appendDup(1);
    codeExpression(node.index(), code);
    codeExpression(node.value(), code);
    code.appendArrayStore(typeOf(targetType.base()));
    return start;
  }
  
  public InstructionHandle codeNew(IxCode.NewObject node, CodeProxy code) {
    IxCode.ClassTypeRef type = node.constructor.affiliation();
    InstructionHandle start = code.appendNew((ObjectType)typeOf(type));
    code.append(InstructionConstants.DUP);
    for (int i = 0; i < node.parameters.length; i++) {
      codeExpression(node.parameters[i], code);
    }
    String className = type.name();
    Type[] arguments = typesOf(node.constructor.getArgs());
    short kind = Constants.INVOKESPECIAL;
    code.appendInvoke(className, "<init>", Type.VOID, arguments, kind);
    return start;
  }
  
  public InstructionHandle codeNewArray(IxCode.NewArray node, CodeProxy code){
    InstructionHandle start = codeExpressions(node.parameters, code);
    IxCode.ArrayTypeRef type = node.arrayType;
    code.appendNewArray(typeOf(type.component()), (short)node.parameters.length);
    return start;
  }
  
  public InstructionHandle codeStaticMethodCall(
    IxCode.CallStatic node, CodeProxy code
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
    String className = node.target.name();
    String name = node.method.name();
    Type returnType = typeOf(node.type());
    Type[] arguments = typesOf(node.method.arguments());
    short kind = Constants.INVOKESTATIC;
    code.appendInvoke(className, name, returnType, arguments, kind);
    return start;
  }

  public InstructionHandle codeBinaryExpression(
    IxCode.BinaryTerm node, CodeProxy code
  ){
    if(node.kind() == LOGICAL_AND){
      return codeLogicalAnd(node, code);
    }else if(node.kind() == LOGICAL_OR){
      return codeLogicalOr(node, code);
    }else if(node.kind() == ELVIS){
      return codeElvis(node, code);
    }
    IxCode.Term left = node.lhs();
    IxCode.Term right = node.rhs();
    InstructionHandle start = codeExpression(left, code);
    codeExpression(right, code);
    switch (node.kind()) {
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
  
  public void bitShiftR2(CodeProxy code, IxCode.TypeRef type){
    if(type == IxCode.BasicTypeRef.INT){
      code.append(InstructionConstants.ISHR);
    }else if(type == IxCode.BasicTypeRef.LONG){
      code.append(InstructionConstants.LSHR);
    }else{
      throw new RuntimeException();
    }
  }
  
  public void bitShiftL2(CodeProxy code, IxCode.TypeRef type){
    if(type == IxCode.BasicTypeRef.INT){
      code.append(InstructionConstants.ISHL);
    }else if(type == IxCode.BasicTypeRef.LONG){
      code.append(InstructionConstants.LSHL);
    }else{
      throw new RuntimeException();
    }
  }
  
  public void bitShiftR3(CodeProxy code, IxCode.TypeRef type){
    if(type == IxCode.BasicTypeRef.INT){
      code.append(InstructionConstants.IUSHR);
    }else if(type == IxCode.BasicTypeRef.LONG){
      code.append(InstructionConstants.LUSHR);
    }else{
      throw new RuntimeException();
    }
  }
  
  public InstructionHandle codeLogicalAnd(IxCode.BinaryTerm node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.lhs(), code);
    BranchHandle b1 = null, b2 = null, b3 = null;
    
    b1 = code.append(new IFEQ(null));
    codeExpression(node.rhs(), code);
    b2 = code.append(new IFEQ(null));
    code.append(InstructionConstants.ICONST_1);
    b3 = code.append(new GOTO(null));
    InstructionHandle failure = code.append(InstructionConstants.ICONST_0);
    b1.setTarget(failure);
    b2.setTarget(failure);
    b3.setTarget(code.append(InstructionConstants.NOP));      
    return start;
  }

  public InstructionHandle codeLogicalOr(IxCode.BinaryTerm node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.lhs(), code);
    BranchHandle b1 = null, b2 = null, b3 = null;
    b1 = code.append(new IFNE(null));
    codeExpression(node.rhs(), code);
    b2 = code.append(new IFNE(null));
    code.append(InstructionConstants.ICONST_0);
    b3 = code.append(new GOTO(null));
    InstructionHandle success = code.append(InstructionConstants.ICONST_1);
    b1.setTarget(success);
    b2.setTarget(success);
    b3.setTarget(code.append(new NOP()));
    return start;
  }
  
  public InstructionHandle codeElvis(IxCode.BinaryTerm node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.lhs(), code);
    code.appendDup(1);
    code.appendNull(typeOf(node.type()));
    BranchHandle b1 = code.append(new IF_ACMPEQ(null));
    BranchHandle b2 = code.append(new GOTO(null));
    b1.setTarget(code.appendPop(1));
    codeExpression(node.rhs(), code);
    b2.setTarget(code.append(new NOP()));
    return start;
  }
  
  public void bitAnd(CodeProxy code, IxCode.TypeRef type) {
    if (type == IxCode.BasicTypeRef.INT || type == IxCode.BasicTypeRef.BOOLEAN) {
      code.append(new IAND());
    } else if(type == IxCode.BasicTypeRef.LONG) {
      code.append(new LAND());
    } else {
      throw new RuntimeException();
    }
  }
  
  public void bitOr(CodeProxy code, IxCode.TypeRef type) {
    if(type == IxCode.BasicTypeRef.INT || type == IxCode.BasicTypeRef.BOOLEAN) {
      code.append(new IOR());
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LOR());
    } else {
      throw new RuntimeException();
    }
  }
  
  public void xor(CodeProxy code, IxCode.TypeRef type) {
    if(type == IxCode.BasicTypeRef.INT || type == IxCode.BasicTypeRef.BOOLEAN) {
      code.append(new IXOR());
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LXOR());
    } else {
      throw new RuntimeException();
    }
  }

  public void eq(CodeProxy code, IxCode.TypeRef type) {
    BranchHandle b1 = null;
    if(type == IxCode.BasicTypeRef.INT || type == IxCode.BasicTypeRef.CHAR ||
       type == IxCode.BasicTypeRef.BOOLEAN) {
      b1 = code.append(new IF_ICMPEQ(null));
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LCMP());
      b1 = code.append(new IFEQ(null));
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      b1 = code.append(new IFEQ(null));
    } else if (type == IxCode.BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      b1 = code.append(new IFEQ(null));
    } else {
      b1 = code.append(new IF_ACMPEQ(null));
    }
    processBranch(code, b1);
  }
  
  public void noteq(CodeProxy code, IxCode.TypeRef type) {
    BranchHandle b1 = null;
    if(type == IxCode.BasicTypeRef.INT || type == IxCode.BasicTypeRef.CHAR ||
       type == IxCode.BasicTypeRef.BOOLEAN) {
      b1 = code.append(new IF_ICMPNE(null));
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LCMP());
      b1 = code.append(new IFNE(null));
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      b1 = code.append(new IFNE(null));
    } else if (type == IxCode.BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      b1 = code.append(new IFNE(null));
    } else {
      b1 = code.append(new IF_ACMPNE(null));
    }
    processBranch(code, b1);
  }
  
  public void gt(CodeProxy code, IxCode.TypeRef type) {
    BranchHandle b1 = null;
    if (type == IxCode.BasicTypeRef.INT){
      b1 = code.append(new IF_ICMPGT(null));
    } else if (type == IxCode.BasicTypeRef.LONG){
      code.append(new LCMP());
      b1 = code.append(new IFGT(null));
    } else if (type == IxCode.BasicTypeRef.FLOAT){
      code.append(new FCMPL());
      b1 = code.append(new IFGT(null));
    } else if (type == IxCode.BasicTypeRef.DOUBLE){
      code.append(new DCMPL());
      b1 = code.append(new IFGT(null));
    } else {
      throw new RuntimeException("");
    }
    processBranch(code, b1);
  }
  
  public void gte(CodeProxy code, IxCode.TypeRef type) {
    BranchHandle comparation = null;
    if(type == IxCode.BasicTypeRef.INT) {
      comparation = code.append(new IF_ICMPGE(null));
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LCMP());
      comparation = code.append(new IFGE(null));
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      comparation = code.append(new IFGE(null));
    } else if (type == IxCode.BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      comparation = code.append(new IFGE(null));
    } else {
      throw new RuntimeException("");
    }
    processBranch(code, comparation);
  }

  public void lte(CodeProxy code, IxCode.TypeRef type) {
    BranchHandle b1 = null;
    if (type == IxCode.BasicTypeRef.INT) {
      b1 = code.append(new IF_ICMPLE(null));
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LCMP());
      b1 = code.append(new IFLT(null));
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FCMPL());
      b1 = code.append(new IFLE(null));
    } else if (type == IxCode.BasicTypeRef.DOUBLE) {
      code.append(new DCMPL());
      b1 = code.append(new IFLE(null));
    } else {
      throw new RuntimeException("");
    }
    processBranch(code, b1);
  }

  
  public void lt(CodeProxy code, IxCode.TypeRef type) {
    BranchHandle comparation = null;
    if(type == IxCode.BasicTypeRef.INT) {
      comparation = code.append(new IF_ICMPLT(null));
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LCMP());
      comparation = code.append(new IFLT(null));
    } else if (type == IxCode.BasicTypeRef.FLOAT){
      code.append(new FCMPL());
      comparation = code.append(new IFLT(null));
    } else if (type == IxCode.BasicTypeRef.DOUBLE) {
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
  
  public InstructionHandle codeChar(IxCode.CharacterValue node, CodeProxy code) {
    return code.appendConstant(new Character(node.getValue()));
  }

  public InstructionHandle codeString(IxCode.StringValue node, CodeProxy code) {
    return code.appendConstant(node.value());
  }
  
  public InstructionHandle codeInteger(IxCode.IntValue node, CodeProxy code){
    return code.appendConstant(new Integer(node.getValue()));
  }
  
  public InstructionHandle codeLong(IxCode.LongValue node, CodeProxy code){
    return code.appendConstant(new Long(node.getValue()));
  }
  
  public InstructionHandle codeFloat(IxCode.FloatValue node, CodeProxy code){
    return code.appendConstant(new Float(node.getValue()));
  }
  
  public InstructionHandle codeDouble(IxCode.DoubleValue node, CodeProxy code){
    return code.appendConstant(new Double(node.getValue()));
  }
  
  public InstructionHandle codeBoolean(IxCode.BoolValue node, CodeProxy code){
    return code.appendConstant(Boolean.valueOf(node.getValue()));
  }
  
  public InstructionHandle codeNull(IxCode.NullValue node, CodeProxy code){
    return code.append(InstructionConstants.ACONST_NULL);
  }
  
  public InstructionHandle codeUnaryExpression(
    IxCode.UnaryTerm node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getOperand(), code);
    IxCode.TypeRef type = node.getOperand().type();
    switch(node.getKind()){
    	case PLUS: plus(code, type); break;
    	case MINUS: minus(code, type); break;
    	case NOT: not(code, type); break;
    	case BIT_NOT: bitNot(code, type); break;
    	default: throw new RuntimeException();
    }
    return start;
  }
  
  private void plus(CodeProxy code, IxCode.TypeRef type){
    if(
     type != IxCode.BasicTypeRef.INT && type != IxCode.BasicTypeRef.LONG &&
     type != IxCode.BasicTypeRef.FLOAT && type != IxCode.BasicTypeRef.DOUBLE){
      throw new RuntimeException();
    }
    /*nothing to do*/
  }

  private void minus(CodeProxy code, IxCode.TypeRef type){
    if(type == IxCode.BasicTypeRef.INT){
      code.append(InstructionConstants.INEG);
    }else if(type == IxCode.BasicTypeRef.LONG){
      code.append(InstructionConstants.LNEG);
    }else if(type == IxCode.BasicTypeRef.FLOAT){
      code.append(InstructionConstants.FNEG);
    }else if(type == IxCode.BasicTypeRef.DOUBLE){
      code.append(InstructionConstants.DNEG);
    }else{
      throw new RuntimeException();
    }
  }

  private void not(CodeProxy code, IxCode.TypeRef type){
    if(type == IxCode.BasicTypeRef.BOOLEAN){
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

  private void bitNot(CodeProxy code, IxCode.TypeRef type){
    if(type == IxCode.BasicTypeRef.INT){
      code.append(new ICONST(-1));
      code.append(new IXOR());
    }else if(type == IxCode.BasicTypeRef.LONG){
      code.append(new LCONST(-1));
      code.append(new LXOR());
    }else{
      throw new RuntimeException();
    }
  }

  public InstructionHandle codeCast(IxCode.AsInstanceOf node, CodeProxy code) {
    IxCode.Term target = node.target();
    InstructionHandle start = codeExpression(target, code);
    code.appendCast(typeOf(target.type()), typeOf(node.destination()));
    return start;
  }
  
  public InstructionHandle codeIsInstance(IxCode.InstanceOf node, CodeProxy code){
    InstructionHandle start = codeExpression(node.target, code);
    code.appendInstanceOf((ReferenceType)typeOf(node.checked()));
    return start;
  }
  
  public InstructionHandle codeSelf(IxCode.This node, CodeProxy code){
    return code.append(InstructionConstants.ALOAD_0);
  }

  public InstructionHandle codeOuterThis(IxCode.OuterThis node, CodeProxy code){
    code.appendThis();
    code.appendGetField(currentClosureName, OUTER_THIS, Type.OBJECT);
    return code.appendCast(Type.OBJECT, typeOf(node.type()));
  }

  public InstructionHandle codeFieldRef(IxCode.RefField node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.target, code);
    IxCode.ClassTypeRef symbol = (IxCode.ClassTypeRef) node.target.type();
    code.appendGetField(symbol.name(), node.field.name(), typeOf(node.type()));
    return start;
  }

  public InstructionHandle codeFieldAssign(IxCode.SetField node, CodeProxy code) {
    InstructionHandle start = codeExpression(node.getObject(), code);    
    codeExpression(node.getValue(), code);
    if(isWideType(node.getValue().type())){
      code.append(InstructionConstants.DUP2_X1);
    }else{
      code.append(InstructionConstants.DUP_X1);
    }
    IxCode.ClassTypeRef symbol = (IxCode.ClassTypeRef) node.getObject().type();
    code.appendPutField(symbol.name(), node.getField().name(), typeOf(node.type()));
    return start;
  }

  private void add(CodeProxy code, IxCode.TypeRef type) {
    if (type == IxCode.BasicTypeRef.INT) {
      code.append(new IADD());
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LADD());
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FADD());
    } else {
      code.append(new DADD());
    }
  }

  private void sub(CodeProxy code, IxCode.TypeRef type) {
    if (type == IxCode.BasicTypeRef.INT) {
      code.append(new ISUB());
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LSUB());
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FSUB());
    } else {
      code.append(new DSUB());
    }
  }
  
  private void mul(CodeProxy code, IxCode.TypeRef type) {
    if (type == IxCode.BasicTypeRef.INT) {
      code.append(new IMUL());
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LMUL());
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FMUL());
    } else {
      code.append(new DMUL());
    }
  }

  private void div(CodeProxy code, IxCode.TypeRef type) {
    if (type == IxCode.BasicTypeRef.INT) {
      code.append(new IDIV());
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LDIV());
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FDIV());
    } else {
      code.append(new DDIV());
    }
  }

  private void mod(CodeProxy code, IxCode.TypeRef type) {
    if (type == IxCode.BasicTypeRef.INT) {
      code.append(new IREM());
    } else if (type == IxCode.BasicTypeRef.LONG) {
      code.append(new LREM());
    } else if (type == IxCode.BasicTypeRef.FLOAT) {
      code.append(new FREM());
    } else {
      code.append(new DREM());
    }
  }
  
  private int frameObjectIndex(int origin, IxCode.TypeRef[] arguments){
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

  private int[] makeIndexTableFor(int origin, LocalFrame frame) {
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
  
  private int[] makeIndexTableForClosureFrame(LocalFrame frame) {
    LocalBinding[] bindings = frame.entries();
    int[] indexTable = new int[bindings.length];
    int maxIndex = 0;
    for(int i = 0; i < bindings.length; i++) {
      indexTable[i] = maxIndex;
      maxIndex++;
    }
    return indexTable;
  }

  private boolean isWideType(IxCode.TypeRef symbol) {
    if (symbol == IxCode.BasicTypeRef.DOUBLE || symbol == IxCode.BasicTypeRef.LONG)
      return true;
    return false;
  }

  private Type typeOf(IxCode.TypeRef type) {
    return translateIxTypeToVmType(type);
  }

  private Type[] typesOf(IxCode.TypeRef[] types) {
    Type[] destinationTypes = new Type[types.length];
    for (int i = 0; i < destinationTypes.length; i++) {
      destinationTypes[i] = translateIxTypeToVmType(types[i]);
    }
    return destinationTypes;
  }

  public static class CodeProxy {
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
}

