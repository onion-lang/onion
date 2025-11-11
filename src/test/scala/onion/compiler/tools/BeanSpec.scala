package onion.compiler.tools

import onion.tools.Shell

class BeanSpec extends AbstractShellSpec {
  describe("Bean class with setter function as member reference") {
    it("compiles") {
      val resultBean = shell.run(
        """
          | class Bean <: Serializable {
          |   value :Int
          | public:
          |   def this {
          |   }
          |
          |   def this(value :Int){
          |     this.value = value
          |   }
          |
          |   def getValue :Int = this.value
          |
          |   def setValue(value :Int) {
          |     this.value = value
          |   }
          |
          |   def toString :String = "Bean(value = " + this.value + ")"
          |
          |   static def main(args: String[]): String {
          |     bean = new Bean
          |     bean.value = 200 // The reference of setter functions!
          |     return JInteger::toString(bean.value)
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("200") == resultBean)
    }
  }
}