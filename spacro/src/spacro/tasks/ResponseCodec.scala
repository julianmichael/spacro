package spacro.tasks

import io.circe.{Encoder, Decoder}

trait ResponseDecoder[Request <: { type Response }] {
  def getDecoder(request: Request): Decoder[request.Response]
}

trait ResponseEncoder[Request <: { type Response }] {
  def getEncoder(request: Request): Encoder[request.Response]
}

trait ResponseCodec[
  Request <: { type Response }
] extends ResponseDecoder[Request]
    with ResponseEncoder[Request]
