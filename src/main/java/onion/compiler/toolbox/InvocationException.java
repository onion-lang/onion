package onion.compiler.toolbox;

public class InvocationException extends RuntimeException {
  public InvocationException(Exception reason) {
    super(reason);
  }
}
