package org.onion_lang.onion.compiler;

/**
 * ML-like option type.
 * @author Mizushima
 *
 * @param <T> type of the value
 */
public abstract class Option<T> {
  private static Option NONE = new Option() {
    public boolean isEmpty() { return true; }
    public Object get() { throw new UnsupportedOperationException("NONE.get()"); }
  };
  public abstract boolean isEmpty();
  public abstract T get();
  public static <T> Option<T> some(final T value) {
    return new Option<T>() {
      public boolean isEmpty() { return false; }
      public T get() { return value; }
    };
  }
  @SuppressWarnings("unchecked")
  public static <T> Option<T> none() {
    return (Option<T>)NONE;
  };
}
