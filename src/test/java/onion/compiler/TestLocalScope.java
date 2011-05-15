/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.util.Set;


import junit.framework.TestCase;

/**
 * @author Kota Mizushima
 * Date: 2005/07/05
 */
public class TestLocalScope extends TestCase {
  private LocalScope scope;
  private LocalBinding[] bindings;
  private String[] names;
  private String[] names2;
  
  public static void main(String[] args) {
    junit.textui.TestRunner.run(TestLocalScope.class);
  }

  protected void setUp() throws Exception {
    scope = new LocalScope(null);
    bindings = new LocalBinding[]{
      new LocalBinding(0, IRT.BasicTypeRef.INT),
      new LocalBinding(1, IRT.BasicTypeRef.DOUBLE),
      new LocalBinding(2, IRT.BasicTypeRef.LONG),
      new LocalBinding(3, IRT.BasicTypeRef.DOUBLE)
    };
    names = new String[]{"hoge", "foo", "bar", "hogehoge"};
    names2 = new String[]{"a", "b", "c", "d"};
  }

  protected void tearDown() throws Exception {
  }

  public void testEntries() {
    putAll(scope, names, bindings);
    Set entries = scope.entries();
    assertTrue(entries.size() == names.length);
    for(int i = 0; i < bindings.length; i++){
      assertTrue(entries.contains(bindings[i]));
    }
  }

  public void testContains() {
    putAll(scope, names, bindings);
    for(int i = 0; i < names.length; i++){
      assertTrue(scope.contains(names[i]));
    }
    for(int i = 0; i < names2.length; i++){
      assertFalse(scope.contains(names2[i]));
    }
  }

  public void testPutAndGet() {
    putAll(scope, names, bindings);
    for(int i = 0; i < names.length; i++){
      assertSame(scope.get(names[i]), bindings[i]);
    }
  }

  public void testLookup() {
    putAll(scope, names, bindings);
    LocalScope child = new LocalScope(scope);
    putAll(child, names2, bindings);
    for(int i = 0; i < names.length; i++){
      assertSame(child.lookup(names[i]), bindings[i]);
    }
  }
  
  private boolean putAll(
    LocalScope scope,String[] names, LocalBinding[] bindings){
    boolean contains = false;
    for(int i = 0; i < names.length; i++){
      if(scope.put(names[i], bindings[i])){
        contains = true;
      }
    }
    return contains;
  }
}
