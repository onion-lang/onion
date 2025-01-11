import sbt.*
import sbt.internal.inc.classpath.*
import Keys.*
import sbt.internal.librarymanagement.StringUtilities

import java.util.jar.{Attributes, Manifest}

lazy val onion = (project in file(".")).settings(onionSettings:_*)

lazy val dist = TaskKey[Unit]("onion-dist")

lazy val distPath = SettingKey[File]("onion-dist-path")

lazy val runScript = inputKey[Unit]("Runs the ScriptRunner with arguments")

fullRunInputTask(
  runScript,
  Compile,
  "onion.tools.ScriptRunner"
)

def isArchive(file: File): Boolean = {
  val fileName = file.getName
  val lc = fileName.toLowerCase
  lc.endsWith(".jar") || lc.endsWith(".zip") ||
    lc.endsWith(".war") || lc.endsWith(".ear")
}

def manifestExtra(artifact: File, classpath: Classpath) = {
  val libs = classpath.map(_.data).filter(isArchive) :+ artifact
  val mf = new Manifest
  mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, libs.map("lib/" + _.getName).mkString(" "))
  Package.JarManifest(mf)
}

def distTask(target: File, out: File, artifact: File, classpath: Classpath) = {
  val libs = classpath.map(_.data).filter(isArchive)
  val libdir = out / "lib"
  IO.createDirectory(libdir)
  val map = libs.map(source => (source.asFile, libdir / source.getName))
  IO.copy(map)
  IO.copyDirectory(file("bin"), out / "bin")
  IO.copyDirectory(file("run"), out / "run")
  IO.copyFile(artifact, out / "onion.jar")
  IO.copyFile(file("README.md"), out / "README.md")
  val files = (out ** AllPassFilter).get.flatMap(f=> f.relativeTo(out).map(r=>(f, r.getPath)))
  IO.zip(files, target / "onion-dist.zip", Some(System.currentTimeMillis()))
}

def javacc(classpath: Classpath, output: File, log: Logger): Seq[File] = {
  Fork.java(
    ForkOptions().withOutputStrategy(
      OutputStrategy.LoggedOutput(log)
    ),
    "-cp" ::
    Path.makeString(classpath.map(_.data)) ::
    List(
      "javacc",
      "-UNICODE_INPUT=true",
      "-JAVA_UNICODE_ESCAPE=true",
      "-BUILD_TOKEN_MANAGER=true",
      "-OUTPUT_DIRECTORY=%s/onion/compiler/parser".format(output.toString),
      "grammar/JJOnionParser.jj"
    )
  ) match {
    case exitCode if exitCode != 0 => sys.error("Nonzero exit code returned from javacc: " + exitCode)
    case 0 =>
  }
  (output ** "*.java").get
}

lazy val onionSettings = Seq(
  version := "0.2.0-SNAPSHOT",
  scalaVersion := "3.6.2",
  name := "onion",
  organization := "org.onion_lang",
  Compile / unmanagedSourceDirectories  := {
    (Seq((Compile / javaSource).value) ++ Seq((Compile / scalaSource).value) ++ Seq((Compile / sourceManaged).value))
  },
  scalacOptions ++= Seq("-encoding", "utf8", "-unchecked", "-deprecation", "-feature", "-language:implicitConversions", "-language:existentials"),
  javacOptions ++= Seq("-sourcepath", "src.lib", "-Xlint:unchecked", "-source", "21"),
  libraryDependencies ++= Seq(
    "org.apache.bcel" % "bcel" % "6.0",
    "org.ow2.asm" % "asm" % "5.0.2",
    "net.java.dev.javacc" % "javacc" % "5.0",
    "junit" % "junit" % "4.7" % "test",
    "org.scalatest" %% "scalatest" % "3.2.19" % "test"
  ),
  Compile / sourceGenerators += Def.task {
    val cp = (Compile / externalDependencyClasspath).value
    val dir = (Compile / sourceManaged).value
    val s = streams.value
    val parser = dir / "java" / "onion" / "compiler" / "parser" / "JJOnionParser.java"
    val grammar = new java.io.File("grammar") / "JJOnionParser.jj"
    if(grammar.lastModified() > parser.lastModified()) {
      javacc(cp, dir / "java", s.log)
    } else {
      Seq()
    }
  }.taskValue,
  Compile / packageBin / packageOptions := {
    val main = mainClass.value
    val opts = (Compile / packageBin / packageOptions).value
    opts ++ main.map{m => Package.MainClass(m)}
  },
  Compile / packageBin / packageOptions := {
    val opts = (Compile / packageBin / packageOptions).value
    val a = (Compile / packageBin / artifactPath).value
    val cp = (Runtime / dependencyClasspath).value
    opts :+ manifestExtra(a, cp)
  },
  dist := {
    val t = target.value
    val out = (dist / distPath).value
    val p = (Compile / packageBin).value
    val cp = (Runtime / fullClasspath).value
    distTask(t, out, p, cp)
  },
  dist / distPath := {
    target.value / "dist"
  },
  mainClass := Some("onion.tools.CompilerFrontend"),
  assembly / assemblyJarName := "onion.jar"
)

fork in run := true