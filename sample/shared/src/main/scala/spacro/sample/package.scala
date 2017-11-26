package spacro

package object sample {
  // in shared code, you should define a Prompt and Response data type for each task.
  // They should be serializable and you should not expect to have to change these often;
  // all HIT data will be written with serialized versions of the prompts and responses.
  // A good rule of thumb is to keep the minimal necessary amount of information in them.
  case class SamplePrompt(id: Int)
  case class SampleResponse(isGood: Boolean)

  // you must define a task key (string) for every task, which is unique to that task.
  // this will be used as a URL parameter to access the right client code, websocket flow, etc.
  // when interfacing between the client and server.
  val sampleTaskKey = "sample"

  // You then may define your API datatypes for the ajax and websocket APIs (if necessary).

  case class SampleAjaxResponse(sentence: String)

  case class SampleAjaxRequest(prompt: SamplePrompt) {
    type Response = SampleAjaxResponse
  }
  object SampleAjaxRequest {
    import spacro.tasks._
    import upickle.default._

    implicit val responseRW = new ResponseRW[SampleAjaxRequest] {
      override def getReader(request: SampleAjaxRequest) = implicitly[Reader[request.Response]]
      override def getWriter(request: SampleAjaxRequest) = implicitly[Writer[request.Response]]
    }
  }
}
