package onion.compiler;

import onion.compiler.ClosureLocalBinding;
import onion.compiler.util.Strings;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 21:20:19
 * To change this template use File | Settings | File Templates.
 */
public class IRT {
    /**
     * This interface represents an internal representation node of onion program.
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static interface Node {
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/06
     */
    public static class ArrayLength extends Term {
      private final Term target;

      public ArrayLength(Term target) {
        this(null, target);
      }

      public ArrayLength(Location location, Term target) {
        super(location);
        this.target = target;
      }

      public Term getTarget() {
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
    public static class RefArray extends Term {
      private final Term object, index;

      public RefArray(Term target, Term index) {
        this(null, target, index);
      }

      public RefArray(Location location, Term target, Term index) {
        super(location);
        this.object = target;
        this.index = index;
      }

      public Term getIndex() {
        return index;
      }

      public Term getObject() {
        return object;
      }

      public TypeRef type(){
        return ((ArrayTypeRef)object.type()).base();
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    public static class SetArray extends Term {
      private final Term object, index, value;

      public SetArray(Term target, Term index, Term value) {
        this(null, target, index, value);
      }

      public SetArray(Location location, Term target, Term index, Term value) {
        super(location);
        this.object = target;
        this.index = index;
        this.value = value;
      }

      public Term index() {
        return index;
      }

      public Term object() {
        return object;
      }

      public Term value() {
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
    public static class Begin extends Term {
      private final Term[] terms;

      public Begin(Term[] terms) {
        this(null, terms);
      }

      public Begin(Location location, Term[] terms) {
        super(location);
        this.terms = terms;
      }

      public Begin(List expressions) {
        this((Term[])expressions.toArray(new Term[0]));
      }

      public Begin(Term term) {
        this(new Term[]{term});
      }

      public Begin(Term expression1, Term expression2){
        this(new Term[]{expression1, expression2});
      }

      public Begin(Term expression1, Term expression2, Term expression3){
        this(new Term[]{expression1, expression2, expression3});
      }

      public Term[] getExpressions() {
        return terms;
      }

      public TypeRef type() {
        return terms[terms.length - 1].type();
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class BinaryTerm extends Term {
      public static interface Constants {
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
      private final Term lhs, rhs;
      private final TypeRef type;

      public BinaryTerm(int kind, TypeRef type, Term lhs, Term rhs) {
        this(null, kind, type, lhs, rhs);
      }
      
      public BinaryTerm(Location location, int kind, TypeRef type, Term lhs, Term rhs) {
        super(location);
        this.kind = kind;
        this.lhs = lhs;
        this.rhs = rhs;
        this.type = type;
      }

      public Term lhs(){ return lhs; }

      public Term rhs(){ return rhs; }

      public int kind(){ return kind; }

      public TypeRef type(){ return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class StatementBlock extends ActionStatement {
      private final ActionStatement[] statements;

      public StatementBlock(Location location, ActionStatement... statements) {
        super(location);
        this.statements = statements;
      }

      public StatementBlock(ActionStatement... statements) {
        this(null, statements);
      }

      public StatementBlock(List<ActionStatement> statements) {
        this((ActionStatement[])statements.toArray(new ActionStatement[0]));
      }

      public ActionStatement[] getStatements() {
        return statements;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class BoolValue extends Term {
      private final boolean value;

      public BoolValue(Location location, boolean value){
        super(location);
        this.value = value;
      }
      
      public BoolValue(boolean value) {
        this(null, value);
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
    public static class Break extends ActionStatement {
      public Break() {
        this(null);
      }
      public Break(Location location) {
        super(location);
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class ByteValue extends Term {
      private final byte value;

      public ByteValue(Location location, byte value) {
        super(location);
        this.value = value;
      }

      public ByteValue(byte value) {
        this(null, value);
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
    public static class Call extends Term {
      public final Term target;
      public final MethodRef method;
      public final Term[] parameters;

      public Call(Location location, Term target, MethodRef method, Term[] parameters) {
        super(location);
        this.target = target;
        this.method = method;
        this.parameters = parameters;
      }

      public Call(Term target, MethodRef method, Term[] parameters) {
        this(null, target, method, parameters);
      }

      public TypeRef type() { return method.returnType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    public static class CallStatic extends Term {
      public ObjectTypeRef target;
      public MethodRef method;
      public Term[] parameters;

      public CallStatic(Location location, ObjectTypeRef target, MethodRef method, Term[] parameters) {
        super(location);
        this.target = target;
        this.method = method;
        this.parameters = parameters;
      }

      public CallStatic(ObjectTypeRef target, MethodRef method, Term[] parameters) {
        this(null, target, method, parameters);
      }

      public TypeRef type() { return method.returnType(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/22
     */
    public static class CallSuper extends Term {
      private final Term target;
      private final MethodRef method;
      private final Term[] params;

      public CallSuper(Location location, Term target, MethodRef method, Term[] params) {
        super(location);
        this.target = target;
        this.method = method;
        this.params = params;
      }

      public CallSuper(Term target, MethodRef method, Term[] params) {
        this(null, target, method, params);
      }

      public TypeRef type() {
        return method.returnType();
      }

      public Term getTarget() {
        return target;
      }

      public MethodRef getMethod() {
        return method;
      }

      public Term[] getParams() {
        return params;
      }
    }

  /**
   * @author Kota Mizushima
   * Date: 2005/04/17
   */
  public static class AsInstanceOf extends Term {
    private final Term target;
    private final TypeRef destination;

    public AsInstanceOf(Location location, Term target, TypeRef destination) {
      super(location);
      this.target = target;
      this.destination = destination;
    }

    public AsInstanceOf(Term target, TypeRef destination) {
      this(null, target, destination);
    }

    public TypeRef destination() {
      return destination;
    }

    public Term target() {
      return target;
    }

    public TypeRef type() {
      return destination;
    }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/06/17
   */
  public static class CharacterValue extends Term {
    private final char value;

    public CharacterValue(Location location, char value) {
      super(location);
      this.value = value;
    }

    public CharacterValue(char value) {
      this(null, value);
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
  public static class ClassDefinition extends AbstractClassTypeRef implements Node, Named {
    private final Location location;
    private boolean isInterface;
    private int modifier;
    private String name;
    private ClassTypeRef superClass;
    private ClassTypeRef[] interfaces;
    private OrderedTable<FieldRef> fields = new OrderedTable<FieldRef>();
    private MultiTable<MethodRef> methods = new MultiTable<MethodRef>();
    private List<ConstructorRef> constructors = new ArrayList<ConstructorRef>();
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
    public ClassDefinition(Location location, boolean isInterface, int modifier, String name, ClassTypeRef superClass, ClassTypeRef[] interfaces) {
      this.location = location;
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
    public static ClassDefinition newInterface(Location location, int modifier, String name, ClassTypeRef[] interfaces){
      return new ClassDefinition(location, true, modifier, name, null, interfaces);
    }

    /**
     * This method creates class definition.
     * @param modifier
     * @param name
     * @param superClass
     * @param interfaces
     * @return
     */
    public static ClassDefinition newClass(Location location, int modifier, String name, ClassTypeRef superClass, ClassTypeRef[] interfaces){
      return new ClassDefinition(location, false, modifier, name, superClass, interfaces);
    }

    /**
     * This method creates class definition.
     * @param modifier
     * @param name
     * @return
     */
    public static ClassDefinition newClass(int modifier, String name) {
      return new ClassDefinition(null, false, modifier, name, null, null);
    }

    public Location location() {
      return location;
    }

    public boolean isInterface() {
      return isInterface;
    }

    public int modifier() {
      return modifier;
    }

    public String name() {
      return name;
    }

    public void setSuperClass(ClassTypeRef superClass) {
      this.superClass = superClass;
    }

    public ClassTypeRef superClass() {
      return superClass;
    }

    public void setInterfaces(ClassTypeRef[] interfaces) {
      this.interfaces = interfaces;
    }

    public ClassTypeRef[] interfaces() {
      return interfaces;
    }

    public void setResolutionComplete(boolean isInResolution) {
      this.isResolutionComplete = isInResolution;
    }

    public boolean isResolutionComplete() {
      return isResolutionComplete;
    }

    public void add(MethodRef method) {
      methods.add(method);
    }

    public void add(FieldRef field) {
      fields.add(field);
    }

    public void add(ConstructorRef constructor) {
      constructors.add(constructor);
    }

    public void addDefaultConstructor() {
      constructors.add(ConstructorDefinition.newDefaultConstructor(this));
    }

    public MethodRef[] methods() {
      return (MethodRef[])(methods.values().toArray(new MethodRef[0]));
    }

    public MethodRef[] methods(String name) {
      return (MethodRef[])(methods.get(name).toArray(new MethodRef[0]));
    }

    public FieldRef[] fields() {
      return (FieldRef[])(fields.values().toArray(new FieldRef[0]));
    }

    public FieldRef field(String name) {
      return fields.get(name);
    }

    public ConstructorRef[] constructors() {
      return (ConstructorRef[])(constructors.toArray(new ConstructorRef[0]));
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
  public static class ConstructorDefinition implements Node, ConstructorRef {
    private Location location;
    private int modifier;
    private ClassTypeRef classType;
    private TypeRef[] arguments;
    private StatementBlock block;
    private Super superInitializer;
    private LocalFrame frame;

    public ConstructorDefinition(int modifier, ClassTypeRef classType, TypeRef[] arguments, StatementBlock block, Super superInitializer) {
      this(null, modifier, classType, arguments, block, superInitializer);
    }

    public ConstructorDefinition(Location location, int modifier, ClassTypeRef classType, TypeRef[] arguments, StatementBlock block, Super superInitializer) {
      this.location = location;
      this.modifier = modifier;
      this.classType = classType;
      this.arguments = arguments;
      this.block = block;
      this.superInitializer = superInitializer;
    }

    public static ConstructorDefinition newDefaultConstructor(ClassTypeRef type) {
      StatementBlock block = new StatementBlock(new Return(null));
      Super init = new Super(type.superClass(), new TypeRef[0], new Term[0]);
      ConstructorDefinition node =  new ConstructorDefinition(Modifier.PUBLIC, type, new TypeRef[0], block, init);
      node.setFrame(new LocalFrame(null));
      return node;
    }

    public Location location() {
      return location;
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
  public static class Continue extends ActionStatement {
    public Continue(Location location){
      super(location);
    }
    public Continue(){
      this(null);
    }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/06/17
   */
  public static class DoubleValue extends Term {
    private final double value;

    public DoubleValue(Location location, double value) {
      super(location);
      this.value = value;
    }

    public DoubleValue(double value) {
      this(null, value);
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
  abstract public static class Term implements Node {
    private final Location location;
    public Term(Location location) {
      this.location = location;
    }

    public abstract TypeRef type();

    public Location location(){
      return location;
    }

    public boolean isBasicType(){
      return type().isBasicType();
    }

    public boolean isArrayType() {
      return type().isArrayType();
    }

    public boolean isClassType() {
      return type().isClassType();
    }

    public boolean isNullType() {
      return type().isNullType();
    }

    public boolean isReferenceType(){ return type().isObjectType(); }

    public static Term defaultValue(TypeRef type){
      if(type == BasicTypeRef.CHAR) return new CharacterValue((char)0);
      if(type == BasicTypeRef.BYTE)return new ByteValue((byte)0);
      if(type == BasicTypeRef.SHORT) return new ShortValue((short)0);
      if(type == BasicTypeRef.INT) return new IntValue(0);
      if(type == BasicTypeRef.LONG) return new LongValue(0);
      if(type == BasicTypeRef.FLOAT) return new FloatValue(0.0f);
      if(type == BasicTypeRef.DOUBLE) return new DoubleValue(0.0);
      if(type == BasicTypeRef.BOOLEAN) return new BoolValue(false);
      if(type.isObjectType()) return new NullValue();
      return null;
    }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/06/17
   */
  public static class ExpressionActionStatement extends ActionStatement {
    public final Term term;
    public ExpressionActionStatement(Location location, Term term){
      super(location);
      this.term = term;
    }
    public ExpressionActionStatement(Term term){
      this(null, term);
    }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/04/17
   */
  public static class FieldDefinition implements Node, FieldRef {
    private final Location location;
    private final int modifier;
    private final ClassTypeRef affiliation;
    private final String name;
    private final TypeRef type;

    public FieldDefinition(Location location, int modifier, ClassTypeRef affiliation, String name, TypeRef type) {
      this.location = location;
      this.modifier = modifier;
      this.affiliation = affiliation;
      this.name = name;
      this.type = type;
    }

    public Location location() {
      return location;
    }

    public int modifier() {
      return modifier;
    }

    public ClassTypeRef affiliation() {
      return affiliation;
    }

    public String name() {
      return name;
    }

    public TypeRef type() {
      return type;
    }

  }

  /**
   * @author Kota Mizushima
   * Date: 2005/04/17
   */
  public static class RefField extends Term {
    public Term target;
    public FieldRef field;

    public RefField(Location location, Term target, FieldRef field) {
      super(location);
      this.target = target;
      this.field = field;
    }

    public RefField(Term target, FieldRef field) {
      this(null, target, field);
    }

    public TypeRef type() { return field.type(); }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/04/17
   */
  public static class SetField extends Term {
    private final Term object;
    private final FieldRef field;
    private final Term value;

    public SetField(Location location, Term target, FieldRef field, Term value) {
      super(location);
      this.object = target;
      this.field = field;
      this.value = value;
    }

    public SetField(Term target, FieldRef field, Term value) {
      this(null, target, field, value);
    }

    public TypeRef type() { return field.type(); }

    public Term getObject() { return object; }

    public FieldRef getField() { return field; }

    public Term getValue() { return value; }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/06/17
   */
  public static class FloatValue extends Term {
    private final float value;

    public FloatValue(Location location, float value){
      super(location);
      this.value = value;
    }

    public FloatValue(float value){
      this(null, value);
    }

    public float getValue() { return value; }

    public TypeRef type() { return BasicTypeRef.FLOAT; }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/04/17
   */
  public static class IfStatement extends ActionStatement {
    private final Term condition;
    private final ActionStatement thenStatement, elseStatement;

    public IfStatement(Location location, Term condition, ActionStatement thenStatement, ActionStatement elseStatement){
      super(location);
      this.condition = condition;
      this.thenStatement = thenStatement;
      this.elseStatement = elseStatement;
    }

    public IfStatement(Term condition, ActionStatement thenStatement, ActionStatement elseStatement){
      this(null, condition, thenStatement, elseStatement);
    }

    public Term getCondition() {
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
  public static class InstanceOf extends Term {
    public Term target;
    public TypeRef checked;

    public InstanceOf(Location location, Term target, TypeRef checked){
      super(location);
      this.target = target;
      this.checked = checked;
    }

    public InstanceOf(Term target, TypeRef checked) {
      this(null, target, checked);
    }

    public TypeRef checked() {
      return checked;
    }

    public Term target() {
      return target;
    }

    public TypeRef type() {
      return BasicTypeRef.BOOLEAN;
    }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/06/17
   */
  public static class IntValue extends Term {
    private int value;
    public IntValue(Location location, int value){
      super(location);
      this.value = value;
    }
    public IntValue(int value){
      this(null, value);
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
  public static class ListLiteral extends Term {
    private final Term[] elements;
    private final TypeRef type;

    public ListLiteral(Location location, Term[] elements, TypeRef type) {
      super(location);
      this.elements = elements;
      this.type = type;
    }

    public ListLiteral(Term[] elements, TypeRef type) {
      this(null, elements, type);
    }

    public Term[] getElements() {
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
  public static class RefLocal extends Term {
    private int frame;
    private int index;
    private TypeRef type;

    public RefLocal(Location location, int frame, int index, TypeRef type){
      super(location);
      this.frame = frame;
      this.index = index;
      this.type = type;
    }

    public RefLocal(ClosureLocalBinding bind) {
      this(null, bind.getFrameIndex(), bind.getIndex(), bind.getType());
    }

    public RefLocal(int frame, int index, TypeRef type) {
      this(null, frame, index, type);
    }

    public int frame(){ return frame; }

    public int index(){ return index; }

    public TypeRef type() { return type; }
  }

  /**
   * @author Kota Mizushima
   * Date: 2005/04/17
   */
  public static class SetLocal extends Term {
    private final int frame;
    private final int index;
    private final Term value;
    private final TypeRef type;

    public SetLocal(Location location, int frame, int index, TypeRef type, Term value){
      super(location);
      this.frame = frame;
      this.index = index;
      this.type = type;
      this.value = value;
    }

    public SetLocal(int frame, int index, TypeRef type, Term value){
      this(null, frame, index, type, value);
    }

    public SetLocal(ClosureLocalBinding bind, Term value){
      this(null, bind.getFrameIndex(), bind.getIndex(), bind.getType(), value);
    }

    public int getFrame() {
      return frame;
    }

    public int getIndex() {
      return index;
    }

    public TypeRef type() {
      return type;
    }

    public Term getValue() {
      return value;
    }
  }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class NewClosure extends Term {
      private ClassTypeRef type;
      private MethodRef method;
      private ActionStatement block;
      private LocalFrame frame;

      public NewClosure(Location location, ClassTypeRef type, MethodRef method, ActionStatement block) {
        super(location);
        this.type =  type;
        this.method = method;
        this.block = block;
      }

      public NewClosure(ClassTypeRef type, MethodRef method, ActionStatement block) {
        this(null, type, method, block);
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
     * Date: 2005/06/17
     */
    public static class LongValue extends Term {
      private final long value;

      public LongValue(Location location, long value){
        super(location);
        this.value = value;
      }

      public LongValue(long value){
        this(null, value);
      }

      public long getValue() { return value; }

      public TypeRef type() { return BasicTypeRef.LONG; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class ConditionalLoop extends ActionStatement {
      public final Term condition;
      public final ActionStatement stmt;

      public ConditionalLoop(Location location, Term condition, ActionStatement stmt) {
        super(location);
        this.condition = condition;
        this.stmt = stmt;
      }
      public ConditionalLoop(Term condition, ActionStatement stmt) {
        this(null, condition, stmt);
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class Member implements Node {
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
    public static class MethodDefinition implements Node, MethodRef {
      private final Location location;
      private int modifier;
      private ClassTypeRef classType;
      private String name;
      private TypeRef[] arguments;
      private StatementBlock block;
      private TypeRef returnType;
      private boolean closure;
      private LocalFrame frame;

      public MethodDefinition(Location location, int modifier, ClassTypeRef classType, String name, TypeRef[] arguments, TypeRef returnType, StatementBlock block){
        this.location = location;
        this.modifier = modifier;
        this.classType = classType;
        this.name = name;
        this.arguments = arguments;
        this.returnType = returnType;
        this.block = block;
      }

      public Location location() {
        return location;
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
    public static class NewObject extends Term {
      public final ConstructorRef constructor;
      public final Term[] parameters;

      public NewObject(Location location, ConstructorRef constructor, Term[] parameters){
        super(location);
        this.constructor = constructor;
        this.parameters = parameters;
      }

      public NewObject(ConstructorRef constructor, Term[] parameters){
        this(null, constructor, parameters);
      }

      public TypeRef type() { return constructor.affiliation(); }
    }

    public static class NewArray extends Term {
      public final ArrayTypeRef arrayType;
      public final Term[] parameters;

      public NewArray(Location location, ArrayTypeRef arrayType, Term[] parameters){
        super(location);
        this.arrayType = arrayType;
        this.parameters = parameters;
      }

      public NewArray(ArrayTypeRef arrayType, Term[] parameters){
        this(null, arrayType, parameters);
      }

      public TypeRef type() { return arrayType; }
    }


    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class NOP extends ActionStatement {
      public NOP(Location location) {
        super(location);
      }
      public NOP() {
        this(null);
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class NullValue extends Term {
      public NullValue(Location location){
        super(location);
      }

      public NullValue(){
        super(null);
      }

      public TypeRef type() { return NullTypeRef.NULL; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class Return extends ActionStatement {
      public final Term term;

      public Return(Location location, Term term) {
        super(location);
        this.term = term;
      }

      public Return(Term term) {
        this(null, term);
      }

    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class ShortValue extends Term {
      private short value;
      public ShortValue(Location location, short value) {
        super(location);
        this.value = value;
      }
      
      public ShortValue(short value){
        this(null, value);
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
    abstract public static class ActionStatement {
      private final Location location;
      public ActionStatement(Location location) {
        this.location = location;
      }
      public Location location() {
        return location;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class RefStaticField extends Term {
      public ClassTypeRef target;
      public FieldRef field;

      public RefStaticField(Location location, ClassTypeRef target, FieldRef field){
        super(location);
        this.target = target;
        this.field = field;
      }

      public RefStaticField(ClassTypeRef target, FieldRef field){
        this(null, target, field);
      }

      public TypeRef type() { return field.type(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class SetStaticField extends Term {
      public ObjectTypeRef target;
      public FieldRef field;
      public Term value;

      public SetStaticField(Location location, ObjectTypeRef target, FieldRef field, Term value){
        super(location);
        this.target = target;
        this.field = field;
        this.value = value;
      }

      public SetStaticField(ObjectTypeRef target, FieldRef field, Term value){
        this(null, target, field, value);
      }

      public TypeRef type() { return field.type(); }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class StringValue extends Term {
      public String value;
      public TypeRef type;

      public StringValue(Location location, String value, TypeRef type){
        super(location);
        this.value = value;
        this.type = type;
      }

      public StringValue(String value, TypeRef type){
        this(null, value, type);
      }

      public String value() { return value; }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class Super implements Node {
      private ClassTypeRef classType;
      private TypeRef[] arguments;
      private Term[] terms;

      public Super(
        ClassTypeRef classType, TypeRef[] arguments, Term[] terms){
        this.classType = classType;
        this.arguments = arguments;
        this.terms = terms;
      }

      public ClassTypeRef getClassType() {
        return classType;
      }

      public TypeRef[] getArguments() {
        return arguments;
      }

      public Term[] getExpressions() {
        return terms;
      }

    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class Synchronized extends ActionStatement {
      public final Term term;
      public final ActionStatement statement;
      public Synchronized(Location location, Term term, ActionStatement statement){
        super(location);
        this.term = term;
        this.statement = statement;
      }
      public Synchronized(Term term, ActionStatement statement){
        this(null, term, statement);
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class OuterThis extends Term {
      private final ClassTypeRef type;

      public OuterThis(Location location, ClassTypeRef type){
        super(location);
        this.type = type;
      }

      public OuterThis(ClassTypeRef classType){
        this(null, classType);
      }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class This extends Term {
      private final ClassTypeRef type;

      public This(Location location, ClassTypeRef type){
        super(location);
        this.type = type;
      }

      public This(ClassTypeRef classType){
        this(null, classType);
      }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class Throw extends ActionStatement {
      public final Term term;
      public Throw(Location location, Term term){
        super(location);
        this.term = term;
      }
      public Throw(Term term){
        this(null, term);
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/17
     */
    public static class Try extends ActionStatement {
      public ActionStatement tryStatement;
      public ClosureLocalBinding[] catchTypes;
      public ActionStatement[] catchStatements;
      public Try(Location location, ActionStatement tryStatement, ClosureLocalBinding[] catchTypes, ActionStatement[] catchStatements){
        super(location);
        this.tryStatement = tryStatement;
        this.catchTypes = catchTypes;
        this.catchStatements = catchStatements;
      }
      public Try(ActionStatement tryStatement, ClosureLocalBinding[] catchTypes, ActionStatement[] catchStatements){
        this(null, tryStatement, catchTypes, catchStatements);
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class UnaryTerm extends Term {
      public static interface Constants {
          int PLUS = 0;
          int MINUS = 1;
          int NOT = 2;
          int BIT_NOT = 3;
      }

      private final int kind;
      private final Term operand;
      private final TypeRef type;

      public UnaryTerm(Location location, int kind, TypeRef type, Term operand) {
        super(location);
        this.kind = kind;
        this.operand = operand;
        this.type = type;
      }

      public UnaryTerm(int kind, TypeRef type, Term operand) {
        this(null, kind, type, operand);
      }

      public int getKind(){
        return kind;
      }

      public Term getOperand(){
        return operand;
      }

      public TypeRef type() { return type; }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/15
     */
    abstract public static class AbstractClassTypeRef extends AbstractObjectTypeRef implements ClassTypeRef {
      private ConstructorRefFinder constructorRefFinder;

      public AbstractClassTypeRef() {
        constructorRefFinder = new ConstructorRefFinder();
      }

      public ConstructorRef[] findConstructor(Term[] params){
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
    abstract public static class AbstractObjectTypeRef implements ObjectTypeRef {
      private MethodRefFinder methodRefFinder;
      private FieldRefFinder fieldRefFinder;

      public AbstractObjectTypeRef() {
        methodRefFinder = new MethodRefFinder();
        fieldRefFinder = new FieldRefFinder();
      }

      public FieldRef findField(String name) {
        return fieldRefFinder.find(this, name);
      }

      public MethodRef[] findMethod(String name, Term[] params) {
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
    public static class ArrayTypeRef extends AbstractObjectTypeRef {
      private ClassTable table;
      private TypeRef component;
      private int dimension;
      private ClassTypeRef superClass;
      private ClassTypeRef[] interfaces;
      private String name;

      public ArrayTypeRef(TypeRef component, int dimension, ClassTable table){
        this.component = component;
        this.dimension = dimension;
        this.table = table;
        this.superClass = table.load("java.lang.Object");
        this.interfaces = new ClassTypeRef[]{table.load("java.io.Serializable"), table.load("java.lang.Cloneable")};
        this.name = Strings.repeat("[", dimension) + component.name();
      }

      public TypeRef component(){
        return component;
      }

      public TypeRef base(){
        return dimension == 1 ? component : table.loadArray(component, dimension - 1);
      }

      public int dimension(){
        return dimension;
      }

      public boolean isInterface() {
        return false;
      }

      public int modifier() {
        return 0;
      }

      public ClassTypeRef superClass() {
        return superClass;
      }

      public ClassTypeRef[] interfaces() {
        return interfaces;
      }

      public MethodRef[] methods() {
        return superClass.methods();
      }

      public MethodRef[] methods(String name) {
        return superClass.methods(name);
      }

      public FieldRef[] fields() {
        return superClass.fields();
      }

      public FieldRef field(String name) {
        return superClass.field(name);
      }

      public String name() {
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
    public static class BasicTypeRef implements TypeRef {
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

      public String name(){
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
    public static interface ClassTypeRef extends ObjectTypeRef {
      ConstructorRef[] constructors();
      ConstructorRef[] findConstructor(Term[] params);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    public static class ConstructorRefFinder {
      private final Comparator<IRT.ConstructorRef> sorter = new Comparator<ConstructorRef>(){
        public int compare(IRT.ConstructorRef c1, IRT.ConstructorRef c2) {
          TypeRef[] arg1 = c1.getArgs();
          TypeRef[] arg2 = c2.getArgs();
          if(isAllSuperType(arg2, arg1)) return -1;
          if(isAllSuperType(arg1, arg2)) return 1;
          return 0;
        }
      };
      private final ParameterMatcher matcher = new StandardParameterMatcher();
      public ConstructorRef[] find(ClassTypeRef target, Term[] args){
        Set<ConstructorRef> constructors = new TreeSet<ConstructorRef>(new ConstructorRefComparator());
        if(target == null) return new ConstructorRef[0];
        ConstructorRef[] cs = target.constructors();
        for(int i = 0; i < cs.length; i++){
          ConstructorRef c = cs[i];
          if(matcher.matches(c.getArgs(), args)) constructors.add(c);
        }
        List<ConstructorRef> selected = new ArrayList<ConstructorRef>();
        selected.addAll(constructors);
        Collections.sort(selected, sorter);
        if(selected.size() < 2){
          return selected.toArray(new ConstructorRef[0]);
        }
        ConstructorRef constructor1 = selected.get(0);
        ConstructorRef constructor2 = selected.get(1);
        if(isAmbiguous(constructor1, constructor2)){
          return selected.toArray(new ConstructorRef[0]);
        }
        return new ConstructorRef[]{constructor1};
      }

      private boolean isAmbiguous(ConstructorRef constructor1, ConstructorRef constructor2){
        return sorter.compare(constructor1, constructor2) >= 0;
      }

      private boolean isAllSuperType(TypeRef[] arg1, TypeRef[] arg2){
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
    public static interface ConstructorRef extends MemberRef {
      public ClassTypeRef affiliation();
      public TypeRef[] getArgs();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    public static class ConstructorRefComparator implements Comparator<ConstructorRef> {

      public ConstructorRefComparator() {
      }

      public int compare(ConstructorRef c1, ConstructorRef c2) {
        TypeRef[] args1 = c1.getArgs();
        TypeRef[] args2 = c2.getArgs();
        int result = args1.length - args2.length;
        if(result != 0){
          return result;
        }
        for(int i = 0; i < args1.length; i++){
          if(args1[i] != args2[i]) return args1[i].name().compareTo(args2[i].name());
        }
        return 0;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/15
     */
    public static class FieldRefFinder {
      public FieldRef find(ObjectTypeRef target, String name){
        if(target == null) return null;
        FieldRef field = target.field(name);
        if(field != null) return field;
        field = find(target.superClass(), name);
        if(field != null) return field;
        ClassTypeRef[] interfaces = target.interfaces();
        for(ClassTypeRef anInterface:target.interfaces()){
          field = find(anInterface, name);
          if(field != null) return field;
        }
        return null;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/15
     */
    public static interface FieldRef extends MemberRef {
      public int modifier();
      public ClassTypeRef affiliation();
      public TypeRef type();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    public static class FieldRefComparator implements Comparator<FieldRef> {
      public FieldRefComparator() {
      }

      public int compare(FieldRef o1, FieldRef o2) {
        return o1.name().compareTo(o2.name());
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/21
     */
    public static interface MemberRef extends Named {
      int modifier();
      ClassTypeRef affiliation();
      String name();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    public static class MethodRefFinder {
      private final Comparator<MethodRef> sorter = new Comparator<MethodRef>(){
        public int compare(MethodRef m1, MethodRef m2) {
          TypeRef[] arg1 = m1.arguments();
          TypeRef[] arg2 = m2.arguments();
          if(isAllSuperType(arg2, arg1)) return -1;
          if(isAllSuperType(arg1, arg2)) return 1;
          return 0;
        }
      };

      private final ParameterMatcher matcher = new StandardParameterMatcher();

      public MethodRefFinder() {
      }

      public MethodRef[] find(ObjectTypeRef target, String name, Term[] arguments){
        Set<MethodRef> methods = new TreeSet<MethodRef>(new MethodRefComparator());
        find(methods, target, name, arguments);
        List<MethodRef> selectedMethods = new ArrayList<MethodRef>();
        selectedMethods.addAll(methods);
        Collections.sort(selectedMethods, sorter);
        if(selectedMethods.size() < 2){
          return (MethodRef[]) selectedMethods.toArray(new MethodRef[0]);
        }
        MethodRef method1 = selectedMethods.get(0);
        MethodRef method2 = selectedMethods.get(1);
        if(isAmbiguous(method1, method2)){
          return (MethodRef[]) selectedMethods.toArray(new MethodRef[0]);
        }
        return new MethodRef[]{method1};
      }

      public boolean isAmbiguous(MethodRef method1, MethodRef method2){
        return sorter.compare(method1, method2) >= 0;
      }

      private void find(Set<MethodRef> methods, ObjectTypeRef target, String name, Term[] params){
        if(target == null) return;
        MethodRef[] ms = target.methods(name);
        for(MethodRef m:target.methods(name)){
          if(matcher.matches(m.arguments(), params)) methods.add(m);
        }
        ClassTypeRef superClass = target.superClass();
        find(methods, superClass, name, params);
        ClassTypeRef[] interfaces = target.interfaces();
        for(ClassTypeRef anInterface:interfaces) {
          find(methods, anInterface, name, params);
        }
      }

      private static boolean isAllSuperType(TypeRef[] arg1, TypeRef[] arg2){
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
    public static interface MethodRef extends MemberRef {
      public ClassTypeRef affiliation();
      public TypeRef[] arguments();
      public TypeRef returnType();
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/07/12
     */
    public static class MethodRefComparator implements Comparator<MethodRef> {
      public int compare(MethodRef m1, MethodRef m2) {
        int result = m1.name().compareTo(m2.name());
        if(result != 0) return result;
        TypeRef[] args1 = m1.arguments();
        TypeRef[] args2 = m2.arguments();
        result = args1.length - args2.length;
        if(result != 0) return result;
        for(int i = 0; i < args1.length; i++){
          if(args1[i] != args2[i]) return args1[i].name().compareTo(args2[i].name());
        }
        return 0;
      }
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/04/17
     */
    public static class NullTypeRef implements TypeRef {
      public static NullTypeRef NULL = new NullTypeRef("null");

      private String name;

      private NullTypeRef(String name) {
        this.name = name;
      }

      public String name(){
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
    public static interface ObjectTypeRef extends TypeRef {
      boolean isInterface();
      int modifier();
      ClassTypeRef superClass();
      ClassTypeRef[] interfaces();
      MethodRef[] methods();
      MethodRef[] methods(String name);
      FieldRef[] fields();
      FieldRef field(String name);
      MethodRef[] findMethod(String name, Term[] params);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    public static interface ParameterMatcher {
      public boolean matches(TypeRef[] arguments, Term[] parameters);
    }

    /**
     * @author Kota Mizushima
     * Date: 2005/06/30
     */
    public static class StandardParameterMatcher implements ParameterMatcher {
      public StandardParameterMatcher() {
      }

      public boolean matches(TypeRef[] arguments, Term[] parameters){
        if(arguments.length != parameters.length) return false;
        TypeRef[] parameterTypes = new TypeRef[parameters.length];
        for(int i = 0; i < parameters.length; i++){
          parameterTypes[i] = parameters[i].type();
        }
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
    public static interface TypeRef {
      String name();
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
    public static class TypeRules {

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
            return left == ((ArrayTypeRef) right).superClass();
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
        return isSuperType(left.base(), right.base());
      }

      private static boolean isSuperTypeForClass(ClassTypeRef left, ClassTypeRef right){
        if(right == null) return false;
        if(left == right) return true;
        if(isSuperTypeForClass(left, right.superClass())) return true;
        for(int i = 0; i < right.interfaces().length; i++){
          if(isSuperTypeForClass(left, right.interfaces()[i])) return true;
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
