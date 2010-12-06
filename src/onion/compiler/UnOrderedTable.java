package onion.compiler;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/07
 * Time: 2:47:03
 * To change this template use File | Settings | File Templates.
 */
public class UnOrderedTable<E extends Named> extends AbstractTable<E> {
  public UnOrderedTable() {
    super(new HashMap<String,E>());
  }
}
