package onion.compiler

trait ProcessingUnit[A, B] {self =>
  type Environment
  def newEnvironment(source: A): Environment
  def doProcess(source: A, environment: Environment): B
  def pre(source: A): Unit = {}
  def post(source: A, result: B): Unit = {}

  final def process(source: A): B = {
    pre(source)
    val result = doProcess(source, newEnvironment(source))
    post(source, result)
    result
  }

  def andThen[C](nextUnit: ProcessingUnit[B, C]): ProcessingUnit[A, C] = new ProcessingUnit[A, C] {
    type Environment = Null
    def newEnvironment(source: A): Environment = null
    def doProcess(source: A, environment: Null): C = nextUnit.process(self.process(source))
  }
}