name := """magnetic"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"
//scalaVersion := "2.11.1"

resolvers += "Apache repo" at "https://repository.apache.org/content/repositories/releases"

libraryDependencies ++= {
  val sprayV = "1.3.1"
  val slickV = "0.8.0"
  val scalaKafkaV = "0.1.0.0"
  val curatorV = "2.6.0"
  Seq(
    jdbc,
    anorm,
    cache,
    ws,
    "com.typesafe.play" %% "play-slick" % slickV,
    "io.spray" % "spray-can" % sprayV,
    "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
    "com.typesafe.slick" %% "slick-codegen" % "2.1.0",
    "ly.stealth" % "scala-kafka" % scalaKafkaV,
    "com.sclasen" %% "akka-kafka" % "0.0.7",
    "org.slf4j" % "log4j-over-slf4j" % "1.6.6",
    "org.apache.curator" % "curator-x-discovery" % curatorV,
    "com.loopfor.zookeeper" %% "zookeeper-client" % "1.2.1"
  )
}

