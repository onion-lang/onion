/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import onion.compiler.*;
import onion.compiler.exceptions.ScriptException;
import onion.compiler.util.Messages;
import onion.compiler.util.Systems;
import onion.tools.option.CommandLineParser;
import onion.tools.option.OptionConf;
import onion.tools.option.ParseFailure;
import onion.tools.option.ParseResult;
import onion.tools.option.ParseSuccess;


/**
 * 
 * @author Kota Mizushima
 * 
 */
public class ScriptRunner {
  private static final String CLASSPATH = "-classpath";
  private static final String SCRIPT_SUPER_CLASS = "-super";
  private static final String ENCODING = "-encoding";
  private static final String MAX_ERROR = "-maxErrorReport";  
  
  private static final String[] DEFAULT_CLASSPATH = new String[]{"."};
  private static final String DEFAULT_ENCODING = System.getProperty("file.encoding");
  private static final String DEFAULT_OUTPUT = ".";
  private static final int DEFAULT_MAX_ERROR = 10;
  
  private CommandLineParser commandLineParser;
  
  private static OptionConf conf(String optionName, boolean requireArgument) {
    return new OptionConf(optionName, requireArgument);
  }
    
  public ScriptRunner() {
    commandLineParser = new CommandLineParser(
      new OptionConf[]{
        conf(CLASSPATH, true),
        conf(SCRIPT_SUPER_CLASS, true),
        conf(ENCODING, true),
        conf(MAX_ERROR, true)
      }
    );
  }
  
  public int run(String[] commandLine) {
    if(commandLine.length == 0){
      printUsage();
      return -1;
    }
    ParseSuccess result = parseCommandLine(commandLine);
    if(result == null) return -1;
    CompilerConfig config = createConfig(result);
    if(config == null) return -1;
    String[] params = (String[]) result.getArguments().toArray(new String[0]);
    if(params.length == 0) {
      printUsage();
      return -1;
    }
    CompiledClass[] classes = compile(config, new String[]{params[0]});
    if(classes == null) return -1;    
    String[] scriptParams = new String[params.length - 1];
    for(int i = 1; i < params.length; i++) {
      scriptParams[i - 1] = params[i];
    }
    OnionShell shell = new OnionShell(OnionClassLoader.class.getClassLoader(), config.getClassPath());
    return shell.run(classes, scriptParams);
  }
  
  protected void printUsage(){
    printerr("Usage: onion [-options] <source file> <command line arguments>");
    printerr("options: ");
    printerr("  -super <super class>        specify script's super class");
    printerr("  -classpath <class path>     specify classpath");
    printerr("  -encoding <encoding>        specify source file encoding");
    printerr("  -maxErrorReport <number>    set number of errors reported");
  }
  
  private ParseSuccess parseCommandLine(String[] commandLine){
    ParseResult result = commandLineParser.parse(commandLine);
    if(result.getStatus() == ParseResult.FAILURE){
      ParseFailure failure = (ParseFailure)result;
      String[] lackedOptions = failure.getLackedOptions();
      String[] invalidOptions = failure.getInvalidOptions();
      for (int i = 0; i < invalidOptions.length; i++) {
        printerr(Messages.get("error.command.invalidArgument", invalidOptions[i]));
      }
      for (int i = 0; i < lackedOptions.length; i++) {
        printerr(Messages.get("error.command..noArgument", lackedOptions[i]));
      }
      return null;
    }
    return (ParseSuccess)result;
  }
  
  private CompilerConfig createConfig(ParseSuccess result) {
    Map option = result.getOptions();
    Map noargOption = result.getNoArgumentOptions();
    String[] classpath = checkClasspath((String)option.get(CLASSPATH));
    String encoding = checkEncoding((String)option.get(ENCODING));
    Integer maxErrorReport = checkMaxErrorReport((String)option.get(MAX_ERROR));
    if(encoding == null || maxErrorReport == null) {
      return null;
    }
    return new CompilerConfig(classpath, "", encoding, "." , maxErrorReport.intValue());
  }  
  
  private CompiledClass[] compile(CompilerConfig config, String[] fileNames) {
    return new OnionCompiler(config).compile(fileNames);
  }
  
  private String[] checkClasspath(String classpath){
    if(classpath == null) return DEFAULT_CLASSPATH;
    String[] paths = pathArray(classpath);
    return paths;
  }
  
  private String checkEncoding(String encoding){
    if(encoding == null) return System.getProperty("file.encoding");
    try {
      "".getBytes(encoding);
      return encoding;
    }catch(UnsupportedEncodingException e){
      System.err.println(Messages.get("error.command.invalidEncoding", ENCODING));
      return null;
    }
  }
  
  private Integer checkMaxErrorReport(String maxErrorReport){
    if(maxErrorReport == null) return new Integer(DEFAULT_MAX_ERROR);
    int value;
    try{
      value = Integer.parseInt(maxErrorReport);
      if(value > 0){
        return new Integer(value);
      }
    }catch(NumberFormatException e){/* nothing to do */}
    printerr(Messages.get("error.command.requireNaturalNumber", MAX_ERROR));
    return null;
  }
  
  private static String[] pathArray(String path){
    return path.split(Systems.getPathSeparator());
  }
  
  private static void printerr(String message){
    System.err.println(message);
  }
  
  public static void main(String[] args) throws Throwable {
    try {
      new ScriptRunner().run(args);
    } catch (ScriptException e) {
      throw e.getCause();
    }
  }
}
