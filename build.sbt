lazy val root = (project in file(".")).settings(
  inThisBuild(
    List(
      scalaVersion := "2.12.7",
    )),
  name := "TCP-Forwarding",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.17",
  ),
  scalafmtOnCompile := true,
)
