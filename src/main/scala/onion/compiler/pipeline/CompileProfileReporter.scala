package onion.compiler.pipeline

import java.io.{File, PrintStream}

object CompileProfileReporter {
  def report(profile: CompileProfile, settings: CompileProfileSettings): Unit = {
    if (!settings.enabled) return
    settings.output match
      case Some(path) if path.equalsIgnoreCase("stdout") =>
        Console.out.println(render(profile, settings.format))
      case Some(path) if path.equalsIgnoreCase("stderr") =>
        Console.err.println(render(profile, settings.format))
      case Some(path) =>
        writeToFile(path, render(profile, settings.format))
      case None =>
        Console.err.println(render(profile, settings.format))
  }

  def render(profile: CompileProfile, format: CompileProfileFormat): String =
    format match
      case CompileProfileFormat.Text => renderText(profile)
      case CompileProfileFormat.Json => renderJson(profile)

  def renderVerbose(profile: CompileProfile): String =
    val builder = new StringBuilder
    profile.phases.foreach { phase =>
      builder.append(f"[verbose] ${phase.name}: ${phase.elapsedMillis}%.2fms")
      builder.append(System.lineSeparator())
    }
    builder.append(f"[verbose] Total: ${profile.totalElapsedMillis}%.2fms (${profile.sourceCount} source files)")
    builder.toString()

  private def renderText(profile: CompileProfile): String = {
    val builder = new StringBuilder
    builder.append("compile-profile").append(System.lineSeparator())
    builder.append(s"  sources: ${profile.sourceCount}").append(System.lineSeparator())
    builder.append(s"  classpath-entries: ${profile.classpathSize}").append(System.lineSeparator())
    builder.append(s"  generated-classes: ${profile.generatedClasses}").append(System.lineSeparator())
    builder.append("  phases:").append(System.lineSeparator())
    profile.phases.foreach { phase =>
      builder.append(f"    - ${phase.name}%-18s ${phase.elapsedMillis}%.2fms (in=${phase.inputCount}, out=${phase.outputCount})")
      builder.append(System.lineSeparator())
    }
    builder.append(f"  total: ${profile.totalElapsedMillis}%.2fms")
    builder.toString()
  }

  private def renderJson(profile: CompileProfile): String = {
    val phases = profile.phases.map { phase =>
      s"""{"name":"${escape(phase.name)}","elapsedNanos":${phase.elapsedNanos},"elapsedMillis":${phase.elapsedMillis},"inputCount":${phase.inputCount},"outputCount":${phase.outputCount}}"""
    }.mkString("[", ",", "]")

    s"""{"sourceCount":${profile.sourceCount},"classpathSize":${profile.classpathSize},"generatedClasses":${profile.generatedClasses},"totalElapsedNanos":${profile.totalElapsedNanos},"totalElapsedMillis":${profile.totalElapsedMillis},"phases":$phases}"""
  }

  private def writeToFile(path: String, content: String): Unit = {
    val file = new File(path)
    val parent = file.getParentFile
    if (parent != null) parent.mkdirs()
    val out = new PrintStream(file)
    try out.print(content)
    finally out.close()
  }

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
}
