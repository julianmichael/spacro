package spacro

import jjm.DotKleisli

import io.circe.{Encoder, Decoder}
import io.circe.generic.JsonCodec

package object sample {
  // in shared code, you should define a Prompt and Response data type for each task.
  // They should be serializable and you should not expect to have to change these often;
  // all HIT data will be written with serialized versions of the prompts and responses.
  // A good rule of thumb is to keep the minimal necessary amount of information in them.
  @JsonCodec case class SamplePrompt(id: Int)
  @JsonCodec case class SampleResponse(isGood: Boolean)

  // you must define a task key (string) for every task, which is unique to that task.
  // this will be used as a URL parameter to access the right client code, websocket flow, etc.
  // when interfacing between the client and server.
  val sampleTaskKey = "sample"

  // You then may define your API datatypes for the ajax and websocket APIs (if necessary).

  // Response sent to client from server
  @JsonCodec case class SampleAjaxResponse(sentence: String)

  // Request sent to server from client
  @JsonCodec case class SampleAjaxRequest(prompt: SamplePrompt) {
    // On this request type, we specify its expected response
    type Out = SampleAjaxResponse
  }

  object SampleAjaxRequest {
    import spacro.tasks._

    // These values provide a way to, given an AJAX request, encode/decode its response
    // to/from JSON. This is necessary for implementing the AJAX communication
    // between client and server.
    implicit val sampleAjaxRequestDotDecoder = new DotKleisli[Decoder, SampleAjaxRequest] {
      def apply(request: SampleAjaxRequest) = implicitly[Decoder[request.Out]]
    }
    implicit val sampleAjaxRequestDotEncoder = new DotKleisli[Encoder, SampleAjaxRequest] {
      def apply(request: SampleAjaxRequest) = implicitly[Encoder[request.Out]]
    }
  }
}
