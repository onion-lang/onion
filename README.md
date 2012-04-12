# Onion - statically typed object-oriented programming language on JVM

Onion is an object-oriented programming language.  Codes of Onion compiles
into JVM class files.  Please refer Install.txt for installation.

## Usage of tools
### onionc
  
#### Usage:

    onionc [options] source files...
   
###  Available options:
+ classpath <classpath> Set classpath of source files in compilation.
+ encoding <encoding> Set encoding of source files.
+ -d <output directory> Set output directory of results.
+ -maxErrorReports <error count> Set the maximum number of comiplation errors reported.

Onionc compiles source files into class files in the directorys corresponding to module names
of source files rooted by "-d" option.  If "-d" is not specified, the value of "-d" is specified as the current directory.

For example, if source files which module name is "org.onion_lang"
is compiled, class files is generated in:
  Unix-like OS : org/onion_lang
  Windows: org\onion_lang
.
    
### onion
    
#### Usage:
    onionc [options] source files... [command line arguments]
    
#### Available optionsÅF
+ -classpath <classpath> Set classpath of source files in compilation.
+ -encoding <encoding> Set encoding of source files.
+ -maxErrorReports <error count> Set the maximum number of comiplation errors reported.
    
Onionc compiles source files into in-memory classfiles and execute them.  The entry
point is:
  If there is explicit class definitions and they have main methods,
  The main method of the class on the top.  Otherwise, the first statement
  in the toplevel is the entry point.
    
## Limitations

+ Some compilation-time checks is not implemented  (e.g.
  checking that abstract methods are implemented, checking that
  final methods are not overriden).  It maybe that the code compiled
  successfully will be rejected by class file verifier.
+	Currently, qualitiy of onionc implementation is not high.  The comiler
  crashes in some codes.  The source-codes in example directory is
  compiled and executed property.
+ There are some features that is supported partially.
  For example, finally clause in try-catch is not supported yet.

This software includes softwares developed by 
Apache Software Foundation (http://www.apache.org/).
