package spacro.tasks

sealed trait HeartbeatingWebSocketMessage[+A]
case object Heartbeat extends HeartbeatingWebSocketMessage[Nothing]
case class WebSocketMessage[A](content: A) extends HeartbeatingWebSocketMessage[A]
object HeartbeatingWebSocketMessage {
  import upickle.default._
  implicit def reader[A: Reader]: Reader[HeartbeatingWebSocketMessage[A]] =
    Reader.merge(macroR[Heartbeat.type], macroR[WebSocketMessage[A]])
  implicit def writer[A: Writer]: Writer[HeartbeatingWebSocketMessage[A]] =
    Writer.merge(macroW[Heartbeat.type], macroW[WebSocketMessage[A]])
}
