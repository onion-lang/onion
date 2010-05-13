/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.lang.core.type;

import java.util.Comparator;


/**
 * @author Kota Mizushima
 * Date: 2005/07/12
 */
public class FieldSymbolComparator implements Comparator {
  public FieldSymbolComparator() {
  }

  public int compare(Object arg0, Object arg1) {
    FieldSymbol f1 = (FieldSymbol) arg0;
    FieldSymbol f2 = (FieldSymbol) arg1;
    return f1.getName().compareTo(f2.getName());
  }
}
