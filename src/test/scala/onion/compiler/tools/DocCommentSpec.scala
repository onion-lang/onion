package onion.compiler.tools

import onion.tools.doc.DocComment
import org.scalatest.funspec.AnyFunSpec

class DocCommentSpec extends AnyFunSpec {
  describe("DocComment.parse") {
    it("extracts summary, param, and return tags") {
      val raw =
        """/**
          | * Adds two numbers together.
          | *
          | * More detail about the addition goes here.
          | *
          | * @param a the first addend
          | * @param b the second addend
          | * @return the sum of a and b
          | */""".stripMargin
      val doc = DocComment.parse(raw)
      assert(doc.summary == "Adds two numbers together.")
      assert(doc.body.contains("More detail about the addition"))
      assert(doc.params.map(_.arg) == List("a", "b"))
      assert(doc.params.head.desc == "the first addend")
      assert(doc.returns.isDefined)
      assert(doc.returns.get.desc == "the sum of a and b")
    }

    it("collects throws, see, since, deprecated, and author tags") {
      val raw =
        """/**
          | * Does something risky.
          | * @throws IllegalStateException when broken
          | * @see SomethingElse
          | * @since 1.2
          | * @deprecated use the other one
          | * @author Kota
          | */""".stripMargin
      val doc = DocComment.parse(raw)
      assert(doc.throwsTags.head.arg == "IllegalStateException")
      assert(doc.throwsTags.head.desc == "when broken")
      assert(doc.see.head.desc == "SomethingElse")
      assert(doc.since.get.desc == "1.2")
      assert(doc.deprecated.get.desc == "use the other one")
      assert(doc.author.head.desc == "Kota")
    }

    it("treats the whole first paragraph as summary when no tags precede a blank line") {
      val raw = "/** A one-line summary. */"
      val doc = DocComment.parse(raw)
      assert(doc.summary == "A one-line summary.")
      assert(doc.tags.isEmpty)
    }
  }
}
