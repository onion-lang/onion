package onion.compiler.tools

import onion.{Cli, Outcome}
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters._

/**
 * Every failure path in `onion.Cli` called `System.exit`, so a CLI error was uncatchable,
 * unrecoverable, and untestable in-process. The existing specs say so out loud and route
 * around it — `CliParseSpec` covers only the paths that succeed, and `CliErrorMessageSpec`
 * has four cases, all happy-path, because asserting on an error message meant killing the
 * test JVM.
 *
 * `tryParse` is the same parse reporting defects instead of exiting, so the error paths
 * are finally reachable from a test. These are the cases that could not be written before.
 */
class CliTryParseSpec extends AnyFunSpec {

  private def defects(args: Array[String], spec: String): List[String] =
    Cli.tryParse(args, spec) match {
      case bad: Outcome.Bad[Array[String]] @unchecked => bad.defects.asScala.map(_.describe).toList
      case _                                          => Nil
    }

  private def parsed(args: Array[String], spec: String): List[String] =
    Cli.tryParse(args, spec).get.toList

  describe("the paths that used to kill the JVM") {
    it("reports an unknown option") {
      val ds = defects(Array("--nope"), "name")
      assert(ds.size == 1)
      assert(ds.head.contains("--nope"), ds.head)
    }

    it("reports a value flag given no value") {
      val ds = defects(Array("--count"), "count=")
      assert(ds.head.contains("--count"), ds.head)
      assert(ds.head.contains("a value"), ds.head)
    }

    it("reports a missing positional, by name") {
      val ds = defects(Array.empty[String], "path")
      assert(ds.head.contains("path"), ds.head)
    }

    it("reports every missing positional at once") {
      // Exiting on the first one meant you fixed them one run at a time.
      assert(defects(Array.empty[String], "src,dst").size == 2)
    }

    it("reports an unexpected extra argument") {
      val ds = defects(Array("a", "b"), "only")
      assert(ds.head.contains("b"), ds.head)
    }

    it("surfaces --help rather than printing and exiting") {
      val ds = defects(Array("--help"), "name,count=,loud?")
      assert(ds.head.contains("usage:"), ds.head)
      assert(ds.head.contains("--count"), ds.head)
    }
  }

  describe("the paths that already worked still do") {
    it("binds positionals, flags and switches") {
      assert(parsed(Array("f.txt", "--count", "3", "--loud"), "path,count=,loud?") ==
        List("f.txt", "3", "true"))
    }

    it("accepts the GNU --name=value form") {
      assert(parsed(Array("f.txt", "--count=3"), "path,count=") == List("f.txt", "3"))
    }

    it("leaves an absent optional as null") {
      assert(parsed(Array("f.txt"), "path,count=") == List("f.txt", null))
    }

    it("agrees with the exiting parse on a successful run") {
      val viaTry = parsed(Array("f.txt", "--count=3"), "path,count=")
      val viaParse = Cli.parse(Array("f.txt", "--count=3"), "path,count=").toList
      assert(viaTry == viaParse)
    }
  }
}
