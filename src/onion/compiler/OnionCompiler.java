/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.MessageFormat;

import onion.compiler.error.CompilationException;
import onion.compiler.error.CompileError;
import onion.compiler.pass.*;
import onion.compiler.util.*;
import onion.lang.syntax.Location;


/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
public class OnionCompiler {
  private final CompilerConfig config;
  
  public OnionCompiler(CompilerConfig config) {
    this.config = config;
  }
  
  public CompilerConfig getConfig() {
    return config;
  }
  
  public CompiledClass[] compile(String[] fileNames) {
    InputSource[] srcs = new InputSource[fileNames.length];
    for (int i = 0; i < srcs.length; i++) {
      srcs[i] = new FileInputSource(fileNames[i]);        
    }
    return compile(srcs);
  }
  
  public CompiledClass[] compile(InputSource[] srcs) {
    try {
      Parsing        pass1 = new Parsing(config);
      CodeAnalysis   pass2 = new CodeAnalysis(config);
      CodeGeneration pass3 = new CodeGeneration(config);
      return pass3.process(pass2.process(pass1.process(srcs)));
    }catch(CompilationException ex){
      int count = ex.size();
      for(int i = 0; i < count; i++){
        printError(ex.get(i));
      }
      System.err.println(Messages.get("error.count", new Integer(count)));
      return null;
    }
  }
  
  private void printError(CompileError error) {
    Location location = error.getLocation();
    String sourceFile = error.getSourceFile();
    StringBuffer message = new StringBuffer();
    if(sourceFile == null){
      message.append(MessageFormat.format("{0}", new Object[]{error.getMessage()}));
    }else{
      String line = null, lineNum = null;
      try {
        line = location != null ? getLine(sourceFile, location.getLine()) : "";
        lineNum = location != null ? Integer.toString(location.getLine()) : "";
      } catch (IOException e) {
        e.printStackTrace();
      }
      message.append(
        MessageFormat.format(
          "{0}:{1}: {2}", new Object[]{sourceFile, lineNum, error.getMessage()}
        )
      );
      
      message.append(Systems.getLineSeparator());
      message.append("\t\t");
      message.append(line);
      message.append(Systems.getLineSeparator());
      message.append("\t\t");
      if(location != null){
        message.append(getCursor(location.getColumn()));
      }
    }
    System.err.println(new String(message));
  }

  private String getCursor(int column) {
    return Strings.repeat(" ", column - 1) + "^";
  }
  
  private String getLine(String sourceFile, int lineNumber) throws IOException {
    BufferedReader reader = Inputs.newReader(sourceFile);
    try {
      int countLineNumber = 1;
      String line = null;
      while((line = reader.readLine()) != null){
        if(countLineNumber == lineNumber) break;
        countLineNumber++;
      }
      return line;
    } finally {
      reader.close();
    }
  }
}