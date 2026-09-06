package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

import java.lang.reflect.Modifier

/**
 * `onion.View` is a pure internal adapter used only by `Iterables.mapMap` to view a
 * `Map` as a mutable `Collection[Map.Entry]` (see src/main/java/onion/Iterables.java).
 * Unlike every other class under src/main/java/onion, it is not part of the documented
 * stdlib surface (no mention in docs/reference/stdlib.md or the Japanese translation),
 * has no dedicated coverage spec, and is referenced from nowhere outside the `onion`
 * package -- so it has no reason to be public. This guards the narrowed visibility so
 * it does not silently widen back out.
 */
class ViewInternalVisibilitySpec extends AnyFunSpec {

  private val viewClass = Class.forName("onion.View")

  it("keeps onion.View package-private, not a public stdlib class") {
    assert(
      !Modifier.isPublic(viewClass.getModifiers),
      "onion.View is a Map-to-Collection adapter used only by onion.Iterables in the " +
        "same package; it should not be public API"
    )
  }

  it("keeps onion.View#asCollection package-private, not a public stdlib method") {
    val method = viewClass.getDeclaredMethod("asCollection", classOf[java.util.Map[_, _]])
    assert(
      !Modifier.isPublic(method.getModifiers),
      "onion.View#asCollection should not be public API"
    )
  }
}
