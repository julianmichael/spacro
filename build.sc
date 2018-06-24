import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._
import mill.util.Ctx
import coursier.maven.MavenRepository
import ammonite.ops._

val thisScalaVersion = "2.11.8"
val thisScalaJSVersion = "0.6.19"

val macroParadiseVersion = "2.1.0"

val upickleVersion = "0.4.3"
val scalatagsVersion = "0.6.5"
val monocleVersion = "1.4.0"

val macmemoVersion = "0.4"
val scalaXmlVersion = "1.0.2"
val akkaActorVersion = "2.4.8"
val akkaHttpVersion = "10.0.10"
val akkaHttpCorsVersion = "0.2.2"
val scalaArmVersion = "2.0"
val scalaLoggingVersion = "3.5.0"
val awsJavaSdkVersion = "1.11.198"

val scalajsDomVersion = "0.9.0"
val scalajsJqueryVersion = "0.9.0"
val scalajsReactVersion = "1.1.0"
val scalajsScalaCSSVersion = "0.5.3"

trait SimpleJSDeps extends Module {
  def jsDeps = T { Agg.empty[String] }
  def downloadedJSDeps = T {
    for(url <- jsDeps()) yield {
      val filename = url.substring(url.lastIndexOf("/") + 1)
      %("curl", "-o", filename, url)(T.ctx().dest)
      T.ctx().dest / filename
    }
  }
  def aggregatedJSDeps = T {
    val targetPath = T.ctx().dest / "jsdeps.js"
    downloadedJSDeps().foreach { path =>
      write.append(targetPath, read!(path))
      write.append(targetPath, "\n")
    }
    targetPath
  }
}

trait SpacroModule extends ScalaModule with PublishModule {

  def platformSegment: String

  def millSourcePath = build.millSourcePath / "spacro"

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  def scalaVersion = thisScalaVersion

  // typelevel scala
  override def mapDependencies = T.task { (d: coursier.Dependency) =>
    val artifacts = Set("scala-library", "scala-compiler", "scala-reflect")
    if(d.module.organization == "org.scala-lang" || artifacts(d.module.name)) {
      d.copy(module = d.module.copy(organization = "org.typelevel"), version = scalaVersion())
    } else d
  }

  def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature"
  )

  def ivyDeps = Agg(
    ivy"com.lihaoyi::upickle::$upickleVersion",
    ivy"com.lihaoyi::scalatags::$scalatagsVersion",
    ivy"com.github.julien-truffaut::monocle-core::$monocleVersion",
    ivy"com.github.julien-truffaut::monocle-macro::$monocleVersion"
  )

  def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    ivy"org.scalamacros:::paradise:$macroParadiseVersion"
  )

  // publish settings

  def artifactName = "spacro"
  def publishVersion = "0.1.0-SNAPSHOT"
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
  object jvm extends SpacroModule {
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
  object js extends SpacroModule with ScalaJSModule with SimpleJSDeps {
    def scalaJSVersion = thisScalaJSVersion
    def platformSegment = "js"

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.scala-js::scalajs-dom::$scalajsDomVersion",
      ivy"be.doeraene::scalajs-jquery::$scalajsJqueryVersion",
      ivy"com.github.japgolly.scalajs-react::core::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-monocle::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-cats::$scalajsReactVersion",
      ivy"com.github.japgolly.scalacss::core::$scalajsScalaCSSVersion",
      ivy"com.github.japgolly.scalacss::ext-react::$scalajsScalaCSSVersion"
    )

    def jsDeps = Agg(
      "https://code.jquery.com/jquery-2.1.4.min.js",
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react.js",
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react-dom.js"
    )
  }
}
