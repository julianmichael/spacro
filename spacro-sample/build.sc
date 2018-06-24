import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._
import ammonite.ops._

val thisScalaVersion = "2.11.8"
val thisScalaJSVersion = "0.6.19"

val macroParadiseVersion = "2.1.0"

val akkaActorVersion = "2.4.8"

val monocleVersion = "1.4.0"
val upickleVersion = "0.4.3"
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

trait SpacroSampleModule extends ScalaModule {

  def platformSegment: String

  def millSourcePath = build.millSourcePath / "sample"

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  def scalaVersion = thisScalaVersion

  // typelevel scala
  override def mapDependencies(d: coursier.Dependency) = {
    val artifacts = Set("scala-library", "scala-compiler", "scala-reflect")
    if(d.module.organization == "org.scala-lang" || artifacts(d.module.name)) {
      d.copy(module = d.module.copy(organization = "org.typelevel"), version = thisScalaVersion)
    } else d
  }

  def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature"
  )

  def ivyDeps = Agg(
    ivy"org.julianmichael::spacro::0.1.0-SNAPSHOT"
  )

  def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    ivy"org.scalamacros:::paradise:$macroParadiseVersion"
  )
}

object sample extends Module {
  object jvm extends SpacroSampleModule {

    def platformSegment = "jvm"

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.typesafe.akka::akka-actor:$akkaActorVersion",
      ivy"ch.qos.logback:logback-classic:1.2.3"
    )

    def resources = T.sources(
      millSourcePath / "resources",
      sample.js.fastOpt().path / RelPath.up,
      sample.js.aggregatedJSDeps() / RelPath.up
    )

  }
  object js extends SpacroSampleModule with ScalaJSModule with SimpleJSDeps {

    def scalaJSVersion = thisScalaJSVersion

    def platformSegment = "js"

    def mainClass = T(Some("spacro.sample.Dispatcher"))

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.lihaoyi::upickle::$upickleVersion",
      ivy"com.github.julien-truffaut::monocle-core::$monocleVersion",
      ivy"com.github.julien-truffaut::monocle-macro::$monocleVersion",
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
