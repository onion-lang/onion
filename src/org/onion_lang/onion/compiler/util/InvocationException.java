package org.onion_lang.onion.compiler.util;

public class InvocationException extends RuntimeException {
  public InvocationException(Exception reason) {
    super(reason);
  }
}
