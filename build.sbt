name := """lobid-gnd"""
organization := "org.lobid"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

scalaVersion := "2.12.4"

libraryDependencies += guice

libraryDependencies += ws

libraryDependencies += "com.github.jsonld-java" % "jsonld-java" % "0.12.0"

libraryDependencies += "javax.mail" % "mail" % "1.4.1"

libraryDependencies += "org.apache.jena" % "apache-jena-libs" % "3.7.0"

libraryDependencies += "org.metafacture" % "metafacture-framework" % "5.4.0"

libraryDependencies += "org.metafacture" % "metafacture-flowcontrol" % "5.4.0"

libraryDependencies += "org.metafacture" % "metafacture-io" % "5.4.0"

libraryDependencies += "org.metafacture" % "metafacture-xml" % "5.4.0"

libraryDependencies += "org.metafacture" % "metafacture-elasticsearch" % "5.4.0"

libraryDependencies += "org.elasticsearch" % "elasticsearch" % "5.6.3"

libraryDependencies += "org.elasticsearch.client" % "transport" % "5.6.3"

libraryDependencies += "org.dspace" % "oclc-harvester2" % "1.0.0"

libraryDependencies += "org.jooq" % "joox-java-6" % "1.6.0"

libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.16.0"

libraryDependencies += "org.apache.logging.log4j" % "log4j-1.2-api" % "2.16.0"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.16.0"

libraryDependencies += "org.hamcrest" % "hamcrest-library" % "1.3" % Test

resolvers += Resolver.mavenLocal

trapExit := false
