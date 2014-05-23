unmanagedSourceDirectories in Compile <<= (Seq(javaSource in Compile) ++ Seq(scalaSource in Compile) ++ Seq(baseDirectory{ _  / "target" / "scala-2.11" / "src_managed" / "main" / "java" })).join
