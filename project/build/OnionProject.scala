import sbt._
import sbt.{FileUtilities => Files}

class OnionProject(info: ProjectInfo) extends DefaultProject(info)
{
  private final val LOGGER = new ConsoleLogger
  override def compileOptions = super.compileOptions ++ compileOptions("-encoding", "utf8")
  override def javaCompileOptions = super.javaCompileOptions ++ javaCompileOptions("-sourcepath", "src.lib", "-source", "1.5")
  lazy val grammar = task {
    Run.run("javacc", List("lib"/"javacc-4.0"/"javacc.jar"), List("-UNICODE_INPUT=true", "-JAVA_UNICODE_ESCAPE=true", "-OUTPUT_DIRECTORY=src/main/parser/onion/compiler/parser", "grammar/JJOnionParser.jj"), LOGGER)
  }
  override def mainClass = Some("onion.tools.OnionCompilerFrontend")
  override def manifestClassPath = Some("lib/bcel.jar lib/scala-compiler.jar lib/scala-library.jar")
  override lazy val compile = compileAction dependsOn(grammar) describedAs("compile java and scala sources")
  override lazy val clean =  cleanAction && task { Files.clean(Path.fromFile("src/main/parser/onion"), LOGGER); None }
  override lazy val `package` = {
    packageAction && task {
      val currentTime = System.currentTimeMillis()
      val installPath = Path.fromFile(system[String]("install.path").get match {
        case Some(path) => path
        case None => readLine("install path: ")
      })
      val installLibPath = installPath/"lib"
      val installBinPath = installPath/"bin"

      val binPath = "."/"bin"
      val libPath = "."/"lib"
      val scalaLibPath = libPath/"scala"
      val bcelLibPath = libPath/"bcel"

      if(!installPath.exists){
        Files.createDirectory(installPath, LOGGER)
      }else if(jarPath.lastModified > installPath.lastModified) {
        Files.clean(installPath, LOGGER)
        Files.createDirectory(installPath, LOGGER)
      }
      Files.copyDirectory("."/"bin", installPath/"bin", LOGGER)
      Files.copyDirectory("."/"example", installPath/"example", LOGGER)
      Files.copyFile(jarPath, installPath/"onion.jar", LOGGER)
      for(libFile <- List(bcelLibPath/"bcel.jar", scalaLibPath/"scala-library.jar", scalaLibPath/"scala-compiler.jar")){
        Files.copyFile(libFile, installLibPath/libFile.name, LOGGER)
      }
      Files.copy(List("."/"README.markdown"), installPath, true, LOGGER)
      None
    }
  }
  override lazy val packageProject = packageProjectAction dependsOn(clean) describedAs("package all files contained in this project except generated files")
  override def mainSourceRoots = super.mainSourceRoots +++ "src"/"main"/"parser" +++ "src"/"main"/"onion_lib"
  lazy val packageBinaryProject = task {
    println("implement this")
    None
  }
}