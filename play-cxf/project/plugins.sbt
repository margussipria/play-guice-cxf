// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.typesafeRepo("releases")
)

def propOr(name: String, value: String): String = {
  (sys.props get name) orElse (sys.env get name) getOrElse value
}

// Use the Play sbt plugin for Play projects
addSbtPlugin("org.playframework" % "sbt-plugin" % propOr("play.version", "3.0.12"))

addSbtPlugin("io.paymenthighway.sbt" % "sbt-cxf" % "1.7")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")

addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.7.0")

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.2")
