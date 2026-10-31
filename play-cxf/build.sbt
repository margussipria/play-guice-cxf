def playVersionSuffix: String = {
  val versions = PlayVersion.split('.')
  require(versions.length >= 2)
  versions.take(2).mkString
}

val CxfVersion = "3.4.3"
val PlayVersion = play.core.PlayVersion.current

enablePlugins(JacocoPlugin)

def module(id: String, base: java.io.File): Project = {
  Project(id, base)
    .settings(
      homepage := Some(new URL("https://github.com/margussipria/play-guice-cxf")),
      licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),

      organization := "eu.sipria.play",

      version := "1.9.0",

      scalaVersion := "2.13.6",
      crossScalaVersions := Seq("2.12.14", "2.13.6"),

      scalacOptions ++= Seq(
        "-deprecation"
      ),

      Test / testOptions ++= Seq(
        Tests.Argument("-oDF"),
        Tests.Argument(TestFrameworks.ScalaTest, "-u", "%s" format ((Test / target).value / "test-reports"))
      ),

      Test / fork := true
    )
}

val guiceCore = module("guice-cxf-core", file("guice-cxf-core"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.4",

      "org.apache.cxf" % "cxf-core"               % CxfVersion,
      "org.apache.cxf" % "cxf-rt-frontend-jaxws"  % CxfVersion  % Provided,

      "com.google.inject" % "guice"               % "4.2.3",

      "org.playframework" %% "play"               % PlayVersion % Optional,
    )
  )

val guiceClient = module("guice-cxf-client", file("guice-cxf-client"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.cxf" % "cxf-rt-transports-http" % CxfVersion  % Provided,
      "org.apache.cxf" % "cxf-rt-frontend-jaxws"  % CxfVersion  % Provided,

      "com.typesafe" % "config"                   % "1.4.1"
    )
  )
  .dependsOn(guiceCore)
  .aggregate(guiceCore)

val guicePlayEndpoint = module("guice-cxf-endpoint-play", file("guice-cxf-endpoint-play"))
  .enablePlugins(CxfPlugin)
  .settings(

    libraryDependencies ++= Seq(
      "org.playframework" %% "play"                     % PlayVersion % Provided,
      "org.playframework" %% "play-guice"               % PlayVersion % Provided,
      "org.playframework" %% "play-logback"             % PlayVersion % Test,

      "org.apache.cxf" % "cxf-core"                     % CxfVersion,
      "org.apache.cxf" % "cxf-rt-frontend-jaxws"        % CxfVersion  % Provided,

      "org.playframework" %% "play"                     % PlayVersion % Test,
      "org.playframework" %% "play-guice"               % PlayVersion % Test,
      "org.playframework" %% "play-pekko-http-server"    % PlayVersion % Test,

      "org.apache.cxf" % "cxf-rt-transports-http"       % CxfVersion  % Test,

      "org.scalatest"           %% "scalatest"          % "3.1.4" % Test,
      "org.scalatestplus.play"  %% "scalatestplus-play" % "5.1.0" % Test
    ),

    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, major)) if major >= 13 =>
          Seq(
            "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0" % Test
          )
        case _ =>
          Seq.empty
      }
    },

    CXF / version := CxfVersion,

    Test / cxfDefaultArgs := Seq(
      "-exsh", "true",
      "-validate",
      "-wv", "1.1"
    ),

    Test / cxfWSDLs := Seq(
      Wsdl("DateAndTime", (Test / resourceDirectory).value / "wsdl/DateAndTime.wsdl", Seq(
        "-wsdlLocation", "classpath:wsdl/DateAndTime.wsdl"
      ))
    )
  )
  .dependsOn(guiceCore, guiceClient % "test->test")
  .aggregate(guiceCore)

val root = module("root", file("."))
  .settings(
    // for publishing "legacy" style named artifact (combining client + endpoint)
    name := s"play-guice-cxf_play$playVersionSuffix"
  )
  .dependsOn(guiceClient, guicePlayEndpoint)
  .aggregate(guiceClient, guicePlayEndpoint)
