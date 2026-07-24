import sbt.*
import sbt.internal.inc.classpath.*
import Keys.*
import sbt.internal.librarymanagement.StringUtilities

import java.util.jar.{Attributes, Manifest}

lazy val onion = (project in file(".")).settings(onionSettings:_*)

lazy val dist = taskKey[Unit]("Builds a runnable distribution under target/dist")

lazy val distPath = settingKey[File]("Output directory used by the dist task")

lazy val runScript = inputKey[Unit]("Runs the ScriptRunner with arguments")
lazy val bench = inputKey[Unit]("Runs the benchmark suite (compatibility alias)")
lazy val benchmark = inputKey[Unit]("Runs the versioned readiness benchmark suite")

fullRunInputTask(
  runScript,
  Compile,
  "onion.tools.ScriptRunner"
)

fullRunInputTask(
  bench,
  Compile,
  "onion.tools.BenchmarkRunner"
)

fullRunInputTask(
  benchmark,
  Compile,
  "onion.tools.BenchmarkRunner"
)

lazy val repl = inputKey[Unit]("Starts the interactive REPL")

fullRunInputTask(
  repl,
  Compile,
  "onion.tools.Repl"
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

def distTask(target: File, out: File, artifact: File, classpath: Classpath, version: String) = {
  val libs = classpath.map(_.data).filter(isArchive)
  val libdir = out / "lib"
  IO.createDirectory(libdir)
  val map = libs.map(source => (source.asFile, libdir / source.getName))
  IO.copy(map)
  IO.copyDirectory(file("bin"), out / "bin")
  IO.copyDirectory(file("run"), out / "run")
  (out / "bin" * "*").get
    .filterNot(_.getName.toLowerCase.endsWith(".bat"))
    .foreach(_.setExecutable(true, false))
  IO.copyFile(artifact, out / "onion.jar")
  IO.copyFile(file("README.md"), out / "README.md")
  val files = (out ** AllPassFilter).get.flatMap(f=> f.relativeTo(out).map(r=>(f, r.getPath)))
  IO.zip(files, target / s"onion-dist-$version.zip", Some(System.currentTimeMillis()))
}

def javacc(classpath: Classpath, output: File, log: Logger): Seq[File] = {
  val parserOutput = output / "onion" / "compiler" / "parser"
  // Regenerate from scratch. JavaCC overwrites same-named files but never removes
  // ones it no longer emits, so a grammar change that adds or removes a token would
  // otherwise leave orphaned *.java behind and break the build until the directory is
  // cleaned by hand. Wiping it first makes `.jj` edits self-sufficient under sbt.
  IO.delete(parserOutput)
  IO.createDirectory(parserOutput)
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
      s"-OUTPUT_DIRECTORY=${parserOutput.toString}",
      "grammar/JJOnionParser.jj"
    )
  ) match {
    case exitCode if exitCode != 0 => sys.error("Nonzero exit code returned from javacc: " + exitCode)
    case 0 =>
  }
  (output ** "*.java").get
}

lazy val onionSettings = Seq(
  scalaVersion := "3.3.7",
  name := "onion",
  organization := "org.onion_lang",
  // Generated parser sources come in via sourceGenerators (managedSources); listing
  // sourceManaged here too made them unmanaged as well, so the directory was scanned
  // before javacc's IO.delete ran and a regenerate-from-scratch tripped over the
  // already-scanned (now removed) files. Keep only the hand-written source roots.
  Compile / unmanagedSourceDirectories  := {
    Seq((Compile / javaSource).value, (Compile / scalaSource).value)
  },
  scalacOptions ++= Seq("-encoding", "utf8", "-unchecked", "-deprecation", "-feature", "-language:implicitConversions", "-language:existentials"),
  javacOptions ++= Seq("-sourcepath", "src.lib", "-Xlint:unchecked", "-source", "17"),
  libraryDependencies ++= Seq(
    "org.ow2.asm" % "asm" % "9.8",
    "org.ow2.asm" % "asm-commons" % "9.8",
    "org.ow2.asm" % "asm-tree" % "9.8",
    "org.ow2.asm" % "asm-util" % "9.8",
    "net.java.dev.javacc" % "javacc" % "5.0",
    "org.jline" % "jline" % "3.25.1",
    "org.eclipse.lsp4j" % "org.eclipse.lsp4j" % "0.21.1",
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
      // Up to date: still report the already-generated sources so they stay on the
      // compile source path. They are managedSources now (no longer also listed as
      // unmanaged), so an empty Seq() here would drop the parser from the build.
      (dir / "java" ** "*.java").get
    }
  }.taskValue,
  Compile / packageBin / packageOptions := {
    val main = (Compile / mainClass).value
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
    val v = version.value
    distTask(t, out, p, cp, v)
  },
  dist / distPath := {
    target.value / "dist"
  },
  Compile / mainClass := Some("onion.tools.CompilerFrontend"),
  assembly / assemblyJarName := s"onion-${version.value}.jar",
  assembly / assemblyMergeStrategy := {
    case "module-info.class" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

run / fork := true
run / connectInput := true
repl / fork := true
repl / connectInput := true
