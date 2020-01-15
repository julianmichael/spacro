import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._
import mill.scalalib.scalafmt._
import mill.util.Ctx
import mill.define.Cross
import coursier.maven.MavenRepository
import ammonite.ops._

val thisPublishVersion = "0.3.1-SNAPSHOT"

val scalaVersions = List("2.12.8")
val thisScalaJSVersion = "0.6.27"

val macroParadiseVersion = "2.1.0"
val kindProjectorVersion = "0.9.4"

val jjmVersion = "0.1.0"

val scalatagsVersion = "0.6.5"
val macmemoVersion = "0.4"
val scalaXmlVersion = "1.1.0"
val akkaActorVersion = "2.4.20"
val akkaHttpVersion = "10.0.10"
val akkaHttpCorsVersion = "0.2.2"
val scalaArmVersion = "2.0"
val scalaLoggingVersion = "3.5.0"
val awsJavaSdkVersion = "1.11.198"

val scalajsDomVersion = "0.9.6"
val scalajsJqueryVersion = "0.9.3"

trait SpacroModule extends CrossScalaModule with PublishModule with ScalafmtModule {

  def platformSegment: String

  def millSourcePath = build.millSourcePath / "spacro"

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature"
  )

  def ivyDeps = Agg(
    ivy"org.julianmichael::jjm-core::$jjmVersion",
    ivy"com.lihaoyi::scalatags::$scalatagsVersion",
  )

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    ivy"org.scalamacros:::paradise:$macroParadiseVersion",
    ivy"org.spire-math::kind-projector:$kindProjectorVersion"
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
