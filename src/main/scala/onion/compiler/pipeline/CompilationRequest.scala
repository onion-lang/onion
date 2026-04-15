package onion.compiler.pipeline

import onion.compiler.CompilerConfig
import onion.compiler.source.SourceHandle

final case class CompilationRequest(
  sources: Seq[SourceHandle],
  config: CompilerConfig
)
