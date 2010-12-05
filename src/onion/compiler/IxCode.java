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
    class ArrayLength extends Expression {
      private final Expression target;

      public ArrayLength(Expression target) {
        this.target = target;
      }

      public Expression getTarget() {
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
    class ArrayRef extends Expression {
      private final Expression object, index;

      public ArrayRef(Expression target, Expression index) {
        this.object = target;
        this.index = index;
      }

      public Expression getIndex() {
        return index;
      }

      public Expression getObject() {
        return object;
      }

      public TypeRef type(){
        return ((ArrayTypeRef)object.type()).getBase();
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    class ArraySet extends Expression {
      private final Expression object, index, value;

      public ArraySet(
        Expression target, Expression index, Expression value
      ) {
        this.object = target;
        this.index = index;
        this.value = value;
      }

      public Expression getIndex() {
        return index;
      }

      public Expression getObject() {
        return object;
      }

      public Expression getValue() {
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
    class Begin extends Expression {
      private final Expression[] expressions;

      public Begin(Expression[] expressions) {
        this.expressions = expressions;
      }

      public Begin(List expressions) {
        this((Expression[])expressions.toArray(new Expression[0]));
      }

      public Begin(Expression expression) {
        this(new Expression[]{expression});
      }

      public Begin(Expression expression1, Expression expression2){
        this(new Expression[]{expression1, expression2});
      }

      public Begin(Expression expression1, Expression expression2, Expression expression3){
        this(new Expression[]{expression1, expression2, expression3});
      }

      public Expression[] getExpressions() {
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
    class BinaryExpression extends Expression {
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
      private final Expression left, right;
      private final TypeRef type;

      public BinaryExpression(
        int kind, TypeRef type, Expression left, Expression right
      ) {
        this.kind = kind;
        this.left = left;
        this.right = right;
        this.type = type;
      }

      public Expression getLeft(){
        return left;
      }

      public Expression getRight(){
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
    class StatementBlock implements ActionStatement {
      private final ActionStatement[] statements;

      public StatementBlock(ActionStatement[] statements) {
        this.statements = statements;
      }

      public StatementBlock(List statements) {
        this((ActionStatement[])statements.toArray(new ActionStatement[0]));
      }

      public StatementBlock(ActionStatement statement) {
        this(new ActionStatement[]{statement});
      }

      public StatementBlock(ActionStatement statement1, ActionStatement statement2){
        this(new ActionStatement[]{statement1, statement2});
      }

      public StatementBlock(ActionStatement statement1, ActionStatement statement2, ActionStatement statement3){
        this(new ActionStatement[]{statement1, statement2, statement3});
      }

      public ActionStatement[] getStatements() {
        return statements;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class BoolLiteral extends Expression {
      private final boolean value;

      public BoolLiteral(boolean value){
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
    class Break implements ActionStatement {
      public Break() {}
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class ByteLiteral extends Expression {
      private final byte value;

      public ByteLiteral(byte value) {
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
    class Call extends Expression {
      public final Expression target;
      public final MethodRef method;
      public final Expression[] parameters;

      public Call(
        Expression target, MethodRef method, Expression[] parameters) {
        this.target = target;
        this.method = method;
        this.parameters = parameters;
      }

      public TypeRef type() { return method.returnType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    class CallStatic extends Expression {
      public ObjectTypeRef target;
      public MethodRef method;
      public Expression[] parameters;

      public CallStatic(
        ObjectTypeRef target, MethodRef method, Expression[] parameters) {
        this.target = target;
        this.method = method;
        this.parameters = parameters;
      }

      public TypeRef type() { return method.returnType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    class CallSuper extends Expression {
      private final Expression target;
      private final MethodRef method;
      private final Expression[] params;

      public CallSuper(Expression target, MethodRef method, Expression[] params) {
        this.target = target;
        this.method = method;
        this.params = params;
      }

      public TypeRef type() {
        return method.returnType();
      }

      public Expression getTarget() {
        return target;
      }

      public MethodRef getMethod() {
        return method;
      }

      public Expression[] getParams() {
        return params;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class AsInstanceOf extends Expression {
      private final Expression target;
      private final TypeRef conversion;

      public AsInstanceOf(Expression target, TypeRef conversion) {
        this.target = target;
        this.conversion = conversion;
      }

      public TypeRef getConversion() {
        return conversion;
      }

      public Expression getTarget() {
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
    class CharacterLiteral extends Expression {
      private final char value;

      public CharacterLiteral(char value) {
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
    class ClassDefinition extends AbstractClassTypeRef implements Node {
      private boolean isInterface;
      private int modifier;
      private String name;
      private ClassTypeRef superClass;
      private ClassTypeRef[] interfaces;
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
      public ClassDefinition(boolean isInterface, int modifier, String name, ClassTypeRef superClass, ClassTypeRef[] interfaces) {
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
      public static ClassDefinition newInterface(int modifier, String name, ClassTypeRef[] interfaces){
        return new ClassDefinition(true, modifier, name, null, interfaces);
      }

      /**
       * This method creates class definition.
       * @param modifier
       * @param name
       * @param superClass
       * @param interfaces
       * @return
       */
      public static ClassDefinition newClass(int modifier, String name, ClassTypeRef superClass, ClassTypeRef[] interfaces){
        return new ClassDefinition(false, modifier, name, superClass, interfaces);
      }

      /**
       * This method creates class definition.
       * @param modifier
       * @param name
       * @return
       */
      public static ClassDefinition newClass(int modifier, String name) {
        return new ClassDefinition(false, modifier, name, null, null);
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

      public void setSuperClass(ClassTypeRef superClass) {
        this.superClass = superClass;
      }

      public ClassTypeRef getSuperClass() {
        return superClass;
      }

      public void setInterfaces(ClassTypeRef[] interfaces) {
        this.interfaces = interfaces;
      }

      public ClassTypeRef[] getInterfaces() {
        return interfaces;
      }

      public void setResolutionComplete(boolean isInResolution) {
        this.isResolutionComplete = isInResolution;
      }

      public boolean isResolutionComplete() {
        return isResolutionComplete;
      }

      public void addMethod(MethodRef method) {
        methods.add(method);
      }

      public void addField(FieldRef field) {
        fields.add(field);
      }

      public void addConstructor(ConstructorRef constructor) {
        constructors.add(constructor);
      }

      public void addDefaultConstructor() {
        constructors.add(ConstructorDefinition.newDefaultConstructor(this));
      }

      public MethodRef[] getMethods() {
        return ((MethodRef[])methods.toArray(new MethodRef[0]));
      }

      public FieldRef[] getFields() {
        return ((FieldRef[])fields.toArray(new FieldRef[0]));
      }

      public ConstructorRef[] getConstructors() {
        return ((ConstructorRef[]) constructors.toArray(new ConstructorRef[0]));
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
    class NewClosure extends Expression {
      private ClassTypeRef type;
      private MethodRef method;
      private ActionStatement block;
      private LocalFrame frame;

      public NewClosure(ClassTypeRef type, MethodRef method, ActionStatement block) {
        this.type =  type;
        this.method = method;
        this.block = block;
      }

      public int getModifier() {
        return method.modifier();
      }

      public ClassTypeRef getClassType() {
        return type;
      }

      public MethodRef getMethod(){
        return method;
      }

      public String getName() {
        return method.name();
      }

      public TypeRef[] getArguments() {
        return method.arguments();
      }

      public TypeRef getReturnType() {
        return method.returnType();
      }

      public ActionStatement getBlock() {
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
    class ConstructorDefinition implements Node, ConstructorRef {
      private int modifier;
      private ClassTypeRef classType;
      private TypeRef[] arguments;
      private StatementBlock block;
      private Super superInitializer;
      private LocalFrame frame;

      public ConstructorDefinition(
        int modifier, ClassTypeRef classType,
        TypeRef[] arguments, StatementBlock block, Super superInitializer
      ) {
        this.modifier = modifier;
        this.classType = classType;
        this.arguments = arguments;
        this.block = block;
        this.superInitializer = superInitializer;
      }

      public static ConstructorDefinition newDefaultConstructor(ClassTypeRef type) {
        StatementBlock block = new StatementBlock(new Return(null));
        Super init = new Super(type.getSuperClass(), new TypeRef[0], new Expression[0]);
        ConstructorDefinition node =  new ConstructorDefinition(Modifier.PUBLIC, type, new TypeRef[0], block, init);
        node.setFrame(new LocalFrame(null));
        return node;
      }

      public String name() {
        return "new";
      }

      public TypeRef[] getArgs() {
        return arguments;
      }

      public ClassTypeRef affiliation() {
        return classType;
      }

      public int modifier() {
        return modifier;
      }

      public Super getSuperInitializer() {
        return superInitializer;
      }

      public void setSuperInitializer(Super superInitializer) {
        this.superInitializer = superInitializer;
      }

      public void setBlock(StatementBlock block) {
        this.block = block;
      }

      public StatementBlock getBlock() {
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
    class Continue implements ActionStatement {
      public Continue(){}
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class DoubleLiteral extends Expression {
      private final double value;

      public DoubleLiteral(double value) {
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
    abstract class Expression implements Node {
      public Expression() {}

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

      public static Expression defaultValue(TypeRef type){
        if(type == BasicTypeRef.CHAR) return new CharacterLiteral((char)0);
        if(type == BasicTypeRef.BYTE)return new ByteLiteral((byte)0);
        if(type == BasicTypeRef.SHORT) return new ShortLiteral((short)0);
        if(type == BasicTypeRef.INT) return new IntLiteral(0);
        if(type == BasicTypeRef.LONG) return new LongLiteral(0);
        if(type == BasicTypeRef.FLOAT) return new FloatLiteral(0.0f);
        if(type == BasicTypeRef.DOUBLE) return new DoubleLiteral(0.0);
        if(type == BasicTypeRef.BOOLEAN) return new BoolLiteral(false);
        if(type.isObjectType()) return new NullLiteral();
        return null;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class ExpressionStatement implements ActionStatement {
      public Expression expression;
      public ExpressionStatement(Expression expression){
        this.expression = expression;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class FieldDefinition implements Node, FieldRef {

      private int modifier;
      private ClassTypeRef classType;
      private String name;
      private TypeRef type;

      public FieldDefinition(
        int modifier, ClassTypeRef classType, String name, TypeRef type) {
        this.modifier = modifier;
        this.classType = classType;
        this.name = name;
        this.type = type;
      }

      public ClassTypeRef affiliation() {
        return classType;
      }

      public int modifier() {
        return modifier;
      }

      public String name() {
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
    class RefField extends Expression {
      public Expression target;
      public FieldRef field;

      public RefField(Expression target, FieldRef field) {
        this.target = target;
        this.field = field;
      }

      public TypeRef type() { return field.getType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class SetField extends Expression {
      private final Expression object;
      private final FieldRef field;
      private final Expression value;

      public SetField(
        Expression target, FieldRef field, Expression value
      ) {
        this.object = target;
        this.field = field;
        this.value = value;
      }

      public TypeRef type() { return field.getType(); }

      public Expression getObject() { return object; }

      public FieldRef getField() { return field; }

      public Expression getValue() { return value; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class FloatLiteral extends Expression {
      private final float value;

      public FloatLiteral(float value){
        this.value = value;
      }

      public float getValue() { return value; }

      public TypeRef type() { return BasicTypeRef.FLOAT; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class IfStatement implements ActionStatement {
      private final Expression condition;
      private final ActionStatement thenStatement, elseStatement;

      public IfStatement(
        Expression condition,
        ActionStatement thenStatement, ActionStatement elseStatement){
        this.condition = condition;
        this.thenStatement = thenStatement;
        this.elseStatement = elseStatement;
      }

      public Expression getCondition() {
        return condition;
      }

      public ActionStatement getThenStatement() {
        return thenStatement;
      }

      public ActionStatement getElseStatement() {
        return elseStatement;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class InstanceOf extends Expression {
      public Expression target;
      public TypeRef checked;

      public InstanceOf(Expression target, TypeRef checked){
        this.target = target;
        this.checked = checked;
      }

      public TypeRef getCheckType() {
        return checked;
      }

      public Expression getTarget() {
        return target;
      }

      public TypeRef type() { return BasicTypeRef.BOOLEAN; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class IntLiteral extends Expression {
      private int value;
      public IntLiteral(int value){
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
    class ListLiteral extends Expression {
      private final Expression[] elements;
      private final TypeRef type;

      public ListLiteral(Expression[] elements, TypeRef type) {
        this.elements = elements;
        this.type = type;
      }

      public Expression[] getElements() {
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
    class RefLocal extends Expression {
      private int frame;
      private int index;
      private TypeRef type;

      public RefLocal(ClosureLocalBinding bind) {
        this.frame = bind.getFrame();
        this.index = bind.getIndex();
        this.type = bind.getType();
      }

      public RefLocal(int frame, int index, TypeRef type){
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
    class SetLocal extends Expression {
      private final int frame;
      private final int index;
      private final Expression value;
      private final TypeRef type;

      public SetLocal(int frame, int index, TypeRef type, Expression value){
        this.frame = frame;
        this.index = index;
        this.value = value;
        this.type = type;
      }

      public SetLocal(ClosureLocalBinding bind, Expression value){
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

      public Expression getValue() {
        return value;
      }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class LongLiteral extends Expression {
      private final long value;

      public LongLiteral(long value){
        this.value = value;
      }

      public long getValue() { return value; }

      public TypeRef type() { return BasicTypeRef.LONG; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class ConditionalLoop implements ActionStatement {
      public final Expression condition;
      public final ActionStatement stmt;

      public ConditionalLoop(Expression condition, ActionStatement stmt) {
        this.condition = condition;
        this.stmt = stmt;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class Member implements Node {
      private final int modifier;
      private final TypeRef classType;

      public Member(int modifier, TypeRef classType){
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
    class MethodDefinition implements Node, MethodRef {
      private int modifier;
      private ClassTypeRef classType;
      private String name;
      private TypeRef[] arguments;
      private StatementBlock block;
      private TypeRef returnType;
      private boolean closure;
      private LocalFrame frame;

      public MethodDefinition(
        int modifier, ClassTypeRef classType, String name, TypeRef[] arguments,
        TypeRef returnType, StatementBlock block){
        this.modifier = modifier;
        this.classType = classType;
        this.name = name;
        this.arguments = arguments;
        this.returnType = returnType;
        this.block = block;
      }

      public int modifier() {
        return modifier;
      }

      public ClassTypeRef affiliation() {
        return classType;
      }

      public String name() {
        return name;
      }

      public TypeRef[] arguments() {
        return arguments;
      }

      public TypeRef returnType(){
        return returnType;
      }

      public StatementBlock getBlock() {
        return block;
      }

      public void setBlock(StatementBlock block){
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
    class NewObject extends Expression {
      public final ConstructorRef constructor;
      public final Expression[] parameters;

      public NewObject(ConstructorRef constructor, Expression[] parameters){
        this.constructor = constructor;
        this.parameters = parameters;
      }

      public TypeRef type() { return constructor.affiliation(); }
    }

    class NewArray extends Expression {
      public final ArrayTypeRef arrayType;
      public final Expression[] parameters;

      public NewArray(ArrayTypeRef arrayType, Expression[] parameters){
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
    interface Node {
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class NOP implements ActionStatement {
      public NOP() {}
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class NullLiteral extends Expression {
      public NullLiteral(){}

      public TypeRef type() { return NullTypeRef.NULL; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class Return implements ActionStatement {
      public final Expression expression;

      public Return(Expression expression) {
        this.expression = expression;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class ShortLiteral extends Expression {
      private short value;
      public ShortLiteral(short value){
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
    interface ActionStatement {

    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class StaticFieldRef extends Expression {
      public ClassTypeRef target;
      public FieldRef field;

      public StaticFieldRef(ClassTypeRef target, FieldRef field){
        this.target = target;
        this.field = field;
      }

      public TypeRef type() { return field.getType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class StaticFieldSet extends Expression {
      public ObjectTypeRef target;
      public FieldRef field;
      public Expression value;

      public StaticFieldSet(
        ObjectTypeRef target, FieldRef field, Expression value){
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
    class StringLiteral extends Expression {
      public String value;
      public TypeRef type;

      public StringLiteral(String value, TypeRef type){
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
    class Super implements Node {
      private ClassTypeRef classType;
      private TypeRef[] arguments;
      private Expression[] expressions;

      public Super(
        ClassTypeRef classType, TypeRef[] arguments, Expression[] expressions){
        this.classType = classType;
        this.arguments = arguments;
        this.expressions = expressions;
      }

      public ClassTypeRef getClassType() {
        return classType;
      }

      public TypeRef[] getArguments() {
        return arguments;
      }

      public Expression[] getExpressions() {
        return expressions;
      }

    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class Synchronized implements ActionStatement {
      public Expression expression;
      public ActionStatement statement;
      public Synchronized(Expression expression, ActionStatement statement){
        this.expression = expression;
        this.statement = statement;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class This extends Expression {
      private ClassTypeRef classType;

      public This(ClassTypeRef classType){
        this.classType = classType;
      }

      public TypeRef type() { return classType; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class Throw implements ActionStatement {
      public Expression expression;
      public Throw(Expression expression){
        this.expression = expression;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    class Try implements ActionStatement {
      public ActionStatement tryStatement;
      public ClosureLocalBinding[] catchTypes;
      public ActionStatement[] catchStatements;
      public Try(
        ActionStatement tryStatement, ClosureLocalBinding[] catchTypes,
        ActionStatement[] catchStatements){
        this.tryStatement = tryStatement;
        this.catchTypes = catchTypes;
        this.catchStatements = catchStatements;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    class UnaryExpression extends Expression {
      public interface Constants {
          int PLUS = 0;
          int MINUS = 1;
          int NOT = 2;
          int BIT_NOT = 3;
      }

      private int kind;
      private Expression operand;
      private TypeRef type;

      public UnaryExpression(
        int kind, TypeRef type, Expression operand) {
        this.kind = kind;
        this.operand = operand;
        this.type = type;
      }

      public int getKind(){
        return kind;
      }

      public Expression getOperand(){
        return operand;
      }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/15
     */
    abstract class AbstractClassTypeRef extends AbstractObjectTypeRef implements ClassTypeRef {
      private ConstructorRefFinder constructorRefFinder;

      public AbstractClassTypeRef() {
        constructorRefFinder = new ConstructorRefFinder();
      }

      public ConstructorRef[] findConstructor(Expression[] params){
        return constructorRefFinder.find(this, params);
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
    abstract class AbstractObjectTypeRef implements ObjectTypeRef {
      private MethodRefFinder methodRefFinder;
      private FieldRefFinder fieldRefFinder;

      public AbstractObjectTypeRef() {
        methodRefFinder = new MethodRefFinder();
        fieldRefFinder = new FieldRefFinder();
      }

      public FieldRef findField(String name) {
        return fieldRefFinder.find(this, name);
      }

      public MethodRef[] findMethod(String name, Expression[] params) {
        return methodRefFinder.find(this, name, params);
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
    class ArrayTypeRef extends AbstractObjectTypeRef {
      private ClassTable table;
      private TypeRef component;
      private int dimension;
      private ClassTypeRef superClass;
      private ClassTypeRef[] interfaces;
      private String name;

      public ArrayTypeRef(TypeRef component, int dimension, ClassTable table){
        this.component = component;
        this.dimension = dimension;
        this.superClass = table.load("java.lang.Object");
        this.interfaces = new ClassTypeRef[]{
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

      public ClassTypeRef getSuperClass() {
        return superClass;
      }

      public ClassTypeRef[] getInterfaces() {
        return interfaces;
      }

      public MethodRef[] getMethods() {
        return superClass.getMethods();
      }

      public FieldRef[] getFields() {
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
    interface ClassTypeRef extends ObjectTypeRef {
      ConstructorRef[] getConstructors();
      ConstructorRef[] findConstructor(Expression[] params);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    class ConstructorRefFinder {
      private static Comparator sorter = new Comparator(){
        public int compare(Object constructor1, Object constructor2) {
          ConstructorRef c1 = (ConstructorRef)constructor1;
          ConstructorRef c2 = (ConstructorRef)constructor2;
          TypeRef[] arg1 = c1.getArgs();
          TypeRef[] arg2 = c2.getArgs();
          int length = arg1.length;
          if(isAllSuperType(arg2, arg1)) return -1;
          if(isAllSuperType(arg1, arg2)) return 1;
          return 0;
        }
      };

      private ParameterMatcher matcher;

      public ConstructorRefFinder() {
        this.matcher = new StandardParameterMatcher();
      }

      public ConstructorRef[] find(ClassTypeRef target, Expression[] args){
        Set constructors = new TreeSet(new ConstructorRefComparator());
        find(constructors, target, args);
        List selected = new ArrayList();
        selected.addAll(constructors);
        Collections.sort(selected, sorter);
        if(selected.size() < 2){
          return (ConstructorRef[]) selected.toArray(new ConstructorRef[0]);
        }
        ConstructorRef constructor1 = (ConstructorRef) selected.get(0);
        ConstructorRef constructor2 = (ConstructorRef) selected.get(1);
        if(isAmbiguous(constructor1, constructor2)){
          return (ConstructorRef[]) selected.toArray(new ConstructorRef[0]);
        }
        return new ConstructorRef[]{constructor1};
      }

      private boolean isAmbiguous(ConstructorRef constructor1, ConstructorRef constructor2){
        return sorter.compare(constructor1, constructor2) >= 0;
      }

      private void find(Set constructors, ClassTypeRef target, Expression[] arguments){
        if(target == null) return;
        ConstructorRef[] cs = target.getConstructors();
        for(int i = 0; i < cs.length; i++){
          ConstructorRef c = cs[i];
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
    interface ConstructorRef extends MemberRef {
      public ClassTypeRef affiliation();
      public TypeRef[] getArgs();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    class ConstructorRefComparator implements Comparator {

      public ConstructorRefComparator() {
      }

      public int compare(Object arg0, Object arg1) {
        ConstructorRef c1 = (ConstructorRef)arg0;
        ConstructorRef c2 = (ConstructorRef)arg1;
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
    class FieldRefFinder {

      public FieldRefFinder() {
      }

      public FieldRef find(ObjectTypeRef target, String name){
        if(target == null) return null;
        FieldRef[] fields = target.getFields();
        for (int i = 0; i < fields.length; i++) {
          if(fields[i].name().equals(name)){
            return fields[i];
          }
        }
        FieldRef field = find(target.getSuperClass(), name);
        if(field != null) return field;
        ClassTypeRef[] interfaces = target.getInterfaces();
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
    interface FieldRef extends MemberRef {
      public int modifier();
      public ClassTypeRef affiliation();
      public TypeRef getType();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    class FieldRefComparator implements Comparator {
      public FieldRefComparator() {
      }

      public int compare(Object arg0, Object arg1) {
        FieldRef f1 = (FieldRef) arg0;
        FieldRef f2 = (FieldRef) arg1;
        return f1.name().compareTo(f2.name());
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    interface MemberRef {
      int modifier();
      ClassTypeRef affiliation();
      String name();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    class MethodRefFinder {
      private static Comparator sorter = new Comparator(){
        public int compare(Object method1, Object method2) {
          MethodRef m1 = (MethodRef)method1;
          MethodRef m2 = (MethodRef)method2;
          TypeRef[] arg1 = m1.arguments();
          TypeRef[] arg2 = m2.arguments();
          int length = arg1.length;
          if(isAllSuperType(arg2, arg1)) return -1;
          if(isAllSuperType(arg1, arg2)) return 1;
          return 0;
        }
      };

      private ParameterMatcher matcher;

      public MethodRefFinder() {
        this.matcher = new StandardParameterMatcher();
      }

      public MethodRef[] find(ObjectTypeRef target, String name, Expression[] arguments){
        Set methods = new TreeSet(new MethodRefComparator());
        find(methods, target, name, arguments);
        List selectedMethods = new ArrayList();
        selectedMethods.addAll(methods);
        Collections.sort(selectedMethods, sorter);
        if(selectedMethods.size() < 2){
          return (MethodRef[]) selectedMethods.toArray(new MethodRef[0]);
        }
        MethodRef method1 = (MethodRef) selectedMethods.get(0);
        MethodRef method2 = (MethodRef) selectedMethods.get(1);
        if(isAmbiguous(method1, method2)){
          return (MethodRef[]) selectedMethods.toArray(new MethodRef[0]);
        }
        return new MethodRef[]{method1};
      }

      public boolean isAmbiguous(MethodRef method1, MethodRef method2){
        return sorter.compare(method1, method2) >= 0;
      }

      private void find(
        Set methods, ObjectTypeRef target, String name, Expression[] params){
        if(target == null) return;
        MethodRef[] ms = target.getMethods();
        for(int i = 0; i < ms.length; i++){
          MethodRef m = ms[i];
          if(m.name().equals(name) && matcher.matches(m.arguments(), params)){
            methods.add(m);
          }
        }
        ClassTypeRef superClass = target.getSuperClass();
        find(methods, superClass, name, params);
        ClassTypeRef[] interfaces = target.getInterfaces();
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
    interface MethodRef extends MemberRef {
      public ClassTypeRef affiliation();
      public TypeRef[] arguments();
      public TypeRef returnType();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    class MethodRefComparator implements Comparator {

      public MethodRefComparator() {
      }

      public int compare(Object arg0, Object arg1) {
        MethodRef m1 = (MethodRef)arg0;
        MethodRef m2 = (MethodRef)arg1;
        int result = m1.name().compareTo(m2.name());
        if(result != 0){
          return result;
        }
        TypeRef[] args1 = m1.arguments();
        TypeRef[] args2 = m2.arguments();
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
      ClassTypeRef getSuperClass();
      ClassTypeRef[] getInterfaces();
      MethodRef[] getMethods();
      FieldRef[] getFields();
      FieldRef findField(String name);
      MethodRef[] findMethod(String name, Expression[] params);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    interface ParameterMatcher {
      public boolean matches(TypeRef[] arguments, Expression[] parameters);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    class StandardParameterMatcher implements ParameterMatcher {
      public StandardParameterMatcher() {
      }

      public boolean matches(TypeRef[] arguments, Expression[] parameters){
        return matchesSub(arguments, parameters);
      }

      private static boolean matchesSub(
        TypeRef[] arguments, Expression[] parameters){
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
            return isSuperTypeForClass((ClassTypeRef) left, (ClassTypeRef) right);
          }
          if(right.isArrayType()){
            return left == ((ArrayTypeRef) right).getSuperClass();
          }
          if(right.isNullType()){
            return true;
          }
          return false;
        }
        if(left.isArrayType()){
          if(right.isArrayType()){
            return isSuperTypeForArray((ArrayTypeRef) left, (ArrayTypeRef) right);
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
        ArrayTypeRef left, ArrayTypeRef right){
        return isSuperType(left.getBase(), right.getBase());
      }

      private static boolean isSuperTypeForClass(ClassTypeRef left, ClassTypeRef right){
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
