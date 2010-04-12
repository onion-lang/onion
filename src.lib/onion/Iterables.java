package onion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Iterables {
  private static <A, B> void _map(Iterable<A> it, Function1<A, B> f, Collection<B> result) {
    List<B> newList = new ArrayList<B>();
    for(A arg: it) result.add(f.call(arg));
  }
  public static <A, B> List<B> map(List<A> list, Function1<A, B> f) {
    List<B> result = new ArrayList<B>();
    _map(list, f, result);
    return result;
  }
  public static <A, B> Iterable<B> map(Iterable<A> list, Function1<A, B> f) {
    return map(list, f);
  }
  public static <A, B> Set<B> map(Set<A> set, Function1<A, B> f) {
    Set<B> result = new HashSet<B>();
    _map(set, f, result);
    return result;
  }
  
  public static <A, B, C, D> Map<C, D> mapMap(Map<A, B> map, Function1<Map.Entry<A, B>, Map.Entry<C, D>> f) {   
    Collection<Map.Entry<A, B>> mapView = View.asCollection(map);
    Map<C, D> result = new HashMap<C, D>();
    _map(mapView, f, View.asCollection(result));
    return result;
  }
  
  public static <A, B> B foldl(Iterable<A> it, B init, Function2<B, A, B> f) {
    B result = init;
    for(A a:it) result = f.call(result, a);
    return result;
  }  
}