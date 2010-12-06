package onion.compiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/06
 * Time: 23:09:43
 * To change this template use File | Settings | File Templates.
 */
public class OrderedTable<E extends Named> extends AbstractTable<E> {
  public OrderedTable() {
    super(new LinkedHashMap<String, E>());
  }
}
