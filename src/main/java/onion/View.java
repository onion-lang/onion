package onion;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class View {
  public static <A, B> Collection<Map.Entry<A, B>> asCollection(
    final Map<A, B> map
  ) {
    return new Collection<Map.Entry<A, B>>() {
      public boolean add(Entry<A, B> e) {
        map.put(e.getKey(), e.getValue());
        return true;
      }

      public boolean addAll(Collection<? extends Entry<A, B>> c) {
        for(Map.Entry<A, B> e:c) add(e);
        return true;
      }

      public void clear() {
        map.clear();
      }

      public boolean contains(Object o) {
        if(o == null || !(o instanceof Map.Entry<?, ?>)) return false;
        Map.Entry<A, B> m = (Map.Entry<A, B>)o;
        return map.containsKey(m.getKey()) 
            && map.get(m.getKey()).equals(m.getValue());
      }

      public boolean containsAll(Collection<?> c) {
        for(Object o:c) if(!contains(o)) return false;
        return true;
      }

      public boolean isEmpty() {
        return map.isEmpty();
      }

      public Iterator<Entry<A, B>> iterator() {
        return map.entrySet().iterator();
      }

      public boolean remove(Object o) {
        if(!(o instanceof Map.Entry<?, ?>)) return false;
        map.remove(((Map.Entry<A, B>) o).getKey());
        return true;
      }

      public boolean removeAll(Collection<?> c) {
        for(Object o:c) remove(o);
        return true;
      }

      public boolean retainAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
      }

      public int size() {
        return map.size();
      }

      public Object[] toArray() {
        return map.entrySet().toArray();
      }

      public <T> T[] toArray(T[] a) {
        return map.entrySet().toArray(a);
      }      
    };
  }
}
