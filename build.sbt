name := "onion"

organization := "org.onion_lang"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

testOptions in Test += Tests.Argument("-u", "target/scalatest-reports")
