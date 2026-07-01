# Scripting & CLI Examples

Onion works well for command-line scripts and small automation tasks. This page shows practical patterns for argument parsing, process execution, and file I/O.

## Parsing Command-Line Arguments

Use the `Args` module to parse flags, options, and positional arguments.

```onion
val parsed = Args::parse(args)

val name: String = parsed.option("name", "World")
val count: Int = parsed.intOption("count", 1)
val verbose: Boolean = parsed.flag("verbose")
val rest: List[String] = parsed.positional()

if verbose {
  println("name=" + name + " count=" + count)
}

for var i: Int = 0; i < count; i = i + 1 {
  println("Hello, " + name + "!")
}
```

Run with:

```bash
onion greet.on --name Alice --count 3 --verbose
```

## Reading and Writing Files

Use `file"..."` resource literals or the `file(...)` function for dynamic paths.

```onion
val content: String = file("input.txt").text()
println("Read " + content.length() + " characters")

file("output.txt").write("Hello from Onion\n")
```

CSV files are also supported:

```onion
val rows: List[Map[String, String]] = file("data.csv").csvRows()
foreach row: Object in rows {
  val m = row as Map
  println("name=" + m.get("name") + " age=" + m.get("age"))
}
```

## Running Shell Commands

The `Proc` module makes it easy to run external programs and capture output.

```onion
val result = Proc::capture("git", "status")
if result.succeeded() {
  println(result.stdout())
} else {
  println("failed: " + result.stderr())
}
```

You can also run pipelines through the shell:

```onion
val sorted = Proc::capture("sh", "-c", "cat words.txt | sort | uniq -c")
println(sorted.stdout())
```

## Static Imports for Cleaner Scripts

Import individual static methods to avoid repeating the class name:

```onion
import { java.lang.Math::max; java.lang.Math::min; }

println(max(10, 20))
println(min(10, 20))
```

You can also import an entire class's static members:

```onion
import { java.lang.Math }

println(max(10, 20))
println(Math::PI)
```

## Complete Example: CLI + Config File

**`ConfigApp.on`** combines argument parsing with a YAML config file.

```onion
record ServerConfig(host: String, port: Int, debug: Boolean) derive!(Yaml)

def defaultConfig(): ServerConfig {
  return new ServerConfig("localhost", 8080, false)
}

val parsed = Args::parse(args)
val configPath: String = parsed.option("config", "")
val portOverride: Int = parsed.intOption("port", -1)
val debugFlag: Boolean = parsed.flag("debug")

val base: ServerConfig =
  if configPath.length() > 0 {
    val loaded = ServerConfig::fromYaml(file(configPath).text())
    if loaded != null { loaded } else { defaultConfig() }
  } else {
    defaultConfig()
  }

val port = if portOverride >= 0 { portOverride } else { base.port() }
val debug = if debugFlag { true } else { base.debug() }

println("host=" + base.host())
println("port=" + port)
println("debug=" + debug)
```

Run with:

```bash
onion ConfigApp.on --config server.yaml --port 9000 --debug
```

## Process Pipeline Example

**`ShellPipeline.on`** runs `wc`, `sort`, and `head` as a pipeline.

```onion
val inputPath = "words.txt"

val countResult = Proc::capture("wc", "-l", inputPath)
println("wc exit=" + countResult.status() + " out=" + countResult.stdout().trim())

val pipelineResult = Proc::capture("sh", "-c", "sort " + inputPath + " | head -n 3")
println(pipelineResult.stdout())
```

## Unit Converter with Extension Methods

**`UnitConverter.on`** uses extension methods to add unit conversions to `Double`.

```onion
extension Double {
  def celsiusToFahrenheit(): Double {
    return self * 9.0 / 5.0 + 32.0
  }
  def kilometersToMiles(): Double {
    return self * 0.621371
  }
  def rounded(decimals: Int): Double {
    val factor = Math::pow(10.0, decimals as Double)
    return (Math::round(self * factor) as Double) / factor
  }
}

val celsius = 25.0
println(celsius + "C = " + celsius.celsiusToFahrenheit().rounded(2) + "F")
```

## Next Steps

- [JSON & HTTP Examples](json-http.md) - Network and data format scripting
- [Error Handling Examples](error-handling.md) - Validate inputs and handle failures
- [Tools: Script Runner](../tools/script-runner.md) - Run scripts directly
