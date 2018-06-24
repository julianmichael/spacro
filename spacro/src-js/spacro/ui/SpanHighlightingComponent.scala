package spacro.ui

import spacro.util.Span

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

class SpanHighlightingComponent[Index] {

  sealed trait SpanHighlightingStatus
  case object NoSpan extends SpanHighlightingStatus
  @Lenses case class Highlighting(index: Index, anchor: Int, endpoint: Int) extends SpanHighlightingStatus
  object SpanHighlightingStatus {
    val highlighting = GenPrism[SpanHighlightingStatus, Highlighting]
  }

  @Lenses case class SpanHighlightingState(
    spans: Map[Index, List[Span]],
    status: SpanHighlightingStatus)
  object SpanHighlightingState {
    def initial = SpanHighlightingState(Map.empty[Index, List[Span]].withDefaultValue(Nil), NoSpan)
  }

  case class SpanHighlightingContext(
    setSpan: Map[Index, List[Span]] => Callback,
    hover: Index => Int => Callback,
    touch: Index => Int => Callback,
    cancel: Callback)

  case class SpanHighlightingProps(
    isEnabled: Boolean,
    enableSpanOverlap: Boolean = false,
    update: SpanHighlightingState => Callback,
    render: (SpanHighlightingState, SpanHighlightingContext) => VdomElement)

  class SpanHighlightingBackend(scope: BackendScope[SpanHighlightingProps, SpanHighlightingState]) {

    def setSpan(spans: Map[Index, List[Span]]): Callback =
      scope.modState(SpanHighlightingState.spans.set(spans))

    // not sure why I can't just sequence the update after a modState call. but it seemed to get stuck on the stale state
    // for some reason during the update even though it was sequenced after.
    private[this] def modStateWithUpdate(f: SpanHighlightingState => SpanHighlightingState): Callback =
      scope.props >>= { props =>
        if(!props.isEnabled) Callback.empty else scope.state >>= { state =>
          val newState = f(state)
          if(newState != state) {
            scope.setState(newState) >> props.update(newState)
          } else Callback.empty
        }
      }

    def hover(props: SpanHighlightingProps)(index: Index)(endpoint: Int) = modStateWithUpdate {
      case SpanHighlightingState(spans, Highlighting(`index`, anchor, _)) =>
        val relevantSpans = if(props.enableSpanOverlap) {
          spans.get(index).getOrElse(Nil)
        } else spans.values.toList.flatten
        val range = if(anchor <= endpoint) (anchor to endpoint)
                    else (anchor to endpoint by -1)
        val newExtremum = range.takeWhile(i =>
          !relevantSpans.exists(_.contains(i))
        ).last
        SpanHighlightingState(spans, Highlighting(index, anchor, newExtremum))
      case x => x
    }

    def touch(props: SpanHighlightingProps)(index: Index)(wordIndex: Int): Callback = modStateWithUpdate {
      case SpanHighlightingState(spans, NoSpan) => spans(index).zipWithIndex.find(_._1.contains(wordIndex)).map(_._2) match {
        case None =>
          val relevantSpans = if(props.enableSpanOverlap) {
            spans.get(index).getOrElse(Nil)
          } else spans.values.toList.flatten
          if(relevantSpans.exists(_.contains(wordIndex))) SpanHighlightingState(spans, NoSpan) // do nothing
          else SpanHighlightingState(spans, Highlighting(index, wordIndex, wordIndex)) // start highlighting
        case Some(i) => // remove span
          SpanHighlightingState(spans.updated(index, spans(index).take(i) ++ spans(index).drop(i + 1)), NoSpan)
      }
      case SpanHighlightingState(spans, Highlighting(`index`, x, y)) =>
        val relevantSpans = if(props.enableSpanOverlap) {
          spans.get(index).getOrElse(Nil)
        } else spans.values.toList.flatten
        if(relevantSpans.exists(_.contains(wordIndex))) SpanHighlightingState(spans, Highlighting(index, x, y)) // do nothing
        else SpanHighlightingState(spans.updated(index, Span(x, y) :: spans(index)), NoSpan) // finish span
      case x => x
    }

    def cancel = modStateWithUpdate(SpanHighlightingState.status.set(NoSpan))

    def render(props: SpanHighlightingProps, state: SpanHighlightingState) =
      props.render(state, SpanHighlightingContext(setSpan, hover(props), touch(props), cancel))
  }

  val SpanHighlighting = ScalaComponent.builder[SpanHighlightingProps]("Span Highlighting")
    .initialState(SpanHighlightingState.initial)
    .renderBackend[SpanHighlightingBackend]
    .build
}
