package onion.tools.project

final case class ProjectError(message: String, cause: Option[Throwable] = None)
