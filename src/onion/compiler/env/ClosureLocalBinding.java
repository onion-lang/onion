/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import onion.lang.core.type.TypeRef;

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
public class ClosureLocalBinding extends LocalBinding {
  private int frame;
  
  public ClosureLocalBinding(int frame, int index, TypeRef type) {
    super(index, type);
    this.frame = frame;
  }

  public void setFrame(int frameIndex) {
    this.frame = frameIndex;
  }

  public int getFrame() {
    return frame;
  }
  
  public boolean equals(Object object) {
    if(!(object instanceof ClosureLocalBinding)) return false;
    ClosureLocalBinding bind = (ClosureLocalBinding) object;
    if(frame != bind.frame) return false;
    if(getIndex() != bind.getIndex()) return false;
    if(getType() != bind.getType()) return false;
    return true;
  }
  
  public int hashCode() {
    return frame + getIndex() + getType().hashCode();
  }
}
