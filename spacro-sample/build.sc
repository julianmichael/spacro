import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._
import mill.scalalib.scalafmt._
import os._

val thisScalaVersion = "2.12.13"
val thisScalaJSVersion = "1.4.0"

val spacroVersion = "0.4.0"

val macroParadiseVersion = "2.1.1"

// cats libs -- maintain versions matching up
val scalajsReactVersion = "1.7.7"
val scalacssVersion = "0.7.0"

val scalajsDomVersion = "1.1.0"
val scalajsJqueryVersion = "1.0.0"

val logbackVersion = "1.2.3"


// trait SimpleJSDeps extends Module {
//   def jsDeps = T { Agg.empty[String] }
//   def downloadedJSDeps = T {
//     for(url <- jsDeps()) yield {
//       val filename = url.substring(url.lastIndexOf("/") + 1)
//         %("curl", "-o", filename, url)(T.ctx().dest)
//       T.ctx().dest / filename
//     }
//   }
//   def aggregatedJSDeps = T {
//     val targetPath = T.ctx().dest / "jsdeps.js"
//     downloadedJSDeps().foreach { path =>
//       write.append(targetPath, read!(path))
//       write.append(targetPath, "\n")
//     }
//     targetPath
//   }
// }
// for some reason the $file import doesn't work anymore?
// import $file.`scripts-build`.SimpleJSDepsBuild, SimpleJSDepsBuild.SimpleJSDeps
trait SimpleJSDeps extends Module {
  def jsDeps = T {
    Agg.empty[String]
  }
  def downloadedJSDeps = T {
    for (url <- jsDeps())
      yield {
        val filename = url.substring(url.lastIndexOf("/") + 1)
        os.proc("curl", "-o", filename, url).call(cwd = T.ctx().dest)
        T.ctx().dest / filename
      }
  }
  def aggregatedJSDeps = T {
    val targetPath = T.ctx().dest / "jsdeps.js"
    os.write.append(targetPath, "")
    downloadedJSDeps().foreach { path =>
      os.write.append(targetPath, os.read(path))
      os.write.append(targetPath, "\n")
    }
    PathRef(targetPath)
  }
}


trait SpacroSampleModule extends ScalaModule with ScalafmtModule {

  def scalaVersion = thisScalaVersion

  def platformSegment: String

  def millSourcePath = build.millSourcePath / "sample"

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature"
  )

  // uncomment when depending on newer spacro snapshot
  // def repositories = super.repositories ++ Seq(
  //   coursier.maven.MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
  // )

  def ivyDeps = Agg(
    ivy"org.julianmichael::spacro::$spacroVersion"
  )

  def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    ivy"org.scalamacros:::paradise:$macroParadiseVersion"
  )
}

object sample extends Module {
  object jvm extends SpacroSampleModule {

    def platformSegment = "jvm"

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"ch.qos.logback:logback-classic:$logbackVersion"
    )

    def resources = T.sources(
      millSourcePath / "resources",
      sample.js.fastOpt().path / RelPath.up,
      sample.js.aggregatedJSDeps().path / RelPath.up
    )

  }
  object js extends SpacroSampleModule with ScalaJSModule with SimpleJSDeps {

    def scalaJSVersion = thisScalaJSVersion

    def platformSegment = "js"

    def mainClass = T(Some("spacro.sample.Dispatcher"))

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.scala-js::scalajs-dom::$scalajsDomVersion",
      ivy"be.doeraene::scalajs-jquery::$scalajsJqueryVersion",
      ivy"com.github.japgolly.scalajs-react::core::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-monocle::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-cats::$scalajsReactVersion",
      ivy"com.github.japgolly.scalacss::core::$scalacssVersion",
      ivy"com.github.japgolly.scalacss::ext-react::$scalacssVersion"
    )

    def jsDeps = Agg(
      "https://code.jquery.com/jquery-2.1.4.min.js",
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react.js",
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react-dom.js"
    )
  }
}
