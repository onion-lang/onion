package onion.compiler;

public final class Pair<A, B> {
  public final A _1;
  public final B _2;
  public Pair(A _1, B _2) {
    this._1 = _1;
    this._2 = _2;
  }
  public static <A, B> Pair<A, B> make(A _1, B _2) {
    return new Pair<A, B>(_1, _2);
  }
}
