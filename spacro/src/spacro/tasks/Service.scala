package spacro.tasks

import io.circe.{Encoder, Decoder}
import io.circe.HCursor
import io.circe.Json

trait Service[Request <: { type Response }] {
  def processRequest(request: Request): request.Response
}

object Service {
  case class UnitRequest() { final type Response = Unit }
  case object UnitRequest {
    implicit val responseCodec = new ResponseCodec[UnitRequest] {
      override def getDecoder(request: UnitRequest) = implicitly[Decoder[Unit]]
      override def getEncoder(request: UnitRequest) = implicitly[Encoder[Unit]]
    }
    implicit val unitRequestDecoder: Decoder[UnitRequest] = Decoder.instance((_: HCursor) => Right(UnitRequest()))
    implicit val unitRequestEncoder: Encoder[UnitRequest] = Encoder.instance((_: UnitRequest) => Json.obj())
  }

  val unitServer = new Service[UnitRequest] {
    override def processRequest(request: UnitRequest) = ()
  }
}
