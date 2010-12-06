/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.pass;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import onion.compiler.CompileError;
import onion.compiler.CompilerConfig;
import onion.compiler.InputSource;
import onion.compiler.CompilationException;
import onion.compiler.parser.*;
import onion.compiler.util.*;
import onion.lang.syntax.CompilationUnit;
import onion.compiler.Location;


/**
 * parsing phase
 * @author Kota Mizushima
 * Date: 2005/04/19
 */
public class Parsing {
  private CompilerConfig config;

  public Parsing(CompilerConfig config) {
    this.config = config;
  }
  
  /**
   * Parses files indicated by paths.
   * @param paths an array of path which indicate file which will be parsed
   * @return parsed compilation units
   */
  public CompilationUnit[] process(InputSource[] srcs) {
    CompilationUnit[] units = new CompilationUnit[srcs.length];
    List problems = new ArrayList();
    for(int i = 0; i < srcs.length; i++){
      try{
        units[i] = parse(srcs[i].openReader(), srcs[i].getName());
      }catch(IOException e){
        problems.add(new CompileError(null, null, Messages.get("error.parsing.read_error", srcs[i].getName())));
      }catch(ParseException e){
        Token error = e.currentToken.next;
        String expected = e.tokenImage[e.expectedTokenSequences[0][0]];
        problems.add(new CompileError(
          srcs[i].getName(), new Location(error.beginLine, error.beginColumn),
          Messages.get("error.parsing.syntax_error", error.image, expected)
        ));
      }
    }
    if(problems.size() > 0) throw new CompilationException(problems);
    return units;
  }
  
  public CompilationUnit parse(Reader reader, String fileName) throws ParseException {
    CompilationUnit unit =  new JavaccOnionParser(reader).unit();
    unit.setSourceFileName(fileName);
    return unit;
  }
}