package onion.tools.readiness.benchmark

import java.nio.file.{Path, Paths}

enum BenchmarkOutputFormat:
  case Text
  case Json

final case class BenchmarkOptions(
  runConfig: BenchmarkRunConfig = BenchmarkRunConfig(),
  output: Path = Paths.get("target/readiness/benchmark-v1.json"),
  stdoutFormat: BenchmarkOutputFormat = BenchmarkOutputFormat.Text
)

object BenchmarkOptions:
  def parse(args: Array[String]): Either[String, BenchmarkOptions] =
    parseAt(args.toVector, 0, BenchmarkOptions())

  private def parseAt(
    args: Vector[String],
    index: Int,
    options: BenchmarkOptions
  ): Either[String, BenchmarkOptions] =
    if index >= args.size then Right(options)
    else
      args(index) match
        case "--iterations" =>
          value(args, index, "--iterations").flatMap { raw =>
            positiveInt(raw, "iterations").flatMap { count =>
              parseAt(
                args,
                index + 2,
                options.copy(
                  runConfig = options.runConfig.copy(measuredIterations = count)
                )
              )
            }
          }
        case "--warmups" =>
          value(args, index, "--warmups").flatMap { raw =>
            nonNegativeInt(raw, "warmups").flatMap { count =>
              parseAt(
                args,
                index + 2,
                options.copy(
                  runConfig = options.runConfig.copy(warmupIterations = count)
                )
              )
            }
          }
        case "--timeout-seconds" =>
          value(args, index, "--timeout-seconds").flatMap { raw =>
            positiveInt(raw, "timeout seconds").flatMap { seconds =>
              parseAt(
                args,
                index + 2,
                options.copy(
                  runConfig = options.runConfig.copy(
                    timeoutMillis = seconds.toLong * 1000L
                  )
                )
              )
            }
          }
        case "--output" =>
          value(args, index, "--output").flatMap { path =>
            parseAt(args, index + 2, options.copy(output = Paths.get(path)))
          }
        case "--format" =>
          value(args, index, "--format").flatMap {
            case "text" =>
              parseAt(
                args,
                index + 2,
                options.copy(stdoutFormat = BenchmarkOutputFormat.Text)
              )
            case "json" =>
              parseAt(
                args,
                index + 2,
                options.copy(stdoutFormat = BenchmarkOutputFormat.Json)
              )
            case other => Left(s"unsupported benchmark format: $other")
          }
        case "--json" =>
          parseAt(
            args,
            index + 1,
            options.copy(stdoutFormat = BenchmarkOutputFormat.Json)
          )
        case other => Left(s"unknown benchmark option: $other")

  private def value(
    args: Vector[String],
    index: Int,
    option: String
  ): Either[String, String] =
    args.lift(index + 1).toRight(s"missing value for $option")

  private def positiveInt(raw: String, label: String): Either[String, Int] =
    raw.toIntOption.filter(_ > 0).toRight(s"$label must be a positive integer")

  private def nonNegativeInt(raw: String, label: String): Either[String, Int] =
    raw.toIntOption.filter(_ >= 0).toRight(s"$label must be non-negative")
