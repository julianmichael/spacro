package spacro.ui

import spacro.tasks._

import scalajs.js
import org.scalajs.dom
import org.scalajs.jquery.jQuery

import scala.concurrent.ExecutionContext.Implicits.global

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._

import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

import upickle.default._

import monocle._
import monocle.macros._
import japgolly.scalajs.react.MonocleReact._

import upickle.default._

import scala.concurrent.Future

// for one-time loading of content, e.g., via ajax
class AsyncContentComponent[Response] {

  sealed trait AsyncContentState
  case object Loading extends AsyncContentState
  case class Loaded(content: Response) extends AsyncContentState

  case class AsyncContentProps(
    getContent: () => Future[Response],
    willLoad: (Response => Callback) = (_ => Callback.empty),
    didLoad: (Response => Callback) = (_ => Callback.empty),
    render: (AsyncContentState => VdomElement)
  )

  class AsyncContentBackend(scope: BackendScope[AsyncContentProps, AsyncContentState]) {

    def load(props: AsyncContentProps): Callback = Callback.future {
      props.getContent().map { response =>
        props.willLoad(response) >> scope.setState(Loaded(response)) >> props.didLoad(response)
      }
    }

    def render(props: AsyncContentProps, s: AsyncContentState) =
      props.render(s)
  }

  val AsyncContent = ScalaComponent
    .builder[AsyncContentProps]("Ajax Loadable")
    .initialState(Loading: AsyncContentState)
    .renderBackend[AsyncContentBackend]
    .componentDidMount(context => context.backend.load(context.props))
    .build
}
