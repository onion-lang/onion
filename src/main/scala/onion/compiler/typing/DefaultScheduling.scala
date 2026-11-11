package onion.compiler.typing

/**
 * The order in which default arguments are typed (#2653, rule on the issue after three panel
 * blocks on #2798):
 *
 *  1. Held back: a default that mentions a top-level `val`/`var`, or calls a method or constructor
 *     whose default is held back, repeating until nothing new is held back. It is not run here;
 *     the body pass types it in source order, as before #2653.
 *  2. Every other default is typed before any body, in dependency order: a default that calls
 *     `g` is typed after `g`'s own defaults. The members of a cycle are typed in declaration
 *     order, after everything the cycle depends on.
 *
 * Matching is by name, so a false match only keeps a default in source order.
 */
private[compiler] object DefaultScheduling {
  /**
   * One declaration's defaults: its name, the names they mention, the top-level `val`/`var`s
   * among them, how to mark the declaration held back, and how to type them. `hold` gets the
   * top-level `val`/`var`s at the root of the hold and the held-back functions in between, in
   * the order they must be declared, for the E0005/E0021 hint (panel round 9 on #2798).
   */
  final case class Job(name: String, mentions: Set[String], topLevelReads: Set[String], hold: (Seq[String], Seq[String]) => Unit, run: () => Unit) {
    def readsTopLevel: Boolean = topLevelReads.nonEmpty
  }

  /** Runs the jobs that are not held back, each after the jobs it depends on; `enter` runs before each job. */
  def run[U](jobs: Seq[(U, Job)])(enter: U => Unit): Unit = {
    var held = jobs.filter { case (_, j) => j.readsTopLevel }.map(_._2).toSet
    // A job is held back by a mention of any *other* held-back job's name; that includes a
    // same-named overload or constructor, but not the job itself (panel round 7 on #2798).
    var grew = true
    while (grew) {
      val more = jobs.collect { case (_, j) if !held.contains(j) && heldBy(j, held).nonEmpty => j }
      grew = more.nonEmpty
      held ++= more
    }
    markHeld(jobs.map(_._2).filter(held.contains), held)
    val ready = jobs.filterNot { case (_, j) => held.contains(j) }
    stronglyConnected(ready).foreach { component =>
      // Within a cycle, declaration order, as every default was typed before #2653 (panel round
      // 8 on #2798): `def a(x = b(1))` / `def b(y = a())` types `a` first, so `b`'s `a()` can omit x.
      component.foreach { case (unit, job) => enter(unit); job.run() }
    }
  }

  /**
   * The jobs' strongly connected components under "mentions the name of", each in declaration
   * order, a component after every component it depends on (Tarjan's algorithm).
   */
  private def stronglyConnected[U](ready: Seq[(U, Job)]): Seq[Seq[(U, Job)]] = {
    val position = ready.map(_._2).zipWithIndex.toMap
    val byName = ready.groupBy(_._2.name)
    def dependencies(job: Job): Seq[(U, Job)] =
      job.mentions.toSeq.sorted.flatMap(name => byName.getOrElse(name, Seq())).filter(_._2 ne job)
    val index = scala.collection.mutable.Map.empty[Job, Int]
    val low = scala.collection.mutable.Map.empty[Job, Int]
    val stack = scala.collection.mutable.Stack.empty[(U, Job)]
    val onStack = scala.collection.mutable.Set.empty[Job]
    val components = Seq.newBuilder[Seq[(U, Job)]]
    def connect(entry: (U, Job)): Unit = {
      val job = entry._2
      index(job) = index.size
      low(job) = index(job)
      stack.push(entry)
      onStack += job
      for (dependency <- dependencies(job)) {
        val other = dependency._2
        if (!index.contains(other)) { connect(dependency); low(job) = low(job) min low(other) }
        else if (onStack.contains(other)) low(job) = low(job) min index(other)
      }
      if (low(job) == index(job)) {
        val members = Seq.newBuilder[(U, Job)]
        var top = stack.pop()
        onStack -= top._2
        members += top
        while (top._2 ne job) { top = stack.pop(); onStack -= top._2; members += top }
        components += members.result().sortBy(e => position(e._2))
      }
    }
    ready.foreach(entry => if (!index.contains(entry._2)) connect(entry))
    components.result()
  }

  /**
   * Marks each held-back job with what a call above it must move: the top-level `val`/`var`s
   * it reaches through held-back defaults, and the held-back functions on the way, each after
   * the ones its default calls. Following "declare g above this call" alone gave E0002 when
   * g's default read `base` (panel round 9 on #2798).
   */
  private def markHeld(order: Seq[Job], held: Set[Job]): Unit = {
    val byName = order.groupBy(_.name)
    def deps(job: Job): Seq[Job] = heldBy(job, held).toSeq.flatMap(byName.getOrElse(_, Seq()))
    val roots = scala.collection.mutable.Map.from(order.map(j => j -> j.topLevelReads))
    val via = scala.collection.mutable.Map.from(order.map(j => j -> Set.empty[String]))
    // Depth: how many held-back functions lie between a job and a top-level read. A cycle
    // stops growing after `order.size` rounds.
    val depth = scala.collection.mutable.Map.from(order.map(j => j -> 0))
    var changed = true
    var rounds = 0
    while (changed && rounds <= order.size) {
      changed = false
      rounds += 1
      for (job <- order; dep <- deps(job)) {
        val r = roots(job) ++ roots(dep)
        val v = via(job) ++ via(dep) + dep.name - job.name
        val d = depth(job) max (depth(dep) + 1)
        if (r != roots(job) || v != via(job) || d != depth(job)) {
          roots(job) = r; via(job) = v; depth(job) = d; changed = true
        }
      }
    }
    val depthOf = order.groupMapReduce(_.name)(depth)(_ max _)
    order.foreach { job =>
      job.hold(roots(job).toSeq.sorted, via(job).toSeq.sortBy(n => (depthOf.getOrElse(n, 0), n)))
    }
  }

  /** The names `job` mentions that belong to a held-back job other than itself. */
  private def heldBy(job: Job, held: Set[Job]): Set[String] =
    job.mentions & held.collect { case other if other ne job => other.name }
}
