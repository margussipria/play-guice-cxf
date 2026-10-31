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
addSbtPlugin("org.playframework" % "sbt-plugin" % propOr("play.version", "3.0.5"))

addSbtPlugin("io.paymenthighway.sbt" % "sbt-cxf" % "1.7")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.2.2")

addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.1.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")
