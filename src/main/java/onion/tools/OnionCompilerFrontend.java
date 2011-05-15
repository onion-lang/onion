/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import onion.compiler.CompiledClass;
import onion.compiler.OnionCompiler;
import onion.compiler.CompilerConfig;
import onion.compiler.ScriptException;
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
public class OnionCompilerFrontend {
  private static final String CLASSPATH = "-classpath";
  private static final String SCRIPT_SUPER_CLASS = "-super";
  private static final String ENCODING = "-encoding";
  private static final String OUTPUT = "-d";
  private static final String MAX_ERROR = "-maxErrorReport";

  private static final String[] DEFAULT_CLASSPATH = new String[]{"."};
  private static final String DEFAULT_ENCODING = System.getProperty("file.encoding");
  private static final String DEFAULT_OUTPUT = ".";
  private static final int DEFAULT_MAX_ERROR = 10;

  private CommandLineParser commandLineParser;

  private static OptionConf conf(String option, boolean requireArg) {
    return new OptionConf(option, requireArg);
  }

  public OnionCompilerFrontend(){
    commandLineParser = new CommandLineParser(
      new OptionConf[]{
        conf(CLASSPATH, true),
        conf(SCRIPT_SUPER_CLASS, true),
        conf(ENCODING, true),
        conf(OUTPUT, true),
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
    String[] params = (String[]) result.getArguments().toArray(new String[0]);
    if(params.length == 0) {
      printUsage();
      return -1;
    }
    if(config == null) return -1;
    CompiledClass[] classes = compile(config, params);
    if(classes == null) return -1;
    return generateFiles(classes) ? 0 : -1;
  }

  private String getSimpleName(String fqcn){
    int index = fqcn.lastIndexOf(".");
    if(index < 0){
      return fqcn;
    }else{
      return fqcn.substring(index + 1, fqcn.length());
    }
  }

  private String getOutputPath(String outDir, String fqcn) {
    String name = getSimpleName(fqcn);
    return outDir + Systems.getFileSeparator() + name + ".class";
  }

  private boolean generateFiles(CompiledClass[] binaries){
    List generated = new ArrayList();
    for (int i = 0; i < binaries.length; i++) {
      CompiledClass binary = binaries[i];
      String outDir = binary.getOutputPath();
      new File(outDir).mkdirs();
      String outPath = getOutputPath(outDir, binary.getClassName());
      File targetFile = new File(outPath);
      try{
        if(!targetFile.exists()) targetFile.createNewFile();
        generated.add(targetFile);
        BufferedOutputStream out = new BufferedOutputStream(
          new FileOutputStream(targetFile)
        );
        try {
          out.write(binary.getContent());
        }finally{
          out.close();
        }
      }catch(IOException e){
        for(Iterator it = generated.iterator(); it.hasNext();){
          ((File)it.next()).delete();
        }
        return false;
      }
    }
    return true;
  }

  protected void printUsage() {
    printerr("Usage: onionc [-options] source_file ...");
    printerr("options: ");
    printerr("  -super <super class>        specify script's super class");
    printerr("  -d <path>                   specify output directory");
    printerr("  -classpath <path>           specify classpath");
    printerr("  -encoding <encoding>        specify source file encoding");
    printerr("  -maxErrorReport <number>    set number of errors reported");
  }

  private ParseSuccess parseCommandLine(String[] commandLine) {
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
    String outputDirectory = checkOutputDirectory((String)option.get(OUTPUT));
    Integer maxErrorReport = checkMaxErrorReport((String)option.get(MAX_ERROR));
    if(encoding == null || maxErrorReport == null || outputDirectory == null) {
      return null;
    }
    return new CompilerConfig(
      classpath, "", encoding, outputDirectory, maxErrorReport.intValue()
    );
  }

  private CompiledClass[] compile(CompilerConfig config, String[] fileNames) {
    OnionCompiler compiler = new OnionCompiler(config);
    return compiler.compile(fileNames);
  }

  private String[] checkClasspath(String classpath) {
    if(classpath == null) return DEFAULT_CLASSPATH;
    String[] paths = pathArray(classpath);
    return paths;
  }

  private String checkOutputDirectory(String outputDirectory) {
    if(outputDirectory == null) return DEFAULT_OUTPUT;
    return outputDirectory;
  }

  private String checkEncoding(String encoding) {
    if(encoding == null) return DEFAULT_ENCODING;
    try {
      "".getBytes(encoding);
      return encoding;
    }catch(UnsupportedEncodingException e){
      printerr(Messages.get("error.command.invalidEncoding", ENCODING));
      return null;
    }
  }

  private Integer checkMaxErrorReport(String maxErrorReport) {
    if(maxErrorReport == null) return new Integer(DEFAULT_MAX_ERROR);
    try{
      int value = Integer.parseInt(maxErrorReport);
      if(value > 0){
        return new Integer(value);
      }
    }catch(NumberFormatException e){/* nothing to do */}
    printerr(Messages.get("error.command.requireNaturalNumber", MAX_ERROR));
    return null;
  }

  private static String[] pathArray(String path) {
    return path.split(Systems.getPathSeparator());
  }

  private static void printerr(String message) {
    System.err.println(message);
  }

  public static void main(String[] args) throws Throwable {
    try {
      new OnionCompilerFrontend().run(args);
    } catch (ScriptException e) {
      throw e.getCause();
    }
  }
}
