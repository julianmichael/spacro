import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._
import mill.scalalib.scalafmt._
import mill.util.Ctx
import mill.define.Cross
import coursier.maven.MavenRepository
import ammonite.ops._

val thisPublishVersion = "0.4.0"
val jjmVersion = "0.2.0"

val scalaVersions = List(
  "2.12.13",
  // "2.13.4" // TODO: need to update or remove akka dependency
)
val thisScalaJSVersion = "1.4.0"

val macroParadiseVersion = "2.1.1"
val kindProjectorVersion = "0.11.3"

val scalatagsVersion = "0.9.3"
val macmemoVersion = "0.4"
val scalaXmlVersion = "1.3.0"

val akkaActorVersion = "2.4.20"
val akkaHttpVersion = "10.0.10"
val akkaHttpCorsVersion = "0.2.2"
// val akkaActorVersion = "2.6.12"
// val akkaHttpVersion = "10.2.3"
// val akkaHttpCorsVersion = "1.1.1"
val scalaLoggingVersion = "3.9.2"
val awsJavaSdkVersion = "1.11.198"
val scalaArmVersion = "2.0"

val scalajsDomVersion = "1.1.0"
val scalajsJqueryVersion = "1.0.0"

trait SpacroModule extends CrossScalaModule with PublishModule with ScalafmtModule {

  def is212 = crossScalaVersion.startsWith("2.12")

  // val scalaArmVersion = if(is212) "2.0" else "2.1"

  def platformSegment: String

  def millSourcePath = build.millSourcePath / "spacro"

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
  )// ++ (if(is212) Seq() else Seq("-Ymacro-annotations")

  def ivyDeps = Agg(
    ivy"org.julianmichael::jjm-core::$jjmVersion",
    ivy"com.lihaoyi::scalatags::$scalatagsVersion",
  )

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    ivy"org.typelevel:::kind-projector:$kindProjectorVersion"
  ) ++ (
    if(is212) Agg(ivy"org.scalamacros:::paradise:$macroParadiseVersion") else {
      Agg()
    }
  )



  // publish settings

  def artifactName = "spacro"
  def publishVersion = thisPublishVersion
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "org.julianmichael",
    url = "https://github.com/julianmichael/spacro",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("julianmichael", "spacro"),
    developers = Seq(
      Developer("julianmichael", "Julian Michael","https://github.com/julianmichael")
    )
  )
}

object spacro extends Module {
  object jvm extends Cross[SpacroJvmModule](scalaVersions: _*)
  class SpacroJvmModule(val crossScalaVersion: String) extends SpacroModule {
    def platformSegment = "jvm"
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.softwaremill.macmemo::macros:$macmemoVersion",
      ivy"org.scala-lang.modules::scala-xml:$scalaXmlVersion",
      ivy"com.typesafe.akka::akka-actor:$akkaActorVersion",
      ivy"com.typesafe.akka::akka-http:$akkaHttpVersion",
      ivy"ch.megard::akka-http-cors:$akkaHttpCorsVersion",
      ivy"com.jsuereth::scala-arm:$scalaArmVersion",
      ivy"com.typesafe.scala-logging::scala-logging:$scalaLoggingVersion",
      ivy"com.amazonaws:aws-java-sdk:$awsJavaSdkVersion"
    )
  }

  object js extends Cross[SpacroJsModule](scalaVersions: _*)
  class SpacroJsModule(val crossScalaVersion: String) extends SpacroModule with ScalaJSModule {
    def scalaJSVersion = thisScalaJSVersion
    def platformSegment = "js"

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.scala-js::scalajs-dom::$scalajsDomVersion",
      ivy"be.doeraene::scalajs-jquery::$scalajsJqueryVersion",
    )
  }
}
