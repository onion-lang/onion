# Onion - statically typed object-oriented programming language on JVM

Onion is an object-oriented and statically typed programming language. Source
codes of Onion compiles into JVM class files (in memory / file).

Originally, Onion was written in Java.  Recently, It has been rewritten in Scala
completely except Parser, using JavaCC.

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

#### Available options
+ -classpath <classpath> classpath of source files in compilation.
+ -encoding <encoding> encoding of source files.
+ -maxErrorReports <error count> the maximum number of comiplation errors reported.

Onionc compiles source files into in-memory classfiles and execute them.  The entry
point is:
1. A main method if there is an explicit class definition and it have the main method.
2. The main method of the class on the top.
3. Otherwise, the first statement on the top.

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

## BuildHive (Jenkins)

Onion uses BuildHive, Jenkins hosting service, for CI.  See the followings:

https://buildhive.cloudbees.com/view/My%20Repositories/job/kmizu/job/onion/

This software includes softwares developed by 
Apache Software Foundation (http://www.apache.org/).
