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

    val reference = Ref[A]

    def setReference: Callback = scope.setState(scala.util.Try(reference.unsafeGet).toOption)

    def render(props: ReferenceProps, state: Option[A]) =
      props.render(props.referencedTag.withRef(reference), state)
  }

  val Reference = ScalaComponent
    .builder[ReferenceProps]("Reference")
    .initialState(None: ReferenceState)
    .renderBackend[ReferenceBackend]
    .componentDidMount(_.backend.setReference)
    .componentWillReceiveProps(_.backend.setReference)
    .build
}
