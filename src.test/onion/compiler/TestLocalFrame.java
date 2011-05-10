/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;


import junit.framework.TestCase;

/**
 * @author Kota Mizushima
 * Date: 2005/07/11
 */
public class TestLocalFrame extends TestCase {
  private LocalFrame frame;

  public static void main(String[] args) {
    junit.textui.TestRunner.run(TestLocalFrame.class);
  }

  /*
   * @see TestCase#setUp()
   */
  protected void setUp() throws Exception {
    frame = new LocalFrame(null);
  }

  /*
   * @see TestCase#tearDown()
   */
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testLocalFrame() {
  }

  public void testGetParent() {
    LocalFrame child = new LocalFrame(frame);
    assertSame(child.parent(), frame);
  }

  public void testOpenAndCloseScope() {
    LocalScope scope = frame.scope();
    frame.openScope();
    assertSame(scope, frame.scope().parent());
    frame.closeScope();
    assertSame(scope, frame.scope());
  }

  public void testEntries() {
    frame.add("foo", IRT.BasicTypeRef.BOOLEAN);
    frame.add("bar", IRT.BasicTypeRef.BYTE);
    frame.add("baz", IRT.BasicTypeRef.INT);
    LocalBinding[] binds = frame.entries();
  }

  public void testAddAndLookup() {
    assertNull(frame.lookup("foo"));
    int index = frame.add("foo", IRT.BasicTypeRef.BOOLEAN);
    ClosureLocalBinding bind, bind2;
    bind = frame.lookupOnlyCurrentScope("foo");
    assertEquals(bind.getIndex(), index);
    assertEquals(bind.getType(), IRT.BasicTypeRef.BOOLEAN);
    bind2 = frame.lookup("foo");
    assertEquals(bind, bind2);
  }

  public void testSetAllClosed() {
    frame = new LocalFrame(new LocalFrame(new LocalFrame(frame)));
    LocalFrame newFrame;
    newFrame = frame;
    while(newFrame != null){
      assertFalse(newFrame.isClosed());
      newFrame = newFrame.parent();
    }
    newFrame = frame;
    newFrame.setAllClosed(true);
    while(newFrame != null){
      assertTrue(newFrame.isClosed());
      newFrame = newFrame.parent();
    }
  }

  public void testClosed() {
    assertFalse(frame.isClosed());
    frame.setClosed(true);
    assertTrue(frame.isClosed());
    frame.setClosed(false);
    assertFalse(frame.isClosed());
  }

  public void testDepth() {
    LocalFrame child =  new LocalFrame(frame);
    assertEquals(child.depth(), frame.depth() + 1);
  }

}
