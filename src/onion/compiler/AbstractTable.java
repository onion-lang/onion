package onion.compiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/07
 * Time: 2:43:30
 * To change this template use File | Settings | File Templates.
 */
public class AbstractTable<E extends Named> implements Iterable<E> {
  private final Map<String, E> mapping;
  public AbstractTable(Map<String, E> mapping) {
    this.mapping = mapping;
  }

  protected Map<String, E> mapping() {
    return mapping;
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
