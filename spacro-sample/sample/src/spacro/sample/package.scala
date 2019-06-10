package spacro

import jjm.DotKleisli

import io.circe.{Encoder, Decoder}

package object sample {
  // in shared code, you should define a Prompt and Response data type for each task.
  // They should be serializable and you should not expect to have to change these often;
  // all HIT data will be written with serialized versions of the prompts and responses.
  // A good rule of thumb is to keep the minimal necessary amount of information in them.
  case class SamplePrompt(id: Int)
  object SamplePrompt {
    import io.circe.generic.semiauto._
    implicit val samplePromptDecoder: Decoder[SamplePrompt] = deriveDecoder[SamplePrompt]
    implicit val samplePromptEncoder: Encoder[SamplePrompt] = deriveEncoder[SamplePrompt]
  }
  case class SampleResponse(isGood: Boolean)
  object SampleResponse {
    import io.circe.generic.semiauto._
    implicit val sampleResponseDecoder: Decoder[SampleResponse] = deriveDecoder[SampleResponse]
    implicit val sampleResponseEncoder: Encoder[SampleResponse] = deriveEncoder[SampleResponse]
  }

  // you must define a task key (string) for every task, which is unique to that task.
  // this will be used as a URL parameter to access the right client code, websocket flow, etc.
  // when interfacing between the client and server.
  val sampleTaskKey = "sample"

  // You then may define your API datatypes for the ajax and websocket APIs (if necessary).

  case class SampleAjaxResponse(sentence: String)
  object SampleAjaxResponse {
    import io.circe.generic.semiauto._
    implicit val sampleAjaxResponseDecoder: Decoder[SampleAjaxResponse] = deriveDecoder[SampleAjaxResponse]
    implicit val sampleAjaxResponseEncoder: Encoder[SampleAjaxResponse] = deriveEncoder[SampleAjaxResponse]
  }

  case class SampleAjaxRequest(prompt: SamplePrompt) {
    type Out = SampleAjaxResponse
  }

  object SampleAjaxRequest {
    import spacro.tasks._
    import io.circe.generic.semiauto._

    implicit val sampleAjaxRequestDecoder: Decoder[SampleAjaxRequest] = deriveDecoder[SampleAjaxRequest]
    implicit val sampleAjaxRequestEncoder: Encoder[SampleAjaxRequest] = deriveEncoder[SampleAjaxRequest]

    implicit val sampleAjaxRequestDotDecoder = new DotKleisli[Decoder, SampleAjaxRequest] {
      def apply(request: SampleAjaxRequest) = implicitly[Decoder[request.Out]]
    }
    implicit val sampleAjaxRequestDotEncoder = new DotKleisli[Encoder, SampleAjaxRequest] {
      def apply(request: SampleAjaxRequest) = implicitly[Encoder[request.Out]]
    }
  }
}
