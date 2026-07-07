package onion.compiler.tools

import onion.tools.doc.Markdown
import org.scalatest.funspec.AnyFunSpec

class MarkdownSpec extends AnyFunSpec {
  describe("Markdown.toHtml") {
    it("renders ATX headings") {
      val html = Markdown.toHtml("# Title\n\n## Sub")
      assert(html.contains("<h1>Title</h1>"))
      assert(html.contains("<h2>Sub</h2>"))
    }

    it("renders bold and italic") {
      val html = Markdown.toHtml("This is **bold** and *italic* and _also_.")
      assert(html.contains("<strong>bold</strong>"))
      assert(html.contains("<em>italic</em>"))
      assert(html.contains("<em>also</em>"))
    }

    it("renders inline code") {
      val html = Markdown.toHtml("Use `x + 1` here.")
      assert(html.contains("<code>x + 1</code>"))
    }

    it("renders a fenced code block") {
      val html = Markdown.toHtml("```onion\nval x = 1\n```")
      assert(html.contains("<pre><code"))
      assert(html.contains("val x = 1"))
      assert(html.contains("language-onion"))
    }

    it("renders unordered lists") {
      val html = Markdown.toHtml("- one\n- two")
      assert(html.contains("<ul>"))
      assert(html.contains("<li>one</li>"))
      assert(html.contains("<li>two</li>"))
    }

    it("renders ordered lists") {
      val html = Markdown.toHtml("1. first\n2. second")
      assert(html.contains("<ol>"))
      assert(html.contains("<li>first</li>"))
      assert(html.contains("<li>second</li>"))
    }

    it("renders links") {
      val html = Markdown.toHtml("See [Onion](https://example.com/onion).")
      assert(html.contains("""<a href="https://example.com/onion">Onion</a>"""))
    }

    it("escapes <, >, and &") {
      val html = Markdown.toHtml("a < b && c > d")
      assert(html.contains("<"))
      assert(html.contains(">"))
      assert(html.contains("&"))
      assert(!html.contains("a < b"))
    }

    it("wraps plain text in paragraphs") {
      val html = Markdown.toHtml("hello world")
      assert(html.contains("<p>hello world</p>"))
    }
  }
}
