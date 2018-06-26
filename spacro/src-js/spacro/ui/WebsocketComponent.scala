package spacro.ui

import spacro.tasks._

import scalajs.js
import org.scalajs.dom
import org.scalajs.dom.WebSocket
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

import upickle.default._

class WebsocketComponent[Request: Writer, Response: Reader] {

  sealed trait WebsocketState
  case object Connecting extends WebsocketState
  case class Connected(sendMessage: Request => Callback) extends WebsocketState

  case class WebsocketProps(
    websocketURI: String,
    onMessage: (Response => Callback) = (_ => Callback.empty),
    render: (WebsocketState => VdomElement)
  )

  class WebsocketBackend(scope: BackendScope[WebsocketProps, WebsocketState]) {

    def connect(props: WebsocketProps): Callback = scope.state map {
      case Connecting =>
        val socket = new WebSocket(props.websocketURI)
        socket.onopen = { (event: Event) =>
          scope
            .setState(
              Connected(
                (r: Request) =>
                  Callback(
                    socket.send(write[HeartbeatingWebSocketMessage[Request]](WebSocketMessage(r)))
                )
              )
            )
            .runNow
        }
        socket.onerror = { (event: Event) =>
          val msg = s"Connection failure. Error: $event"
          System.err.println(msg)
        }
        socket.onmessage = { (event: MessageEvent) =>
          val msg = event.data.toString
          read[HeartbeatingWebSocketMessage[Response]](msg) match {
            case Heartbeat                  => socket.send(msg)
            case WebSocketMessage(response) => props.onMessage(response).runNow
          }
        }
        socket.onclose = { (event: CloseEvent) =>
          val cleanly = if (event.wasClean) "cleanly" else "uncleanly"
          val msg =
            s"WebSocket connection closed $cleanly with code ${event.code}. reason: ${event.reason}"
          System.err.println(msg)
        }
      case Connected(_) =>
        System.err.println("Already connected.")
    }

    def render(props: WebsocketProps, s: WebsocketState) =
      props.render(s)
  }

  val Websocket = ScalaComponent
    .builder[WebsocketProps]("Websocket")
    .initialState(Connecting: WebsocketState)
    .renderBackend[WebsocketBackend]
    .componentDidMount(context => context.backend.connect(context.props))
    .build
}
