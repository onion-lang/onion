package onion.compiler.diagnostics

import onion.compiler.Location

trait Diagnostic {
  def sourceFile: String
  def location: Location
  def message: String
  def code: Option[String]
}
