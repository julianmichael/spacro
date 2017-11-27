package spacro.ui

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw._
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

class HighlightingComponent[Index] {

  sealed trait HighlightingStatus
  case object DoNothing extends HighlightingStatus
  case object Highlight extends HighlightingStatus
  case object Erase extends HighlightingStatus

  @Lenses case class HighlightingState(
    span: Set[Index],
    status: HighlightingStatus)
  object HighlightingState {
    def init(is: Set[Index]) = HighlightingState(is, DoNothing)
  }

  case class HighlightingContext(
    setSpan: Set[Index] => Callback,
    startHighlight: Callback,
    startErase: Callback,
    stop: Callback,
    touchElement: Index => Callback)

  case class HighlightingProps(
    isEnabled: Boolean,
    preUpdate: HighlightingState => HighlightingState = identity,
    update: HighlightingState => Callback,
    initial: Set[Index] = Set.empty[Index],
    render: (HighlightingState, HighlightingContext) => VdomElement)

  class HighlightingBackend(scope: BackendScope[HighlightingProps, HighlightingState]) {

    def setSpan(span: Set[Index]): Callback = for {
      p <- scope.props
      s <- scope.state
      _ <- scope.setState(p.preUpdate(s.copy(span = span)))
      _ <- scope.state >>= p.update
    } yield ()

    def touchElement(props: HighlightingProps)(index: Index): Callback = scope.modState {
      case s @ HighlightingState(span, status) =>
        if(!props.isEnabled) s
        else props.preUpdate(
          status match {
            case DoNothing => s
            case Highlight => HighlightingState(span + index, status)
            case Erase => HighlightingState(span - index, status)
          }
        )
    } >> scope.props >>= (p => scope.state >>= p.update)

    def setHighlightingStatus(s: HighlightingStatus): Callback = for {
      p <- scope.props
      _ <- scope.modState(HighlightingState.status.set(s) andThen p.preUpdate)
      _ <- scope.state >>= p.update
    } yield ()

    val startHighlight: Callback = setHighlightingStatus(Highlight)
    val startErase: Callback = setHighlightingStatus(Erase)
    val stop: Callback = setHighlightingStatus(DoNothing)

    def render(props: HighlightingProps, state: HighlightingState) =
      props.render(state, HighlightingContext(setSpan, startHighlight, startErase, stop, touchElement(props)))
  }

  val Highlighting = ScalaComponent.builder[HighlightingProps]("Highlighting")
    .initialStateFromProps(props => HighlightingState.init(props.initial))
    .renderBackend[HighlightingBackend]
    .build
}
