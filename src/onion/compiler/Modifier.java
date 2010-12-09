package onion.compiler;
/* ************************************************************** *
* *
* Copyright (c) 2005, Kota Mizushima, All rights reserved. *
* *
* *
* This software is distributed under the modified BSD License. *
* ************************************************************** */

/**
* @author Kota Mizushima
* Date: 2005/04/10
*/
public class Modifier {
  public static final int INTERNAL = 1;
  public static final int SYNCHRONIZED = 2;
  public static final int FINAL = 4;
  public static final int ABSTRACT = 8;
  public static final int VOLATILE = 16;
  public static final int STATIC = 32;
  public static final int INHERITED = 64;
  public static final int PUBLIC = 128;
  public static final int PROTECTED = 256;
  public static final int PRIVATE = 512;
  public static final int FORWARDED = 1024;

  public static boolean check(int modifier, int bitFlag){
    return (modifier & bitFlag) != 0;
  }

  public static boolean isInternal(int modifier){
    return check(modifier, INTERNAL);
  }

  public static boolean isStatic(int modifier){
    return check(modifier, STATIC);
  }

  public static boolean isSynchronized(int modifier){
    return check(modifier, SYNCHRONIZED);
  }

  public static boolean isFinal(int modifier){
    return check(modifier, FINAL);
  }

  public static boolean isAbstract(int modifier){
    return check(modifier, ABSTRACT);
  }

  public static boolean isVolatile(int modifier){
    return check(modifier, VOLATILE);
  }

  public static boolean isInherited(int modifier){
    return check(modifier, INHERITED);
  }

  public static boolean isPublic(int modifier){
    return check(modifier, PUBLIC);
  }

  public static boolean isProtected(int modifier){
    return check(modifier, PROTECTED);
  }

  public static boolean isPrivate(int modifier){
    return check(modifier, PRIVATE);
  }

  public static boolean isForwarded(int modifier){
    return check(modifier, FORWARDED);
  }

}
