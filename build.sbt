import sbt._
import Keys._
import classpath._
import java.util.jar.{Attributes, Manifest}

lazy val root = Project(
  id = "onion",
  base = file(".")
).settings(onionSettings:_*)

lazy val dist = TaskKey[Unit]("onion-dist")
lazy val distPath = SettingKey[File]("onion-dist-path")

def manifestExtra(artifact: File, classpath: Classpath) = {
  val libs = classpath.map(_.data).filter(ClasspathUtilities.isArchive) :+ artifact
  val mf = new Manifest
  mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, libs.map("lib/" + _.getName).mkString(" "))
  Package.JarManifest(mf)
}

def distTask(target: File, out: File, artifact: File, classpath: Classpath) = {
  val libs = classpath.map(_.data).filter(ClasspathUtilities.isArchive)
  val libdir = out / "lib"
  IO.createDirectory(libdir)
  val map = libs.map(source => (source.asFile, libdir / source.getName))
  IO.copy(map)
  IO.copyDirectory(file("bin"), out / "bin")
  IO.copyDirectory(file("run"), out / "run")
  IO.copyFile(artifact, out / "onion.jar")
  IO.copyFile(file("README.md"), out / "README.md")
  val files = (out ***).get.flatMap(f=> f.relativeTo(out).map(r=>(f, r.getPath)))
  IO.zip(files, target / "onion-dist.zip")
}

def javacc(classpath: Classpath, output: File, log: Logger): Seq[File] = {
  Fork.java(None,
    "-cp" ::
      Path.makeString(classpath.map(_.data)) ::
      List(
        "javacc",
        "-UNICODE_INPUT=true",
        "-JAVA_UNICODE_ESCAPE=true",
        "-OUTPUT_DIRECTORY=%s/onion/compiler/parser".format(output.toString),
        "grammar/JJOnionParser.jj"
      ),
    log
  ) match {
    case exitCode if exitCode != 0 => sys.error("Nonzero exit code returned from javacc: " + exitCode)
    case 0 =>
  }
  (output ** "*.java").get
}

lazy val onionSettings = Seq(
  version := "0.2-SNAPSHOT",
  scalaVersion := "2.12.1",
  name := "onion",
  organization := "org.onion_lang",
  unmanagedSourceDirectories in Compile := {
    (Seq((javaSource in Compile).value) ++ Seq((scalaSource in Compile).value) ++ Seq((sourceManaged in Compile).value))
  },
  scalacOptions ++= Seq("-encoding", "utf8", "-unchecked", "-deprecation", "-feature", "-language:implicitConversions", "-language:existentials"),
  javacOptions ++= Seq("-sourcepath", "src.lib", "-Xlint:unchecked", "-source", "1.8"),
  libraryDependencies += {
    val version = scalaVersion.value
    "org.scala-lang" % "scala-compiler" % version
  },
  libraryDependencies ++= Seq(
    "org.apache.bcel" % "bcel" % "6.0",
    "org.ow2.asm" % "asm" % "5.0.2",
    "net.java.dev.javacc" % "javacc" % "4.0",
    "junit" % "junit" % "4.7" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  ),
  sourceGenerators in Compile += Def.task {
    val cp = (externalDependencyClasspath in Compile).value
    val dir = (sourceManaged in Compile).value
    val s = streams.value
    val parser = dir / "java" / "onion" / "compiler" / "parser" / "JJOnionParser.java"
    val grammar = new java.io.File("grammar") / "JJOnionParser.jj"
    if(grammar.lastModified() > parser.lastModified()) {
      javacc(cp, dir / "java", s.log)
    } else {
      Seq()
    }
  }.taskValue,
  packageOptions in (Compile, packageBin) := {
    val main = mainClass.value
    val opts = (packageOptions in (Compile, packageBin)).value
    opts ++ main.map{m => Package.MainClass(m)}
  },
  packageOptions in (Compile, packageBin) := {
    val opts = (packageOptions in (Compile, packageBin)).value
    val a = (artifactPath in (Compile, packageBin)).value
    val cp = (dependencyClasspath in Runtime).value
    opts :+ manifestExtra(a, cp)
  },
  dist := {
    val t = target.value
    val out = (distPath in dist).value
    val p = (packageBin in Compile).value
    val cp = (fullClasspath in Runtime).value
    distTask(t, out, p, cp)
  },
  distPath in dist := {
    target.value / "dist"
  },
  mainClass := Some("onion.tools.CompilerFrontend"),
  assemblyJarName in assembly := "onion.jar"
)
