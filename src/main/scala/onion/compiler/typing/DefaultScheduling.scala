package onion.compiler.typing

/**
 * The order in which default arguments are typed (#2653, rule on the issue after three panel
 * blocks on #2798):
 *
 *  1. Held back: a default that mentions a top-level `val`/`var`, or calls a method or constructor
 *     whose default is held back, repeating until nothing new is held back. It is not run here;
 *     the body pass types it in source order, as before #2653.
 *  2. Every other default is typed before any body, in dependency order: a default that calls
 *     `g` is typed after `g`'s own defaults. A cycle falls back to declaration order.
 *
 * Matching is by name, so a false match only keeps a default in source order.
 */
private[compiler] object DefaultScheduling {
  /**
   * One declaration's defaults: its name, the names they mention, the top-level `val`/`var`s
   * among them, how to mark the declaration held back (with the names that hold it, for the
   * E0005/E0021 hint), and how to type them.
   */
  final case class Job(name: String, mentions: Set[String], topLevelReads: Set[String], hold: Set[String] => Unit, run: () => Unit) {
    def readsTopLevel: Boolean = topLevelReads.nonEmpty
  }

  /** Runs the jobs that are not held back, each after the jobs it depends on; `enter` runs before each job. */
  def run[U](jobs: Seq[(U, Job)])(enter: U => Unit): Unit = {
    var held = jobs.filter { case (_, j) => j.readsTopLevel }.map(_._2).toSet
    held.foreach(j => j.hold(j.topLevelReads))
    // A job is held back by a mention of any *other* held-back job's name; that includes a
    // same-named overload or constructor, but not the job itself (panel round 7 on #2798).
    var grew = true
    while (grew) {
      val more = jobs.collect { case (_, j) if !held.contains(j) && heldBy(j, held).nonEmpty => j }
      more.foreach(j => j.hold(heldBy(j, held)))
      grew = more.nonEmpty
      held ++= more
    }
    val ready = jobs.filterNot { case (_, j) => held.contains(j) }
    val byName = ready.groupBy(_._2.name)
    val visiting = scala.collection.mutable.Set.empty[Job]
    val done = scala.collection.mutable.Set.empty[Job]
    def visit(entry: (U, Job)): Unit = {
      val (unit, job) = entry
      if (!done.contains(job) && !visiting.contains(job)) {
        visiting += job
        for (name <- job.mentions.toSeq.sorted; dependency <- byName.getOrElse(name, Seq()) if dependency._2 ne job) visit(dependency)
        enter(unit)
        job.run()
        done += job
      }
    }
    ready.foreach(visit)
  }

  /** The names `job` mentions that belong to a held-back job other than itself. */
  private def heldBy(job: Job, held: Set[Job]): Set[String] =
    job.mentions & held.collect { case other if other ne job => other.name }
}
