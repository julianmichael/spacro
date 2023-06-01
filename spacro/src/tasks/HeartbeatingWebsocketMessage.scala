package spacro.tasks

import io.circe.{Encoder, Decoder}

sealed trait HeartbeatingWebSocketMessage[+A]
case object Heartbeat                      extends HeartbeatingWebSocketMessage[Nothing]
case class WebSocketMessage[A](content: A) extends HeartbeatingWebSocketMessage[A]
object HeartbeatingWebSocketMessage {
  import io.circe.generic.semiauto._
  implicit def heartbeatingWebSocketMessageDecoder[A: Decoder]
    : Decoder[HeartbeatingWebSocketMessage[A]] = deriveDecoder[HeartbeatingWebSocketMessage[A]]
  implicit def heartbeatingWebSocketMessageEncoder[A: Encoder]
    : Encoder[HeartbeatingWebSocketMessage[A]] = deriveEncoder[HeartbeatingWebSocketMessage[A]]
}
