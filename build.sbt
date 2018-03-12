name := """lobid-gnd"""
organization := "org.lobid"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

scalaVersion := "2.12.2"

libraryDependencies += guice

libraryDependencies += "com.github.jsonld-java" % "jsonld-java" % "0.11.1"

libraryDependencies += "com.github.jsonld-java" % "jsonld-java-jena" % "0.4.1"

libraryDependencies += "org.culturegraph" % "metafacture-core" % "4.0.0"

libraryDependencies += "org.elasticsearch" % "elasticsearch" % "2.4.5"

libraryDependencies += "org.dspace" % "oclc-harvester2" % "0.1.12"

libraryDependencies += "xalan" % "xalan" % "2.7.2"

resolvers += Resolver.mavenLocal
