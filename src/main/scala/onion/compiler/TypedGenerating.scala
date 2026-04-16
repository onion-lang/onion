package onion.compiler

import onion.compiler.codegen.legacy.TypedGeneratingBridge

/**
 * Legacy public facade for callers that still instantiate
 * `onion.compiler.TypedGenerating` directly.
 *
 * The actual bridge implementation now lives under
 * `onion.compiler.codegen.legacy.TypedGeneratingBridge`.
 */
class TypedGenerating(config: CompilerConfig)
  extends TypedGeneratingBridge(config)
