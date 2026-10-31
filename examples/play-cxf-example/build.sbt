name := "play-cxf-example"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.18"
crossScalaVersions := Seq("2.13.18", "3.3.9")

val CxfVersion = "4.1.9"

resolvers ++= Seq(
  Resolver.sonatypeCentralSnapshots
)

updateOptions := updateOptions.value.withLatestSnapshots(true)

libraryDependencies ++= Seq(
  guice,

  "org.apache.cxf" % "cxf-core"                 % CxfVersion,
  "org.apache.cxf" % "cxf-rt-frontend-jaxws"    % CxfVersion,
  "org.apache.cxf" % "cxf-rt-transports-http"   % CxfVersion,

  "eu.sipria.play" %% "play-guice-cxf_play30" % "2.0.0" /* changing() */,

  "org.scalatest"           %% "scalatest"          % "3.2.20"  % Test,
  "org.scalatestplus.play"  %% "scalatestplus-play" % "7.0.2"   % Test
)

CXF / version := CxfVersion

cxfDefaultArgs := Seq(
  "-p", "services"
)

cxfWSDLs := Seq(
  Wsdl("SunsetRiseService", (resourceDirectory in Compile).value / "HelloWorld.wsdl", Seq(
    "-wsdlLocation", "conf/HelloWorld.wsdl"
  ))
)

scalacOptions := Seq(
  "-deprecation"
)

lazy val root = Project("play-cxf-example", file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(CxfPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "hello"
  )
