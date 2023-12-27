package onion.compiler

trait Processor[A, B] {self =>
  type Environment
  def newEnvironment(source: A): Environment
  protected def processBody(source: A, environment: Environment): B
  protected def preprocess(source: A): Unit = {}
  protected def postprocess(source: A, result: B): Unit = {}

  final def process(source: A): B = {
    preprocess(source)
    val result = processBody(source, newEnvironment(source))
    postprocess(source, result)
    result
  }

  def andThen[C](nextUnit: Processor[B, C]): Processor[A, C] = new Processor[A, C] {
    type Environment = Null
    def newEnvironment(source: A): Environment = null
    def processBody(source: A, environment: Null): C = nextUnit.process(self.process(source))
  }
}