/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox;

import java.io.*;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * Utility class for IO.
 * @author Kota Mizushima
 * Date: 2005/04/19
 */
public class Inputs {

  private Inputs() {
  }

  public static BufferedReader newReader(String path) 
  	throws FileNotFoundException{    
    return new BufferedReader(new FileReader(new File(path)));
  }
  
  public static BufferedReader newReader(String path, String encoding)
		throws FileNotFoundException, UnsupportedEncodingException {
    return new BufferedReader(
      new InputStreamReader(
        new FileInputStream(new File(path)), encoding));
}

  
  public static PrintWriter newWriter(String path) throws IOException{
    return new PrintWriter(new BufferedWriter(new FileWriter(path)));
  }
  
  public static BufferedInputStream newInputStream(String path) 
  	throws FileNotFoundException{
    return new BufferedInputStream(new FileInputStream(path));
  }
  
  public static BufferedOutputStream newOutputStream(String path)
  	throws FileNotFoundException{
    return new BufferedOutputStream(new FileOutputStream(path));
  }
}
