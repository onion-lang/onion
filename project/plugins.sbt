addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.4.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
// Signs the artifacts staged for Maven Central (publishSigned). 2.3.x is the sbt 2 line
// (com.github.sbt:sbt-pgp_sbt2_3), built against sbt 2.0.0 / Scala 3.8.4 like sbt 2.0.6.
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.2")
