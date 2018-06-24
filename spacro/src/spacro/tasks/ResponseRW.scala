package spacro.tasks

import upickle.default._

trait ResponseReader[Request <: { type Response }] {
  def getReader(request: Request): Reader[request.Response]
}

trait ResponseWriter[Request <: { type Response }] {
  def getWriter(request: Request): Writer[request.Response]
}

trait ResponseRW[
  Request <: { type Response }
] extends ResponseReader[Request] with ResponseWriter[Request]
