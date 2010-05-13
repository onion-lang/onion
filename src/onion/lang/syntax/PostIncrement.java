package onion.lang.syntax;

import onion.lang.syntax.visitor.ASTVisitor;

public class PostIncrement extends UnaryExpression {
  public PostIncrement(Expression target) {
    super(target, "++");
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

}
