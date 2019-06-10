package spacro.sample

import spacro.tasks._

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.jquery.jQuery

import scala.concurrent.ExecutionContext.Implicits.global

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._

import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

import monocle._
import monocle.macros._
import japgolly.scalajs.react.MonocleReact._

/** Sample client built using React. */
object Client extends TaskClient[SamplePrompt, SampleResponse, SampleAjaxRequest] {

  sealed trait State
  case object Loading extends State

  @Lenses case class Loaded(
    sentence: String,
    isGood: Boolean
  ) extends State

  object State {
    def loading[A]: Prism[State, Loading.type] = GenPrism[State, Loading.type]
    def loaded[A]: Prism[State, Loaded] = GenPrism[State, Loaded]
  }

  val isGoodLens = State.loaded composeLens Loaded.isGood

  class FullUIBackend(scope: BackendScope[Unit, State]) {

    def load: Callback = Callback.future {
      makeAjaxRequest(SampleAjaxRequest(prompt)).map {
        case SampleAjaxResponse(sentence) =>
          scope.modState(
            {
              case Loading          => Loaded(sentence, false)
              case l @ Loaded(_, _) => System.err.println("Data already loaded."); l
            }: PartialFunction[State, State]
          )
      }
    }

    def updateResponse: Callback = scope.state.map { st =>
      isGoodLens.getOption(st).map(SampleResponse.apply).foreach(setResponse)
    }

    def render(s: State) = {
      <.div(
        instructions,
        s match {
          case Loading =>
            <.p("""Connecting to server... (if this message does not disappear,
                 that means the server is down.
                 Try refreshing the page now or in a few minutes.)""")
          case Loaded(sentence, isGood) =>
            <.div(
              <.blockquote(sentence),
              <.p(
                <.label(
                  <.input(
                    ^.`type` := "checkbox",
                    ^.checked := isGood,
                    ^.onChange --> scope.modState(isGoodLens.modify(!_))
                  ),
                  "Yes, it is a good sentence."
                )
              ),
              <.p(
                <.input(
                  ^.`type` := "text",
                  ^.name := FieldLabels.feedbackLabel,
                  ^.placeholder := "Feedback? (Optional)",
                  ^.margin := "1px",
                  ^.padding := "1px",
                  ^.width := "484px"
                )
              ),
              <.input(
                ^.`type` := "submit",
                ^.disabled := isNotAssigned,
                ^.id := FieldLabels.submitButtonLabel,
                ^.value := "submit"
              )
            )
        }
      )
    }
  }

  val FullUI = ScalaComponent
    .builder[Unit]("Full UI")
    .initialState(Loading: State)
    .renderBackend[FullUIBackend]
    .componentDidMount(context => context.backend.load)
    .componentDidUpdate(context => context.backend.updateResponse)
    .build

  def main(): Unit = jQuery { () =>
    FullUI().renderIntoDOM(dom.document.getElementById(FieldLabels.rootClientDivLabel))
  }

  private[this] val instructions = <.div(
    <.h2("""Task Summary"""),
    <.p("""This is a sample task. Please indicate whether the given sentence is good.
           Examples of good sentences include:"""),
    <.ul(
      <.li("""Tell her that a double-income family is actually the true Igbo tradition
           because in pre-colonial times, mothers farmed and traded."""),
      <.li("""Chudi does not deserve any special gratitude or praise, nor do you -
           you both made the choice to bring a child into the world,
           and the responsibility for that child belongs equally to you both.""")
    ),
    <.p("""Examples of not-good sentences include:"""),
    <.ul(
      <.li("""So because of her unfounded concern over vote rigging, she committed voter fraud."""),
      <.li("""Comey told FBI employees he didn't want to "be misleading to the American people"
           by not supplementing the record of the investigation.""")
    ),
    <.hr(),
    <.p(s"""Please indicate whether the following sentence is good:""")
  )
}
