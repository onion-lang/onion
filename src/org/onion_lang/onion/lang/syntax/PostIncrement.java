package org.onion_lang.onion.lang.syntax;

import org.onion_lang.onion.lang.syntax.visitor.ASTVisitor;

public class PostIncrement extends UnaryExpression {
  public PostIncrement(Expression target) {
    super(target, "++");
  }

  public Object accept(ASTVisitor visitor, Object context) {
    return visitor.visit(this, context);
  }

}
