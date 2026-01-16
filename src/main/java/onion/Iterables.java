package onion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Iterables {
  private static <A, B> void _map(Iterable<A> it, Function1<A, B> f, Collection<B> result) {
    for(A arg: it) result.add(f.call(arg));
  }
  public static <A, B> List<B> map(List<A> list, Function1<A, B> f) {
    List<B> result = new ArrayList<B>();
    _map(list, f, result);
    return result;
  }
  public static <A, B> Iterable<B> map(Iterable<A> list, Function1<A, B> f) {
    List<B> result = new ArrayList<B>();
    _map(list, f, result);
    return result;
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

  // Filter collection elements
  public static <T> List<T> filter(List<T> list, Function1<T, Boolean> predicate) {
    List<T> result = new ArrayList<>();
    for (T item : list) {
      Boolean keep = predicate.call(item);
      if (keep != null && keep) {
        result.add(item);
      }
    }
    return result;
  }

  // Filter iterable elements
  public static <T> Iterable<T> filter(Iterable<T> iterable, Function1<T, Boolean> predicate) {
    List<T> result = new ArrayList<>();
    for (T item : iterable) {
      Boolean keep = predicate.call(item);
      if (keep != null && keep) {
        result.add(item);
      }
    }
    return result;
  }

  // Reduce collection to single value
  public static <T> Object reduce(List<T> list, Object initial, Function2<Object, T, Object> reducer) {
    Object acc = initial;
    for (T item : list) {
      acc = reducer.call(acc, item);
    }
    return acc;
  }

  // Check if any element matches predicate (Scala-style naming)
  public static <T> boolean exists(Iterable<T> iterable, Function1<T, Boolean> predicate) {
    for (T item : iterable) {
      Boolean matches = predicate.call(item);
      if (matches != null && matches) {
        return true;
      }
    }
    return false;
  }

  // Check if all elements match predicate (Scala-style naming)
  public static <T> boolean forAll(Iterable<T> iterable, Function1<T, Boolean> predicate) {
    for (T item : iterable) {
      Boolean matches = predicate.call(item);
      if (matches == null || !matches) {
        return false;
      }
    }
    return true;
  }

  // Create list from varargs
  @SafeVarargs
  public static <T> List<T> listOf(T... elements) {
    List<T> list = new ArrayList<>();
    for (T elem : elements) {
      list.add(elem);
    }
    return list;
  }

  // Create list with size
  public static <T> List<T> newList(int size) {
    return new ArrayList<>(size);
  }

  // Get first element or null
  public static <T> T first(List<T> list) {
    return list.isEmpty() ? null : list.get(0);
  }

  // Get last element or null
  public static <T> T last(List<T> list) {
    return list.isEmpty() ? null : list.get(list.size() - 1);
  }

  // Reverse list
  public static <T> List<T> reverse(List<T> list) {
    List<T> result = new ArrayList<>(list);
    Collections.reverse(result);
    return result;
  }

  // Take first n elements
  public static <T> List<T> take(List<T> list, int n) {
    if (n >= list.size()) return new ArrayList<>(list);
    return new ArrayList<>(list.subList(0, n));
  }

  // Drop first n elements
  public static <T> List<T> drop(List<T> list, int n) {
    if (n >= list.size()) return new ArrayList<>();
    return new ArrayList<>(list.subList(n, list.size()));
  }
}
