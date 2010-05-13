/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.env;

import java.util.*;

import org.onion_lang.onion.lang.core.type.TypeRef;


/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
public class LocalFrame {
  private LocalFrame parent;
  private LocalScope scope;
  private List<LocalScope> allScopes;
  private int maxIndex;
  private boolean closed;
  
  /**
   * Creates a new frame.
   * @param parent the parent frame
   */
  public LocalFrame(LocalFrame parent) {
    this.parent = parent;
    this.scope = new LocalScope(null);
    this.allScopes = new ArrayList<LocalScope>();
    allScopes.add(scope);
  }
  
  /**
   * Gets the parent frame.
   * @return
   */
  public LocalFrame getParent(){
    return parent;
  }
  
  /**
   * Opens a new scope.
   */
  public void openScope(){
    scope = new LocalScope(scope);
    allScopes.add(scope);
  }
  
  /**
   * Closes the current scope.
   */
  public void closeScope(){
    scope = scope.getParent();
  }

  LocalScope getScope(){
    return scope;
  } 
  
  public LocalBinding[] entries(){
    Set<LocalBinding> entries = entrySet();
    LocalBinding[] binds = new LocalBinding[entries.size()];
    Iterator<LocalBinding> iterator = entries.iterator();
    for(int i = 0; i < binds.length; i++){
      binds[i] = iterator.next();
    }
    Arrays.sort(binds, new Comparator<LocalBinding>(){
      public int compare(LocalBinding b1, LocalBinding b2) {
        int i1 = b1.getIndex(), i2 = b2.getIndex();
        return i1 < i2 ? -1 : i1 > i2 ? 1 : 0;
      }
    });
    return binds;
  }
  
  public int addEntry(String name, TypeRef type){
    LocalBinding bind = scope.get(name);    
    //if name is already registered, it returns -1 which means failure.
    if(bind != null) return -1;
    
    int index = maxIndex;
    maxIndex++;
    scope.put(name, new LocalBinding(index, type));
    return index;
  }
  
  public ClosureLocalBinding lookup(String name){
    LocalFrame frame = this;
    int frameIndex = 0;
    while(frame != null){
      LocalBinding binding = frame.scope.lookup(name);
      if(binding != null){
        return new ClosureLocalBinding(
          frameIndex, binding.getIndex(), binding.getType()
        );
      }      
      frameIndex++;
      frame = frame.parent;
    }
    return null;
  }

  public ClosureLocalBinding lookupOnlyCurrentScope(String name) {
    LocalBinding binding = scope.get(name);
    if(binding != null){
      return new ClosureLocalBinding(
        0, binding.getIndex(), binding.getType()
      );
    }
    return null;
  }
  
  public void setAllClosed(boolean closed){
    LocalFrame frame = this;
    while(frame != null){
      frame.setClosed(closed);
      frame = frame.getParent();
    }
  }
  
  public void setClosed(boolean closed){
    this.closed = closed;
  }
  
  public boolean isClosed(){
    return closed;
  }
  
  public int depth(){
    LocalFrame frame = this;
    int depth = -1;
    while(frame != null){
      depth++;
      frame = frame.getParent();
    }
    return depth;
  }
  
  private Set<LocalBinding> entrySet(){
    Set<LocalBinding> entries = new HashSet<LocalBinding>();
    for(LocalScope s : allScopes) {
      entries.addAll(s.entries());
    }
    return entries;
  }
}