package onion.compiler.util;

import scala.collection.immutable.List;
import scala.collection.immutable.Nil$;
import scala.collection.immutable.$colon$colon;

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/10/29
 * Time: 11:19:55
 * To change this template use File | Settings | File Templates.
 */
public class SLists {
  public static <T> List<T> list(T... elements) {
    List<T> result = SLists.<T>nil();
    for(T e:elements) {
      result = SLists.cons(e, result);
    }
    return result.reverse();
  }
  public static <T> List<T> cons(T head, List<T> tail) {
    return new $colon$colon<T>(head, tail);
  }
  public static <T> List<T> nil() {
    return (List<T>)(Object)Nil$.MODULE$;
  }
}