package onion.compiler

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 23:27:53
 * To change this template use File | Settings | File Templates.
 */

trait ProcessingUnit[A, B] {self =>
  type Environment
  def newEnvironment(source: A): Environment
  def doProcess(source: A, environment: Environment): B
  def pre(source: A) {}
  def post(source: A, result: B) {}

  final def process(source: A): B = {
    pre(source)
    val result = doProcess(source, newEnvironment(source))
    post(source, result)
    result
  }

  def andThen[C](nextUnit: ProcessingUnit[B, C]): ProcessingUnit[A, C] = new ProcessingUnit[A, C] {
    type Environment = Null
    def newEnvironment(source: A): Null = null
    def doProcess(source: A, environment: Null): C = nextUnit.process(self.process(source))
  }
}