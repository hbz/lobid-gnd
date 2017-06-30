name := """lobid-authorities"""
organization := "org.lobid"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.2"

libraryDependencies += guice

libraryDependencies += "com.github.jsonld-java" % "jsonld-java" % "0.10.0"

libraryDependencies += "com.github.jsonld-java" % "jsonld-java-jena" % "0.4.1"
