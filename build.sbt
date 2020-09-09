name := "akka-streams-demo"
organization := "com.inovatrend"
version := "1.0.0"
maintainer := "ivan.turcinovic@inovatrend.com"

scalaVersion := "2.13.3"
val akkaVersion = "2.6.5"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.2"
libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-elasticsearch" % "2.0.1"
libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % "2.0.0"

val circeVersion = "0.12.3"
libraryDependencies += "io.circe" %% "circe-core" % circeVersion
libraryDependencies += "io.circe" %% "circe-generic" % circeVersion
libraryDependencies += "io.circe" %% "circe-parser" % circeVersion

val slf4jVersion: String = "1.7.23"
val logbackVersion: String = "1.2.3"
libraryDependencies += "org.slf4j" % "slf4j-api" % slf4jVersion
libraryDependencies += "org.slf4j" % "jcl-over-slf4j" % slf4jVersion
libraryDependencies += "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
libraryDependencies += "ch.qos.logback" % "logback-classic" % logbackVersion
libraryDependencies += "ch.qos.logback" % "logback-core" % logbackVersion
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.11"
