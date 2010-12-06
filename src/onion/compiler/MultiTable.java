package onion.compiler;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/07
 * Time: 2:48:39
 * To change this template use File | Settings | File Templates.
 */
public class MultiTable<E extends Named> implements Iterable<E> {
  private final Map<String, List<E>> mapping;
  public MultiTable() {
    this.mapping =  new HashMap<String, List<E>>();
  }
  public boolean add(E entry) {
    List<E> value = mapping.get(entry.name());
    if(value == null) {
      value = new ArrayList<E>();
      value.add(entry);
      mapping.put(entry.name(), value);
      return false;
    }else {
      value.add(entry);
      return true;
    }
  }
  public List<E> get(String key) {
    return mapping.get(key);
  }
  public List<E> values() {
    List<E> list = new ArrayList<E>();
    for(List<E> value:mapping.values()) list.addAll(value);
    return list;
  }
  public Iterator<E> iterator() {
    return values().iterator();
  }
}
