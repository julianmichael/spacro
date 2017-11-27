package spacro.ui

import org.scalajs.dom.html

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._

import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

import cats.implicits._

// allows you to easily use refs inline in DOM creation, if, for example,
// you need to set the location of some element (e.g., a dropdown menu)
// on the basis of the location of another.
class ReferenceComponent[A <: vdom.TopNode] {

  case class ReferenceProps(
    referencedTag: VdomTagOf[A],
    render: (VdomTagOf[A], Option[A]) => VdomTag
  )

  type ReferenceState = Option[A]

  class ReferenceBackend(scope: BackendScope[ReferenceProps, ReferenceState]) {

    var reference: A = _

    def setReference: Callback = scope.setState(Option(reference))

    def render(props: ReferenceProps, state: Option[A]) =
      props.render(props.referencedTag.ref(reference = _), state)
  }

  val Reference = ScalaComponent.builder[ReferenceProps]("Reference")
    .initialState(None: ReferenceState)
    .renderBackend[ReferenceBackend]
    .componentDidMount(_.backend.setReference)
    .componentWillReceiveProps(_.backend.setReference)
    .build
}
