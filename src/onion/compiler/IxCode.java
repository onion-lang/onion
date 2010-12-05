package onion.compiler;

import onion.compiler.env.ClassTable;
import onion.compiler.env.ClosureLocalBinding;
import onion.compiler.env.LocalFrame;
import onion.compiler.util.Strings;
import onion.lang.syntax.Modifier;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 21:20:19
 * To change this template use File | Settings | File Templates.
 */
public interface IxCode {
    /**
     * @author Kota Mizushima
     * Date: 2005/07/06
     */
    class IrArrayLength extends IrExpression {
      private final IrExpression target;

      public IrArrayLength(IrExpression target) {
        this.target = target;
      }

      public IrExpression getTarget() {
        return target;
      }

      public TypeRef type(){
        return BasicTypeRef.INT;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    class IrArrayRef extends IrExpression {
      private final IrExpression object, index;

      public IrArrayRef(IrExpression target, IrExpression index) {
        this.object = target;
        this.index = index;
      }

      public IrExpression getIndex() {
        return index;
      }

      public IrExpression getObject() {
        return object;
      }

      public TypeRef type(){
        return ((ArraySymbol)object.type()).getBase();
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    class IrArraySet extends IrExpression {
      private final IrExpression object, index, value;

      public IrArraySet(
        IrExpression target, IrExpression index, IrExpression value
      ) {
        this.object = target;
        this.index = index;
        this.value = value;
      }

      public IrExpression getIndex() {
        return index;
      }

      public IrExpression getObject() {
        return object;
      }

      public IrExpression getValue() {
        return value;
      }

      public TypeRef type(){
        return value.type();
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrBegin extends IrExpression {
      private final IrExpression[] expressions;

      public IrBegin(IrExpression[] expressions) {
        this.expressions = expressions;
      }

      public IrBegin(List expressions) {
        this((IrExpression[])expressions.toArray(new IrExpression[0]));
      }

      public IrBegin(IrExpression expression) {
        this(new IrExpression[]{expression});
      }

      public IrBegin(IrExpression expression1, IrExpression expression2){
        this(new IrExpression[]{expression1, expression2});
      }

      public IrBegin(IrExpression expression1, IrExpression expression2, IrExpression expression3){
        this(new IrExpression[]{expression1, expression2, expression3});
      }

      public IrExpression[] getExpressions() {
        return expressions;
      }

      public TypeRef type() {
        return expressions[expressions.length - 1].type();
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrBinExp extends IrExpression {
      public interface Constants {
          int ADD = 0;
          int SUBTRACT = 1;
          int MULTIPLY = 2;
          int DIVIDE = 3;
          int MOD = 4;

          //logical operator
          int LOGICAL_AND = 5;
          int LOGICAL_OR = 6;

          //bit operator
          int BIT_AND = 7;
          int BIT_OR = 8;
          int XOR = 9;
          int BIT_SHIFT_L2 = 10;
          int BIT_SHIFT_R2 = 11;
          int BIT_SHIFT_R3 = 12;

          //comparation operator
          int LESS_THAN = 13;
          int GREATER_THAN = 14;
          int LESS_OR_EQUAL = 15;
          int GREATER_OR_EQUAL = 16;
          int EQUAL = 17;
          int NOT_EQUAL = 18;

        //other operator
        int ELVIS = 19;
      }

      private final int kind;
      private final IrExpression left, right;
      private final TypeRef type;

      public IrBinExp(
        int kind, TypeRef type, IrExpression left, IrExpression right
      ) {
        this.kind = kind;
        this.left = left;
        this.right = right;
        this.type = type;
      }

      public IrExpression getLeft(){
        return left;
      }

      public IrExpression getRight(){
        return right;
      }

      public int getKind(){
        return kind;
      }

      public TypeRef type(){ return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrBlock implements IrStatement {
      private final IrStatement[] statements;

      public IrBlock(IrStatement[] statements) {
        this.statements = statements;
      }

      public IrBlock(List statements) {
        this((IrStatement[])statements.toArray(new IrStatement[0]));
      }

      public IrBlock(IrStatement statement) {
        this(new IrStatement[]{statement});
      }

      public IrBlock(IrStatement statement1, IrStatement statement2){
        this(new IrStatement[]{statement1, statement2});
      }

      public IrBlock(IrStatement statement1, IrStatement statement2, IrStatement statement3){
        this(new IrStatement[]{statement1, statement2, statement3});
      }

      public IrStatement[] getStatements() {
        return statements;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrBool extends IrExpression {
      private final boolean value;

      public IrBool(boolean value){
        this.value = value;
      }

      public boolean getValue() {
        return value;
      }

      public TypeRef type(){
        return BasicTypeRef.BOOLEAN;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrBreak implements IrStatement {
      public IrBreak() {}
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrByte extends IrExpression {
      private final byte value;

      public IrByte(byte value) {
        this.value = value;
      }

      public byte getValue() {
        return value;
      }

      public TypeRef type() {
        return BasicTypeRef.BYTE;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    class IrCall extends IrExpression {
      public final IrExpression target;
      public final MethodSymbol method;
      public final IrExpression[] parameters;

      public IrCall(
        IrExpression target, MethodSymbol method, IrExpression[] parameters) {
        this.target = target;
        this.method = method;
        this.parameters = parameters;
      }

      public TypeRef type() { return method.getReturnType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    class IrCallStatic extends IrExpression {
      public ObjectTypeRef target;
      public MethodSymbol method;
      public IrExpression[] parameters;

      public IrCallStatic(
        ObjectTypeRef target, MethodSymbol method, IrExpression[] parameters) {
        this.target = target;
        this.method = method;
        this.parameters = parameters;
      }

      public TypeRef type() { return method.getReturnType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    class IrCallSuper extends IrExpression {
      private final IrExpression target;
      private final MethodSymbol method;
      private final IrExpression[] params;

      public IrCallSuper(IrExpression target, MethodSymbol method, IrExpression[] params) {
        this.target = target;
        this.method = method;
        this.params = params;
      }

      public TypeRef type() {
        return method.getReturnType();
      }

      public IrExpression getTarget() {
        return target;
      }

      public MethodSymbol getMethod() {
        return method;
      }

      public IrExpression[] getParams() {
        return params;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrCast extends IrExpression {
      private final IrExpression target;
      private final TypeRef conversion;

      public IrCast(IrExpression target, TypeRef conversion) {
        this.target = target;
        this.conversion = conversion;
      }

      public TypeRef getConversion() {
        return conversion;
      }

      public IrExpression getTarget() {
        return target;
      }

      public TypeRef type() {
        return conversion;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrChar extends IrExpression {
      private final char value;

      public IrChar(char value) {
        this.value = value;
      }

      public char getValue() {
        return value;
      }

      public TypeRef type() {
        return BasicTypeRef.CHAR;
      }
    }

    /**
     * This class represents class or interface definitions of internal language.
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrClass extends AbstractClassSymbol implements IrNode {
      private boolean isInterface;
      private int modifier;
      private String name;
      private ClassSymbol superClass;
      private ClassSymbol[] interfaces;
      private List fields = new ArrayList();
      private List methods = new ArrayList();
      private List constructors = new ArrayList();
      private boolean isResolutionComplete;
      private boolean hasCyclicity;
      private String sourceFile;

      /**
       *
       * @param isInterface indicates whether this class is interface or class
       * @param modifier class modifier
       * @param name class name. it cannot be null
       * @param superClass super class
       * @param interfaces super interfaces
       */
      public IrClass(boolean isInterface, int modifier, String name, ClassSymbol superClass, ClassSymbol[] interfaces) {
        this.isInterface = isInterface;
        this.modifier = modifier;
        this.name = name;
        this.superClass = superClass;
        this.interfaces = interfaces;
      }

      /**
       * This method creates interface definition.
       * @param modifier
       * @param name
       * @param interfaces
       * @return
       */
      public static IrClass newInterface(int modifier, String name, ClassSymbol[] interfaces){
        return new IrClass(true, modifier, name, null, interfaces);
      }

      /**
       * This method creates class definition.
       * @param modifier
       * @param name
       * @param superClass
       * @param interfaces
       * @return
       */
      public static IrClass newClass(int modifier, String name, ClassSymbol superClass, ClassSymbol[] interfaces){
        return new IrClass(false, modifier, name, superClass, interfaces);
      }

      /**
       * This method creates class definition.
       * @param modifier
       * @param name
       * @return
       */
      public static IrClass newClass(int modifier, String name) {
        return new IrClass(false, modifier, name, null, null);
      }

      public boolean isInterface() {
        return isInterface;
      }

      public int getModifier() {
        return modifier;
      }

      public void setModifier(int modifier) {
        this.modifier = modifier;
      }

      public void setName(String name) {
        this.name = name;
      }

      public String getName() {
        return name;
      }

      public void setSuperClass(ClassSymbol superClass) {
        this.superClass = superClass;
      }

      public ClassSymbol getSuperClass() {
        return superClass;
      }

      public void setInterfaces(ClassSymbol[] interfaces) {
        this.interfaces = interfaces;
      }

      public ClassSymbol[] getInterfaces() {
        return interfaces;
      }

      public void setResolutionComplete(boolean isInResolution) {
        this.isResolutionComplete = isInResolution;
      }

      public boolean isResolutionComplete() {
        return isResolutionComplete;
      }

      public void addMethod(MethodSymbol method) {
        methods.add(method);
      }

      public void addField(FieldSymbol field) {
        fields.add(field);
      }

      public void addConstructor(ConstructorSymbol constructor) {
        constructors.add(constructor);
      }

      public void addDefaultConstructor() {
        constructors.add(IrConstructor.newDefaultConstructor(this));
      }

      public MethodSymbol[] getMethods() {
        return ((MethodSymbol[])methods.toArray(new MethodSymbol[0]));
      }

      public FieldSymbol[] getFields() {
        return ((FieldSymbol[])fields.toArray(new FieldSymbol[0]));
      }

      public ConstructorSymbol[] getConstructors() {
        return ((ConstructorSymbol[]) constructors.toArray(new ConstructorSymbol[0]));
      }

      public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
      }

      public String getSourceFile(){
        return sourceFile;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrClosure extends IrExpression {
      private ClassSymbol type;
      private MethodSymbol method;
      private IrStatement block;
      private LocalFrame frame;

      public IrClosure(ClassSymbol type, MethodSymbol method, IrStatement block) {
        this.type =  type;
        this.method = method;
        this.block = block;
      }

      public int getModifier() {
        return method.getModifier();
      }

      public ClassSymbol getClassType() {
        return type;
      }

      public MethodSymbol getMethod(){
        return method;
      }

      public String getName() {
        return method.getName();
      }

      public TypeRef[] getArguments() {
        return method.getArguments();
      }

      public TypeRef getReturnType() {
        return method.getReturnType();
      }

      public IrStatement getBlock() {
        return block;
      }

      public void setFrame(LocalFrame frame){
        this.frame = frame;
      }

      public LocalFrame getFrame(){
        return frame;
      }

      public TypeRef type() {
        return type;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrConstructor implements IrNode, ConstructorSymbol {
      private int modifier;
      private ClassSymbol classType;
      private TypeRef[] arguments;
      private IrBlock block;
      private IrSuper superInitializer;
      private LocalFrame frame;

      public IrConstructor(
        int modifier, ClassSymbol classType,
        TypeRef[] arguments, IrBlock block, IrSuper superInitializer
      ) {
        this.modifier = modifier;
        this.classType = classType;
        this.arguments = arguments;
        this.block = block;
        this.superInitializer = superInitializer;
      }

      public static IrConstructor newDefaultConstructor(ClassSymbol type) {
        IrBlock block = new IrBlock(new IrReturn(null));
        IrSuper init = new IrSuper(type.getSuperClass(), new TypeRef[0], new IrExpression[0]);
        IrConstructor node =  new IrConstructor(Modifier.PUBLIC, type, new TypeRef[0], block, init);
        node.setFrame(new LocalFrame(null));
        return node;
      }

      public String getName() {
        return "new";
      }

      public TypeRef[] getArgs() {
        return arguments;
      }

      public ClassSymbol getClassType() {
        return classType;
      }

      public int getModifier() {
        return modifier;
      }

      public IrSuper getSuperInitializer() {
        return superInitializer;
      }

      public void setSuperInitializer(IrSuper superInitializer) {
        this.superInitializer = superInitializer;
      }

      public void setBlock(IrBlock block) {
        this.block = block;
      }

      public IrBlock getBlock() {
        return block;
      }

      public void setFrame(LocalFrame frame) {
        this.frame = frame;
      }

      public LocalFrame getFrame() {
        return frame;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrContinue implements IrStatement {
      public IrContinue(){}
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrDouble extends IrExpression {
      private final double value;

      public IrDouble(double value) {
        this.value = value;
      }

      public double getValue() {
        return value;
      }

      public TypeRef type() {
        return BasicTypeRef.DOUBLE;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    abstract class IrExpression implements IrNode {
      public IrExpression() {}

      public abstract TypeRef type();

      public boolean isBasicType(){
        return type().isBasicType();
      }

      public boolean isArrayType() {
        return type().isArrayType();
      }

      public boolean isClassType() {
        return type().isClassType();
      }

      public boolean isNullType() { return type().isNullType(); }

      public boolean isReferenceType(){ return type().isObjectType(); }

      public static IrExpression defaultValue(TypeRef type){
        if(type == BasicTypeRef.CHAR) return new IrChar((char)0);
        if(type == BasicTypeRef.BYTE)return new IrByte((byte)0);
        if(type == BasicTypeRef.SHORT) return new IrShort((short)0);
        if(type == BasicTypeRef.INT) return new IrInt(0);
        if(type == BasicTypeRef.LONG) return new IrLong(0);
        if(type == BasicTypeRef.FLOAT) return new IrFloat(0.0f);
        if(type == BasicTypeRef.DOUBLE) return new IrDouble(0.0);
        if(type == BasicTypeRef.BOOLEAN) return new IrBool(false);
        if(type.isObjectType()) return new IrNull();
        return null;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrExpStmt implements IrStatement {
      public IrExpression expression;
      public IrExpStmt(IrExpression expression){
        this.expression = expression;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrField implements IrNode, FieldSymbol {

      private int modifier;
      private ClassSymbol classType;
      private String name;
      private TypeRef type;

      public IrField(
        int modifier, ClassSymbol classType, String name, TypeRef type) {
        this.modifier = modifier;
        this.classType = classType;
        this.name = name;
        this.type = type;
      }

      public ClassSymbol getClassType() {
        return classType;
      }

      public int getModifier() {
        return modifier;
      }

      public String getName() {
        return name;
      }

      public TypeRef getType() {
        return type;
      }

    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrFieldRef extends IrExpression {
      public IrExpression target;
      public FieldSymbol field;

      public IrFieldRef(IrExpression target, FieldSymbol field) {
        this.target = target;
        this.field = field;
      }

      public TypeRef type() { return field.getType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrFieldSet extends IrExpression {
      private final IrExpression object;
      private final FieldSymbol field;
      private final IrExpression value;

      public IrFieldSet(
        IrExpression target, FieldSymbol field, IrExpression value
      ) {
        this.object = target;
        this.field = field;
        this.value = value;
      }

      public TypeRef type() { return field.getType(); }

      public IrExpression getObject() { return object; }

      public FieldSymbol getField() { return field; }

      public IrExpression getValue() { return value; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrFloat extends IrExpression {
      private final float value;

      public IrFloat(float value){
        this.value = value;
      }

      public float getValue() { return value; }

      public TypeRef type() { return BasicTypeRef.FLOAT; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrIf implements IrStatement{
      private final IrExpression condition;
      private final IrStatement thenStatement, elseStatement;

      public IrIf(
        IrExpression condition,
        IrStatement thenStatement, IrStatement elseStatement){
        this.condition = condition;
        this.thenStatement = thenStatement;
        this.elseStatement = elseStatement;
      }

      public IrExpression getCondition() {
        return condition;
      }

      public IrStatement getThenStatement() {
        return thenStatement;
      }

      public IrStatement getElseStatement() {
        return elseStatement;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrInstanceOf extends IrExpression {
      public IrExpression target;
      public TypeRef checked;

      public IrInstanceOf(IrExpression target, TypeRef checked){
        this.target = target;
        this.checked = checked;
      }

      public TypeRef getCheckType() {
        return checked;
      }

      public IrExpression getTarget() {
        return target;
      }

      public TypeRef type() { return BasicTypeRef.BOOLEAN; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrInt extends IrExpression {
      private int value;
      public IrInt(int value){
        this.value = value;
      }
      public int getValue() {
        return value;
      }

      public TypeRef type() { return BasicTypeRef.INT; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    class IrList extends IrExpression {
      private final IrExpression[] elements;
      private final TypeRef type;

      public IrList(IrExpression[] elements, TypeRef type) {
        this.elements = elements;
        this.type = type;
      }

      public IrExpression[] getElements() {
        return elements;
      }

      public TypeRef type() {
        return type;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrLocalRef extends IrExpression {
      private int frame;
      private int index;
      private TypeRef type;

      public IrLocalRef(ClosureLocalBinding bind) {
        this.frame = bind.getFrame();
        this.index = bind.getIndex();
        this.type = bind.getType();
      }

      public IrLocalRef(int frame, int index, TypeRef type){
        this.frame = frame;
        this.index = index;
        this.type = type;
      }

      public int frame(){ return frame; }

      public int index(){ return index; }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrLocalSet extends IrExpression {
      private final int frame;
      private final int index;
      private final IrExpression value;
      private final TypeRef type;

      public IrLocalSet(int frame, int index, TypeRef type, IrExpression value){
        this.frame = frame;
        this.index = index;
        this.value = value;
        this.type = type;
      }

      public IrLocalSet(ClosureLocalBinding bind, IrExpression value){
        this.frame = bind.getFrame();
        this.index = bind.getIndex();
        this.type = bind.getType();
        this.value = value;
      }

      public int getFrame() {
        return frame;
      }

      public int getIndex() {
        return index;
      }

      public IrExpression getValue() {
        return value;
      }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrLong extends IrExpression {
      private final long value;

      public IrLong(long value){
        this.value = value;
      }

      public long getValue() { return value; }

      public TypeRef type() { return BasicTypeRef.LONG; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrLoop implements IrStatement {
      public final IrExpression condition;
      public final IrStatement stmt;

      public IrLoop(IrExpression condition, IrStatement stmt) {
        this.condition = condition;
        this.stmt = stmt;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrMember implements IrNode{
      private final int modifier;
      private final TypeRef classType;

      public IrMember(int modifier, TypeRef classType){
        this.modifier = modifier;
        this.classType = classType;
      }

      public TypeRef getClassType() {
        return classType;
      }

      public int getModifier() {
        return modifier;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrMethod implements IrNode, MethodSymbol {
      private int modifier;
      private ClassSymbol classType;
      private String name;
      private TypeRef[] arguments;
      private IrBlock block;
      private TypeRef returnType;
      private boolean closure;
      private LocalFrame frame;

      public IrMethod(
        int modifier, ClassSymbol classType, String name, TypeRef[] arguments,
        TypeRef returnType, IrBlock block){
        this.modifier = modifier;
        this.classType = classType;
        this.name = name;
        this.arguments = arguments;
        this.returnType = returnType;
        this.block = block;
      }

      public int getModifier() {
        return modifier;
      }

      public ClassSymbol getClassType() {
        return classType;
      }

      public String getName() {
        return name;
      }

      public TypeRef[] getArguments() {
        return arguments;
      }

      public TypeRef getReturnType(){
        return returnType;
      }

      public IrBlock getBlock() {
        return block;
      }

      public void setBlock(IrBlock block){
        this.block = block;
      }

      public void setClosure(boolean closure){
        this.closure = closure;
      }

      public boolean hasClosure(){
        return closure;
      }

      public void setFrame(LocalFrame frame) {
        this.frame = frame;
      }

      public LocalFrame getFrame() {
        return frame;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    class IrNew extends IrExpression {
      public final ConstructorSymbol constructor;
      public final IrExpression[] parameters;

      public IrNew(ConstructorSymbol constructor, IrExpression[] parameters){
        this.constructor = constructor;
        this.parameters = parameters;
      }

      public TypeRef type() { return constructor.getClassType(); }
    }

    class IrNewArray extends IrExpression {
      public final ArraySymbol arrayType;
      public final IrExpression[] parameters;

      public IrNewArray(ArraySymbol arrayType, IrExpression[] parameters){
        this.arrayType = arrayType;
        this.parameters = parameters;
      }

      public TypeRef type() { return arrayType; }
    }

    /**
     * This interface represents an internal representation node of onion program.
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    interface IrNode {
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrNOP implements IrStatement {
      public IrNOP() {}
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrNull extends IrExpression {
      public IrNull(){}

      public TypeRef type() { return NullTypeRef.NULL; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrReturn implements IrStatement {
      public final IrExpression expression;

      public IrReturn(IrExpression expression) {
        this.expression = expression;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrShort extends IrExpression {
      private short value;
      public IrShort(short value){
        this.value = value;
      }
      public short getValue() {
        return value;
      }

      public TypeRef type() { return BasicTypeRef.SHORT; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    interface IrStatement {

    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrStaticFieldRef extends IrExpression {
      public ClassSymbol target;
      public FieldSymbol field;

      public IrStaticFieldRef(ClassSymbol target, FieldSymbol field){
        this.target = target;
        this.field = field;
      }

      public TypeRef type() { return field.getType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrStaticFieldSet extends IrExpression {
      public ObjectTypeRef target;
      public FieldSymbol field;
      public IrExpression value;

      public IrStaticFieldSet(
        ObjectTypeRef target, FieldSymbol field, IrExpression value){
        this.target = target;
        this.field = field;
        this.value = value;
      }

      public TypeRef type() { return field.getType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrString extends IrExpression {
      public String value;
      public TypeRef type;

      public IrString(String value, TypeRef type){
        this.value = value;
        this.type = type;
      }

      public String getValue() { return value; }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrSuper implements IrNode {
      private ClassSymbol classType;
      private TypeRef[] arguments;
      private IrExpression[] expressions;

      public IrSuper(
        ClassSymbol classType, TypeRef[] arguments, IrExpression[] expressions){
        this.classType = classType;
        this.arguments = arguments;
        this.expressions = expressions;
      }

      public ClassSymbol getClassType() {
        return classType;
      }

      public TypeRef[] getArguments() {
        return arguments;
      }

      public IrExpression[] getExpressions() {
        return expressions;
      }

    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrSynchronized implements IrStatement {
      public IrExpression expression;
      public IrStatement statement;
      public IrSynchronized(IrExpression expression, IrStatement statement){
        this.expression = expression;
        this.statement = statement;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrThis extends IrExpression {
      private ClassSymbol classType;

      public IrThis(ClassSymbol classType){
        this.classType = classType;
      }

      public TypeRef type() { return classType; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrThrow implements IrStatement {
      public IrExpression expression;
      public IrThrow(IrExpression expression){
        this.expression = expression;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IrTry implements IrStatement {
      public IrStatement tryStatement;
      public ClosureLocalBinding[] catchTypes;
      public IrStatement[] catchStatements;
      public IrTry(
        IrStatement tryStatement, ClosureLocalBinding[] catchTypes,
        IrStatement[] catchStatements){
        this.tryStatement = tryStatement;
        this.catchTypes = catchTypes;
        this.catchStatements = catchStatements;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IrUnaryExp extends IrExpression {
      public interface Constants {
          int PLUS = 0;
          int MINUS = 1;
          int NOT = 2;
          int BIT_NOT = 3;
      }

      private int kind;
      private IrExpression operand;
      private TypeRef type;

      public IrUnaryExp(
        int kind, TypeRef type, IrExpression operand) {
        this.kind = kind;
        this.operand = operand;
        this.type = type;
      }

      public int getKind(){
        return kind;
      }

      public IrExpression getOperand(){
        return operand;
      }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/15
     */
    abstract class AbstractClassSymbol extends AbstractObjectSymbol implements ClassSymbol {
      private ConstructorFinder constructorFinder;

      public AbstractClassSymbol() {
        constructorFinder = new ConstructorFinder();
      }

      public ConstructorSymbol[] findConstructor(IrExpression[] params){
        return constructorFinder.find(this, params);
      }

      public boolean isClassType() {
        return true;
      }

      public boolean isArrayType() {
        return false;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/15
     */
    abstract class AbstractObjectSymbol implements ObjectTypeRef {
      private MethodFinder methodFinder;
      private FieldFinder fieldFinder;

      public AbstractObjectSymbol() {
        methodFinder = new MethodFinder();
        fieldFinder = new FieldFinder();
      }

      public FieldSymbol findField(String name) {
        return fieldFinder.find(this, name);
      }

      public MethodSymbol[] findMethod(String name, IrExpression[] params) {
        return methodFinder.find(this, name, params);
      }

      public boolean isBasicType() {
        return false;
      }

      public boolean isNullType() {
        return false;
      }

      public boolean isObjectType() {
        return true;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class ArraySymbol extends AbstractObjectSymbol {
      private ClassTable table;
      private TypeRef component;
      private int dimension;
      private ClassSymbol superClass;
      private ClassSymbol[] interfaces;
      private String name;

      public ArraySymbol(TypeRef component, int dimension, ClassTable table){
        this.component = component;
        this.dimension = dimension;
        this.superClass = table.load("java.lang.Object");
        this.interfaces = new ClassSymbol[]{
          table.load("java.io.Serializable"),
          table.load("java.lang.Cloneable")
        };
        this.name = Strings.repeat("[", dimension) + component.getName();
      }

      public TypeRef getComponent(){
        return component;
      }

      public TypeRef getBase(){
        if(dimension == 1){
          return component;
        }else{
          return table.loadArray(component, dimension - 1);
        }
      }

      public int getDimension(){
        return dimension;
      }

      public boolean isInterface() {
        return false;
      }

      public int getModifier() {
        return 0;
      }

      public ClassSymbol getSuperClass() {
        return superClass;
      }

      public ClassSymbol[] getInterfaces() {
        return interfaces;
      }

      public MethodSymbol[] getMethods() {
        return superClass.getMethods();
      }

      public FieldSymbol[] getFields() {
        return superClass.getFields();
      }

      public String getName() {
        return name;
      }

      public boolean isArrayType() {
        return true;
      }

      public boolean isClassType() {
        return false;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class BasicTypeRef implements TypeRef {
      public static final BasicTypeRef BYTE = new BasicTypeRef("byte");
      public static final BasicTypeRef SHORT = new BasicTypeRef("short");
      public static final BasicTypeRef CHAR = new BasicTypeRef("char");
      public static final BasicTypeRef INT = new BasicTypeRef("int");
      public static final BasicTypeRef LONG = new BasicTypeRef("long");
      public static final BasicTypeRef FLOAT = new BasicTypeRef("float");
      public static final BasicTypeRef DOUBLE = new BasicTypeRef("double");
      public static final BasicTypeRef BOOLEAN = new BasicTypeRef("boolean");
      public static final BasicTypeRef VOID = new BasicTypeRef("void");

      private String name;

      private BasicTypeRef(String name) {
        this.name = name;
      }

      public String getName(){
        return name;
      }

      public boolean isNumeric(){
        return isInteger() && isReal();
      }

      public boolean isInteger(){
        return this == BYTE || this == SHORT || this == INT || this == LONG;
      }

      public boolean isReal(){
        return this == FLOAT || this == DOUBLE;
      }

      public boolean isBoolean(){
        return this == BOOLEAN;
      }

      public boolean isArrayType() {
        return false;
      }

      public boolean isBasicType() {
        return true;
      }

      public boolean isClassType() {
        return false;
      }

      public boolean isNullType() {
        return false;
      }

      public boolean isObjectType() {
        return false;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    interface ClassSymbol extends ObjectTypeRef {
      ConstructorSymbol[] getConstructors();
      ConstructorSymbol[] findConstructor(IrExpression[] params);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    class ConstructorFinder {
      private static Comparator sorter = new Comparator(){
        public int compare(Object constructor1, Object constructor2) {
          ConstructorSymbol c1 = (ConstructorSymbol)constructor1;
          ConstructorSymbol c2 = (ConstructorSymbol)constructor2;
          TypeRef[] arg1 = c1.getArgs();
          TypeRef[] arg2 = c2.getArgs();
          int length = arg1.length;
          if(isAllSuperType(arg2, arg1)) return -1;
          if(isAllSuperType(arg1, arg2)) return 1;
          return 0;
        }
      };

      private ParameterMatcher matcher;

      public ConstructorFinder() {
        this.matcher = new StandardParameterMatcher();
      }

      public ConstructorSymbol[] find(ClassSymbol target, IrExpression[] args){
        Set constructors = new TreeSet(new ConstructorSymbolComparator());
        find(constructors, target, args);
        List selected = new ArrayList();
        selected.addAll(constructors);
        Collections.sort(selected, sorter);
        if(selected.size() < 2){
          return (ConstructorSymbol[]) selected.toArray(new ConstructorSymbol[0]);
        }
        ConstructorSymbol constructor1 = (ConstructorSymbol) selected.get(0);
        ConstructorSymbol constructor2 = (ConstructorSymbol) selected.get(1);
        if(isAmbiguous(constructor1, constructor2)){
          return (ConstructorSymbol[]) selected.toArray(new ConstructorSymbol[0]);
        }
        return new ConstructorSymbol[]{constructor1};
      }

      private boolean isAmbiguous(ConstructorSymbol constructor1, ConstructorSymbol constructor2){
        return sorter.compare(constructor1, constructor2) >= 0;
      }

      private void find(Set constructors, ClassSymbol target, IrExpression[] arguments){
        if(target == null) return;
        ConstructorSymbol[] cs = target.getConstructors();
        for(int i = 0; i < cs.length; i++){
          ConstructorSymbol c = cs[i];
          if(matcher.matches(c.getArgs(), arguments)){
            constructors.add(c);
          }
        }
      }

      private static boolean isAllSuperType(
        TypeRef[] arg1, TypeRef[] arg2){
        for(int i = 0; i < arg1.length; i++){
          if(!TypeRules.isSuperType(arg1[i], arg2[i])) return false;
        }
        return true;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/15
     */
    interface ConstructorSymbol extends MemberSymbol {
      public ClassSymbol getClassType();
      public TypeRef[] getArgs();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    class ConstructorSymbolComparator implements Comparator {

      public ConstructorSymbolComparator() {
      }

      public int compare(Object arg0, Object arg1) {
        ConstructorSymbol c1 = (ConstructorSymbol)arg0;
        ConstructorSymbol c2 = (ConstructorSymbol)arg1;
        int result;
        TypeRef[] args1 = c1.getArgs();
        TypeRef[] args2 = c2.getArgs();
        result = args1.length - args2.length;
        if(result != 0){
          return result;
        }
        for(int i = 0; i < args1.length; i++){
          if(args1[i] != args2[i]){
            return args1[i].getName().compareTo(args2[i].getName());
          }
        }
        return 0;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/15
     */
    class FieldFinder {

      public FieldFinder() {
      }

      public FieldSymbol find(ObjectTypeRef target, String name){
        if(target == null) return null;
        FieldSymbol[] fields = target.getFields();
        for (int i = 0; i < fields.length; i++) {
          if(fields[i].getName().equals(name)){
            return fields[i];
          }
        }
        FieldSymbol field = find(target.getSuperClass(), name);
        if(field != null) return field;
        ClassSymbol[] interfaces = target.getInterfaces();
        for(int i = 0; i < interfaces.length; i++){
          field = find(interfaces[i], name);
          if(field != null) return field;
        }
        return null;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/15
     */
    interface FieldSymbol extends MemberSymbol {
      public int getModifier();
      public ClassSymbol getClassType();
      public TypeRef getType();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    class FieldSymbolComparator implements Comparator {
      public FieldSymbolComparator() {
      }

      public int compare(Object arg0, Object arg1) {
        FieldSymbol f1 = (FieldSymbol) arg0;
        FieldSymbol f2 = (FieldSymbol) arg1;
        return f1.getName().compareTo(f2.getName());
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    interface MemberSymbol {
      int getModifier();
      ClassSymbol getClassType();
      String getName();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    class MethodFinder {
      private static Comparator sorter = new Comparator(){
        public int compare(Object method1, Object method2) {
          MethodSymbol m1 = (MethodSymbol)method1;
          MethodSymbol m2 = (MethodSymbol)method2;
          TypeRef[] arg1 = m1.getArguments();
          TypeRef[] arg2 = m2.getArguments();
          int length = arg1.length;
          if(isAllSuperType(arg2, arg1)) return -1;
          if(isAllSuperType(arg1, arg2)) return 1;
          return 0;
        }
      };

      private ParameterMatcher matcher;

      public MethodFinder() {
        this.matcher = new StandardParameterMatcher();
      }

      public MethodSymbol[] find(ObjectTypeRef target, String name, IrExpression[] arguments){
        Set methods = new TreeSet(new MethodSymbolComparator());
        find(methods, target, name, arguments);
        List selectedMethods = new ArrayList();
        selectedMethods.addAll(methods);
        Collections.sort(selectedMethods, sorter);
        if(selectedMethods.size() < 2){
          return (MethodSymbol[]) selectedMethods.toArray(new MethodSymbol[0]);
        }
        MethodSymbol method1 = (MethodSymbol) selectedMethods.get(0);
        MethodSymbol method2 = (MethodSymbol) selectedMethods.get(1);
        if(isAmbiguous(method1, method2)){
          return (MethodSymbol[]) selectedMethods.toArray(new MethodSymbol[0]);
        }
        return new MethodSymbol[]{method1};
      }

      public boolean isAmbiguous(MethodSymbol method1, MethodSymbol method2){
        return sorter.compare(method1, method2) >= 0;
      }

      private void find(
        Set methods, ObjectTypeRef target, String name, IrExpression[] params){
        if(target == null) return;
        MethodSymbol[] ms = target.getMethods();
        for(int i = 0; i < ms.length; i++){
          MethodSymbol m = ms[i];
          if(m.getName().equals(name) && matcher.matches(m.getArguments(), params)){
            methods.add(m);
          }
        }
        ClassSymbol superClass = target.getSuperClass();
        find(methods, superClass, name, params);
        ClassSymbol[] interfaces = target.getInterfaces();
        for(int i = 0; i < interfaces.length; i++){
          find(methods, interfaces[i], name, params);
        }
      }

      private static boolean isAllSuperType(
        TypeRef[] arg1, TypeRef[] arg2){
        for(int i = 0; i < arg1.length; i++){
          if(!TypeRules.isSuperType(arg1[i], arg2[i])) return false;
        }
        return true;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/15
     */
    interface MethodSymbol extends MemberSymbol {
      public ClassSymbol getClassType();
      public TypeRef[] getArguments();
      public TypeRef getReturnType();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    class MethodSymbolComparator implements Comparator {

      public MethodSymbolComparator() {
      }

      public int compare(Object arg0, Object arg1) {
        MethodSymbol m1 = (MethodSymbol)arg0;
        MethodSymbol m2 = (MethodSymbol)arg1;
        int result = m1.getName().compareTo(m2.getName());
        if(result != 0){
          return result;
        }
        TypeRef[] args1 = m1.getArguments();
        TypeRef[] args2 = m2.getArguments();
        result = args1.length - args2.length;
        if(result != 0){
          return result;
        }
        for(int i = 0; i < args1.length; i++){
          if(args1[i] != args2[i]){
            return args1[i].getName().compareTo(args2[i].getName());
          }
        }
        return 0;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class NullTypeRef implements TypeRef {
      public static NullTypeRef NULL = new NullTypeRef("null");

      private String name;

      private NullTypeRef(String name) {
        this.name = name;
      }

      public String getName(){
        return name;
      }

      public boolean isArrayType() {
        return false;
      }

      public boolean isBasicType() {
        return false;
      }

      public boolean isClassType() {
        return false;
      }

      public boolean isNullType() {
        return true;
      }

      public boolean isObjectType(){
        return false;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    interface ObjectTypeRef extends TypeRef {
      boolean isInterface();
      int getModifier();
      ClassSymbol getSuperClass();
      ClassSymbol[] getInterfaces();
      MethodSymbol[] getMethods();
      FieldSymbol[] getFields();
      FieldSymbol findField(String name);
      MethodSymbol[] findMethod(String name, IrExpression[] params);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    interface ParameterMatcher {
      public boolean matches(TypeRef[] arguments, IrExpression[] parameters);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    class StandardParameterMatcher implements ParameterMatcher {
      public StandardParameterMatcher() {
      }

      public boolean matches(TypeRef[] arguments, IrExpression[] parameters){
        return matchesSub(arguments, parameters);
      }

      private static boolean matchesSub(
        TypeRef[] arguments, IrExpression[] parameters){
        if(arguments.length != parameters.length) return false;
        TypeRef[] parameterTypes = new TypeRef[parameters.length];
        for(int i = 0; i < parameters.length; i++){
          parameterTypes[i] = parameters[i].type();
        }
        return matchesSub(arguments, parameterTypes);
      }

      private static boolean matchesSub(
        TypeRef[] arguments, TypeRef[] parameterTypes){
        for(int i = 0; i < arguments.length; i++){
          if(!TypeRules.isSuperType(arguments[i], parameterTypes[i])){
            return false;
          }
        }
        return true;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    interface TypeRef {
      String getName();
      boolean isBasicType();
      boolean isClassType();
      boolean isNullType();
      boolean isArrayType();
      boolean isObjectType();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    class TypeRules {

      private TypeRules() {
      }

      public static boolean isSuperType(
        TypeRef left, TypeRef right){
        if(left.isBasicType()){
          if(right.isBasicType()){
            return isSuperTypeForBasic((BasicTypeRef) left, (BasicTypeRef) right);
          }
          return false;
        }

        if(left.isClassType()){
          if(right.isClassType()){
            return isSuperTypeForClass((ClassSymbol) left, (ClassSymbol) right);
          }
          if(right.isArrayType()){
            return left == ((ArraySymbol) right).getSuperClass();
          }
          if(right.isNullType()){
            return true;
          }
          return false;
        }
        if(left.isArrayType()){
          if(right.isArrayType()){
            return isSuperTypeForArray((ArraySymbol) left, (ArraySymbol) right);
          }
          if(right.isNullType()){
            return true;
          }
          return false;
        }
        return false;
      }

      public static boolean isAssignable(TypeRef left, TypeRef right){
        return isSuperType(left, right);
      }

      private static boolean isSuperTypeForArray(
        ArraySymbol left, ArraySymbol right){
        return isSuperType(left.getBase(), right.getBase());
      }

      private static boolean isSuperTypeForClass(ClassSymbol left, ClassSymbol right){
        if(right == null) return false;
        if(left == right) return true;
        if(isSuperTypeForClass(left, right.getSuperClass())) return true;
        for(int i = 0; i < right.getInterfaces().length; i++){
          if(isSuperTypeForClass(left, right.getInterfaces()[i])) return true;
        }
        return false;
      }

      private static boolean isSuperTypeForBasic(BasicTypeRef left, BasicTypeRef right){
        if(left == BasicTypeRef.DOUBLE){
          if(
            right == BasicTypeRef.CHAR		||
            right == BasicTypeRef.BYTE		|| right == BasicTypeRef.SHORT ||
            right == BasicTypeRef.INT		|| right == BasicTypeRef.LONG	||
            right == BasicTypeRef.FLOAT	|| right == BasicTypeRef.DOUBLE){
            return true;
          }else{
            return false;
          }
        }
        if(left == BasicTypeRef.FLOAT){
          if(
            right == BasicTypeRef.CHAR		|| right == BasicTypeRef.BYTE	||
            right == BasicTypeRef.SHORT	|| right == BasicTypeRef.INT		||
            right == BasicTypeRef.LONG		|| right == BasicTypeRef.FLOAT){
            return true;
          }else{
            return false;
          }
        }
        if(left == BasicTypeRef.LONG){
          if(
            right == BasicTypeRef.CHAR		|| right == BasicTypeRef.BYTE	||
            right == BasicTypeRef.SHORT	|| right == BasicTypeRef.INT		||
            right == BasicTypeRef.LONG){
            return true;
          }else{
            return false;
          }
        }
        if(left == BasicTypeRef.INT){
          if(
            right == BasicTypeRef.CHAR		|| right == BasicTypeRef.BYTE	||
            right == BasicTypeRef.SHORT	|| right == BasicTypeRef.INT){
            return true;
          }else{
            return false;
          }
        }
        if(left == BasicTypeRef.SHORT){
          if(right == BasicTypeRef.BYTE || right == BasicTypeRef.SHORT){
            return true;
          }else{
            return false;
          }
        }
        if(left == BasicTypeRef.BOOLEAN && right == BasicTypeRef.BOOLEAN) return true;
        if(left == BasicTypeRef.BYTE && right == BasicTypeRef.BYTE) return true;
        if(left == BasicTypeRef.CHAR && right == BasicTypeRef.CHAR) return true;
        return false;
      }
    }
}
