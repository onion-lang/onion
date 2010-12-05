/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

/**
 * This class represents an exception while script is running.
 *
 * @author Kota Mizushima
 * Date: 2005/09/15
 */
public class ScriptException extends RuntimeException {
  public ScriptException() {
  }

  public ScriptException(String message) {
    super(message);
  }

  /**
   * @param message error message
   * @param cause why this exception has thrown
   */
  public ScriptException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * @param cause
   */
  public ScriptException(Throwable cause) {
    super(cause);
  }
}
