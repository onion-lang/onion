sealed trait A {
  val tag: Int 
}
object A {
  final val Tag1 = 1
  final val Tag2 = 2
}
case class Tag1() extends A {
  override val tag = A.Tag1
}
case class Tag2() extends A {
  override val tag = A.Tag2
}
object Hello {
  def main(args: Array[String]) {
    val a: A = Tag1()
    a.tag match {
      case A.Tag1 => println("A")
      case A.Tag2 => println("B")
    }
  }
}
