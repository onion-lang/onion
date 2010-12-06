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
public class OrderedTable<E extends Named> implements Iterable<E> {
  private final LinkedHashMap<String, E> mapping;
  public OrderedTable() {
    this.mapping = new LinkedHashMap<String,E>();
  }

  public void add(String key, E value) {
    mapping.put(key, value);
  }

  public void add(E entry) {
    mapping.put(entry.name(), entry);
  }

  public E get(String key) {
    return mapping.get(key);
  }

  public List<E> values() {
    return new ArrayList<E>(mapping.values());
  }

  public Iterator<E> iterator() {
    return mapping.values().iterator();
  }
}
