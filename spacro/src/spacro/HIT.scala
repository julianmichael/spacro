package spacro

import io.circe.{Encoder, Decoder}

/** Represents a HIT that has already been uploaded to MTurk.
  * It may not still be on MTurk, but it should still be saved to disk.
  *
  * Parameterized over a desired data representation for "prompts",
  * which are transformed by a TaskSpecification into questions for the MTurk interface.
  *
  * @tparam Prompt the data representation of a question
  * @param hitType the HIT Type ID for this HIT
  * @param hitId the unique ID assigned to this HIT by MTurk
  * @param prompt the data used to create the question shown to workers
  * @param creationTime the time (millis from epoch) that the HIT was created
  */
case class HIT[Prompt](hitTypeId: String, hitId: String, prompt: Prompt, creationTime: Long)
object HIT {
  import io.circe.generic.semiauto._
  implicit def hitDecoder[A: Decoder] = deriveDecoder[HIT[A]]
  implicit def hitEncoder[A: Encoder] = deriveEncoder[HIT[A]]
}
