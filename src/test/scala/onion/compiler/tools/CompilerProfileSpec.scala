package onion.compiler.tools

import onion.compiler.CompilationOutcome.Success
import onion.compiler._
import onion.compiler.pipeline.{CompileProfileFormat, CompileProfileSettings}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.StringReader
import java.nio.file.Files

class CompilerProfileSpec extends AnyFunSpec with Matchers {
  describe("compile profiling") {
    it("writes a JSON phase profile when enabled") {
      val output = Files.createTempFile("onion-compile-profile", ".json")
      val config = CompilerConfig(
        classPath = Seq("."),
        superClass = "",
        encoding = "UTF-8",
        outputDirectory = "",
        maxErrorReports = 10,
        compileProfile = CompileProfileSettings(
          enabled = true,
          format = CompileProfileFormat.Json,
          output = Some(output.toString)
        )
      )

      val compiler = new OnionCompiler(config)
      val result = compiler.compile(
        Seq(new StreamInputSource(new StringReader("""IO::println("profile")"""), "Profile.on"))
      )

      result shouldBe a [Success]
      val profileJson = Files.readString(output)
      profileJson should include ("\"sourceCount\":1")
      profileJson should include ("\"generatedClasses\":")
      profileJson should include ("\"phases\":[")
      profileJson should include ("\"name\":\"Parsing\"")
      profileJson should include ("\"name\":\"CodeGen\"")
    }
  }
}
