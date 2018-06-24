package spacro.ui

import cats._

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
import monocle.std.option
import japgolly.scalajs.react.MonocleReact._

import cats.data.NonEmptyList

class LocalStateComponent[A] {
  type LocalStateState = A
  type LocalStateContext = A => Callback
  case class LocalStateProps(
    initialValue: A,
    render: (LocalStateState, LocalStateContext) => VdomElement)
  object LocalProps {
    def apply(
      render: (LocalStateState, LocalStateContext) => VdomElement)(
      implicit m: Monoid[A]
    ): LocalStateProps =
      LocalStateProps(m.empty, render)
  }

  class LocalStateBackend(scope: BackendScope[LocalStateProps, LocalStateState]) {
    def set(a: A): Callback = scope.setState(a)
    val context = set _
    def render(props: LocalStateProps, state: LocalStateState) = props.render(state, context)
  }

  val LocalState = ScalaComponent.builder[LocalStateProps]("Local")
    .initialStateFromProps(_.initialValue)
    .renderBackend[LocalStateBackend]
    .build
}
