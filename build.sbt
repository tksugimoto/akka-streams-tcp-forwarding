lazy val root = (project in file(".")).settings(
  inThisBuild(
    List(
      scalaVersion := "2.12.7",
    )),
  name := "TCP-Forwarding",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.17",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.17" % Test,
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  ),
  scalafmtOnCompile := true,
)

maintainer := "tksugimoto"
enablePlugins(JavaAppPackaging)
