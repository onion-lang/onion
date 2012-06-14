/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.option;

import java.util.List;
import java.util.Map;

import onion.tools.option.CommandLineParser;

import junit.framework.TestCase;

/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
public class TestCommandLineParser extends TestCase {
  private CommandLineParser testTarget1;
  private CommandLineParser testTarget2;
  private CommandLineParser testTarget3;

  public static void main(String[] args) {
    junit.textui.TestRunner.run(TestCommandLineParser.class);
  }
  
  private static OptionConf conf(String optionName, boolean requireArgument){
    return new OptionConf(optionName, requireArgument);
  }

  protected void setUp() throws Exception {
    testTarget1 = new CommandLineParser(
      new OptionConf[]{
          conf("-sourcepath", true), conf("-d", false)});
    testTarget2 = new CommandLineParser(
      new OptionConf[]{
          conf("-sourcepath", true), conf("-d", true)});
    testTarget3 = new CommandLineParser(
      new OptionConf[]{
          conf("-sourcepath", false), conf("-d", false)});
          
  }

  protected void tearDown() throws Exception {
  }
  
  private static void checkOption(Map config, String option, String value){
    checkOptionNotNull(config, option);
    assertEquals(config.get(option), value);
  }
  
  private static void checkOptionNotNull(Map config, String option){
    assertNotNull(config.get(option));
  }
  
  private static void checkList(List list, String[] values){
    for(int i = 0; i < list.size(); i++){
      assertEquals(list.get(i), values[i]);
    }
  }
  
  public void testParse(){
    ParseResult result;
    Map options;
    Map noArgumentOptions;
    List arguments;
    
    result = testTarget1.parse(new String[]{"foo", "-sourcepath", "src", "hoge", "-d"});
    assertEquals(result.getStatus(), ParseResult.SUCCEED);
    options = ((ParseSuccess)result).getOptions();
    noArgumentOptions = ((ParseSuccess)result).getNoArgumentOptions();
    arguments = ((ParseSuccess)result).getArguments();
    checkOption(options, "-sourcepath", "src");
    checkOptionNotNull(noArgumentOptions, "-d");
    checkList(arguments, new String[]{"foo", "hoge"});
    
    result = testTarget2.parse(new String[]{"-classpath", "-sourcepath", "src", "-d"});
    assertEquals(result.getStatus(), ParseResult.FAILURE);
    String[] invalidOptions = ((ParseFailure)result).getInvalidOptions();
    String[] lackedOptions = ((ParseFailure)result).getLackedOptions();
    assertEquals(invalidOptions.length, 1);
    assertEquals(invalidOptions[0], "-classpath");
    assertEquals(lackedOptions.length, 1);
    assertEquals(lackedOptions[0], "-d");
    
    result = testTarget3.parse(new String[]{"foo", "-sourcepath", "src", "-d"});
    assertEquals(result.getStatus(), ParseResult.SUCCEED);
    options = ((ParseSuccess)result).getOptions();
    noArgumentOptions = ((ParseSuccess)result).getNoArgumentOptions();
    arguments = ((ParseSuccess)result).getArguments();
    checkOptionNotNull(noArgumentOptions, "-sourcepath");
    checkOptionNotNull(noArgumentOptions, "-d");
    checkList(arguments, new String[]{"foo", "src"});
  }

  public TestCommandLineParser(String arg0) {
    super(arg0);
  }

}