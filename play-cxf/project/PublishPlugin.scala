import sbt.Keys.*
import sbt.*

object PublishPlugin extends sbt.AutoPlugin {
  override def requires: Plugins = empty
  override def trigger: PluginTrigger = allRequirements
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    publishTo := {
      if (isSnapshot.value) {
        Some("central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/")
      } else {
        localStaging.value
      }
    },

    publishMavenStyle := true,

    Test / publishArtifact := false,

    pomIncludeRepository := { _ => false }
  )
}
