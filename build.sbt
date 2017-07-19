name := """lobid-authorities"""
organization := "org.lobid"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.2"

libraryDependencies += guice

libraryDependencies += "com.github.jsonld-java" % "jsonld-java" % "0.11.0-SNAPSHOT"

libraryDependencies += "com.github.jsonld-java" % "jsonld-java-jena" % "0.4.1"

libraryDependencies += "org.culturegraph" % "metafacture-core" % "4.0.0"

libraryDependencies += "org.elasticsearch" % "elasticsearch" % "2.4.5"

resolvers += Resolver.mavenLocal
