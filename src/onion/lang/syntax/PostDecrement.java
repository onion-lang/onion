package onion.lang.syntax;

import onion.lang.syntax.visitor.ASTVisitor;

public class PostDecrement extends UnaryExpression {
  public PostDecrement(Expression target) {
    super(target, "--");
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

}
