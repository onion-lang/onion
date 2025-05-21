package onion.compiler

/**
 * Entry point for byte code generation from typed AST.
 * Internally this delegates to the existing CodeGeneration that expects
 * IRT nodes.  By reusing the implementation we can gradually transition
 * away from the old intermediate layer.
 */
class TypedCodeGeneration(config: CompilerConfig):
  private val backend = new CodeGeneration(config)

  def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass] =
    backend.process(classes.asInstanceOf[Seq[IRT.ClassDefinition]])

