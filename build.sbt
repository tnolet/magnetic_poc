name := """magnetic"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"



libraryDependencies ++= {
  val sprayV = "1.3.1"
  val slickV = "0.8.0"
  Seq(
    jdbc,
    anorm,
    cache,
    ws,
    "com.typesafe.play" %% "play-slick" % slickV,
    "io.spray" % "spray-can" % sprayV,
    "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
    "com.typesafe.slick" %% "slick-codegen" % "2.1.0"
  )
}

