package spacro.tasks

import upickle.default._

trait Service[Request <: { type Response }] {
  def processRequest(request: Request): request.Response
}
object Service {
  case class UnitRequest() { final type Response = Unit }
  case object UnitRequest {
    implicit val responseRW = new ResponseRW[UnitRequest] {
      override def getReader(request: UnitRequest) = implicitly[Reader[Unit]]
      override def getWriter(request: UnitRequest) = implicitly[Writer[Unit]]
    }
  }

  val unitServer = new Service[UnitRequest] {
    override def processRequest(request: UnitRequest) = ()
  }
}
